import java.io.*;
import java.util.*;

import javax.swing.JFileChooser;
import javax.swing.JFrame;



public class folderExplorer {
    static final String javaSuffix = ".java";
    static Map<String,ArrayList<String>> classAndMethods = new HashMap<>();
    public static void main(String[] args) {
        checkMethodsInClasses(getAllJavaFiles(getDirectory()));
        classAndMethods.entrySet().forEach(entry -> {
            System.out.println(entry.getKey() + " " + entry.getValue());
        });
        System.exit(0);
    }

    public static String getDirectory()
    {
        JFrame frame = new JFrame("Swing Tester");
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int option = fileChooser.showOpenDialog(frame);
        if (option == JFileChooser.APPROVE_OPTION) {
            File directory = fileChooser.getSelectedFile();
            return directory.getAbsolutePath();
        }

        return "";
    }

    public static ArrayList<File> getAllJavaFiles(String directoryPath)
    {
        ArrayList<File> javaFiles = new ArrayList<>();
        File directory = new File(directoryPath);
        for (File file : directory.listFiles()) {
                if(file.getAbsolutePath().endsWith(javaSuffix))
                {
                    javaFiles.add(file);
                }
            }
        return javaFiles;
    }


    public static void checkMethodsInClasses(ArrayList<File> javaFiles)
    {
        boolean isString = false;
        String className = "";
        int brace = 0;
        boolean isBrace = false;
        int spaces = 0;
        for(File file: javaFiles)
        {
            try
            {
                ArrayList<String> methods = new ArrayList<>();
                Scanner scanner = new Scanner(new File(file.getAbsolutePath()));
                while (scanner.hasNextLine()) {
                    String line  = scanner.nextLine();
                    String nextLine = "";
                    if(scanner.hasNextLine())
                    {
                        nextLine = scanner.nextLine();
                    }
                    line +=nextLine;
                    for(char c: line.toCharArray())
                    {
                        if(c=='"' && !isString)
                        {
                            isString=true;
                        }
                        else if(c=='"' && isString)
                        {
                            isString = false;
                        }
                    }
                    if(line.contains("{"))
                    {
                        brace++;
                    }
                    if(line.contains("}"))
                    {
                        brace--;
                    }
                    if(line.contains("class")&&!isString)
                    {
                        int classPosition = line.indexOf("class")+"class".length();

                        ArrayList<Character> classNameInChars = new ArrayList<>();
                        for(char c:line.substring(classPosition).toCharArray())
                        {
                            if(c==' ' || c=='{')
                            {
                                spaces++;
                                continue;
                            }
                            if(spaces==1)
                            {
                                classNameInChars.add(c);
                            }
                            else if(spaces==2)
                            {
                                break;
                            }
                        }
                        className = getStringRepresentation(classNameInChars);
                    }
                    if( (line.contains("protected")||line.contains("private") || line.contains("public")) && (brace==2)
                    &&!isString &&!line.contains("class"))
                    {
                        ArrayList<Character> methodsNameInChars = new ArrayList<>();
                        int methodIndex = line.indexOf('(')-1;
                        while(!(line.charAt(methodIndex)==' '))
                        {
                            methodsNameInChars.add(line.charAt(methodIndex));
                            methodIndex--;
                        }
                        Collections.reverse(methodsNameInChars);
                        methods.add(getStringRepresentation(methodsNameInChars));
                    }
                }
                scanner.close();
                classAndMethods.put(className,methods);
            }
            catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            brace = 0;
            spaces = 0;
        }
    }


    static String getStringRepresentation(ArrayList<Character> list)
    {
        StringBuilder builder = new StringBuilder(list.size());
        for(Character ch: list)
        {
            builder.append(ch);
        }
        return builder.toString();
    }

}

