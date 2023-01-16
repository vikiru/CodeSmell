package com.CodeSmell;

import java.io.*;
import java.nio.file.Paths;

public class JoernServer {


    InputStream joernStream;

    public InputStream getStream() {
        return this.joernStream;
    }

    public void start(String directory) {
        // Get the path to joern
        String joernPath = System.getProperty("user.home") + "/bin/joern/joern-cli";

        System.out.println("JoernServer :: Starting Joern in directory: " + directory);
        String directoryPath = Paths.get("").toAbsolutePath() + "/src/main/python";

        // Start up a command prompt terminal (no popup) and start the joern server
        ProcessBuilder joernServerBuilder, joernQueryBuilder, windowsPortFinderBuilder;

        // Open terminal and start the joern server once process for joernServerBuilder starts
        windowsPortFinderBuilder = new ProcessBuilder("cmd.exe", "/c", "netstat -ano | findstr 8080");
        if (System.getProperty("os.name").contains("Windows")) {
            joernServerBuilder = new ProcessBuilder("cmd.exe", "/c", "joern", "--server");
        } else joernServerBuilder = new ProcessBuilder("joern", "--server");
        joernQueryBuilder = new ProcessBuilder("python", "joern_query.py", directory).directory(new File(directoryPath));


        try {
            // Start the server
            Process joernServerProcess = joernServerBuilder.start();
            BufferedReader joernServerReader = new BufferedReader(
                    new InputStreamReader(joernServerProcess.getInputStream()));
            BufferedReader errorReader = new BufferedReader(
                    new InputStreamReader(joernServerProcess.getErrorStream()));

            String line;
            while ((line = joernServerReader.readLine()) != null) {
                System.out.println("JoernServer Input Stream Output :: " + joernServerReader.readLine());
            }
            while ((line = errorReader.readLine()) != null) {
                System.out.println("JoernServer Error Stream Output :: " + line);
            }

            // Execute queries against the local joern server instance.
            Process joernQueryProcess = joernQueryBuilder.start();
            this.joernStream = joernQueryProcess.getInputStream();

            // Reading in joernQuery Error Stream and using waitFor() on joernQuery process causes extreme delays / blocking.

            // For Windows OS, find the process bound to port 8080 and kill it.
            if (System.getProperty("os.name").contains("Windows")) {
                Process findProcess = windowsPortFinderBuilder.start();
                BufferedReader findProcessReader = new BufferedReader(new InputStreamReader(findProcess.getInputStream()));

                // Obtain the PID of the process bound to port 8080
                String lineFree = findProcessReader.readLine();
                String processID = lineFree.substring(lineFree.lastIndexOf(" ")).trim();

                // Kill the process bound to port 8080
                ProcessBuilder portFreerBuilder = new ProcessBuilder("cmd.exe", "/c", "taskkill /F /PID " + processID);
                Process freeProcess = portFreerBuilder.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
