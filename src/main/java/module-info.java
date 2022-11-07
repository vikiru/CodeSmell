module jfx {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires javafx.web;
    requires java.desktop;

    opens com.CodeSmell to javafx.fxml;
    exports com.CodeSmell;
}
