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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

import de.blizzy.backup.settings.Settings;

public class Database {
	private static final String DB_FOLDER_NAME = "$blizzysbackup"; //$NON-NLS-1$
	
	private File folder;

	public Database(Settings settings) {
		this.folder = new File(new File(settings.getOutputFolder()), DB_FOLDER_NAME);
	}
	
	public Connection openDatabaseConnection() throws SQLException {
		try {
			if (!folder.exists()) {
				FileUtils.forceMkdir(folder);
			}
			
			try {
				Class.forName("org.h2.Driver"); //$NON-NLS-1$
			} catch (ClassNotFoundException e) {
				throw new RuntimeException(e);
			}

			Connection conn = DriverManager.getConnection(
					"jdbc:h2:" + folder.getAbsolutePath() + "/backup" + //$NON-NLS-1$ //$NON-NLS-2$
					";LOCK_MODE=0;UNDO_LOG=0;FILE_LOCK=NO;CACHE_SIZE=65536", //$NON-NLS-1$
					"sa", StringUtils.EMPTY); //$NON-NLS-1$
			conn.setAutoCommit(true);
			return conn;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void releaseDatabaseConnection(Connection conn) {
		if (conn != null) {
			try {
				conn.close();
			} catch (SQLException e) {
				// ignore
			}
		}
	}

	public void runStatement(Connection conn, String sql) {
		Statement st = null;
		try {
			st = conn.createStatement();
			st.executeUpdate(sql);
		} catch (SQLException e) {
			throw new RuntimeException(e);
		} finally {
			closeQuietly(st);
		}
	}

	public void closeQuietly(Statement st) {
		if (st != null) {
			try {
				st.close();
			} catch (SQLException e) {
				// ignore
			}
		}
	}
	
	public void closeQuietly(PreparedStatement... pss) {
		for (PreparedStatement ps : pss) {
			closeQuietly(ps);
		}
	}
	
	private void closeQuietly(PreparedStatement ps) {
		if (ps != null) {
			try {
				ps.close();
			} catch (SQLException e) {
				// ignore
			}
		}
	}
	
	public void closeQuietly(ResultSet rs) {
		if (rs != null) {
			try {
				rs.close();
			} catch (SQLException e) {
				// ignore
			}
		}
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
}
