package com.CodeSmell;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class joernServer {
    public static void main(String[] args) {

        // Get the path to joern
        String joernPath = System.getProperty("user.home") + "/bin/joern/joern-cli";

        // Start up a command prompt terminal (no popup) and start the joern server
        ProcessBuilder builder;

        // Open terminal and start the joern server once process for builder starts
        // Different commands for windows and linux
        if (System.getProperty("os.name").contains("Windows")) {
            builder = new ProcessBuilder("cmd.exe", "/c", "cd " + joernPath, "& joern --server");
        } else {
            builder = new ProcessBuilder("sh", "-c", "cd " + joernPath, "& joern --server");
        }

        // Start the server
        try {
            Process p = builder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            System.out.println(reader.readLine());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
