module jfx {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires javafx.web;
    requires java.desktop;
    requires com.google.gson;

    opens com.CodeSmell to javafx.fxml, com.google.gson;
    exports com.CodeSmell;
    exports com.CodeSmell.parser;
    opens com.CodeSmell.parser to com.google.gson, javafx.fxml;
}
