package com.CodeSmell;

import javax.swing.*;
import java.io.File;

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
        Launcher l = new Launcher();
        String directory = l.chooseDirectory();
        if (!skipJoern) JoernServer.start(false, directory);
        MainApp.main(args, directory);
    }

    public String chooseDirectory()
    {
        try {
            UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (UnsupportedLookAndFeelException e) {
            throw new RuntimeException(e);
        }
        JFileChooser fc=new JFileChooser();
        fc.setFileSelectionMode(1);
        fc.showOpenDialog(null);

        File selectedDirectory = fc.getSelectedFile();
        return selectedDirectory.getPath();
    }

}
