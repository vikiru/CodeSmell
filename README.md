# CodeSmell

## Building and Running

0.) Ensure the relevant enviornment variables are set. 
`export JAVA_HOME=/usr/lib/jvm/default-java/`

1.) Run `mvn clean` followed by `mvn package`

2.) Build the project files with Maven
`mvn install`

3.) In order to execute the main program: 
`mvn clean javafx:run`

In order to run the program through the executable jar via the terminal, the following command can be used:
`java -jar ./target/CodeSmell-1.0-SNAPSHOT-shaded.jar`

In order to do this the dependencies (JavaFX) must be copied into the ./target/dist/lib folder.
