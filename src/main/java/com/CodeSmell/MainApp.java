package com.CodeSmell;

import java.util.Set;
import java.io.File;
import java.net.URL;
import java.util.ListResourceBundle;

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

public class MainApp extends Application {

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
        WebControl controller = new WebControl(engine);
        LayoutManager lm = new LayoutManager();

        engine.load(url.toExternalForm());
        engine.getLoadWorker().stateProperty().addListener((ov, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                UMLClass c = new UMLClass(controller);
                c.addField(true, "methodOne(int x, int y): int");
                c.addField(true, "methodTwo(int x, int y): int");
                UMLClass c2 = new UMLClass(controller);
                c2.addField(true, "c2MethodOne(int x, int y): int");
                c2.addField(true, "c2MethodTwo(int x, int y): int");
                c.render(lm);
                c2.render(lm);
                // controller.renderClass(c);
                // controller.renderClass(c2);
                // engine.executeScript("init()");
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
        primaryStage.show();
    }


    public static void main(String[] args) {
        launch(args);
    }
}