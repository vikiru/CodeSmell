# CodeSmell

## Project Description

A tool for detection and visualization of code smells for object-oriented languages.

## Authors

- Golan Hassin
- Visakan Kirubakaran
- Sabin Plaiasu
- Martin Rivard
- Kshitij Sawhney

## Relevant Links

- [Chart of Each Member's Progress](https://github.com/users/vikiru/projects/2/insights/1)
- [Chart of Each Milestone's Progress](https://github.com/users/vikiru/projects/2/insights/4)
- [Documentation](#)
- [Kanban Board](https://github.com/users/vikiru/projects/2)

## Prerequisites

Make sure that the following prerequisites are successfully installed on your machine:

- Java - [Download & Install Java.](https://www.java.com/en/download/manual.jsp) Download Java based on
  your operating system.
- Maven - [Download & Install Maven.](https://maven.apache.org/download.cgi) Choosing the latest version will suffice.
- Python - [Download & Install Python.](https://www.python.org/downloads/) Python 3.7+ is required.
- Joern - Please see the [joern](https://github.com/joernio/joern) repository for more details on installing joern.

## Build and Run Instructions

### Setup

Ensure the relevant environment variables are set properly:

- `export JAVA_HOME=/usr/lib/jvm/default-java/`
- `MAVEN_HOME`

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

To do this, the dependencies (JavaFX) must be copied into the ./target/dist/lib folder.

## Usage

## Testing Instructions

Run the following command to execute all of the tests:

```bash
mvn test
```