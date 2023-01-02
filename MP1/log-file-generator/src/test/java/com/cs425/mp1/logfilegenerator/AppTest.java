package com.cs425.mp1.logfilegenerator;

import static org.junit.Assert.assertTrue;

import com.cs425.mp1.logfilegenerator.App;

import java.text.DateFormat;  
import java.text.SimpleDateFormat;  
import java.util.Date;  
import java.util.Calendar;  
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class AppTest {

    /**
     * Checks if the log file is generated as required by the program
     */
    @Test
    public void isOutputFileGenerated() {
        try {
            App app = new App();
            String[] dummyStr = {"", ""};
            app.main(dummyStr);
    
            Date date = Calendar.getInstance().getTime();  
            DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd"); 
            String strDate = dateFormat.format(date);  
    
            Path path = Paths.get("./output/logs/vm." + strDate + ".log");
            System.out.println("./output/logs/vm." + strDate + ".log");
            assertTrue(Files.exists(path));
        } catch (Exception e) {
            System.out.println(e);
        }
    }
}
