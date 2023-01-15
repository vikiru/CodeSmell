package com.CodeSmell;

import java.io.File;
import java.io.InputStream;
import java.io.FileNotFoundException;
import java.io.FileInputStream;

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
            MainApp.joernStream = server.getStream();
            MainApp.skipJoern = false;
            MainApp.main(args);
        } else {
            try {
                MainApp.joernStream = new FileInputStream(new File
                    (Parser.CPG_BACKUP_JSON));
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
