package jfx;
import java.io.File;
import java.net.URL;
import java.util.ListResourceBundle;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception{
        Parent root = FXMLLoader.load(getClass().getResource("jfx.fxml"));
        primaryStage.setScene(new Scene(root, 400, 300));
        primaryStage.setTitle("JavaFX WebView Example");
        
        String location = new File(System.getProperty("user.dir") + "/boxes.html"
                ).toURI().toURL().toExternalForm();

        System.out.println(location);

        WebView webView = new WebView();
        ClassLoader classLoader = getClass().getClassLoader();
        System.out.printf("Class Path: %s\n", classLoader.getResource(System.getProperty("user.dir") + "/boxes.html"));
        System.out.println("??????");
        
        webView.getEngine().load(location);
        
        //URL url = getClass().getResource("boxes.html");
        //webView.getEngine().load(url.toExternalForm());

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