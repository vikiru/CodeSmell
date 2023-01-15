package com.CodeSmell;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Paths;

import java.io.File;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

public class JoernServer {


    InputStream joernStream;

    public InputStream getReader() {
        return this.joernStream;
    }


    public void start(String directory) {

        // Get the path to joern
        String joernPath = System.getProperty("user.home") + "/bin/joern/joern-cli";
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
                new InputStreamReader(joernServerProcess.getErrorStream()));;
            String line;
            while ((line = joernServerReader.readLine()) != null) {
                System.out.println(joernServerReader.readLine());
            }
            while ((line = errorReader.readLine()) != null) {
                System.out.println(line);
            }

            // Execute queries against the local joern server instance.
            Process joernQueryProcess = joernQueryBuilder.start();

            this.joernStream = joernQueryProcess.getInputStream();
            errorReader = new BufferedReader(new InputStreamReader(joernQueryProcess.getErrorStream()));
            int exitCode = joernQueryProcess.waitFor();
            System.out.println("Joern exit code: " + exitCode);
            while ((line = errorReader.readLine()) != null) {
                System.out.println(line);
            }
            // Wait for joern_query to finish and then cleanup by killing the process which started the
            // local server instance.
            if (exitCode == 0) {
                joernServerProcess.destroy();
                if (joernServerProcess.isAlive()) {
                    joernServerProcess.destroyForcibly();
                }

                // For Windows OS, find the process bound to port 8080 and kill it.
                // todo - add commands for other OS such as linux.
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
                return;
            }
            throw new RuntimeException("Joern server error");
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
