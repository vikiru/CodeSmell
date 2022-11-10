package com.CodeSmell;

import java.util.Set;
import java.io.File;
import java.net.URL;
import java.util.ListResourceBundle;
import java.util.ArrayList;
import java.util.HashMap;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.scene.web.WebEngine;
import javafx.stage.Stage;
import javafx.scene.Node;
import javafx.concurrent.Worker;
import javafx.collections.ListChangeListener;
import javafx.collections.ListChangeListener.Change;
import com.CodeSmell.WebControl;
import com.CodeSmell.UMLClass;
import com.CodeSmell.LayoutManager;
import com.CodeSmell.CPGClass;
import com.CodeSmell.CodePropertyGraph;
import com.CodeSmell.RenderObject;

public class MainApp extends Application {

    public void initializeMainView(CodePropertyGraph cpg) {
        /** 
         *  Renders the UML diagram
         * 
         * */

        // Build the UMLClass objects from the CPGClass objects
        // Get a hashmap to associate the latter with the former.
        HashMap<CPGClass, UMLClass> classMap = new HashMap<CPGClass, UMLClass>();

        for ( CPGClass graphClass : cpg.getClasses() ) {
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
        for ( CodePropertyGraph.Relation r : cpg.getRelations() ) {
            UMLClass source, target;
            source = classMap.get(r.source);
            target = classMap.get(r.destination);
            ClassRelation cr = new ClassRelation(source, target, r.type);
            source.addRelationship(cr);
            target.addRelationship(cr);
            relations.add(cr);
        }

        LayoutManager lm = new LayoutManager();
        lm.positionClasses(new ArrayList<UMLClass>(classMap.values()));
        lm.setRelationPaths(relations);
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
        RenderObject.addRenderEventListener(new WebControl(engine));

        engine.load(url.toExternalForm());
        engine.getLoadWorker().stateProperty().addListener((ov, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                Parser p = new Parser();
                CodePropertyGraph cpg = p.buildCPG("");
                initializeMainView(cpg);
            }
        });

        // hide scroll bars from the webview. source: 
        // https://stackoverflow.com/questions/11206942/how-to-hide-scrollbars-in-the-javafx-webview
        webView.getChildrenUnmodifiable().addListener(new ListChangeListener<Node>() {
          @Override public void onChanged(Change<? extends Node> change) {
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


    public static void main(String[] args) {
        launch(args);
    }
}