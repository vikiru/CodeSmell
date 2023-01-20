package com.CodeSmell.parser;

import java.io.File;
import java.io.InputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;

import com.CodeSmell.parser.Parser;

public class JoernServer {


	InputStream joernStream;

	private static class ThreadedReader extends Thread {

		private BufferedReader reader; 

		public ThreadedReader(BufferedReader reader) {
			this.reader = reader;
		}

		public void run() {
			while (true) {
				try {
					String line;
					while ((line = this.reader.readLine()) != null) {
					   System.out.println(line);
					}
				} catch (FileNotFoundException e) {
				} catch (IOException e) {
					e.printStackTrace();
					System.exit(1);
				}
			}
		}
	}

	public InputStream getStream() {
		return this.joernStream;
	}

	public void start(File directory) {
		if (!directory.isDirectory()) {
			throw new RuntimeException("JoernServer got bad directory: " + directory);
		}

		// Get the path to joern
		String joernPath = System.getProperty("user.home") + "/bin/joern/joern-cli";

		System.out.println("Starting Joern in directory: " + directory);
		String directoryPath = Paths.get("").toAbsolutePath() + "/src/main/python";

		// Start up a command prompt terminal (no popup) and start the joern server
		ProcessBuilder joernServerBuilder, joernQueryBuilder, windowsPortFinderBuilder;

		// Open terminal and start the joern server once process for joernServerBuilder starts
		windowsPortFinderBuilder = new ProcessBuilder("cmd.exe", "/c", "netstat -ano | findstr 8080");
		if (System.getProperty("os.name").contains("Windows")) {
			joernServerBuilder = new ProcessBuilder("cmd.exe", "/c", "joern", "--server");
		} else joernServerBuilder = new ProcessBuilder("joern", "--server");
		joernQueryBuilder = new ProcessBuilder("python", "joern_query.py", 
			directory.toString()).directory(new File(directoryPath));


		try {
			// Start the server
			Process joernServerProcess = joernServerBuilder.start();
			BufferedReader joernServerReader = new BufferedReader(
					new InputStreamReader(joernServerProcess.getInputStream()));
			BufferedReader errorReader = new BufferedReader(
					new InputStreamReader(joernServerProcess.getErrorStream()));
			new ThreadedReader(joernServerReader).start();
			new ThreadedReader(errorReader).start();
			// Execute queries against the local joern server instance.
			Process joernQueryProcess = joernQueryBuilder.start();

			// log joern_query.py standard error output
			new ThreadedReader(
				new BufferedReader(
				new InputStreamReader(
				joernQueryProcess.getErrorStream()))).start();

			// log joern_query.py loggin.info() output
			new ThreadedReader(
				new BufferedReader (
				new FileReader(
				Parser.JOERN_QUERY_LOGFILE))).start();

			this.joernStream = joernQueryProcess.getInputStream();
			System.out.println("Exiting JoernServer callstack");

		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
}
