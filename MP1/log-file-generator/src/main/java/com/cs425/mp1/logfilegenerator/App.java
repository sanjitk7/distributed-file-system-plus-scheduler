package com.cs425.mp1.logfilegenerator;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Scanner;
import java.util.Random;

import java.text.DateFormat;  
import java.text.SimpleDateFormat;  
import java.util.Date;  
import java.util.Calendar;  
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class App {
    private static final Logger LOGGER = LogManager.getLogger(App.class);
    
    private static final int NUMBER_OF_FILES = 4;

    // Constants used by the program
    private static final String ALLOWED_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz1234567890";
    private static final int MAX_LOG_LEVELS = 6;
    private static final int MAX_LOG_LINES = 3000;
    private static final int MAX_NUMBER_OF_WORDS = 20;
    private static final int MAX_WORD_LENGTH = 10;

    private static Random RANDOM = new Random();

    private static void printLog(int logLevelValue, String text) {
        switch (logLevelValue) {
            case 0:
                LOGGER.info(text);
                break;
            case 1:
                LOGGER.error(text);
                break;
            case 2:
                LOGGER.warn(text);
                break;
            case 3:
                LOGGER.debug(text);
                break;
            case 4:
                LOGGER.trace(text);
                break;
            case 5:
                LOGGER.fatal(text);
                break;
        }
    }

    private static String generateRandomStringText() {
        StringBuilder stringBuilder = new StringBuilder();
        int numberOfWords = RANDOM.nextInt(MAX_NUMBER_OF_WORDS) + 1; // enforce sentences of minimum length 1
        for (int i = 0; i < numberOfWords; i++) {
            int wordLength = RANDOM.nextInt(MAX_WORD_LENGTH) + 1; // enforce words of minimum length 1
            for (int j = 0; j < wordLength; j++) {
                int randomChar = RANDOM.nextInt(ALLOWED_CHARS.length());
                stringBuilder.append(ALLOWED_CHARS.charAt(randomChar));
            }
            stringBuilder.append(" ");
        }
        return stringBuilder.toString();
    }

    public static void main(String[] args) throws FileNotFoundException, IOException {
        for (int i = 1; i <= NUMBER_OF_FILES; i++) {
            System.out.println("Working Directory = " + System.getProperty("user.dir"));
            File file = new File("./res/sample.txt");
            Scanner scanner = new Scanner(file);
        
            while (scanner.hasNextLine()) {
                int logLevelValue = RANDOM.nextInt(MAX_LOG_LEVELS);
                String text = scanner.nextLine();
                printLog(logLevelValue, text);
            }
    
            int numberOfRandomizedLogLines = RANDOM.nextInt(MAX_LOG_LINES);
    
            for (int j = 0; j < numberOfRandomizedLogLines; j++) {
                int logLevelValue = RANDOM.nextInt(MAX_LOG_LEVELS);
                String randomText = generateRandomStringText();
                printLog(logLevelValue, randomText);
            }

            Date date = Calendar.getInstance().getTime();  
            DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd"); 
            String strDate = dateFormat.format(date);  
            
            Path path = Paths.get("./output/logs/vm." + strDate + ".log");
            Path pathcopy = Paths.get("./output/logs/vm" + i + ".log");
            Files.copy(path, pathcopy, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Completed writing to file!");

        }
    }
}
