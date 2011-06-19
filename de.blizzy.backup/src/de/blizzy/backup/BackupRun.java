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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.FileTime;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.eclipse.core.runtime.ISafeRunnable;
import org.eclipse.core.runtime.SafeRunner;

class BackupRun implements Runnable {
	private static final DateFormat BACKUP_PATH_FORMAT =
		new SimpleDateFormat("yyyy'/'MM'/'dd'/'HHmm"); //$NON-NLS-1$
	
	private Set<String> folders;
	private String outputFolder;
	private Thread thread;
	private Database database;
	private Connection conn;
	private PreparedStatement psIdentity;
	private PreparedStatement psNewEntry;
	private PreparedStatement psNewFile;
	private PreparedStatement psOldFile;
	private int backupId;
	private List<IBackupRunListener> listeners = new ArrayList<IBackupRunListener>();
	private String currentFile;
	private boolean running = true;

	public BackupRun(Set<String> folders, String outputFolder) {
		this.folders = folders;
		this.outputFolder = outputFolder;
	}

	void runBackup() {
		thread = new Thread(this, "Backup"); //$NON-NLS-1$
		thread.start();
	}

	private void initDatabase() {
		database.runStatement(conn, "CREATE TABLE IF NOT EXISTS backups (" + //$NON-NLS-1$
				"id INT NOT NULL AUTO_INCREMENT, " + //$NON-NLS-1$
				"run_time DATETIME NOT NULL" + //$NON-NLS-1$
				")"); //$NON-NLS-1$
		database.runStatement(conn, "CREATE UNIQUE INDEX IF NOT EXISTS idx_backups ON backups " + //$NON-NLS-1$
				"(id)"); //$NON-NLS-1$
		
		database.runStatement(conn, "CREATE TABLE IF NOT EXISTS files (" + //$NON-NLS-1$
				"id INT NOT NULL AUTO_INCREMENT, " + //$NON-NLS-1$
				"backup_path VARCHAR(" + createBackupFilePath().length() + ") NOT NULL, " + //$NON-NLS-1$ //$NON-NLS-2$
				"checksum VARCHAR(" + DigestUtils.md5Hex(StringUtils.EMPTY).length() + ") NOT NULL, " + //$NON-NLS-1$ //$NON-NLS-2$
				"length INT NOT NULL" + //$NON-NLS-1$
				")"); //$NON-NLS-1$
		database.runStatement(conn, "CREATE UNIQUE INDEX IF NOT EXISTS idx_files ON files " + //$NON-NLS-1$
				"(id)"); //$NON-NLS-1$
		database.runStatement(conn, "CREATE INDEX IF NOT EXISTS idx_old_files ON files " + //$NON-NLS-1$
				"(checksum, length)"); //$NON-NLS-1$
		
		database.runStatement(conn, "CREATE TABLE IF NOT EXISTS entries (" + //$NON-NLS-1$
				"id INT NOT NULL AUTO_INCREMENT, " + //$NON-NLS-1$
				"parent_id INT NULL, " + //$NON-NLS-1$
				"backup_id INT NOT NULL, " + //$NON-NLS-1$
				"type INT NOT NULL, " + //$NON-NLS-1$
				"creation_time DATETIME NULL, " + //$NON-NLS-1$
				"modification_time DATETIME NULL, " + //$NON-NLS-1$
				"hidden INT NOT NULL, " + //$NON-NLS-1$
				"name VARCHAR(1024) NOT NULL, " + //$NON-NLS-1$
				"file_id INT NULL" + //$NON-NLS-1$
				")"); //$NON-NLS-1$
		database.runStatement(conn, "CREATE UNIQUE INDEX IF NOT EXISTS idx_entries ON entries " + //$NON-NLS-1$
				"(id)"); //$NON-NLS-1$
		database.runStatement(conn, "CREATE INDEX IF NOT EXISTS idx_folder_entries ON entries " + //$NON-NLS-1$
				"(backup_id, parent_id)"); //$NON-NLS-1$
		
		database.runStatement(conn, "ANALYZE"); //$NON-NLS-1$
	}

