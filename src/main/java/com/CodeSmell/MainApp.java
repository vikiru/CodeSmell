package com.CodeSmell;

import com.CodeSmell.model.ClassRelation;
import com.CodeSmell.model.RenderObject;
import com.CodeSmell.model.UMLClass;
import com.CodeSmell.parser.CPGClass;
import com.CodeSmell.parser.CodePropertyGraph;
import com.CodeSmell.parser.Parser;
import com.CodeSmell.control.LayoutManager;
import com.CodeSmell.view.WebBridge;
import javafx.application.Application;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Worker;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Screen;
import javafx.stage.Stage;

import static com.CodeSmell.smell.Common.initStatTracker;
import static com.CodeSmell.smell.Common.buildSmellStream;
import com.CodeSmell.smell.Smell;

import java.io.InvalidClassException;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.stream.Stream;


public class MainApp extends Application {

    public static InputStream cpgStream;

    public void printSmellDetections(Smell smell) {
        System.out.println("\n=============================");
        System.out.println("smell " + smell.name);
        System.out.println("=============================\n");
        while (smell.detect()) {
            System.out.println("Detection: ");
            System.out.println(smell.lastDetection);
        }
    }

    static {
        cpgStream = getBackupStream();
    }

    private static FileInputStream getBackupStream() {
        try {
            return new FileInputStream(Parser.CPG_BACKUP_JSON);
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    public static boolean skipJoern;

    public static void main(String[] args) {
        launch(args);
    }

    public void initializeMainView(CodePropertyGraph cpg) {
        /**
         *  Renders the UML diagram
         *
         * */
        //NEEDS TO TAKE IN A LIST OF CODE SMELLS
        //NEED TO ADD PROPERTY TO UMLCLASS FOR CODE SMELL (LIST OF SMELLS)
        // Build the UMLClass objects from the CPGClass objects
        // Get a hashmap to associate the latter with the former.
        //Comapre with ==
        HashMap<CPGClass, UMLClass> classMap = new HashMap<CPGClass, UMLClass>();

        /*
        Method m
        for(Class class : classes)
        {
         for(Method m2: in class)
         {
            m2 == m
            return the class
         }
        }
         */

        for (CPGClass graphClass : cpg.getClasses()) {
            UMLClass c = new UMLClass(graphClass.name, graphClass.getSmells());
            classMap.put(graphClass, c);
            for (CPGClass.Method m : graphClass.getMethods()) {
                c.addMethod(m);
            }
            for (CPGClass.Attribute a : graphClass.getAttributes()) {
                c.addAttribute(a);
            }

            c.render();
        }

        // Build the ClassRelation objects from the CPGClass.Relation objects
        ArrayList<ClassRelation> relations = new ArrayList<ClassRelation>();
        for (CodePropertyGraph.Relation r : cpg.getRelations()) {
            System.out.println(r);
            UMLClass source, target;
            source = classMap.get(r.source);
            target = classMap.get(r.destination);
            ClassRelation cr = new ClassRelation(source, target, r.type, r.multiplicity);
            source.addRelationship(cr);
            target.addRelationship(cr);
            relations.add(cr);
            System.out.println(cr);
        }

        ArrayList<UMLClass> umlClasses = new ArrayList<UMLClass>(classMap.values());

        try {   
            LayoutManager.setLayout(umlClasses, relations);
        } catch (IOException e) {
            throw new RuntimeException(
                    "Error invoking graphviz binary (dot)\n" + e);
        }

    }

    private void removeWhenParserLambdaLimitationFixed(Worker.State newState) {
        if (newState == Worker.State.SUCCEEDED) {
            try {
                if (cpgStream.available() == 0 && skipJoern) {
                    cpgStream = getBackupStream();
                }
                CodePropertyGraph cpg = Parser.initializeCPG(cpgStream, skipJoern);
                initStatTracker(cpg); // todo: run this on another thread and join before
                // smells are started
                Stream<Smell> smells = buildSmellStream(cpg);
                //Convert smells into array that can be parsed
                Smell[] smellsArray = smells.toArray(Smell[]::new);

                //Go through each smell
                for (int i = 0; i < smellsArray.length; i++)
                {
                    Smell currentSmell =  smellsArray[i];

                    while(currentSmell.getDetections()!=null && !currentSmell.getDetections().isEmpty()) {
                        Smell.CodeFragment smellFragment = currentSmell.detectNext();
                        //Detect all the smells and add them to their respective classes
                        if (smellFragment != null) {
                            if (smellFragment.classes != null && smellFragment.classes.length > 0) {
                                for (CPGClass classes : smellFragment.classes) {
                                    classes.addSmell(currentSmell);
                                }
                            } else if (smellFragment.methods != null && smellFragment.methods.length > 0) {
                                for (CPGClass.Method methods : smellFragment.methods) {
                                    methods.getParent().addSmell(currentSmell);
                                }
                            } else if (smellFragment.attributes != null && smellFragment.attributes.length > 0) {
                                for (CPGClass.Attribute smellAttribute : smellFragment.attributes) {
                                    smellAttribute.getParent().addSmell(currentSmell);

                                }
                            }
                        }
                    }
                }
                        //If the codeFragment i.e. the smell has a class attribute, it can
                        //just be added to the class object itself.
                //get the class from the smell and add to the class object
                //If not class level smell call the helper (statTracker)
                //Make fragment from non code level smells
                //smells.
                //smells.forEach(s -> printSmellDetections(s));
                initializeMainView(cpg);
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("jfx.fxml"));
        Rectangle2D screenBounds = Screen.getPrimary().getBounds();
        String location = new File(System.getProperty("user.dir") + "/boxes.html"
        ).toURI().toURL().toExternalForm();

        double startWidth = screenBounds.getWidth() / 2;
        double startHeight = screenBounds.getHeight() * 0.8;
        WebView webView = new WebView();
        webView.setMaxSize(screenBounds.getWidth(), screenBounds.getHeight());
        webView.prefHeightProperty().bind(primaryStage.heightProperty());
        webView.prefWidthProperty().bind(primaryStage.heightProperty());
        webView.getEngine().load(location);
        VBox vBox = new VBox(webView);
        Scene scene = new Scene(vBox, startWidth, startHeight);
        primaryStage.setScene(scene);
        primaryStage.setTitle("CodeSmell Detector");

        URL url = getClass().getResource("boxes.html");
        WebEngine engine = webView.getEngine();
        webView.setZoom(1.0); // allow resizing for other resolutions
        RenderObject.addRenderEventListener(new WebBridge(engine));

        engine.load(url.toExternalForm());
        engine.getLoadWorker().stateProperty().addListener(
                (ov, oldState, newState) -> {
                    removeWhenParserLambdaLimitationFixed(newState);
                });


        // hide scroll bars from the webview. source:
        // https://stackoverflow.com/questions/11206942/how-to-hide-scrollbars-in-the-javafx-webview
        webView.getChildrenUnmodifiable().addListener(new ListChangeListener<Node>() {
            @Override
            public void onChanged(Change<? extends Node> change) {
                Set<Node> scrollBar = webView.lookupAll(".scroll-bar");
                for (Node scroll : scrollBar) {
                    scroll.setVisible(false);
                }
            }
        });
        primaryStage.show();
    }
}