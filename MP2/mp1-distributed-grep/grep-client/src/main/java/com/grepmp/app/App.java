package com.grepmp.app;

import com.grepmp.app.CommandReader;
import com.grepmp.app.ClientProcessor;
import com.grepmp.app.CommandReader;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.Properties;
import java.util.Scanner;
import java.util.Arrays;

import org.apache.commons.lang3.time.StopWatch;

public class App {
    public static String networkConfigPath = "./src/networkConfig.properties";
    public static void main(String[] args) {

        System.out.println("Enter GREP Command: ");
        CommandReader CD = new CommandReader();
        String grepCommand = CD.ReadCommand();

        StopWatch watch = new StopWatch();
        watch.start();

        // output.txt stores the returned data
        // Has to be cleared for each execution run
        try {
            FileWriter writer = new FileWriter("output.txt");
            writer.write("");
            writer.close();
        } catch (IOException e) {
            System.out.println(e);
        }

        try {
            // Getting Server Config data from properties file.
            InputStream propertiesInputStream = new FileInputStream(networkConfigPath);
            Properties networkProperties = new Properties();
            networkProperties.load(propertiesInputStream);

            String[] hostnamesArray = networkProperties.getProperty("hostnames").split(",");
            String[] portsArray = networkProperties.getProperty("ports").split(",");
            String[] filePathArray = networkProperties.getProperty("filepath").split(",");

            // Initalising number of servers and command string
            int N;
            if (hostnamesArray.length == portsArray.length) {
                // System.out.println("Number of IPs = Number of Ports = " + hostnamesArray.length);
                N = hostnamesArray.length;
            } else {
                // System.out.println("Number of IPs != Number of Ports!!\n Setting N=0");
                N = 0;
            }

            ClientProcessor[] cp = new ClientProcessor[N];
            Thread[] clientThread = new Thread[N];

            for (int i = 0; i < N; i++) {
                String serverId = hostnamesArray[i] + "::" + portsArray[i];
                cp[i] = new ClientProcessor(hostnamesArray[i], Integer.parseInt(portsArray[i]), serverId, grepCommand + " " +filePathArray[i]);
                clientThread[i] = new Thread(cp[i]);
                clientThread[i].start();
                // System.out.println("Client Process Thread for i=" + i);
            }

            for (int i = 0; i < N; i++) {
                clientThread[i].join();
            }
            watch.stop();
            System.out.println("TOTAL LINE COUNT: " + ClientProcessor.grepResultTotalLineCount);
            // System.out.println("FILE MATCHES: " + ClientProcessor.grepFileCount);
            System.out.println("TIME ELAPSED: " + watch.getTime());
        } catch (Exception e) {
            System.out.println(e);
        }

    }
}
