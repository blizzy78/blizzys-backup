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
package de.blizzy.backup;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.commons.io.FileUtils;

class Database {
	private File folder;

	Database(File folder) {
		this.folder = folder;
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
					"sa", ""); //$NON-NLS-1$ //$NON-NLS-2$
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

	void runStatement(Connection conn, String sql) {
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

	void closeQuietly(Statement st) {
		if (st != null) {
			try {
				st.close();
			} catch (SQLException e) {
				// ignore
			}
		}
	}
	
	void closeQuietly(PreparedStatement... pss) {
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
	
	void closeQuietly(ResultSet rs) {
		if (rs != null) {
			try {
				rs.close();
			} catch (SQLException e) {
				// ignore
			}
		}
	}
}
