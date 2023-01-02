package com.mp3.sdfs;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class NodeManager extends Thread {
    private String id;
    private String address; // IP address of the node
    private String hostName; // IP address of the node
    private int port = 8000; // Port of the node
    private long spawnTimestamp;
    private List<Member> membershipList = new ArrayList<Member>();

    private DatagramSocket socket = null;

    public volatile boolean exit = false;

    private ScheduledExecutorService pingExecutor = Executors.newScheduledThreadPool(1);
    private ScheduledExecutorService failureDetectionExecutor = Executors.newScheduledThreadPool(1);
    private ScheduledExecutorService socketMonitorExecutor = Executors.newScheduledThreadPool(1);


    private static long PING_EXECUTION_FREQ = 1000; // aka how frequently do you want neighbors to ping each other
    private static long TIMEOUT_PERIOD = 3000; // aka timeout period exceeding which members get dropped

    private static String introducerIpAddress = "172.22.158.215";
    private static int introducerPort = 6000;

    // File System info
    private List<FileReplicas> fileReplicaList = new ArrayList<FileReplicas>();
    private List<SdfsFile> sdfsFileList = new ArrayList<SdfsFile>();

    ServerSocket serverSocket = new ServerSocket(4000);
    ServerSocket fileOnlySocket = new ServerSocket(2000);

    private static Logger logger = LogManager.getLogger(NodeManager.class);

    public NodeManager(String address, String id, String hostName, List<Member> membershipList, long spawnTimestamp) throws IOException {
        this.address = address;
        this.id = id;
        this.hostName = hostName;
        this.membershipList = membershipList;
        this.spawnTimestamp = spawnTimestamp;
    }

    private void sendUdpPacket(String targetIpAddress, int targetPort, String payload) {
        try {
            InetAddress targetInetAddress = InetAddress.getByName(targetIpAddress);
            byte buffer[] = payload.getBytes();
            DatagramPacket udpPayload = new DatagramPacket(buffer, buffer.length, targetInetAddress, targetPort);
            socket.send(udpPayload);
            // logger.info("SENT: " + buffer.length + " bytes " + payload);
        } catch (Exception e) {
            System.out.println(e);
            logger.error(e);
        }
    }

    private List<Integer> findNeighborIndices(String id) {
        List<Integer> indices = new ArrayList<Integer>();

        int index = getPositionInMembershipList(id);
        int n = membershipList.size();

        Set<Integer> memberIndices = new HashSet<Integer>();
        memberIndices.add((index + 1) % n);
        memberIndices.add((index + 2) % n);
        memberIndices.add((index + 3) % n);

        memberIndices.remove(index); // don't count self as a neighbor

        indices.addAll(memberIndices);
        return indices;
    }

    private String marshalMembershipList(List<Member> listOfMembers) {
        try {
            StringWriter marshalledString = new StringWriter();

            JAXBContext context = JAXBContext.newInstance(Members.class);
            Marshaller marshaller = context.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.marshal(new Members(listOfMembers), marshalledString);

            return marshalledString.toString();
        } catch (Exception e) {
            System.out.println(e);
            logger.error(e);
            return "";
        }
    }

    // Unmarshal data received from a member
    private List<Member> unmarshalMembershipList(String data) {
        List<Member> neighborMembershipList = null;
        try {
            Members members;
            StringReader marshalledString = new StringReader(data);

            JAXBContext context = JAXBContext.newInstance(Members.class);
            Unmarshaller unmarshaller = context.createUnmarshaller();
            members = (Members) unmarshaller.unmarshal(marshalledString);
            neighborMembershipList = members.getMembers();
        } catch (Exception e) {
            System.out.println(e);
            logger.error(e);
        }
        return neighborMembershipList;
    }

    private String marshalFileSystem() {
        try {
            StringWriter marshalledString = new StringWriter();

            JAXBContext context = JAXBContext.newInstance(FileSystem.class);
            Marshaller marshaller = context.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.marshal(new FileSystem(sdfsFileList, fileReplicaList), marshalledString);

            return marshalledString.toString();
        } catch (Exception e) {
            System.out.println(e);
            logger.error(e);
            return "";
        }
    }

    // Unmarshal data received from a member
    private void unmarshalFileSystem(String data) {
        try {
            FileSystem fs;
            StringReader marshalledString = new StringReader(data);

            JAXBContext context = JAXBContext.newInstance(FileSystem.class);
            Unmarshaller unmarshaller = context.createUnmarshaller();
            fs = (FileSystem) unmarshaller.unmarshal(marshalledString);
            sdfsFileList = fs.getFileSystem();
            fileReplicaList = fs.getReplicas();
        } catch (Exception e) {
            System.out.println(e);
            logger.error(e);
        }
    }

    // Send a JOIN message to introducer
    // Introducer sends back the membership table
    private void introduceSelfToIntroducer() {
        String payload = "JOIN " + id;
        sendUdpPacket(introducerIpAddress, introducerPort, payload);

        byte[] receivePayload = new byte[100000000];
        DatagramPacket receivePacket = new DatagramPacket(receivePayload, receivePayload.length);

        try {
            // Wait for introducer to respond
            socket.receive(receivePacket);
            String message = new String(receivePacket.getData(), receivePacket.getOffset(), receivePacket.getLength());
            membershipList = unmarshalMembershipList(message);

        } catch (Exception e) {
            System.out.println(e);
            logger.error(e);
        }
    }

    private void broadcastToNeighbors(String payload) {
        if (membershipList.size() > 1) {
            List<Integer> neighborIndices = findNeighborIndices(id);
            for (int i: neighborIndices) {
                String targetIpAddress = membershipList.get(i).getAddress();
                sendUdpPacket(targetIpAddress, port, payload);
            }
        }
    }

    // send broadcast to all of index's neighbors informing about topology change
    public void initiateLeave() {
        String payload = "LEAVE " + id;
        sendUdpPacket(introducerIpAddress, introducerPort, payload);

        Member selfEntry = null;

        for (Member member: membershipList) {
            if (member.getId().equals(id)) {
                selfEntry = member;
                break;
            }
        }

        List<Member> members = new ArrayList<Member>();
        members.add(selfEntry);
        broadcastToNeighbors("LEAVE " + marshalMembershipList(members));
    }

    // When a node joins the group, this thread will constantly ping all of the neighboring nodes
    Runnable pingMonitor = new Runnable() {
        public void run() {
            if (membershipList.size() > 1) {
                List<Integer> neighborIndices = findNeighborIndices(id);
                for (int i: neighborIndices) {
                    long timestamp = System.currentTimeMillis();
                    String targetIpAddress = membershipList.get(i).getAddress();
                    String payload = "PING " + membershipList.get(i).getId() + "@" + timestamp;
                    sendUdpPacket(targetIpAddress, port, payload);
                }

                for (int i = 0; i < membershipList.size(); i++) {
                    if (!neighborIndices.contains(i)) {
                        membershipList.get(i).setLastUpdatedTime(0);
                    }
                } 
            }
        }
    };

    // When a node joins the group, this thread will constantly ping all of the neighboring nodes
    Runnable failureDetector = new Runnable() {
        public void run() {
            List<Integer> indicesToPrune = new ArrayList<Integer>();
            List<Integer> neighborIndices = findNeighborIndices(id);

            for (Integer index: neighborIndices) {
                // If three ping cycles have passed with no response, mark the member as failed
                long currentTimestamp = System.currentTimeMillis();
                long lastUpdatedTime = membershipList.get(index).getLastUpdatedTime();
                if (lastUpdatedTime != 0 && (currentTimestamp - lastUpdatedTime > TIMEOUT_PERIOD)) {
                    System.out.println("No ACK received from neighbor " + membershipList.get(index).getId() + " in " + TIMEOUT_PERIOD + "ms. Disseminating failure...");
                    logger.info("No ACK received from neighbor " + membershipList.get(index).getId() + " in " + TIMEOUT_PERIOD + "ms. Disseminating failure...");
                    indicesToPrune.add(index);
                }
            }

            for (Integer index: indicesToPrune) {
                Member failed = membershipList.get(index);
                int failedIndex = getPositionInMembershipList(failed.getId());

                // Find the list of neighbors of failedIndex
                List<Integer> failedNeighbors = findNeighborIndices(failed.getId());
                for (Integer idx: failedNeighbors) {
                    sendUdpPacket(membershipList.get(idx).getAddress(), 8000, "REPLICATE ");
                }

                // Step 1, mark member as failed to prevent further pings
                // aka remove from membership list
                membershipList.remove(failed);

                // Step 2, share failure details to neighbors
                List<Member> failedMembers = new ArrayList<Member>();
                failedMembers.add(failed);
                String payload = "FAILURE " + marshalMembershipList(failedMembers);
                broadcastToNeighbors(payload);

                // Step 3, share failure details to introducer
                sendUdpPacket(introducerIpAddress, introducerPort, payload);

                if (failed.getIsLeader()) {
                    // Step 4, if the failed node was the leader, initiate new election round
                    // CORNER CASE: if there are only 2 nodes, and the leader fails, self is automatically the new leader
                    if (membershipList.size() == 1) {
                        for (Member member: membershipList) {
                            if (member.getId().equals(id)) {
                                member.setIsLeader(true);
                            }
                        }
                        String secondpayload = "ELECTED " + id;
                        sendUdpPacket(introducerIpAddress, introducerPort, secondpayload);

                        System.out.println("Elected self as leader, propagating ELECTED message...");
                        logger.info("Elected self as leader, propagating ELECTED message...");

                    } else {
                        payload = "ELECTION " + membershipList.get(0).getId();
                        broadcastToNeighbors(payload);
                    }
                }
            }
        }
    };

    Runnable socketMonitor = new Runnable() {
        public void run() {
            try {
                String data = receiveSocketMessage();
                System.out.println("Received " + data);
                
                // There are 3 possibilities
                // 1) Node is neither leader nor stores replica of file aka it is the client
                // 2) Node is leader
                // 3) Node stores file
                // Worst case, we are three hops away from file, and need to execute 1->2->3 in sequence

                if (data.startsWith("put ")) {
                    String[] cmdOptions = data.split(" ");
                    String localFileName = cmdOptions[1];
                    String sdfsFileName = cmdOptions[2];
                    String senderIpAddress = cmdOptions[3];

                    if (isNodeLeader()) {
            
                        List<String> listOfTargetHosts = new ArrayList<String>();
                        int version = 1;

                        // If file is new then simply make its version as 1
                        // Then update entry for file
                        // And compute new nodes to store it in
                        if (isFileNew(sdfsFileName)) {
                            // First find target nodes to map file to and update entries accordingly
                            List<Integer> targetNodeIndices = findNodesToMapFileTo();

                            System.out.println("Mapping to: " + String.join(targetNodeIndices.toString()));
                            for (Integer index: targetNodeIndices) {
                                List<String> fileList = fileReplicaList.get(index).getFileList();
                                fileList.add(sdfsFileName + "_1");
                                fileReplicaList.get(index).setFileList(fileList);
                                listOfTargetHosts.add(fileReplicaList.get(index).getId());
                            }
                           
                            sdfsFileList.add(new SdfsFile(sdfsFileName + "_1", localFileName, sdfsFileName, 1, listOfTargetHosts));
                            System.out.println(marshalFileSystem());
                            
                        
                        // Else increment the existing file version and reuse the same replicas that are used by previous version of file
                        } else {
                            // Update the file version in info list and fetch replica nodes from there directly
                            int highestVersion = 0;
                            for (SdfsFile file: sdfsFileList) {
                                if (file.getLocalFileName().equals(localFileName) && file.getVersion() > highestVersion) {
                                    highestVersion = file.getVersion();
                                    listOfTargetHosts = file.getStoredIn();
                                } 
                            }
                            highestVersion++;
                            sdfsFileList.add(new SdfsFile(sdfsFileName + "_" + highestVersion, localFileName, sdfsFileName, highestVersion, listOfTargetHosts));
                            System.out.println(marshalFileSystem());
                            version = highestVersion;
                        }
            
                        // Wait for quorum of acks then return
                        int acksReceived = 0;

                        try {
                            // Contact each host mapped to the file
                            for (String host: listOfTargetHosts) {
                                // Immediately receive at self if condition true
                                if (host.equals(id)) {
                                    if (senderIpAddress.equals(address)) {
                                        acksReceived++;
                                        // No need to do anything else 
                                    
                                    } else {
                                        receiveFile(sdfsFileName);
                                        acksReceived++;
                                    }
                                    
                                }  else {
                                    String targetIpAddress = host.split(":")[0];
                                    String fileName = sdfsFileName + "_" + version;
                
                                    // First intimate them about the incoming file
                                    sendSocketMessage("put-write " + fileName, targetIpAddress);
                
                                    // Then send the file
                                    sendFile(localFileName, targetIpAddress);
                                }
                            }
                            
                        // Wait for quorum of acks then return
                        int required = (membershipList.size() < 4) ? membershipList.size() - 1 : 3;
                        
                        while (acksReceived < required) {
                            String ack = receiveSocketMessage();
                            acksReceived++;
                        }

                        sendSocketMessage("put-success ", senderIpAddress);
            
                        } catch (Exception e) {
                            System.out.println(e);
                            logger.error(e);
                        }
            
                        // Multicast update to neighbors
                        broadcastToNeighbors("BACKUP " + marshalFileSystem());
            
                    } else {
                        // Contact the leader with file
                        // Asynchronously will receive update
                        String leaderIpAddress = findLeaderIpAddress();
                        sendSocketMessage(data, leaderIpAddress);     
                        sendFile(localFileName, leaderIpAddress);               
                    }

                } else if (data.startsWith("put-write")) {
                    String fileName = data.split("put-write ")[1];
                    receiveFile(fileName);

                    // Send an ack back
                    sendSocketMessage("ACK", findLeaderIpAddress());
                
                } else if (data.startsWith("put-success")) {
                    System.out.println("[TIME] " + System.currentTimeMillis());
                    System.out.println("Put operation success!!");
                
                } else if (data.startsWith("get ")) {

                    String[] cmdOptions = data.split(" ");
                    String sdfsFileName = cmdOptions[1];
                    String localFileName = cmdOptions[2];
                    String senderIpAddress = cmdOptions[3];

                    if (isNodeLeader()) {

                        // Check if file exists in SDFS

                        boolean doesFileExist = false;
                        List<String> listOfTargetHosts = new ArrayList<String>();
                        for (SdfsFile file: sdfsFileList) {
                            if (file.getId().equals(sdfsFileName)) {
                                doesFileExist = true;
                                listOfTargetHosts = file.getStoredIn();
                                break;
                            } 
                        }

                        System.out.println(marshalFileSystem());

                        if (doesFileExist) {
                            // Wait for at least 2 acks
                            int acksReceived = 0;
                            
                            try {
                                // Contact each host mapped to the file
                                for (String host: listOfTargetHosts) {
                                    String targetIpAddress = host.split(":")[0];
                                    System.out.println("Querying " + targetIpAddress + " for " + sdfsFileName);
                
                                    if (targetIpAddress.equals(address)) {
                                        // sendFile(sdfsFileName, address);
                                        acksReceived++;
                                    
                                    } else {
                                        // First query them for the incoming file
                                        sendSocketMessage("get-read " + sdfsFileName + " " + address, targetIpAddress);
                                    }
                                }
                                
                            // Wait for quorum of acks then return
                            int required = (membershipList.size() == 1) ? 1 : 2;
                            
                            while (acksReceived < required) {
                                receiveFile("temp");
                                acksReceived++;
                            }

                            sendSocketMessage("get-success " + localFileName, senderIpAddress);
                            sendFile("temp", senderIpAddress);
                
                            } catch (Exception e) {
                                System.out.println(e);
                                logger.error(e);
                            }
                            
                        } else {
                            sendSocketMessage("get-error", senderIpAddress);
                        }
            
                    } else {
                        // Contact the leader with file
                        // Asynchronously will receive update
                        sendSocketMessage(data, findLeaderIpAddress());                    
                    }

                } else if (data.startsWith("get-read ")) {
                    String fileName = data.split(" ")[1];
                    String senderAddress = data.split(" ")[2];
                    sendFile(fileName, senderAddress);

                } else if (data.startsWith("get-error")) {
                    System.out.print("File does not exist in SDFS");

                } else if (data.startsWith("get-success ")) {
                    String localFilePath = data.split(" ")[1];
                    receiveFile(localFilePath);
                    System.out.println("[TIME] " + System.currentTimeMillis());
                    System.out.print("Fetched file locally to " + localFilePath +"!");
                
                } else if (data.startsWith("store")) {
                    for (FileReplicas replica: fileReplicaList) {
                        if (replica.getId().equals(id)) {
                            // TODO: Fix me!
                            System.out.println(String.join("\t", replica.getFileList()));
                            break;
                        }
                    }
                    
                } else if (data.startsWith("ls")) {
                    if (isNodeLeader()) {
                        String fileName = data.split(" ")[1];
                        String senderIpAddress = data.split(" ")[2];
                        List<String> storedIn = new ArrayList<String>();

                        for (SdfsFile file: sdfsFileList) {
                            if (fileName.equals(file.getSdfsFileName())) {
                                storedIn = file.getStoredIn();
                                break;
                            }
                        }

                        String output = "";

                        for (Member member: membershipList) {
                            if (storedIn.contains(member.getId())) {
                                output = output.concat(member.getHostName() + "\t");
                            }
                        }

                        sendSocketMessage(output, senderIpAddress);

                    } else if (data.startsWith("ls-results")) {

                    } else {
                        sendSocketMessage(data, findLeaderIpAddress());
                    }

                } else if (data.startsWith("delete ")) {
                    String sdfsFileName = data.split(" ")[1];
                    String senderIpAddress = data.split(" ")[2];

                    if (isNodeLeader()) {
                        boolean doesFileExist = false;
                        List<String> listOfTargetHosts = new ArrayList<String>();
                        for (SdfsFile file: sdfsFileList) {
                            if (file.getId().equals(sdfsFileName)) {
                                doesFileExist = true;
                                listOfTargetHosts = file.getStoredIn();
                                break;
                            } 
                        }

                        if (doesFileExist) {

                            int acksReceived = 0;
                            int required = (membershipList.size() < 4) ? membershipList.size() - 1 : 3;


                            for (String targetHost: listOfTargetHosts) {
                                if (targetHost.equals(id)) {
                                    File file = new File(sdfsFileName);

                                    if (file.delete()) {
                                        acksReceived++;
                                        // TODO: update local sdfsFileList
                
                                    } else {
                                        throw new Exception("Failed to delete " + sdfsFileName + " on " + hostName);
                                    }

                                } else {
                                    sendSocketMessage("delete-execute " + sdfsFileName, targetHost.split(":")[0]);
                                }
                            }

                            while (acksReceived < required) {
                                receiveSocketMessage();
                                acksReceived++;
                            }
                            
                            sendSocketMessage("delete-success", senderIpAddress);

                            // FINALLY update filesystem info
                            List<Integer> indicesToPrune = new ArrayList<Integer>();
                            
                            for (int i = 0; i < sdfsFileList.size(); i++) {
                                if (sdfsFileList.get(i).getId().equals(sdfsFileName)) {
                                    indicesToPrune.add(i);
                                }
                            }

                            for (Integer index: indicesToPrune) {
                                sdfsFileList.remove(index.intValue());
                            }

                            for (FileReplicas fr: fileReplicaList) {
                                List<String> updatedListOfFiles = fr.getFileList();
                                updatedListOfFiles.remove(sdfsFileName);
                                fr.setFileList(updatedListOfFiles);
                            }

                            System.out.println(marshalFileSystem());

                            broadcastToNeighbors(marshalFileSystem());
                        }

                    } else {
                        sendSocketMessage(data, findLeaderIpAddress());
                    }
                } else if (data.startsWith("delete-success")) {
                    System.out.println("Successfully deleted");
                    System.out.println("[TIME] " + System.currentTimeMillis());

                } else if (data.startsWith("delete-execute ")) {
                    String sdfsFileName = data.split(" ")[1];
                    File file = new File(sdfsFileName);

                    if (file.delete()) {
                        sendSocketMessage("delete-ack", findLeaderIpAddress());

                        int idx = -1;
                        for (int i = 0; i < sdfsFileList.size(); i++) {
                            if (sdfsFileList.get(i).getId().equals(sdfsFileName)) {
                                idx = i;
                            }

                            sdfsFileList.remove(idx);
                        }

                    } else {
                        throw new Exception("Failed to delete " + sdfsFileName + " on " + hostName);
                    }

                } else if (data.startsWith("get-versions ")) {


                } else if (data.startsWith("get-versions-req ")) {
                    String sdfsFileName = data.split(" ")[1];
                    int versionCount = Integer.parseInt(data.split(" ")[2]);

                }
            
            } catch (Exception e) {
                System.out.println(e);
                logger.error(e);
            }
        }
    };

    private String findLeaderIpAddress() {
        for (Member member: membershipList) {
            if (member.getIsLeader()) {
                return member.getAddress();
            }
        }

        return "";
    }

    // Executes whenever node joins the group
    // And terminates upon leaving
    public void run() {
        try {
            // First bind to the socket
            SocketAddress socketAddress = new InetSocketAddress(InetAddress.getLocalHost(), port);
            socket = new DatagramSocket(socketAddress);

            // Introduce yourself to the Introducer!
            introduceSelfToIntroducer();

            // Update timestamp in self entry to be the spawn timestamp
            for (Member member: membershipList) {
                if (member.getId().equals(id)) {
                    member.setLastUpdatedTime(spawnTimestamp);
                }
            }

            // If you are first node to join, declare yourself as leader. No need for election.
            if (membershipList.size() == 1) {
                membershipList.get(0).setIsLeader(true);

                fileReplicaList.add(new FileReplicas(id, new ArrayList<String>()));

                // There's no other group nodes to send elected message to
                // Notify the introducer
                String payload = "ELECTED " + id;
                sendUdpPacket(introducerIpAddress, introducerPort, payload);
            
            } else {
                // Not the first node, assume some other leader exists
                // Start pinging your neighbors your information
                List<Member> newMembersList = new ArrayList<Member>();
                newMembersList.add(new Member(id, address, hostName, 0, false));
                String broadcast = "NEW_MEMBER " + marshalMembershipList(newMembersList);
                broadcastToNeighbors(broadcast);
            }

            // Then spawn separate processes to start ping-ack'ing with group members
            pingExecutor.scheduleAtFixedRate(pingMonitor, 0, PING_EXECUTION_FREQ, TimeUnit.MILLISECONDS);
            failureDetectionExecutor.scheduleAtFixedRate(failureDetector, 0, PING_EXECUTION_FREQ, TimeUnit.MILLISECONDS);
            socketMonitorExecutor.scheduleAtFixedRate(socketMonitor, 0, 1000, TimeUnit.MILLISECONDS);

            while (!exit) {
                try {
                    // Keep the thread alive, listen for any new packets on the port
                    byte[] receivePayload = new byte[10000000];
                    DatagramPacket receivePacket = new DatagramPacket(receivePayload, receivePayload.length);
                    socket.receive(receivePacket);
                    String message = new String(receivePacket.getData(), receivePacket.getOffset(), receivePacket.getLength());
                    // logger.info("RECEIVED: " + receivePacket.getLength() + "bytes " + message);
                    String senderIpAddress = receivePacket.getAddress().getHostAddress();
    
                    if (message.startsWith("ACK")) {
                        // Handle ack packet logic here
                        String memberId = message.split("@")[0].split(" ")[1];
                        for (Member m: membershipList) {
                            if (m.getId().equals(memberId)) {
                                m.setLastUpdatedTime(System.currentTimeMillis());
                            }
                        }
    
                    } else if (message.startsWith("PING")) {
                        // Respond with an ack
                        String payload = "ACK " + getMessageBody(message);
                        sendUdpPacket(senderIpAddress, port, payload);
                        
                        String newMemberId = message.split("@")[0].split(" ")[1];
                        if (! doesMembershipListContainNode(newMemberId)) {
                            String senderHostName = receivePacket.getAddress().getHostName();
                            membershipList.add(new Member(newMemberId, senderIpAddress, senderHostName, System.currentTimeMillis(), false));
                        }
    
                    } else if (message.startsWith("NEW_MEMBER")) {
                        // Handle new node added to membership list logic here
                        String data = message.split("NEW_MEMBER ")[1].trim();
                        List<Member> tempList = unmarshalMembershipList(data);
                        // If the new member has already been added, then either this node has received this message before or its neighbors have
                        // don't do anything in this case
                        if (!doesListContainEntry(tempList.get(0))) {
                            System.out.println("Adding new member to group...");
                            logger.info("Adding new member to group...");
                            tempList.get(0).printEntry();
                            membershipList.add(tempList.get(0));
                            broadcastToNeighbors("NEW_MEMBER " + marshalMembershipList(tempList));
    
                            if (isNodeLeader()) {
                                fileReplicaList.add(new FileReplicas(tempList.get(0).getId(), new ArrayList<String>()));
                                broadcastToNeighbors("BACKUP " + marshalFileSystem());
                            }
                        }
    
                    } else if (message.startsWith("FAILURE")) {
                        // Handle failure disemmination logic here
                        String data = message.split("FAILURE ")[1].trim();
                        List<Member> tempList = unmarshalMembershipList(data);
    
                        // TODO:
                        // Check if the failed node was the leader AND neighbor
                        // If yes, disseminate file system info to everybody 
    
                        // If the failed member has already been deleted, then either this node has received this message before or its neighbors have
                        // don't do anything in this case
                        if (doesListContainEntry(tempList.get(0))) {
                            System.out.println("Registering failure...");
                            logger.info("Registering failure...");
                            tempList.get(0).printEntry();
                            membershipList.remove(tempList.get(0));
                            broadcastToNeighbors("FAILURE " + marshalMembershipList(tempList));
                        }
    
    
                    } else if (message.startsWith("LEAVE")) {
                        // Handle new node leaving membership list logic here
                        String data = message.split("LEAVE ")[1].trim();
                        List<Member> tempList = unmarshalMembershipList(data);
                        // If the member has already been removed, then either this node has received this message before or its neighbors have
                        // don't do anything in this case
                        if (doesListContainEntry(tempList.get(0))) {
                            System.out.println("Removing member...");
                            logger.info("Removing member...");
                            tempList.get(0).printEntry();
                            membershipList.remove(tempList.get(0));
                            broadcastToNeighbors("LEAVE " + marshalMembershipList(tempList));
                        }
    
                    } else if (message.startsWith("ELECTION")) {
                        String leaderId = message.split("ELECTION ")[1];
    
                        // If received node id == self id, declare self as elected and multicast to neighbors
                        if (leaderId.equals(id)) {
                            broadcastToNeighbors("ELECTED " + id);
                            membershipList.get(getPositionInMembershipList(id)).setIsLeader(true);
                            
                            // Also Notify the introducer
                            String payload = "ELECTED " + id;
                            sendUdpPacket(introducerIpAddress, introducerPort, payload);
    
                            System.out.println("Elected self as leader, propagating ELECTED message...");
                            logger.info("Elected self as leader, propagating ELECTED message...");
    
                        // Else check if the proposed leader is still optimal
                        // If false, start another election run
                        } else if (!leaderId.equals(membershipList.get(0).getId())) {
                            broadcastToNeighbors("ELECTION " + membershipList.get(0).getId());
                            System.out.println("Proposing " + membershipList.get(0).getHostName() + " as leader");
                            logger.info("Proposing " + membershipList.get(0).getHostName() + " as leader");
                            
                        }
    
                    } else if (message.startsWith("ELECTED")) {
                        String leaderId = message.split("ELECTED ")[1];
    
                        // Broadcast message only if the election results have not been updated
                        int index = getPositionInMembershipList(leaderId);
    
                        if (!membershipList.get(index).getIsLeader()) {
                            membershipList.get(index).setIsLeader(true);
                            System.out.println("Elected " + membershipList.get(index).getHostName() +" as leader");
                            logger.info("Elected " +  membershipList.get(index).getHostName() +" as leader");
                            broadcastToNeighbors(message);
                        }
                    
                    } else if (message.startsWith("BACKUP")) {
                        String data = message.split("BACKUP ")[1].trim();
                        System.out.println("Received metadata backup from leader");
                        logger.info("Received metadata backup from leader");
                        unmarshalFileSystem(data);

                    }
                    
                } catch (Exception e) {
                    System.out.println(e);
                    logger.error(e);
                } 
            }

            // If leave is initiated, then this thread is terminated
            if (exit) {
                pingExecutor.shutdown();
                failureDetectionExecutor.shutdown();
                socketMonitorExecutor.shutdown();
                socket.close();
                return;
            }

        } catch(Exception e) {
            System.out.println(e);
            logger.error(e);
        }
    }

    private int getPositionInMembershipList(String id) {
        int index = -1;
        for (int i = 0; i < membershipList.size(); i++) {
            if (membershipList.get(i).getId().equals(id)) {
                return i;
            }
        }
        return index;
    }

    public void printMembershipList() {
        for (Member member: membershipList) {
            member.printEntry();
        }
    }

    private boolean doesListContainEntry(Member member) {
        for (Member entry: membershipList) {
            if (entry.getId().equals(member.getId())) {
                return true;
            }
        }
        return false;
    }
    
    private String getMessageBody(String message) { return message.split(" ")[1]; }

    private boolean doesMembershipListContainNode(String id) {
        for (Member member: membershipList) {
            if (member.getId().equals(id)) {
                return true;
            }
        }
        return false;
    }

    public void printNeighbors() {
        System.out.println(findNeighborIndices(id));
    }

    public boolean isNodeLeader() {
        // Rather than storing variables, refer to the membership list as the source of truth
        for (Member member: membershipList) {
            if (member.getId().equals(id)) {
                if (member.getIsLeader()) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean isFileNew(String fileName) {
        for (SdfsFile file: sdfsFileList) {
            if (file.getSdfsFileName().equals(fileName)) {
                return false;
            }
        }
        return true;
    }

    public List<Integer> findNodesToMapFileTo() {
        // Sort in ascending order by number of files in each node
        Collections.sort(fileReplicaList, new Comparator<FileReplicas>() {
            public int compare(FileReplicas fr1, FileReplicas fr2)  {
                return fr1.getFileList().size() - fr2.getFileList().size();
            }
        });
        
        List<Integer> indices = new ArrayList<Integer>();

        for (int i = 0; i < 4; i++) {
            if (i < fileReplicaList.size()) {
                indices.add(i);
            }
        }

        return indices;
    }

    /**
     * Sockets and their related util functions
     * @param path path of file
     * @param targetIpAddress targetIpAddress to send the file to
     * @throws Exception
     */

    private void sendFile(String path, String targetIpAddress) throws Exception{
        int bytes = 0;
        File file = new File(path);
        Socket clientSocket = new Socket(targetIpAddress, 2000);
        FileInputStream fileInputStream = new FileInputStream(file);
        DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream());
        dos.writeLong(file.length());  

        byte[] buffer = new byte[8192]; // Max size allowed by socket
        while ((bytes=fileInputStream.read(buffer)) != -1){
            dos.write(buffer, 0, bytes);
            dos.flush();
        }

        fileInputStream.close();
        clientSocket.close();
    }

    private void receiveFile(String fileName) throws Exception {
        Socket clientSocket = fileOnlySocket.accept();
        DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
        int bytes = 0;
        FileOutputStream fileOutputStream = new FileOutputStream(fileName);
        
        long size = dis.readLong();
        byte[] buffer = new byte[8192]; // Max size allowed by socket
        while (size > 0 && (bytes = dis.read(buffer, 0, (int) Math.min(buffer.length, size))) != -1) {
            fileOutputStream.write(buffer, 0,bytes);
            size -= bytes;
        }

        fileOutputStream.close();
        System.out.println("Finished writing to location: " + fileName);
    }

    private void sendSocketMessage(String message, String targetIpAddress) throws Exception {
        Socket clientSocket = new Socket(targetIpAddress, 4000);
        DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream());
        dos.writeUTF(message);
        clientSocket.close();
    }

    private String receiveSocketMessage() throws Exception{
        Socket clientSocket = serverSocket.accept();
        DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
        String data = (String) dis.readUTF();
        clientSocket.close();

        return data;
    }
}
