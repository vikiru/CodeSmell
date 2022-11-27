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
        ProcessBuilder builder, builder2;

        // Open terminal and start the joern server once process for builder starts
        // Different commands for windows and linux
        if (System.getProperty("os.name").contains("Windows")) {
            builder = new ProcessBuilder("cmd.exe", "/c", "cd " + joernPath, "& joern --server");
            builder2 = new ProcessBuilder("cmd.exe", "/c", "cd " + directoryPath + "& python joern_query.py");
        } else {
            builder = new ProcessBuilder("sh", "-c", "joern --server");
            builder2 = new ProcessBuilder("sh", "-c", "cd " + directoryPath + "python joern_query.py");
        }

        try {
            // Start the server
            Process joernServerProcess = builder.start();
            BufferedReader joernServerReader = new BufferedReader(new InputStreamReader(joernServerProcess.getInputStream()));
            System.out.println(joernServerReader.readLine());

            // Execute queries against the local joern server instance.
            Process joernQueryProcess = builder2.start();
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
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
