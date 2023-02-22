package com.CodeSmell;

import com.CodeSmell.parser.JoernServer;
import com.CodeSmell.parser.Parser;
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
            MainApp.cpgStream = server.getStream();
            MainApp.skipJoern = false;
            MainApp.main(args);
        } else {
            MainApp.skipJoern = true;
            MainApp.main(args);
        }
    }

    public static File chooseDirectory()
    {   
        //return new File("src/main/java/com/CodeSmell");
        return new File("/home/sabin/Documents/oldfiles/L1G103303/");
    }
}
