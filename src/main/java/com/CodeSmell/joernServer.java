package com.CodeSmell;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;

public class joernServer {
    public static void main(String[] args) {

        // Get the path to joern
        String joernPath = System.getProperty("user.home") + "/bin/joern/joern-cli";
        String directoryPath = Paths.get("").toAbsolutePath() + "/src/main/python";

        // Start up a command prompt terminal (no popup) and start the joern server
        ProcessBuilder joernServerBuilder, joernQueryBuilder, portFinderBuilder;

        // Open terminal and start the joern server once process for joernServerBuilder starts
        // Different commands for windows and linux
        if (System.getProperty("os.name").contains("Windows")) {
            joernServerBuilder = new ProcessBuilder("cmd.exe", "/c", "cd " + joernPath, "& joern --server");
            joernQueryBuilder = new ProcessBuilder("cmd.exe", "/c", "cd " + directoryPath + "& python joern_query.py");
            portFinderBuilder = new ProcessBuilder("cmd.exe", "/c", "netstat -ano | findstr 8080");
        } else {
            joernServerBuilder = new ProcessBuilder("sh", "-c", "joern --server");
            joernQueryBuilder = new ProcessBuilder("sh", "-c", "cd " + directoryPath + "python joern_query.py");
            portFinderBuilder = new ProcessBuilder("cmd.exe", "/c", "netstat -ano | findstr 8080");
        }

        try {
            // Start the server
            Process joernServerProcess = joernServerBuilder.start();
            BufferedReader joernServerReader = new BufferedReader(new InputStreamReader(joernServerProcess.getInputStream()));
            System.out.println(joernServerReader.readLine());

            // Execute queries against the local joern server instance.
            Process joernQueryProcess = joernQueryBuilder.start();
            BufferedReader joernQueryReader = new BufferedReader(new InputStreamReader(joernQueryProcess.getInputStream()));
            String line;
            while ((line = joernQueryReader.readLine()) != null) {
                System.out.println(line);
            }
            // Wait for joern_query to finish and then cleanup by killing the process which started the
            // local server instance.
            int exitCode = joernQueryProcess.waitFor();
            if (exitCode == 0) {
                joernServerProcess.destroy();
                if (joernServerProcess.isAlive()) {
                    joernServerProcess.destroyForcibly();
                }
                // For Windows OS, find the process bound to port 8080 and kill it.
                // todo - add commands for other OS such as linux.
                Process findProcess = portFinderBuilder.start();
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
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
