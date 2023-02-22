package com.CodeSmell.parser;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * A class that is meant to represent that packages that exist within a given codebase.
 */
public final class Package implements Serializable {

    /**
     * The name of the package
     */
    public final String packageName;

    /**
     * All the files contained within the package
     */
    public final ArrayList<File> files = new ArrayList<>();

    /**
     * All the subpackages that exist within the package, if any
     */
    public final ArrayList<Package> subPackages = new ArrayList<>();

    public Package(String packageName) {
        this.packageName = packageName;
    }

    /**
     * Add sub-packages to the current Package
     *
     * @param packageToAdd The package to be added to the current Package
     */
    public static void addPackage(ArrayList<Package> subPackages, Package packageToAdd) {
        subPackages.add(packageToAdd);
    }

    /**
     * Add files to the current Package
     */
    public static void addFile(ArrayList<File> classes, File fileToAdd) {
        classes.add(fileToAdd);
    }

    @Override
    public String toString() {
        return "Package{" +
                "packageName='" + packageName + '\'' +
                ", files=" + files +
                ", subPackages=" + subPackages +
                '}';
    }

    /**
     * A file that exists within a package, can contain 1 or more classes
     */
    public static final class File implements Serializable {
        /**
         * The name of the file (e.g. CPGClass.java)
         */
        public final String fileName;

        /**
         * The full filePath of the file
         */
        public final String filePath;

        /**
         * All the classes that reside within the file
         */
        public final ArrayList<CPGClass> classes = new ArrayList<>();

        public File(String fileName, String filePath) {
            this.fileName = fileName;
            this.filePath = filePath;
        }

        /**
         * Add classes to the current File
         *
         * @param cpgClassToAdd - The class to be added to the current Package
         */
        public static void addClass(ArrayList<CPGClass> classes, CPGClass cpgClassToAdd) {
            classes.add(cpgClassToAdd);
        }

        @Override
        public String toString() {
            return "File{" +
                    "fileName='" + fileName + '\'' +
                    ", filePath='" + filePath + '\'' +
                    ", classes=" + classes +
                    '}';
        }
    }

}
