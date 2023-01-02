import copy
import os
import socket
from collections import defaultdict
import threading
import json
import time

from Schedule import Query, Schedule

MASTER_PORT = 20086
FILE_PORT = 10086
GET_ADDR_PORT = 10087

ACK_PORT = 10088 # Only meant for job acks

class FMaster:
    def __init__(self, master_port: int, file_port: int):
        self.master_port = master_port
        self.ntf_lock = threading.Lock()
        self.node_to_file = {}
        self.ftn_lock = threading.Lock()
        self.file_to_node = {}
        self.host = socket.gethostbyname(socket.gethostname())
        self.file_port = file_port

        # MP4 specific code
        self.schedule_lock = threading.Lock()
        self.schedule = Schedule()
        self.is_query_running = False
        self.ntq_lock = threading.Lock()
        self.node_to_query = {}
        self.job1_node_pool_lock = threading.Lock()
        self.job1_node_pool = set()
        self.job2_node_pool_lock = threading.Lock()
        self.job2_node_pool = set()
        self.job1_completed_lock = threading.Lock()
        self.job1_completed = Schedule()
        self.job2_completed_lock = threading.Lock()
        self.job2_completed = Schedule()

    def repair(self, ip):
        start_time = time.time()
        self.ntf_lock.acquire()
        if ip in self.node_to_file:
            sdfsfileids = self.node_to_file.pop(ip)
        else:
            self.ntf_lock.release()
            return
        self.ntf_lock.release()
        for sdfsfileid in sdfsfileids:
            self.ftn_lock.acquire()
            if sdfsfileid in self.file_to_node:
                ips = list(self.file_to_node[sdfsfileid])
                self.file_to_node[sdfsfileid].remove(ip)
            else:
                self.ftn_lock.release()
                continue
            for ipaddr in ips:
                res = self.issue_repair(sdfsfileid, ipaddr, ips)
                if res == '1':
                    break
            self.ftn_lock.release()
        end_time = time.time()
        print('replication for node: ', ip, " complete")
        if len(sdfsfileids) > 0:
            print('files re-replicated: ')
            for sdfsfileid in sdfsfileids:
                print('  ', sdfsfileid)
        print('time consumed: ', end_time-start_time)
        print('current time: ', time.time())


    def issue_repair(self, sdfsfileid, ip, ips):
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            try:
                s.connect((ip, self.file_port))
            except socket.error as e:
                return
            s.send(b'repair')
            s.recv(1) # for ack
            s.send(json.dumps({'sdfsfileid': sdfsfileid, 'ips': ips}).encode())
            res = s.recv(1).decode()
            return res

    def background(self):
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            s.bind((self.host, self.master_port))
            while True:
                encoded_command, addr = s.recvfrom(4096)
                decoded_command = json.loads(encoded_command.decode())
                command_type = decoded_command['command_type']
                if command_type == 'fail_notice':
                    fail_ip = decoded_command['command_content']
                    for ip in fail_ip:
                        t = threading.Thread(target=self.repair, args=(ip, ))
                        t.start()
                    
                    # TODO: Validate failure scenario
                    for ip in fail_ip:
                        # First, remove node from available worker nodes list
                        query = ""
                        self.ntq_lock.acquire()
                        if ip in self.node_to_query:
                            query = self.node_to_query[ip]
                            del self.node_to_query[ip]
                        self.ntq_lock.release()

                        # Next, mark the job as pending so that scheduler will assign it to another vm
                        self.schedule_lock.acquire()
                        self.schedule.update_assigned_vm(query, "")
                        self.schedule.update_status(query, "")
                        self.schedule_lock.release()
                        
                        print(f"End of failure scenario, end = {time.time()}")

                elif command_type == 'put_notice':
                    sdfsfileid, ip = decoded_command['command_content']
                    self.ntf_lock.acquire()
                    self.node_to_file.setdefault(ip, set())
                    self.node_to_file[ip].add(sdfsfileid)
                    self.ntf_lock.release()

                    self.ftn_lock.acquire()
                    self.file_to_node.setdefault(sdfsfileid, set())
                    self.file_to_node[sdfsfileid].add(ip)
                    self.ftn_lock.release()

                    self.ntq_lock.acquire()
                    self.node_to_query[ip] = ""
                    self.ntq_lock.release()
               
                elif command_type == 'load_model_notice':
                    model_type = decoded_command['command_content']
                    path = "./sdfs/model_1/"

                    if model_type == '2':
                        path = "./sdfs/model_2/"

                    list_of_files_for_model = os.listdir(path)

                    self.schedule_lock.acquire()
                    for file in list_of_files_for_model:
                        if file == ".gitkeep":
                            continue
                        self.schedule.add_new_query(model_type, file)
                    self.schedule_lock.release()
                    
                elif command_type == 'delete_notice':
                    sdfsfileid, ip = decoded_command['command_content']
                    self.ntf_lock.acquire()
                    if ip in self.node_to_file:
                        self.node_to_file[ip].remove(sdfsfileid)
                    self.ntf_lock.release()

                    self.ftn_lock.acquire()
                    if sdfsfileid in self.file_to_node:
                        self.file_to_node[sdfsfileid].remove(ip)
                    self.ftn_lock.release()

    def query_ack_monitor(self):
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.bind((self.host, ACK_PORT))
            s.listen()
            while True:
                conn, addr = s.accept()
                encoded_command = conn.recv(4096)
                decoded_command = json.loads(encoded_command.decode())
                file_path, model_type, result = decoded_command['command_content']
                
                # Once an ack is received, the corresponding query entry needs to be updated
                elapsed_time = 0
                
                self.schedule_lock.acquire()
                start_time = self.schedule.master_schedule[file_path].start_time
                assigned_vm = self.schedule.master_schedule[file_path].assigned_vm
                del self.schedule.master_schedule[file_path]
                self.schedule_lock.release()

                if model_type == '1':
                    self.job1_completed_lock.acquire()
                    self.job1_completed.add_new_query(model_type, file_path)
                    self.job1_completed.update_assigned_vm(file_path, assigned_vm)
                    self.job1_completed.update_start_time(file_path, start_time)
                    self.job1_completed.update_stop_time(file_path, time.time())
                    self.job1_completed.update_status(file_path, "completed")
                    self.job1_completed.update_result(file_path, result)
                    self.job1_completed.update_elapsed_time(file_path)
                    elapsed_time = self.job1_completed.master_schedule[file_path].elapsed_time
                    self.job1_completed_lock.release()

                else:
                    self.job2_completed_lock.acquire()
                    self.job2_completed.add_new_query(model_type, file_path)
                    self.job2_completed.update_assigned_vm(file_path, assigned_vm)
                    self.job2_completed.update_start_time(file_path, start_time)
                    self.job2_completed.update_stop_time(file_path, time.time())
                    self.job2_completed.update_status(file_path, "completed")
                    self.job2_completed.update_result(file_path, result)
                    self.job2_completed.update_elapsed_time(file_path)
                    elapsed_time = self.job2_completed.master_schedule[file_path].elapsed_time
                    self.job2_completed_lock.release()
                
                # TODO: validate
                # VM must be marked as empty to pick up new jobs
                self.ntq_lock.acquire()
                self.node_to_query[addr[0]] = ""
                self.ntq_lock.release()

                # Finally, result must be written to output file
                dictionary = {
                    "file_name": file_path,
                    "result": result,
                    "elapsed_time": elapsed_time
                }

                json_object = json.dumps(dictionary, indent=4)
                output_file_name = "output_model_1.json"
                
                if model_type == '2':
                    output_file_name = "output_model_2.json"
                    
                with open(output_file_name, "a") as outfile:
                    outfile.write(json_object)


    # TODO: Validate this
    def scheduler(self):
        while True:
            self.ntq_lock.acquire()
            ntq_copy = copy.copy(self.node_to_query)
            self.ntq_lock.release()

            for node in ntq_copy:
                if ntq_copy[node] == "": # Empty
                    
                    target_model = ''
                    if node in self.job1_node_pool:
                        target_model = '1'
                    if node in self.job2_node_pool:
                        target_model = '2'

                    self.schedule_lock.acquire()
                    for i in self.schedule.master_schedule:
                        if self.schedule.master_schedule[i].assigned_vm == '' and self.schedule.master_schedule[i].model_type == target_model:
                            with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
                                try:
                                    s.connect((node, self.file_port))
                                except socket.error as e:
                                    return
                                s.settimeout(3.0)
                                s.send(b'execute_query')
                                s.recv(1) # for ack
                                self.schedule.update_start_time(i, time.time())
                                self.schedule.update_status(i, "ongoing")
                                self.schedule.update_assigned_vm(i, node)
                                s.send(json.dumps({'file_path': self.schedule.master_schedule[i].file_name, 'model_type': self.schedule.master_schedule[i].model_type}).encode())
                                s.close()
                        
                            break
                    
                    self.schedule_lock.release()
            
            time.sleep(3)
    
    # TODO: Complete this
    def load_balancer(self):
        while True:
            j1_qr = 1
            j2_qr = 1
            j1_num_queries = 0
            j2_num_queries = 0

            self.job1_completed_lock.acquire()
            self.job2_completed_lock.acquire()

            if self.job1_completed.get_total_num_of_queries() != 0:
                j1_qr = self.get_query_rate('1')

            if self.job2_completed.get_total_num_of_queries() != 0:
                j2_qr = self.get_query_rate('2')

            self.job1_completed_lock.release()
            self.job2_completed_lock.release()

            self.schedule_lock.acquire()
            for i in self.schedule.master_schedule:
                if self.schedule.master_schedule[i].model_type == '1':
                    j1_num_queries += 1
                
                else:
                    j2_num_queries += 1
            self.schedule_lock.release()

            j1_est_time_left = j1_num_queries * j1_qr
            j2_est_time_left = j2_num_queries * j2_qr

            self.ntf_lock.acquire()
            self.job1_node_pool_lock.acquire()
            self.job2_node_pool_lock.acquire()

            j1_target_vm_count = round(len(self.node_to_file) * (j1_est_time_left / (j1_est_time_left + j2_est_time_left)))
            j2_target_vm_count = len(self.node_to_file) - j1_target_vm_count

            self.job1_node_pool = set()
            self.job2_node_pool = set()

            j = 0
            for node in self.node_to_file:
                if j < j1_target_vm_count:
                    self.job1_node_pool.add(node)
                    j += 1

                else:
                    self.job2_node_pool.add(node)

            self.ntf_lock.release()
            self.job1_node_pool_lock.release()
            self.job2_node_pool_lock.release()

            print(f"J1 QR = {j1_qr}, J2 QR = {j2_qr}")
            print(f"J1 num queries = {j1_num_queries}, J2 num queries = {j2_num_queries}")
            print(f"J1 est time left = {j1_est_time_left}, J2 est time left = {j2_est_time_left}")
            print(f"Reconfiguring load, Job1={j1_target_vm_count}, Job2={j2_target_vm_count}...")

    def get_query_rate(self, model_type):
        curr_time = time.time()
        count = 0

        if model_type == '1':
            self.job1_completed_lock.acquire()
            for job in self.job1_completed.master_schedule:
                time_diff = curr_time - self.job1_completed.master_schedule[job].stop_time
                if time_diff >= 0 and time_diff <= 10:
                    count += 1
            self.job1_completed_lock.release()

        else:
            self.job2_completed_lock.acquire()
            for job in self.job2_completed.master_schedule:
                time_diff = curr_time - self.job2_completed.master_schedule[job].stop_time
                if time_diff >= 0 and time_diff <= 10:
                    count += 1
            self.job2_completed_lock.release()

        return count / 10

    def get_query_time(self, model_type):
        total_query_time = 0.0
        count = 0

        if model_type == '1':
            self.job1_completed_lock.acquire()
            for job in self.job1_completed.master_schedule:
                total_query_time += self.job1_completed.master_schedule[job].elapsed_time
                count += 1
            self.job1_completed_lock.release()

        else:
            self.job2_completed_lock.acquire()
            for job in self.job2_completed.master_schedule:
                total_query_time += self.job2_completed.master_schedule[job].elapsed_time
                count += 1
            self.job2_completed_lock.release()

        return total_query_time / count

    def get_addr_thread(self):
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.bind((self.host, GET_ADDR_PORT))
            s.listen()
            while True:
                conn, addr = s.accept()
                sdfsfileid = conn.recv(4096).decode()
                self.ftn_lock.acquire()
                self.file_to_node.setdefault(sdfsfileid, set())
                res = list(self.file_to_node[sdfsfileid])
                self.ftn_lock.release()
                conn.send(json.dumps(res).encode())

    def run(self):
        t1 = threading.Thread(target=self.background)
        t1.start()
        t2 = threading.Thread(target=self.get_addr_thread)
        t2.start()

        print("Commands: info, pending, completed, start, query_rate <model #>, query_time <model #>, vm_info")
        while True:
            command = input('>')
            parsed_command = command.split()
            if command == 'info':
                self.ntf_lock.acquire()
                print(f"NTF: {self.node_to_file}")
                self.ntf_lock.release()

                self.ftn_lock.acquire()
                print(f"FTN: {self.file_to_node}")
                self.ftn_lock.release()

            elif command == 'pending':
                self.schedule_lock.acquire()
                print(self.schedule.print_schedule())
                self.schedule_lock.release()

            elif command == 'completed':
                self.job1_completed_lock.acquire()
                print("Job 1 completed table")
                print(self.job1_completed.print_schedule())
                self.job1_completed_lock.release()

                self.job2_completed_lock.acquire()
                print("Job 2 completed table")
                print(self.job2_completed.print_schedule())
                self.job2_completed_lock.release()

            elif command == 'start' and not self.is_query_running:
                self.is_query_running = True
                t1 = threading.Thread(target=self.scheduler)
                t1.start()

                t2 = threading.Thread(target=self.query_ack_monitor)
                t2.start()

                t3 = threading.Thread(target=self.load_balancer)
                t3.start()

            # TODO: Validate this
            elif parsed_command[0] == 'query_rate':
                model_type = parsed_command[1]
                print(f"Query rate for model {model_type} = {self.get_query_rate(model_type)}")

            # TODO: Validate this
            elif parsed_command[0] == 'query_time':
                model_type = parsed_command[1]
                print(f"Query time for model {model_type} = {self.get_query_time(model_type)}")
    
            elif command == 'vm_info':
                self.ntq_lock.acquire()
                print(f"{self.node_to_query}")
                self.ntq_lock.release()


if __name__ == '__main__':
    master = FMaster(MASTER_PORT, FILE_PORT)
    master.run()

