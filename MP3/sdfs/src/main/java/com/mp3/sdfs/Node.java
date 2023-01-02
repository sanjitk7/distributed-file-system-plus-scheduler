package com.mp3.sdfs;

import java.io.DataOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class Node {
    private static int port = 8000; // Setting port value as constant across all hosts
    private static Logger logger = LogManager.getLogger(Node.class);

    public static void main(String[] args) throws UnknownHostException {
        List<Member> membershipList = new ArrayList<Member>();

        String ipAddress = InetAddress.getLocalHost().getHostAddress();
        long spawnTimestamp = System.currentTimeMillis();
        String id = ipAddress + ":" + port + "_" + spawnTimestamp;
        String hostName = InetAddress.getLocalHost().getHostName();

        NodeManager nodeMgr = null;
        Thread nodeMgrThread = null;

        boolean terminate = false;

        Scanner scanner = new Scanner(System.in);

        try {
            while (!terminate) {
                // Command line interface implementation
                String command = scanner.nextLine();

                if (command.startsWith("list_mem")) {
                    System.out.println("Fetching membership list...");
                    nodeMgr.printMembershipList();

                } else if (command.startsWith("list_self")) {
                    System.out.println("Fetching id...");
                    System.out.println(id);

                } else if (command.startsWith("join")) {
                    System.out.println("Initiating join...");
                    logger.info("Initiating join...");
                    
                    spawnTimestamp = System.currentTimeMillis();
                    id = ipAddress + ":" + port + "_" + spawnTimestamp;

                    // Spin up a new thread to execute the node manager
                    nodeMgr = new NodeManager(ipAddress, id, hostName, membershipList, spawnTimestamp);
                    nodeMgrThread = new Thread(nodeMgr);
                    nodeMgrThread.start();

                } else if (command.startsWith("leave")) {
                    System.out.println("Initiating leave...");
                    logger.info("Initiating leave...");

                    nodeMgr.initiateLeave();
                    
                    // Clear all members in the membership list
                    membershipList.clear();

                    if (nodeMgrThread == null) {
                        System.out.println("ERROR: Unable to perform leave, are you sure the node has joined the group?");
                        logger.info("ERROR: Unable to perform leave, are you sure the node has joined the group?");
                        break;
                    }

                    // Terminate the node manager thread
                    if (nodeMgrThread.isAlive()) {
                        nodeMgr.exit = true;
                    }

                    terminate = true;
                
                } else if (command.startsWith("clear")) {
                    // Clears the console
                    System.out.println("\033[H\033[2J");

                }  else if (command.startsWith("neighbors")) {
                    nodeMgr.printNeighbors();

                }  else if (command.startsWith("put") || command.startsWith("get") || command.startsWith("delete") || command.startsWith("ls") || command.startsWith("store") || command.startsWith("get_version")) {
                    if (nodeMgrThread != null) {

                        int i = 1;
                        while (i != 0) {
                            System.out.println("[TIME] " + System.currentTimeMillis());
                            Socket clientSocket = new Socket(ipAddress, 4000);
                            DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream());
                            dos.writeUTF(command + " " + ipAddress);
                            clientSocket.close();
                            i--;
                        }
                    }

                }  else {
                    System.out.println("ERROR: Unknown option: " + command + ".\nAllowed commands: list_mem, list_self, join, leave, clear");
                    logger.info("ERROR: Unknown option: " + command + ".\nAllowed commands: list_mem, list_self, join, leave, clear");
                }
                System.out.println("\n\n\n");
            }

            scanner.close();
        
        } catch(Exception e) {
            System.out.println(e);
            logger.error(e);
        }
    }
}
