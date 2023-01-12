package com.CodeSmell;

import javafx.application.Application;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Worker;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

public class MainApp extends Application {

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

        for (CPGClass graphClass : cpg.getClasses()) {
            UMLClass c = new UMLClass(graphClass.name);
            classMap.put(graphClass, c);
            for (CPGClass.Method m : graphClass.methods) {
                c.addMethod(m);
            }
            for (CPGClass.Attribute a : graphClass.attributes) {
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
            UMLClass source, target;
            source = classMap.get(r.source);
            target = classMap.get(r.destination);
            ClassRelation cr = new ClassRelation(source, target, r.type, r.multiplicity);
            source.addRelationship(cr);
            target.addRelationship(cr);
            relations.add(cr);
        }
	LayoutManager lm = new LayoutManager(new ArrayList<UMLClass>(classMap.values()), relations);
    }

    private void removeWhenParserLambdaLimitationFixed(Worker.State newState) {
        if (newState == Worker.State.SUCCEEDED) {
            Parser p = new Parser();
            CodePropertyGraph cpg = p.initializeCPG("src/main/python/joernFiles/sourceCode.json");
            initializeMainView(cpg);
        }
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("jfx.fxml"));
        primaryStage.setScene(new Scene(root, 400, 300));
        primaryStage.setTitle("CodeSmell Detector");

        String location = new File(System.getProperty("user.dir") + "/boxes.html"
        ).toURI().toURL().toExternalForm();
        WebView webView = new WebView();
        webView.getEngine().load(location);

        URL url = getClass().getResource("boxes.html");
        WebEngine engine = webView.getEngine();
        webView.setZoom(1.0); // allow resizing for other resolutions
        RenderObject.addRenderEventListener(new WebControl(engine));

        engine.load(url.toExternalForm());
        engine.getLoadWorker().stateProperty().addListener((ov, oldState, newState) -> {
            removeWhenParserLambdaLimitationFixed(newState);
        });

        // hide scroll bars from the webview. source:
        // https://stackoverflow.com/questions/11206942/how-to-hide-scrollbars-in-the-javafx-webview
        webView.getChildrenUnmodifiable().addListener(new ListChangeListener<Node>() {
            @Override
            public void onChanged(Change<? extends Node> change) {
                Set<Node> deadSeaScrolls = webView.lookupAll(".scroll-bar");
                for (Node scroll : deadSeaScrolls) {
                    scroll.setVisible(false);
                }
            }
        });

        VBox vBox = new VBox(webView);
        Scene scene = new Scene(vBox, 960, 600);
        primaryStage.setScene(scene);
        primaryStage.show();
    }
}