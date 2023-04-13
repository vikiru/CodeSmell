# CodeSmell

## Project Description

A tool for detection and visualization of code smells for object-oriented languages.

<p align="center">
   <img src="https://user-images.githubusercontent.com/72267229/231621285-a35836f1-3135-45a5-b78f-f59e95df7a89.png">
   <p align="center"> Fig. 1 System Diagram of the CodeSmell tool </p>
   <p align="justify"> A diagram of the most important architectural components of the tool is shown above. In this package view, many components are omitted in order to keep the diagram readable and emphasize the important components. Java classes are shown in blue to differentiate them from components written in other languages. </p>
</p>

## Authors

- Golan Hassin
- Visakan Kirubakaran
- Sabin Plaiasu
- Martin Rivard

## Relevant Links
- [Wiki](https://github.com/vikiru/CodeSmell/wiki)
- [Documentation](#)
- [3-min Demo Video of Project](https://youtu.be/jmKbGEKAe0I)
- [Kanban Board](https://github.com/users/vikiru/projects/2)
- [Table of Tasks](https://github.com/users/vikiru/projects/2/views/5)

## Prerequisites

Make sure that the following prerequisites are successfully installed on your machine:

- Java - [Download & Install Java](https://www.java.com/en/download/manual.jsp). Download Java based on
  your operating system (JDK 11, 17 and 19 are fine to use).
- Maven - [Download & Install Maven](https://maven.apache.org/download.cgi). Choosing the latest version will suffice.
- Python - [Download & Install Python](https://www.python.org/downloads/). Python 3.7+ is required.
- Joern - Please see the [joern](https://github.com/joernio/joern) repository for more details on installing joern.
- GraphViz - [Download & Install GraphViz](https://graphviz.org/download/source/). Used to generate layout program


## Build and Run Instructions

### Setup

Ensure the relevant environment variables are set properly:

- `JAVA_HOME`
- `MAVEN_HOME`
- `joern`

`dot`, `joern`, `maven`, `java` and `python` must also be part of your system PATH variable to use this program.

### Build Instructions

Run the following command to clean and package the project:

```bash
mvn clean package
```

Build the project files with Maven:

```bash
mvn install
```

### Run Instructions

In order to execute the main program via the terminal, run the following:

```bash
mvn clean javafx:run
```

In order to run the program through the executable jar via the terminal, the following command can be used:

```bash
java -jar ./target/CodeSmell-1.0-SNAPSHOT-shaded.jar
```

## Testing Instructions

Run the following command to execute all of the tests:

```bash
mvn test
```

In order to skip invoking Joern (which can take a significant amount of time on older hardware), pass the command line argument to skip joern through maven like so:
```bash
mvn -Dskip=true javafx:run
```

## License
[MIT](https://github.com/vikiru/CodeSmell/blob/main/LICENSE) © 2023 Golan Hassin, Visakan Kirubakaran, Sabin Plaiasu, Martin Rivard.
