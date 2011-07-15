/*
blizzy's Backup - Easy to use personal file backup application
Copyright (C) 2011 Maik Schreiber

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package de.blizzy.backup.database;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.jooq.Cursor;
import org.jooq.impl.Factory;

import de.blizzy.backup.database.schema.PublicFactory;
import de.blizzy.backup.settings.Settings;

public class Database {
	private static final String DB_FOLDER_NAME = "$blizzysbackup"; //$NON-NLS-1$
	
	private File folder;
	private Connection conn;
	private Factory factory;

	public Database(Settings settings) {
		this.folder = new File(new File(settings.getOutputFolder()), DB_FOLDER_NAME);
	}
	
	public void open() throws SQLException {
		if (conn == null) {
			try {
				if (!folder.exists()) {
					FileUtils.forceMkdir(folder);
				}
				
				try {
					Class.forName("org.h2.Driver"); //$NON-NLS-1$
				} catch (ClassNotFoundException e) {
					throw new RuntimeException(e);
				}
	
				conn = DriverManager.getConnection(
						"jdbc:h2:" + folder.getAbsolutePath() + "/backup" + //$NON-NLS-1$ //$NON-NLS-2$
						";CACHE_SIZE=65536", //$NON-NLS-1$
						"sa", StringUtils.EMPTY); //$NON-NLS-1$
				conn.setAutoCommit(true);
				factory = new PublicFactory(conn);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	public void close() {
		if (conn != null) {
			try {
				conn.close();
			} catch (SQLException e) {
				// ignore
			} finally {
				conn = null;
				factory = null;
			}
		}
	}
	
	public Factory factory() {
		return factory;
	}

	public static boolean containsDatabaseFolder(File folder) {
		return new File(folder, DB_FOLDER_NAME).isDirectory();
	}

	public void backupDatabase(File targetFolder) throws IOException {
		copyFolder(folder, targetFolder);
	}

	private void copyFolder(File folder, File targetFolder) throws IOException {
		FileUtils.forceMkdir(targetFolder);
		
		for (File file : folder.listFiles()) {
			if (file.isDirectory()) {
				copyFolder(file, new File(targetFolder, file.getName()));
			} else {
				Files.copy(file.toPath(), new File(targetFolder, file.getName()).toPath());
			}
		}
	}

	public void initialize(String sampleBackupPath) {
		try {
			factory.query("CREATE TABLE IF NOT EXISTS backups (" + //$NON-NLS-1$
					"id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, " + //$NON-NLS-1$
					"run_time DATETIME NOT NULL, " + //$NON-NLS-1$
					"num_entries INT NULL" + //$NON-NLS-1$
					")") //$NON-NLS-1$
					.execute();
			
			factory.query("CREATE TABLE IF NOT EXISTS files (" + //$NON-NLS-1$
					"id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, " + //$NON-NLS-1$
					"backup_path VARCHAR(" + sampleBackupPath.length() + ") NOT NULL, " + //$NON-NLS-1$ //$NON-NLS-2$
					"checksum VARCHAR(" + DigestUtils.md5Hex(StringUtils.EMPTY).length() + ") NOT NULL, " + //$NON-NLS-1$ //$NON-NLS-2$
					"length BIGINT NOT NULL" + //$NON-NLS-1$
					")") //$NON-NLS-1$
					.execute();
			factory.query("CREATE INDEX IF NOT EXISTS idx_old_files ON files " + //$NON-NLS-1$
					"(checksum, length)") //$NON-NLS-1$
					.execute();
			
			factory.query("CREATE TABLE IF NOT EXISTS entries (" + //$NON-NLS-1$
					"id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, " + //$NON-NLS-1$
					"parent_id INT NULL, " + //$NON-NLS-1$
					"backup_id INT NOT NULL, " + //$NON-NLS-1$
					"type TINYINT NOT NULL, " + //$NON-NLS-1$
					"creation_time DATETIME NULL, " + //$NON-NLS-1$
					"modification_time DATETIME NULL, " + //$NON-NLS-1$
					"hidden BOOLEAN NOT NULL, " + //$NON-NLS-1$
					"name VARCHAR(1024) NOT NULL, " + //$NON-NLS-1$
					"file_id INT NULL" + //$NON-NLS-1$
					")") //$NON-NLS-1$
					.execute();
			factory.query("CREATE INDEX IF NOT EXISTS idx_entries_files ON entries " + //$NON-NLS-1$
					"(file_id)") //$NON-NLS-1$
					.execute();
			factory.query("CREATE INDEX IF NOT EXISTS idx_folder_entries ON entries " + //$NON-NLS-1$
					"(backup_id, parent_id)") //$NON-NLS-1$
					.execute();
			
			factory.query("ANALYZE") //$NON-NLS-1$
					.execute();
		} catch (SQLException e) {
		}
	}

	public void closeQuietly(Cursor<?> cursor) {
		if (cursor != null) {
			try {
				cursor.close();
			} catch (SQLException e) {
				// ignore
			}
		}
	}
}
