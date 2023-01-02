package com.grepmp.app;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import com.grepmp.app.App;

import java.io.File;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;  

import org.junit.Test;
import org.unix4j.Unix4j;
import org.unix4j.unix.Grep;
import org.junit.Test;

public class AppTest {

    /**
     * TC #1: Tests the basic functionality: able to connect to VMs and receive back data
     */
    @Test
    public void e2eTest() {
        String input = "grep -c PUT";

        InputStream in = new ByteArrayInputStream(input.getBytes());
        System.setIn(in);
        App app = new App();
        app.networkConfigPath = "./src/test/java/com/grepmp/app/networkConfigTest.properties";
        String[] strArr = {"", ""};
        app.main(strArr);

        File file = new File("output.txt");
        assertTrue(file.exists());
    }

    /**
     * TC #2: Test the output received from the servers, and whether the counts add up
     * Use the locally generated log files and the grep library to check the output locally and compare it against the output received from each server
     */
    @Test
    public void outputValidationTest() {
        String input = "grep -c PUT";

        InputStream in = new ByteArrayInputStream(input.getBytes());
        System.setIn(in);
        App app = new App();
        app.networkConfigPath = "./src/test/java/com/grepmp/app/networkConfigTest.properties";
        String[] strArr = {"", ""};
        app.main(strArr);

        File file = new File("output.txt");
        
        try {
            String expectedOutput = "";
            for (int i = 1; i <= 4; i++) {
                File logFile = new File("../log-file-generator/output/logs/vm" + i + ".log");
                String countResult = Unix4j.grep(Grep.Options.count, "PUT", logFile).toStringResult();
                expectedOutput = expectedOutput.concat(countResult + "\n");
            }
            
            String[] expectedOutputArr = expectedOutput.split("\n");
            Arrays.sort(expectedOutputArr);
            System.out.println(expectedOutput);

            String actualOutput = Files.readString(Paths.get("output.txt"), StandardCharsets.US_ASCII);
            String[] actualOutputArr = actualOutput.split("\n");
            Arrays.sort(actualOutputArr);
            System.out.println(actualOutput);
            
            assertEquals(expectedOutput.length(), actualOutput.length());
            assertArrayEquals(expectedOutputArr, actualOutputArr);
        } catch (IOException e) {
            System.out.println("IOException");
        }
    }
}
