package com.mp2.membership;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
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
    private int port = 8000; // Port of the node
    private long spawnTimestamp;
    private List<Member> membershipList = new ArrayList<Member>();

    private DatagramSocket socket = null;

    public volatile boolean exit = false;

    private ScheduledExecutorService pingExecutor = Executors.newScheduledThreadPool(1);
    private ScheduledExecutorService failureDetectionExecutor = Executors.newScheduledThreadPool(1);


    private static long PING_EXECUTION_FREQ = 1000; // aka how frequently do you want neighbors to ping each other
    private static long TIMEOUT_PERIOD = 3000; // aka timeout period exceeding which members get dropped

    private static String introducerIpAddress = "172.22.158.215";
    private static int introducerPort = 6000;

    private static Logger logger = LogManager.getLogger(NodeManager.class);

    public NodeManager(String address, String id, List<Member> membershipList, long spawnTimestamp) throws IOException {
        this.address = address;
        this.id = id;
        this.membershipList = membershipList;
        this.spawnTimestamp = spawnTimestamp;
    }

    private void sendUdpPacket(String targetIpAddress, int targetPort, String payload) {
        try {
            InetAddress targetInetAddress = InetAddress.getByName(targetIpAddress);
            byte buffer[] = payload.getBytes();
            DatagramPacket udpPayload = new DatagramPacket(buffer, buffer.length, targetInetAddress, targetPort);
            socket.send(udpPayload);
            logger.info("SENT: " + buffer.length + " bytes " + payload);
        } catch (Exception e) {
            logger.error(e);
        }
    }

    private List<Integer> findNeighborIndices() {
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

    // Send a JOIN message to introducer
    // Introducer sends back the membership table
    private void introduceSelfToIntroducer() {
        String payload = "JOIN " + id;
        sendUdpPacket(introducerIpAddress, introducerPort, payload);

        byte[] receivePayload = new byte[10000000];
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
            List<Integer> neighborIndices = findNeighborIndices();
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
                List<Integer> neighborIndices = findNeighborIndices();
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
            List<Integer> neighborIndices = findNeighborIndices();

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
            }
        }
    };

    // Executes whenever node joins the group
    // And terminates upon leaving
    public void run() {
        try {
            // First bind to the socket
            SocketAddress socketAddress = new InetSocketAddress(InetAddress.getLocalHost(), port);
            socket = new DatagramSocket(socketAddress);

            // Introduce yourself to the Introducer!
            introduceSelfToIntroducer();

            // Start pinging your neighbors
            List<Member> newMembersList = new ArrayList<Member>();
            newMembersList.add(new Member(id, address, 0));
            String broadcast = "NEW_MEMBER " + marshalMembershipList(newMembersList);
            broadcastToNeighbors(broadcast);

            // Update timestamp in self entry to be the spawn timestamp
            for (Member member: membershipList) {
                if (member.getId().equals(id)) {
                    member.setLastUpdatedTime(spawnTimestamp);
                }
            }

            // Then start ping-ack'ing with group members
            pingExecutor.scheduleAtFixedRate(pingMonitor, 0, PING_EXECUTION_FREQ, TimeUnit.MILLISECONDS);
            failureDetectionExecutor.scheduleAtFixedRate(failureDetector, 0, PING_EXECUTION_FREQ, TimeUnit.MILLISECONDS);

            while (!exit) {
                // Keep the thread alive, listen for any new packets on the port
                byte[] receivePayload = new byte[10000000];
                DatagramPacket receivePacket = new DatagramPacket(receivePayload, receivePayload.length);
                socket.receive(receivePacket);
                String message = new String(receivePacket.getData(), receivePacket.getOffset(), receivePacket.getLength());
                logger.info("RECEIVED: " + receivePacket.getLength() + "bytes " + message);
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
                        membershipList.add(new Member(newMemberId, senderIpAddress, System.currentTimeMillis()));
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
                    }

                } else if (message.startsWith("FAILURE")) {
                    // Handle failure disemmination logic here
                    String data = message.split("FAILURE ")[1].trim();
                    List<Member> tempList = unmarshalMembershipList(data);
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
                }
            }
    
            // If leave is initiated, then this thread is terminated
            if (exit) {
                pingExecutor.shutdown();
                failureDetectionExecutor.shutdown();
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
        System.out.println(findNeighborIndices());
    }
}