	public void run() {
		BackupPlugin.getDefault().logMessage("Starting backup"); //$NON-NLS-1$
		
		File dbFolder = new File(outputFolder, "$blizzysbackup"); //$NON-NLS-1$
		database = new Database(dbFolder);
		try {
			conn = database.openDatabaseConnection();
			initDatabase();
			
			psIdentity = conn.prepareStatement("SELECT IDENTITY()"); //$NON-NLS-1$
			psNewEntry = conn.prepareStatement("INSERT INTO entries " + //$NON-NLS-1$
					"(parent_id, backup_id, type, creation_time, modification_time, hidden, " + //$NON-NLS-1$
					"name, file_id) " + //$NON-NLS-1$
					"VALUES (?, ?, ?, ?, ?, ?, ?, ?)"); //$NON-NLS-1$
			psNewFile = conn.prepareStatement("INSERT INTO files " + //$NON-NLS-1$
					"(backup_path, checksum, length) VALUES (?, ?, ?)"); //$NON-NLS-1$
			psOldFile = conn.prepareStatement("SELECT id FROM files WHERE " + //$NON-NLS-1$
					"(checksum = ?) AND (length = ?)"); //$NON-NLS-1$

			PreparedStatement ps = conn.prepareStatement("INSERT INTO backups (run_time) VALUES (?)"); //$NON-NLS-1$
			ps.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
			ps.executeUpdate();
			database.closeQuietly(ps);
			backupId = getLastIdentity();
			
			for (String folder : folders) {
				if (!running) {
					break;
				}

				try {
					backupFolder(new File(folder), -1, folder);
				} catch (IOException e) {
					// TODO
				}
			}
			
			database.runStatement(conn, "ANALYZE"); //$NON-NLS-1$
		} catch (SQLException e) {
			BackupPlugin.getDefault().logError("Error while running backup", e); //$NON-NLS-1$
		} catch (RuntimeException e) {
			BackupPlugin.getDefault().logError("Error while running backup", e); //$NON-NLS-1$
		} finally {
			database.closeQuietly(psIdentity, psNewEntry, psNewFile, psOldFile);
			database.releaseDatabaseConnection(conn);
			
			fireBackupEnded();
			
			BackupPlugin.getDefault().logMessage("Backup done"); //$NON-NLS-1$
		}
	}

