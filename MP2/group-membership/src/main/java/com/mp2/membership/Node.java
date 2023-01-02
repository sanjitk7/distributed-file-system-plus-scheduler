package com.mp2.membership;

import java.net.InetAddress;
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

        NodeManager nodeMgr = null;
        Thread nodeMgrThread = null;

        boolean terminate = false;

        try {
            while (!terminate) {
                // Command line interface implementation
                Scanner scanner = new Scanner(System.in);
                String command = scanner.nextLine();

                switch (command) {
                    case "list_mem":
                        System.out.println("Fetching membership list...");
                        nodeMgr.printMembershipList();
                        break;

                    case "list_self":
                        System.out.println("Fetching id...");
                        System.out.println(id);
                        break;
                    
                    case "join":
                        System.out.println("Initiating join...");
                        logger.info("Initiating join...");
                        
                        spawnTimestamp = System.currentTimeMillis();
                        id = ipAddress + ":" + port + "_" + spawnTimestamp;

                        // Spin up a new thread to execute the node manager
                        nodeMgr = new NodeManager(ipAddress, id, membershipList, spawnTimestamp);
                        nodeMgrThread = new Thread(nodeMgr);
                        nodeMgrThread.start();

                        break;

                    case "leave":
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
                        break;

                    case "clear":
                        System.out.println("\n\n");
                        break;

                    case "neighbors":
                        nodeMgr.printNeighbors();
                        break;

                    default:
                    System.out.println("ERROR: Unknown option: " + command + ".\nAllowed commands: list_mem, list_self, join, leave, clear");
                    logger.info("ERROR: Unknown option: " + command + ".\nAllowed commands: list_mem, list_self, join, leave, clear");
                }
            }

        } catch(Exception e) {
            System.out.println(e);
            logger.error(e);
        }
    }
}
