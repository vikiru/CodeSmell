package com.CodeSmell;

import java.nio.file.StandardCopyOption;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InvalidClassException;
import static java.nio.file.Files.move;

import com.CodeSmell.parser.Parser;
import com.CodeSmell.parser.CodePropertyGraph;
import com.CodeSmell.parser.JoernServer;

import java.util.Date;

public class ProjectManager {

	private static Parser parser = new Parser();
	private static final Project[] testProjects = new Project[] {
		new Project("testproject", "src/test/java/com/testproject"),
		new Project("own", "src/main/java/com/CodeSmell")
	};

	private final static String BACKUP_DIRECTORY = "src/test/java/com/CodeSmell/backups/";
	static { 
		new File(BACKUP_DIRECTORY).mkdir();
	}

	private static class Project {
		public final String name;
		public final File directory;
		private final File backup;
		private CodePropertyGraph cpg;

		public Project(String name, String directory) {
			this.name = name;
			this.directory = new File(directory);
			this.backup = new File(String.format(
				"%s/%s.bac", BACKUP_DIRECTORY, this.name));
		}

		private void loadFromJoern() {
			JoernServer server = new JoernServer();
			server.start(this.directory);
			// moves the Parser's backup file to the backup directory
			try {
				this.cpg = parser.initializeCPG(server.getStream(), false);
				move(
					Parser.CPG_BACKUP_JSON.toPath(),
					this.backup.toPath(), 
					StandardCopyOption.REPLACE_EXISTING);
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(1);
			}
		}

		private void loadFromBackup() {
			try {
				this.cpg = Parser.initializeCPG(
					new FileInputStream(this.backup), true);
			} catch (InvalidClassException e) {
				System.out.println("Serialization failed. Loading joern.");
				loadFromJoern();
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(1);
			}
		}

		private boolean compareDirectoryModificationTime(File directory, 
				long modificationTime) {
			if (!directory.isDirectory()) {
				return directory.lastModified() > modificationTime;
			}
			for (File f : directory.listFiles()) {
				if (compareDirectoryModificationTime(f, modificationTime)) {
					System.out.println("Edit in file " + f);
					System.out.println(f.lastModified());
					return true;
				}
			}
			return false;
		}

		public void load() {
			try {
				if (!compareDirectoryModificationTime(this.directory, 
						this.backup.lastModified())) {
					System.out.println("Loading backup");
					loadFromBackup();
				} else {
					loadFromJoern();
				}
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(1);
			}
		}

		public CodePropertyGraph getCPG() {
			return this.cpg;
		}
	}

	public static CodePropertyGraph getCPG(String name) {
		for (Project project : testProjects) {
			if (project.name.equals(name)) {
				CodePropertyGraph cpg = project.getCPG();
				if (cpg == null) {
					project.load();
					return project.getCPG();
				}
				return cpg;
			}
		}
		throw new IllegalArgumentException("No project with the name " + name);
	}
}