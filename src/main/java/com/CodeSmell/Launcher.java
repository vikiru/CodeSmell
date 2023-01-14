package com.CodeSmell;

public class Launcher {
    public static void main(String[] args) {
        // Run a local joern server instance and execute queries against the imported source code's cpg using joern and create a .json
        // representation of the source code.
        boolean skipJoern = false;
        for (String arg : args) {
            if (arg.equals("--skip-joern=true")) {
                skipJoern = true;
            }
        }
        if (!skipJoern) {
            JoernServer server = new JoernServer();
            server.start(false);
            MainApp.joernReader = server.getReader();
        }
        MainApp.main(args);
    }
}
