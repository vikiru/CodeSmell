package com.CodeSmell;

import com.CodeSmell.parser.JoernServer;
import com.CodeSmell.parser.Parser;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.InputStream;
import java.io.FileNotFoundException;
import java.io.FileInputStream;
import javax.swing.*;
import java.awt.event.*;
import java.awt.*;
import java.util.*;

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
            File directory = Launcher.chooseDirectory();
            if (directory!=null) {
                server.start(directory);
                MainApp.cpgStream = server.getStream();
                MainApp.skipJoern = false;
                MainApp.main(args);
            }
            else {
                System.out.println("No Selection ");
            }
        } else {
            MainApp.skipJoern = true;
            MainApp.main(args);
        }
    }

    public static File chooseDirectory()
    {

        JFileChooser chooser;
        File choosertitle;
        chooser = new JFileChooser();
        chooser.setCurrentDirectory(new java.io.File("."));
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.showOpenDialog(null);
        //
        // disable the "All files" option.
        //
        chooser.setAcceptAllFileFilterUsed(false);
        choosertitle = chooser.getSelectedFile();
        //
        if (choosertitle!=null) {
            System.out.println("getCurrentDirectory(): "
                    +  chooser.getCurrentDirectory());
            System.out.println("getSelectedFile() : "
                    +  chooser.getSelectedFile());
        }
        else {
            System.out.println("No Selection ");
        }
        //return new File("D:/Git/4907Project/src/test/java\\com\\testproject");
        //return new File("/home/sabin/Downloads/sysc3110-risk/");
        return choosertitle;
    }
}
