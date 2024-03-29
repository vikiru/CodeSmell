module jfx {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires javafx.web;
    requires java.desktop;
    requires com.google.gson;

    exports com.CodeSmell;
    exports com.CodeSmell.model;
    opens com.CodeSmell.model to com.google.gson, javafx.fxml;
    opens com.CodeSmell to javafx.fxml, com.google.gson;
    exports com.CodeSmell.parser;
    opens com.CodeSmell.parser to com.google.gson, javafx.fxml;
    exports com.CodeSmell.smell;
    opens com.CodeSmell.smell to com.google.gson, javafx.fxml;
    exports com.CodeSmell.view;
    opens com.CodeSmell.view to com.google.gson, javafx.fxml;
    exports com.CodeSmell.stat;
    opens com.CodeSmell.stat to com.google.gson, javafx.fxml;
}
