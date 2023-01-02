package com.grepservermp.app;

import java.net.*;
import java.io.*;
import com.grepservermp.app.GrepCore;

public class App {

    public static void main(String[] args) {

        String hostname = "localhost";
        int port = Integer.parseInt(args[0]);

        String serverId = hostname + "::" + port;

        try {

            ServerSocket ss = new ServerSocket(port);
            boolean flag = true;

            while (flag) {
                try {

                    Socket s = ss.accept();
                    DataInputStream dis = new DataInputStream(s.getInputStream());
                    String grepCommand = (String) dis.readUTF();
                    
                    System.out.println("Received grepCommand on server side: " + grepCommand);
                    System.out.println("grep command passed to GrepCore module");

                    GrepCore grepCore = new GrepCore(grepCommand);
                    String grepCommandResult = grepCore.doGrep();

                    System.out.println("grep result received from GrepCore module");


                    // Send Data as byte array
                    DataOutputStream dos = new DataOutputStream(s.getOutputStream());
                    byte[] grepCommandResultBytes=grepCommandResult.getBytes("UTF-8");
                    dos.writeInt(grepCommandResultBytes.length);
                    dos.write(grepCommandResultBytes);
                    dos.flush();

                    if (grepCommand.equals("exit")){
                        flag = false;
                        dos.close();
                        ss.close();
                    }
                    // Thread.sleep(10000);
                } catch (Exception e) {
                    System.out.println(e);
                }
            }

            
        } catch (Exception e) {
            System.out.println(e);
        }
    }
}