	private int backupFolder(File folder, int parentFolderId, String overrideName) throws SQLException, IOException {
		DosFileAttributes attrs = Files.readAttributes(folder.toPath(), DosFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
		boolean hidden = false;
		if (attrs != null) {
			hidden = attrs.isHidden();
		}
		
		if (parentFolderId >= 0) {
			psNewEntry.setLong(1, parentFolderId);
		} else {
			psNewEntry.setNull(1, Types.INTEGER);
		}
		psNewEntry.setInt(2, backupId);
		psNewEntry.setInt(3, EntryType.FOLDER.getValue());
		psNewEntry.setNull(4, Types.TIMESTAMP);
		psNewEntry.setNull(5, Types.TIMESTAMP);
		psNewEntry.setInt(6, hidden ? 1 : 0);
		psNewEntry.setString(7, StringUtils.isNotBlank(overrideName) ? overrideName : folder.getName());
		psNewEntry.setNull(8, Types.INTEGER);
		psNewEntry.executeUpdate();
		int id = getLastIdentity();
		
		for (File f : folder.listFiles()) {
			if (!running) {
				break;
			}
			
			try {
				if (f.isDirectory()) {
					backupFolder(f, id, null);
				} else {
					backupFile(f, id);
				}
			} catch (IOException e) {
				// TODO
			}
		}
		
		return id;
	}
	
	private int getLastIdentity() throws SQLException {
		ResultSet rs = null;
		try {
			rs = psIdentity.executeQuery();
			rs.next();
			return rs.getInt(1);
		} finally {
			database.closeQuietly(rs);
		}
	}

	private void backupFile(File file, int parentFolderId) throws SQLException, IOException {
		currentFile = file.getAbsolutePath();
		fireBackupStatusChanged();
		
		String checksum = getChecksum(file);
		int fileId = findOldFile(file, checksum);
		if (fileId <= 0) {
			String backupFilePath = createBackupFilePath();
			File backupFile = Utils.toBackupFile(backupFilePath, outputFolder);
			fileId = backupFileContents(file, backupFile, backupFilePath, checksum);
		}

		DosFileAttributes attrs = Files.readAttributes(file.toPath(), DosFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
		FileTime creationTime = null;
		FileTime lastModifiedTime = null;
		boolean hidden = false;
		if (attrs != null) {
			creationTime = attrs.creationTime();
			lastModifiedTime = attrs.lastModifiedTime();
			hidden = attrs.isHidden();
		}

		psNewEntry.setLong(1, parentFolderId);
		psNewEntry.setInt(2, backupId);
		psNewEntry.setInt(3, EntryType.FILE.getValue());
		if (creationTime != null) {
			psNewEntry.setTimestamp(4, new Timestamp(creationTime.toMillis()));
		} else {
			psNewEntry.setNull(4, Types.TIMESTAMP);
		}
		if (lastModifiedTime != null) {
			psNewEntry.setTimestamp(5, new Timestamp(lastModifiedTime.toMillis()));
		} else {
			psNewEntry.setNull(5, Types.TIMESTAMP);
		}
		psNewEntry.setInt(6, hidden ? 1 : 0);
		psNewEntry.setString(7, file.getName());
		psNewEntry.setInt(8, fileId);
		psNewEntry.executeUpdate();
	}
	
	private int findOldFile(File file, String checksum) throws SQLException {
		psOldFile.setString(1, checksum);
		psOldFile.setLong(2, file.length());
		ResultSet rs = null;
		try {
			rs = psOldFile.executeQuery();
			if (rs.next()) {
				return rs.getInt("id"); //$NON-NLS-1$
			}
		} finally {
			database.closeQuietly(rs);
		}
		return -1;
	}

	private int backupFileContents(File file, File backupFile, String backupFilePath, String checksum)
		throws IOException, SQLException {

		FileUtils.forceMkdir(backupFile.getParentFile());
		OutputStream out = null;
		try {
			out = new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(backupFile)));
			Files.copy(file.toPath(), out);
			out.flush();
		} finally {
			IOUtils.closeQuietly(out);
		}
		
		psNewFile.setString(1, backupFilePath);
		psNewFile.setString(2, checksum);
		psNewFile.setLong(3, file.length());
		psNewFile.executeUpdate();
		
		return getLastIdentity();
	}
	
	private String getChecksum(File file) throws IOException {
		InputStream in = null;
		try {
			in = new BufferedInputStream(new FileInputStream(file));
			return DigestUtils.md5Hex(in);
		} finally {
			IOUtils.closeQuietly(in);
		}
	}

	private String createBackupFilePath() {
		return BACKUP_PATH_FORMAT.format(new Date()) + "/" + UUID.randomUUID().toString(); //$NON-NLS-1$
	}
	
	void addListener(IBackupRunListener listener) {
		synchronized (listeners) {
			listeners.add(listener);
		}
	}

	void removeListener(IBackupRunListener listener) {
		synchronized (listeners) {
			listeners.remove(listener);
		}
	}
	
	private List<IBackupRunListener> getListeners() {
		synchronized (listeners) {
			return new ArrayList<IBackupRunListener>(listeners);
		}
	}
	
	private void fireBackupStatusChanged() {
		final BackupStatusEvent e = new BackupStatusEvent(this);
		for (final IBackupRunListener listener : getListeners()) {
			SafeRunner.run(new ISafeRunnable() {
				public void run() throws Exception {
					listener.backupStatusChanged(e);
				}
				
				public void handleException(Throwable t) {
					// TODO
					t.printStackTrace();
				}
			});
		}
	}

	private void fireBackupEnded() {
		final BackupEndedEvent e = new BackupEndedEvent(this);
		for (final IBackupRunListener listener : getListeners()) {
			SafeRunner.run(new ISafeRunnable() {
				public void run() throws Exception {
					listener.backupEnded(e);
				}
				
				public void handleException(Throwable t) {
					// TODO
					t.printStackTrace();
				}
			});
		}
	}

	String getCurrentFile() {
		return currentFile;
	}

	void stopBackupAndWait() {
		running = false;
		try {
			thread.join();
		} catch (InterruptedException e) {
			// ignore
		}
	}
}
