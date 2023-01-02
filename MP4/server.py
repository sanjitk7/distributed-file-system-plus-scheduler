import socket
import threading
import time
import json

# from macpath import join

# configuration
INTRODUCER_HOST = socket.gethostbyname('fa22-cs425-6501.cs.illinois.edu')
MACHINE_NUM = int(socket.gethostname()[13:15])
LOG_FILEPATH = f'machine.{MACHINE_NUM}.log'
PING_PORT = 20240
MEMBERSHIP_PORT = 20241
PING_INTERVAL = 2.5
PING_TIMEOUT = 2


def encode_command(id, command):
    d = {'id': id, 'command': command}
    return json.dumps(d).encode()

def decode_command(msg):
    decoded = json.loads(msg.decode())
    return decoded['id'], decoded['command']

def encode_ping_ack(id, type):
    d = {'id': id, 'type': type}
    return json.dumps(d).encode()

def decode_ping_ack(msg):
    decoded = json.loads(msg.decode())
    return decoded['id'], decoded['type']

class Node:
    def __init__(self, ping_port: int, membership_port: int, ping_timeout: int, ping_interval: int, log_filepath: str):
        self.commands = set()  # taking record of the command_id of the commands that's been executed
        self.command_count = 1  # used for generating command_id

        self.membership_list = []

        self.log_filepath = log_filepath
        # addresses
        self.host = socket.gethostbyname(socket.gethostname())

        self.ping_port = ping_port
        self.membership_port = membership_port

        self.ping_count = 0  # used for record the round of ping and generate ping_id
        self.ack_cache = {}  # store the returned ack
        self.ack_cache_lock = threading.Lock()

        # settings
        # self.isIntroducer = isIntroducer
        self.ping_timeout = ping_timeout
        self.ping_interval = ping_interval
        self.isIntroducer = (self.host == INTRODUCER_HOST)

        # locks
        self.log_lock = threading.Lock()
        self.membership_lock = threading.Lock()
        self.command_lock = threading.Lock()

        self.debug = False
        self.mode_lock = threading.Lock()
        self.thread_workers = []

        self.bytes_lock = threading.Lock()



    def transmit_message(self, encoded_command, port):
        """
        transmit message to 2 predecessors and 2 successors
        :param encoded_command: the command to be sent
        :param port: the port used to transmit the command
        :return: a list of the membership ids that's been transmitted to
        """
        self.membership_lock.acquire()
        if self.id not in self.membership_list:
            self.membership_lock.release()
            return []
        index = self.membership_list.index(self.id)
        res = set()
        for pos in set([((index + i) % len(self.membership_list)) for i in [-2, -1, 1, 2]]):
            if pos != index:
                res.add(self.membership_list[pos])
                host = self.membership_list[pos].split(':')[0]
                with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
                    s.sendto(encoded_command, (host, port))
                    self.bytes_lock.acquire()
                    self.bytes += len(encoded_command)
                    self.bytes_lock.release()
        self.membership_lock.release()
        return res

    def membership_thread(self):
        """
        meant to be a thread that's handling all the membership updates
        :return: None
        """
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            s.bind((self.host, self.membership_port))
            while True:
                encoded_command, addr = s.recvfrom(4096)
                command_id, command = decode_command(encoded_command)
                # check whether command has been executed
                self.command_lock.acquire()
                if command_id in self.commands:
                    self.command_lock.release()
                    continue
                self.commands.add(command_id)
                self.command_lock.release()
                command_type, command_content = command['type'], command['content']
                leave_type = 0
                if command_type == "join":
                    # if command_type is join, get new membership list
                    new_membership_list = self.update_membership_list(1, command_content)
                    # send back new membership list to the newly joining node
                    json_string = json.dumps(new_membership_list)
                    s.sendto(json_string.encode(), addr)
                    # use an iteration to send to all node to inform them to add the newly joining node
                    command = {'type': 'add', 'content': command_content}
                    encoded_command_tosend = encode_command(command_id, command)
                    self.transmit_message(encoded_command_tosend, self.membership_port)
                    # update old membership list
                    self.membership_list = new_membership_list
                    # log join
                    self.mode_lock.acquire()
                    if self.debug:
                        self.mode_lock.release()
                        print('node ', command_content, ' join')
                    else:
                        self.mode_lock.release()
                    continue
                action = 1 if command_type == "add" else 0
                # if action == 1, log add, if action == 0 log delete                
                new_membership_list = self.update_membership_list(action, command_content)
                # use old membership list and membership_port to pass on the message if the command type is add or remove
                self.transmit_message(encoded_command, self.membership_port)
                self.mode_lock.acquire()
                if self.debug:
                    self.mode_lock.release()
                    print(command_type, ' ', command_content)
                else:
                    self.mode_lock.release()
                self.membership_lock.acquire()
                self.membership_list = new_membership_list
                self.membership_lock.release()
                if command_type == "leave":
                    self.log_generate(command_content, 'leave', self.membership_list)


    def ping_disseminate_thread(self):
        """
        a thread to disseminate ping message every self.ping_interval seconds.
        :return: None
        """
        count = 0
        while True:
            time.sleep(self.ping_interval)  # ping period
            self.mode_lock.acquire()
            if self.debug:
                self.mode_lock.release()
                print('ping round: ', count)
            else:
                self.mode_lock.release()

            count += 1
            t = threading.Thread(target=self.ping_thread)
            t.start()


    def ping_ack_receive(self):
        """
        a thread that's running to receive ping/ack
        :return: None
        """
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            s.bind((self.host, self.ping_port))
            while True:
                msg, addr = s.recvfrom(4096)
                id, type = decode_ping_ack(msg)
                self.mode_lock.acquire()
                if self.debug:
                    self.mode_lock.release()
                    print('receive: ', type, ' from ', addr)
                else:
                    self.mode_lock.release()
                if type['type'] == 'ping':
                    self.handle_ping(id)
                elif type['type'] == 'ack':
                    self.handle_ack(id, type)

    def ping_thread(self):
        # generate ping_id
        ping_id = self.id + '-' + str(self.ping_count)
        self.ping_count += 1
        # encode ping
        encoded_ping = encode_ping_ack(ping_id, {'type': 'ping', 'member_id': self.id})
        # initialize cache for the ping_id
        self.ack_cache_lock.acquire()
        self.ack_cache[ping_id] = set()
        self.ack_cache_lock.release()
        # transmit ping, get the ids of the member that's been pinged
        for i in range(4):
            ids = self.transmit_message(encoded_ping, self.ping_port)
        # wait for some time to receive ack
        time.sleep(self.ping_timeout)
        # get the received ack
        self.ack_cache_lock.acquire()
        ack_cache_for_this_ping_id = self.ack_cache[ping_id]
        self.ack_cache.pop(ping_id)
        self.ack_cache_lock.release()
        # check all the acks that's received
        for id in ids:
            if id not in ack_cache_for_this_ping_id: # if an ack is not received
                new_membership_list = self.update_membership_list(0, id) # get updated membership_list by deleting the member that's missing

                # assign unique command id
                self.command_lock.acquire()
                command_id = self.id + '-' + str(self.command_count)
                self.command_count += 1
                self.commands.add(command_id)
                self.command_lock.release()

                # encode command
                command_content = {'type': 'failed', 'content': id}
                encoded_command_tosend = encode_command(command_id, command_content)
                self.mode_lock.acquire()
                if self.debug:
                    self.mode_lock.release()
                    print("haven't receiving ack from ", id)
                    print('sending command ', command_content) # print statement for debugging
                else:
                    self.mode_lock.release()

                # transmit message, using old membership_list
                self.transmit_message(encoded_command_tosend, self.membership_port)

                # update membership list
                self.membership_lock.acquire()
                self.membership_list = new_membership_list
                self.membership_lock.release()
                self.log_generate(id, 'failed', self.membership_list)

    def handle_ping(self, id: str):
        """
        function for handle received ping
        :param id: a string, the ping id.
        :return: None
        """
        host = id.split('-')[0].split(':')[0]
        encoded_ack = encode_ping_ack(id, {'type': 'ack', 'member_id': self.id})
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            self.mode_lock.acquire()
            if self.debug:
                self.mode_lock.release()
                print('sending ack to: ', (host, self.ping_port)) # print statement for debugging
            else:
                self.mode_lock.release()
            s.sendto(encoded_ack, (host, self.ping_port))

    def handle_ack(self, id: str, type: dict):
        """
        function for handling the received ack
        :param id: ping_id for the ack
        :param type: the content for the ack
        :return: None
        """
        member_id = type['member_id']
        self.ack_cache_lock.acquire()
        self.ack_cache[id].add(member_id)
        self.ack_cache_lock.release()

    def update_membership_list(self, action: int, member_id: str):
        """
        return an updated copy of the old membership list, won't modify the old membership list.
        :param action: 1 for add, 0 for delete
        :param member_id: member id to be added/removed
        :return: None
        """
        self.membership_lock.acquire()
        membership_list = self.membership_list.copy()
        self.membership_lock.release()
        if action:
            membership_list.append(member_id)
            self.log_generate(member_id, 'join', membership_list)
        else:
            if member_id in membership_list:
                membership_list.remove(member_id)
        return membership_list

    def join(self):
        """
        function for handling node register when it first start
        :return: None
        """
        self.id = self.host + ':' + str(time.time())
        self.start_time = time.time()
        self.bytes = 0
        command_id = self.id + '-' + str(self.command_count)
        command_content = {'type': 'join', 'content': self.id}
        self.command_count += 1
        self.commands.add(command_id)  # add the command_id to commands table
        if self.isIntroducer:
            self.membership_list = [self.id]
        else:
            with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
                # get unique command id
                # encode command
                encoded_command_tosend = encode_command(command_id, command_content)
                s.sendto(encoded_command_tosend, (INTRODUCER_HOST, self.membership_port))
                self.bytes += len(encoded_command_tosend)

                json_string, addr = s.recvfrom(4096) # receive encoded membership_list
                new_membership_list = json.loads(json_string) # decode

                self.bytes += len(json_string)
                self.membership_list = new_membership_list # update

        t1 = threading.Thread(target=self.membership_thread)
        t2 = threading.Thread(target=self.ping_disseminate_thread)
        t3 = threading.Thread(target=self.ping_ack_receive)

        t1.start()
        t2.start()
        t3.start()

    def run(self):
        self.join()
        self.log_generate(self.id, 'join', self.membership_list)
        while True:
            command = input('> ')
            if command == 'leave':
                # create command id
                self.command_lock.acquire()
                command_id = self.id + '-' + str(self.command_count)
                self.command_count += 1
                self.commands.add(command_id)
                self.command_lock.release()

                # encode command
                command_content = {'type': 'leave', 'content': self.id}
                encoded_command_tosend = encode_command(command_id, command_content)
                self.transmit_message(encoded_command_tosend, self.membership_port)
                self.membership_lock.acquire()
                self.membership_list = []
                self.membership_lock.release()
                self.log_generate(command_id[:-2], 'leave', self.membership_list)
                print('Leaving...')
                break

            elif command == 'list_mem':
                print('isIntroducer: ', self.isIntroducer)
                self.membership_lock.acquire()
                print(f'there are {len(self.membership_list)} member in membership_list: ')
                for member in self.membership_list:
                    print('    ', member)
                self.membership_lock.release()
                self.command_lock.acquire()
                print(f'{len(self.commands)} commands have been executed')
                self.command_lock.release()
            elif command == 'debug':
                if self.debug:
                    print('debug mode off')
                else:
                    print('debug mode on')
                    self.log_generate(self.id, command, self.membership_list)
                self.debug = not self.debug
            elif command == 'list_self':
                print(self.id)
            elif command == 'join':
                self.join()
                self.log_generate(self.id, 'join', self.membership_list)
            elif command == 'bandwidth':
                self.bytes_lock.acquire()
                b = self.bytes
                self.bytes_lock.release()
                print(b / (time.time()-self.start_time))
            elif command == 'reset_time':
                self.bytes_lock.acquire()
                self.bytes = 0
                self.start_time = time.time()
                self.bytes_lock.release()

    def log_generate(self, node_id: str, action: str, membership_list: list):
        """
        used for generating log file when one of the following situation occurs:
            node failure, node leave, node join

        :param node_id: the node id to be logged.
        :param action: either 'join', 'leave' or 'fail'
        :param filepath: the file path of the log file
        :return: None
        """
        self.log_lock.acquire()
        t = time.strftime("%Y-%m-%d %H:%M:%S", time.localtime())
        with open(LOG_FILEPATH, 'a') as f:
            f.write(f'node ID: {node_id}, change type:{action}, log time: {t}, alive machine: {len(membership_list)} \n')
            f.close()
        self.log_lock.release()

if __name__ == '__main__':
    server = Node(PING_PORT, MEMBERSHIP_PORT, PING_TIMEOUT, PING_INTERVAL, LOG_FILEPATH)
    server.run()
