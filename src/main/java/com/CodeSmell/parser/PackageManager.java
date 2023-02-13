package com.CodeSmell.parser;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;

public class PackageManager {
    public PackageManager(CodePropertyGraph cpg) {
        determineDistinctPackages(cpg);
    }

    /**
     * Iterate through the cpg to determine all the distinct packages and sub-packages, additionally creating
     * files which possess 1 or more classes each (accounts for nested classes).
     *
     * @param cpg The CodePropertyGraph containing all existing classes and relations
     */
    private static void determineDistinctPackages(CodePropertyGraph cpg) {
        // Get all package names, mapped to an array list of files
        TreeMap<String, ArrayList<Package.File>> packageNames = new TreeMap<>();
        ArrayList<Package> distinctPackages = new ArrayList<>();
        for (CPGClass cpgClass : cpg.getClasses()) {
            String packageName = cpgClass.packageName;
            String filePath = cpgClass.filePath;
            String fileName = cpgClass.name + ".java";
            packageNames.putIfAbsent(packageName, new ArrayList<>());
            if (filePath.contains(fileName)) {
                // Handle adding of new files and classes within those files
                Package.File newFile = new Package.File(fileName, filePath);
                var fileClasses = cpg.getClasses().stream().
                        filter(nestedClasses -> nestedClasses.filePath.equals(filePath)).
                        sorted(comparing(classes -> classes.classFullName)).collect(Collectors.toList());
                if (!fileClasses.isEmpty()) {
                    fileClasses.forEach(fileClass -> Package.File.addClass(newFile.classes, fileClass));
                }
                packageNames.get(packageName).add(newFile);
            }
        }
        // Create distinct package objects
        for (Map.Entry<String, ArrayList<Package.File>> entry : packageNames.entrySet()) {
            String packageName = entry.getKey();
            ArrayList<Package.File> files = entry.getValue();
            files.sort(comparing(file -> file.fileName.toLowerCase()));
            Package newPackage = new Package(packageName);
            files.forEach(file -> Package.addFile(newPackage.files, file));
            distinctPackages.add(newPackage);
        }
        // Add all subpackages, by checking length of packageName (greater packageName implies subPackage to current pkg)
        for (Package pkg : distinctPackages) {
            String packageName = pkg.packageName;
            int lastOccurrence = packageName.lastIndexOf(".");
            var result = distinctPackages.stream().
                    filter(p -> p.packageName.lastIndexOf(".") > lastOccurrence).collect(Collectors.toList());
            if (!result.isEmpty()) {
                result.forEach(pkgToAdd -> Package.addPackage(pkg.subPackages, pkgToAdd));
            }
        }
        // Finally add all packages to cpg
        distinctPackages.forEach(cpg::addPackage);
    }
}
