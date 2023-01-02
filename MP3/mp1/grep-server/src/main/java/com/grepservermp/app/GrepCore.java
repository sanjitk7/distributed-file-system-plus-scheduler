package com.grepservermp.app;

import java.io.File;
import java.util.HashSet;

import org.unix4j.Unix4j;
import org.unix4j.unix.Grep;
import org.unix4j.unix.grep.GrepOptionSet_Fcilnvx;

public class GrepCore {
    private GrepOptionSet_Fcilnvx options = null;

    private String cmd = new String();
    private String grepResult = new String();
    private String grepPattern = new String();
    private String grepFileName = new String(); // TO-DO: extend support for multiple files, for now we are assuming there will only be a single file

    private HashSet<String> grepFlags = new HashSet<String>();

    public GrepCore(String grepCommand) {
        this.cmd = grepCommand;
    }

    public String getGrepResult() {
        return this.grepResult;
    }

    public String getGrepPattern() {
        return this.grepPattern;
    }

    public String getGrepFileName() {
        return this.grepFileName;
    }

    public GrepOptionSet_Fcilnvx getGrepOptions() {
        return this.options;
    }

    /**
     * Function sets the appropriate grep flag options
     * If multiple flags are given, the values are appended. E.g. -cF will be represented as Grep.Options.c.F
     * @param flag Single character denoting grep flag value. Allowed values are: i, v, F, n, c, l, x
     */
    private void setGrepOption(String flag) {
        switch(flag) {
            case "i":
                if (options == null) {
                    options = Grep.Options.i;
                }
                options = options.i;
                break;
            case "v":
                if (options == null) {
                    options = Grep.Options.v;
                }
                options = options.v;
                break;
            case "F":
                if (options == null) {
                    options = Grep.Options.F;
                }
                options = options.F;
                // this.isPatternRegex = false;
                break;
            case "n":
                if (options == null) {
                    options = Grep.Options.n;
                }
                options = options.n;
                break;
            case "c":
                if (options == null) {
                    options = Grep.Options.c;
                }
                options = options.c;
                break;
            case "l":
                if (options == null) {
                    options = Grep.Options.l;
                }
                options = options.l;
                break;
            case "x":
                if (options == null) {
                    options = Grep.Options.x;
                }
                options = options.x;
                break;
        }
    }


    /**
     * Function executes grep command
     * Sample usage:
     * File file = new File("res/machine.1.20220904.log");
     * String countResult = Unix4j.grep(Grep.Options.count, "ERROR", file).toStringResult(); // Print count of matches
     * System.out.println(countResult); // Expected output is 829
     * 
     * String textResult = Unix4j.grep(Grep.Options.F.i, "ERROR", file).toStringResult(); // Print the matching strings to console
     */
    private String executeGrep() {
        File file = new File(grepFileName);
        String textResult = new String();
        if (options != null) {
            textResult = Unix4j.grep(options, grepPattern, file).toStringResult();
        } else {
            textResult = Unix4j.grep(grepPattern, file).toStringResult();
        }

        return textResult;
    }


    /**
     * Parses the grep command provided by the user as input and stores flags, pattern string and file name
     * @param grepCore GrepCore object
     */
    private void parseGrepCommand() {
        String[] cmdArr = this.cmd.split(" "); // grep command format is `grep <flags> <pattern> <files>`

        for (int i = 1; i < cmdArr.length; i++) { // Ignore first index
            if (cmdArr[i].startsWith("-")) {
                // May either be in the format `grep -ivFnclx <...>` or `grep -i -v -F -n -c -l -x <...>`
                for (int j = 1; j < cmdArr[i].length(); j++) {
                    this.grepFlags.add(Character.toString(cmdArr[i].charAt(j)));
                }
            } else {
                if (cmdArr[i-1].startsWith("-") || i == 1) {
                    this.grepPattern = cmdArr[i]; // This is the PATTERNS field
                    this.grepPattern  = this.grepPattern .replaceAll("\"", "");
                } else {
                    this.grepFileName = cmdArr[i]; // This is the file name field
                }
            }
        }

        for (String flag: this.grepFlags) {
            this.setGrepOption(flag);
        }

    }

    /**
     * Function executes grep command and returns the result back to the user
     * @return grep result string
     */
    public String doGrep() {
        this.parseGrepCommand();
        
        // Debug prints
        System.out.println(this.grepFlags);
        System.out.println(this.grepPattern);
        System.out.println(this.grepFileName);

        this.grepResult = this.executeGrep();
        return this.grepResult;

    }
}
