package com.mp2.introducer;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class Introducer {
    private static List<Member> membershipList = new ArrayList<Member>();
    private static int port = 6000;
    private static int targetPort = 8000;
    private static DatagramSocket socket;
    private static Logger logger = LogManager.getLogger(Introducer.class);

    private static String getMessageBody(String message) { return message.split(" ")[1]; }

    private static String marshalMembershipList() {
        try {
            StringWriter marshalledString = new StringWriter();

            JAXBContext context = JAXBContext.newInstance(Members.class);
            Marshaller marshaller = context.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.marshal(new Members(membershipList), marshalledString);

            return marshalledString.toString();
        } catch (Exception e) {
            System.out.println(e);
            logger.error(e);
            return "";
        }
    }

    // Unmarshal data received from a member
    private static List<Member> unmarshalMembershipList(String data) {
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

    private static void sendUdpPacket(String targetIpAddress, String payload) {
        try {
            InetAddress targetInetAddress = InetAddress.getByName(targetIpAddress);
            byte buffer[] = payload.getBytes();
            DatagramPacket udpPayload = new DatagramPacket(buffer, buffer.length, targetInetAddress, targetPort);
            socket.send(udpPayload);
            logger.info("SENT: " + buffer.length + " bytes " + payload);
        } catch (Exception e) {
            System.out.println(e);
            logger.error(e);
        }
    }

    public static void main(String[] args) throws IOException {
        System.out.println( "Hello from Introducer!" );

        try {
            socket = new DatagramSocket(port);
            byte[] receivePayload = new byte[256];
            DatagramPacket receivePacket = new DatagramPacket(receivePayload, receivePayload.length);
            
            while (true) {
                socket.receive(receivePacket);
                String message = new String(receivePacket.getData(), receivePacket.getOffset(), receivePacket.getLength());
                String senderIpAddress = receivePacket.getAddress().getHostAddress();
                logger.info("RECEIVED: " + receivePacket.getLength() + "bytes " + message);

                if (message.startsWith("JOIN")) {
                    membershipList.add(new Member(getMessageBody(message), senderIpAddress, 0));
                    System.out.println("Added " + senderIpAddress + " to membership list" );
                    logger.info("Added " + senderIpAddress + " to membership list" );
                    sendUdpPacket(senderIpAddress, marshalMembershipList());
                    
                } else if(message.startsWith("FAILURE")) {
                    String data = message.split("FAILURE ")[1].trim();
                    List<Member> removedMembers = unmarshalMembershipList(data);
                    for (int i = 0; i < membershipList.size(); i++) {
                        Member member = membershipList.get(i);
                        if (membershipList.get(i).getId().equals(removedMembers.get(0).getId())) {
                            System.out.println("Removed " + membershipList.get(i).getId() + " from list" );
                            logger.info("Removed " + membershipList.get(i).getId() + " from list" );
                            membershipList.remove(member);
                            break;
                        }
                    }

                } else if(message.startsWith("LEAVE")) {
                    for (int i = 0; i < membershipList.size(); i++) {
                        if (membershipList.get(i).getId().equals(message.split(" ")[1])) {
                            System.out.println("Cleaned up " + membershipList.get(i).getId() + " from list" );
                            logger.info("Cleaned up " + membershipList.get(i).getId() + " from list" );
                            membershipList.remove(i);
                            break;
                        }
                    }
                }
            }

        } catch(Exception e) {
            System.out.println(e);
            logger.error(e);
        }
    }
}
