# CodeSmell

## Building and Running

1.) Ensure the relevant enviornment variables are set. 
`export JAVA_HOME=/usr/lib/jvm/default-java/`

2.) Build the project files with Maven
`mvn install`

3.) In order to execute the program: 
`mvn javafx:run`

In order to run the program manually the following command can be used:
`java --module-path ./target/dependency --add-modules javafx.controls,javafx.fxml,javafx.controls,javafx.media,javafx.web -jar ./target/CodeSmell-1.0-SNAPSHOT.jar`

In order to do this the dependencies (JavaFX) must be copied into the ./target/dependencies folder.
`mvn javafx:run`