# CodeSmell

## Project Description

## Authors

- Golan Hassin
- Visakan Kirubakaran
- Sabin Plaiasu
- Marin Rivard
- Kshitj Sawhney

## Prerequisites

Make sure that the following prerequisites are successfully installed on your machine:

- Java - [Download & Install Java.](https://www.java.com/en/download/manual.jsp) Download Java based on
  your operating system.
- Maven - [Download & Install Maven.](https://maven.apache.org/download.cgi) Choosing the latest version will suffice.
- Python - [Download & Install Python.](https://www.python.org/downloads/) Python 3 is required.
- Joern - Please see the [joern](https://github.com/joernio/joern) repository for more details on installing joern.

## Build and Run Instructions

0.) Ensure the relevant enviornment variables are set.
`export JAVA_HOME=/usr/lib/jvm/default-java/`

1.) Run `mvn clean` followed by `mvn package`

2.) Build the project files with Maven:

`mvn install`

3.) In order to execute the main program:

`mvn clean javafx:run`

In order to run the program through the executable jar via the terminal, the following command can be used:

`java -jar ./target/CodeSmell-1.0-SNAPSHOT-shaded.jar`

In order to do this the dependencies (JavaFX) must be copied into the ./target/dist/lib folder.

## Testing Instructions

Run `mvn test` to execute all of the tests.