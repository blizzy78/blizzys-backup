/*
blizzy's Backup - Easy to use personal file backup application
Copyright (C) 2011-2012 Maik Schreiber

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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.jooq.Cursor;
import org.jooq.Record;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.Factory;

import de.blizzy.backup.BackupPlugin;
import de.blizzy.backup.Compression;
import de.blizzy.backup.IDatabaseModifyingStorageInterceptor;
import de.blizzy.backup.IStorageInterceptor;
import de.blizzy.backup.Utils;
import de.blizzy.backup.database.schema.PublicFactory;
import de.blizzy.backup.database.schema.Tables;
import de.blizzy.backup.settings.Settings;

public class Database {
	private static final String DB_FOLDER_NAME = "$blizzysbackup"; //$NON-NLS-1$
	
	private String outputFolder;
	private File realFolder;
	private boolean heavyDuty;
	private File folder;
	private Connection conn;
	private Factory factory;

	public Database(Settings settings, boolean heavyDuty) {
		this.outputFolder = settings.getOutputFolder();
		this.realFolder = new File(new File(outputFolder), DB_FOLDER_NAME);
		folder = realFolder;
		this.heavyDuty = heavyDuty;
	}
	
	public void open(Collection<IStorageInterceptor> storageInterceptors) throws SQLException, IOException {
		if (heavyDuty) {
			folder = Files.createTempDirectory("blizzysbackup-").toFile(); //$NON-NLS-1$
			if (realFolder.isDirectory()) {
				copyFolder(realFolder, folder, false);
			}
		}
		open(folder, storageInterceptors);
	}
	
	private void open(File folder, Collection<IStorageInterceptor> storageInterceptors) throws SQLException {
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

				StringBuilder paramsBuf = new StringBuilder("CACHE_SIZE=65536"); //$NON-NLS-1$
				String password = StringUtils.EMPTY;
				for (IStorageInterceptor interceptor : storageInterceptors) {
					if (interceptor instanceof IDatabaseModifyingStorageInterceptor) {
						IDatabaseModifyingStorageInterceptor i = (IDatabaseModifyingStorageInterceptor) interceptor;
						paramsBuf.append(";").append(i.getDatabaseParameters()); //$NON-NLS-1$
						password = i.getDatabasePassword() + " " + password; //$NON-NLS-1$
					}
				}

				conn = DriverManager.getConnection(
						"jdbc:h2:" + folder.getAbsolutePath() + "/backup" + //$NON-NLS-1$ //$NON-NLS-2$
						";" + paramsBuf.toString(), //$NON-NLS-1$
						"sa", password); //$NON-NLS-1$
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
			
			if (heavyDuty) {
				try {
					copyFolder(folder, realFolder, false);
					FileUtils.forceDelete(folder);
				} catch (IOException e) {
					BackupPlugin.getDefault().logError("error while closing database", e); //$NON-NLS-1$
				} finally {
					folder = realFolder;
				}
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
		copyFolder(folder, targetFolder, true);
	}

	private void copyFolder(File folder, File targetFolder, boolean zipFiles) throws IOException {
		FileUtils.forceMkdir(targetFolder);
		FileUtils.cleanDirectory(targetFolder);
		
		for (File file : folder.listFiles()) {
			if (file.isDirectory()) {
				copyFolder(file, new File(targetFolder, file.getName()), zipFiles);
			} else {
				if (zipFiles) {
					Utils.zipFile(file, new File(targetFolder, file.getName() + ".zip")); //$NON-NLS-1$
				} else {
					FileUtils.copyFile(file, new File(targetFolder, file.getName()));
				}
			}
		}
	}

	public void initialize() {
		try {
			int sha256Length = DigestUtils.sha256Hex(StringUtils.EMPTY).length();
			
			factory.query("CREATE TABLE IF NOT EXISTS backups (" + //$NON-NLS-1$
					"id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, " + //$NON-NLS-1$
					"run_time DATETIME NOT NULL, " + //$NON-NLS-1$
					"num_entries INT NULL" + //$NON-NLS-1$
					")") //$NON-NLS-1$
					.execute();
			
			int sampleBackupPathLength = Utils.createSampleBackupFilePath().length();
			factory.query("CREATE TABLE IF NOT EXISTS files (" + //$NON-NLS-1$
					"id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, " + //$NON-NLS-1$
					"backup_path VARCHAR(" + sampleBackupPathLength + ") NOT NULL, " + //$NON-NLS-1$ //$NON-NLS-2$
					"checksum VARCHAR(" + sha256Length + ") NOT NULL, " + //$NON-NLS-1$ //$NON-NLS-2$
					"length BIGINT NOT NULL, " + //$NON-NLS-1$
					"compression TINYINT NOT NULL" + //$NON-NLS-1$
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
					"name_lower VARCHAR(1024) NOT NULL, " + //$NON-NLS-1$
					"file_id INT NULL" + //$NON-NLS-1$
					")") //$NON-NLS-1$
					.execute();
			factory.query("CREATE INDEX IF NOT EXISTS idx_entries_files ON entries " + //$NON-NLS-1$
					"(file_id)") //$NON-NLS-1$
					.execute();
			factory.query("CREATE INDEX IF NOT EXISTS idx_folder_entries ON entries " + //$NON-NLS-1$
					"(backup_id, parent_id)") //$NON-NLS-1$
					.execute();
			factory.query("DROP INDEX IF EXISTS idx_entries_names") //$NON-NLS-1$
					.execute();
			factory.query("CREATE INDEX IF NOT EXISTS idx_entries_names2 ON entries " + //$NON-NLS-1$
					"(name, backup_id, parent_id)") //$NON-NLS-1$
					.execute();
			
			if (!isTableColumnExistent("FILES", "COMPRESSION")) { //$NON-NLS-1$ //$NON-NLS-2$
				factory.query("ALTER TABLE files ADD compression TINYINT NULL DEFAULT " + Compression.GZIP.getValue()) //$NON-NLS-1$
					.execute();
				factory.update(Tables.FILES)
					.set(Tables.FILES.COMPRESSION, Byte.valueOf((byte) Compression.GZIP.getValue()))
					.execute();
				factory.query("ALTER TABLE files ALTER COLUMN compression TINYINT NOT NULL") //$NON-NLS-1$
					.execute();
			}
			
			if (getTableColumnSize("FILES", "CHECKSUM") != sha256Length) { //$NON-NLS-1$ //$NON-NLS-2$
				factory.query("ALTER TABLE files ALTER COLUMN " + //$NON-NLS-1$
					"checksum VARCHAR(" + sha256Length + ") NOT NULL") //$NON-NLS-1$ //$NON-NLS-2$
					.execute();
			}
			
			if (!isTableColumnExistent("ENTRIES", "NAME_LOWER")) { //$NON-NLS-1$ //$NON-NLS-2$
				factory.query("ALTER TABLE entries ADD name_lower VARCHAR(1024) NULL") //$NON-NLS-1$
					.execute();
				factory.update(Tables.ENTRIES)
					.set(Tables.ENTRIES.NAME_LOWER, Tables.ENTRIES.NAME.lower())
					.execute();
				factory.query("ALTER TABLE entries ALTER COLUMN name_lower VARCHAR(1024) NOT NULL") //$NON-NLS-1$
					.execute();
			}
			factory.query("CREATE INDEX IF NOT EXISTS idx_entries_search ON entries " + //$NON-NLS-1$
					"(backup_id, name_lower)") //$NON-NLS-1$
					.execute();
			
			if (getTableColumnSize("FILES", "BACKUP_PATH") != sampleBackupPathLength) { //$NON-NLS-1$ //$NON-NLS-2$
				Cursor<Record> cursor = null;
				try {
					cursor = factory.select(Tables.FILES.ID, Tables.FILES.BACKUP_PATH)
						.from(Tables.FILES)
						.fetchLazy();
					while (cursor.hasNext()) {
						Record record = cursor.fetchOne();
						String backupPath = record.getValue(Tables.FILES.BACKUP_PATH);
						String backupFileName = StringUtils.substringAfterLast(backupPath, "/"); //$NON-NLS-1$
						if (backupFileName.indexOf('-') > 0) {
							Integer id = record.getValue(Tables.FILES.ID);
							File backupFile = Utils.toBackupFile(backupPath, outputFolder);
							File folder = backupFile.getParentFile();
							int maxIdx = Utils.getMaxBackupFileIndex(folder);
							int newIdx = maxIdx + 1;
							String newBackupFileName = Utils.toBackupFileName(newIdx);
							File newBackupFile = new File(folder, newBackupFileName);
							FileUtils.moveFile(backupFile, newBackupFile);
							String newBackupPath = StringUtils.substringBeforeLast(backupPath, "/") + "/" + newBackupFileName; //$NON-NLS-1$ //$NON-NLS-2$
							factory.update(Tables.FILES)
								.set(Tables.FILES.BACKUP_PATH, newBackupPath)
								.where(Tables.FILES.ID.equal(id))
								.execute();
						}
					}

					factory.query("ALTER TABLE files ALTER COLUMN backup_path VARCHAR(" + sampleBackupPathLength + ") NOT NULL") //$NON-NLS-1$ //$NON-NLS-2$
						.execute();
				} finally {
					closeQuietly(cursor);
				}
			}
			
			factory.query("ANALYZE") //$NON-NLS-1$
					.execute();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private boolean isTableColumnExistent(String tableName, String columnName) throws SQLException {
		ResultSet rs = null;
		try {
			rs = conn.getMetaData().getColumns(null, null, tableName, columnName);
			return rs.next();
		} finally {
			closeQuietly(rs);
		}
	}
	
	private int getTableColumnSize(String tableName, String columnName) throws SQLException {
		ResultSet rs = null;
		try {
			rs = conn.getMetaData().getColumns(null, null, tableName, columnName);
			rs.next();
			return rs.getInt(7);
		} finally {
			closeQuietly(rs);
		}
	}

	public void closeQuietly(Cursor<?> cursor) {
		if (cursor != null) {
			try {
				cursor.close();
			} catch (DataAccessException e) {
				// ignore
			}
		}
	}
	
	private void closeQuietly(ResultSet rs) {
		if (rs != null) {
			try {
				rs.close();
			} catch (SQLException e) {
				// ignore
			}
		}
	}
}
