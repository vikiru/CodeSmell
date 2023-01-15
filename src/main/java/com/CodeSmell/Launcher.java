package com.CodeSmell;

import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.FileNotFoundException;

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
            server.start(Launcher.chooseDirectory());
            MainApp.joernReader = server.getReader();
            MainApp.skipJoern = false;
            MainApp.main(args);
        } else {
            try {
                MainApp.joernReader = new BufferedReader(new FileReader(Parser.CPG_BACKUP_JSON));
            } catch (FileNotFoundException e) {
                throw new RuntimeException("-Dskip=true, but CPG JSON not found on drive");
            }
            MainApp.skipJoern = true;
            MainApp.main(args);
        }
    }

    public static String chooseDirectory()
    {
        return "src/test/java/com/testproject";
    }

}
