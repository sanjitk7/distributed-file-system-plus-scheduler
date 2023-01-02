package com.grepmp.app;
import java.util.Scanner;

public class CommandReader {

    String command;

    public String ReadCommand(){
        Scanner sc = new Scanner(System.in);
        command = sc.nextLine();
        sc.close();
        return command;
    }
}
