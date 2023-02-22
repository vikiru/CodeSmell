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

public class MainApp extends Application {

    public static InputStream cpgStream;

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

        // Build the UMLClass objects from the CPGClass objects
        // Get a hashmap to associate the latter with the former.
        HashMap<CPGClass, UMLClass> classMap = new HashMap<CPGClass, UMLClass>();
        initStatTracker(cpg); // todo: run this on another thread and join before
                              // smells are started
        for (CPGClass graphClass : cpg.getClasses()) {
            UMLClass c = new UMLClass(graphClass.name);
            classMap.put(graphClass, c);
            for (CPGClass.Method m : graphClass.getMethods()) {
                c.addMethod(m);
            }
            for (CPGClass.Attribute a : graphClass.getAttributes()) {
                c.addAttribute(a);
            }

            //for (Smell s : SmellDetector.getSmells(cpg)) {
            //    c.addSmell(s);
            //}
            // Render the class at (0, 0) so that it can be sized.
            // Will be moved later when lm.positionClasses() is called.
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