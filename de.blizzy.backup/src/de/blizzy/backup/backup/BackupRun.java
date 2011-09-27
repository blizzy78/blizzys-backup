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
package de.blizzy.backup.backup;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.DigestOutputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.ISafeRunnable;
import org.eclipse.core.runtime.SafeRunner;
import org.jooq.Cursor;
import org.jooq.Record;

import de.blizzy.backup.BackupPlugin;
import de.blizzy.backup.Compression;
import de.blizzy.backup.Utils;
import de.blizzy.backup.database.Database;
import de.blizzy.backup.database.EntryType;
import de.blizzy.backup.database.schema.tables.Backups;
import de.blizzy.backup.database.schema.tables.Entries;
import de.blizzy.backup.settings.Settings;
import de.blizzy.backup.vfs.IFile;
import de.blizzy.backup.vfs.IFileSystemEntry;
import de.blizzy.backup.vfs.IFolder;
import de.blizzy.backup.vfs.ILocation;
import de.blizzy.backup.vfs.IOutputStreamProvider;
import de.blizzy.backup.vfs.filesystem.FileSystemFileOrFolder;

public class BackupRun implements Runnable {
	private Settings settings;
	private Thread thread;
	private Database database;
	private int backupId;
	private List<IBackupRunListener> listeners = new ArrayList<>();
	private boolean running = true;
	private int numEntries;

	public BackupRun(Settings settings) {
		this.settings = settings;
	}

	public void runBackup() {
		thread = new Thread(this, "Backup"); //$NON-NLS-1$
		thread.start();
	}

	@Override
	public void run() {
		BackupPlugin.getDefault().logMessage("Starting backup"); //$NON-NLS-1$
		
		fireBackupStatusChanged(BackupStatus.INITIALIZE);
		
		database = new Database(settings, true);
		try {
			database.open();
			database.initialize();
			
			database.factory()
				.insertInto(Backups.BACKUPS)
				.set(Backups.RUN_TIME, new Timestamp(System.currentTimeMillis()))
				.execute();
			backupId = database.factory().lastID().intValue();
			
			for (ILocation location : settings.getLocations()) {
				if (!running) {
					break;
				}

				try {
					backupFolder(location.getRootFolder(), -1, location.getRootFolder().getAbsolutePath());
				} catch (IOException e) {
					BackupPlugin.getDefault().logError("error while running backup", e); //$NON-NLS-1$
				} finally {
					location.close();
				}
			}
			
			database.factory()
				.update(Backups.BACKUPS)
				.set(Backups.NUM_ENTRIES, Integer.valueOf(numEntries))
				.where(Backups.ID.equal(Integer.valueOf(backupId)))
				.execute();
			
			fireBackupStatusChanged(BackupStatus.CLEANUP);
			removeOldBackups();
			consolidateDuplicateFiles();
			removeUnusedFiles();
			removeOldDatabaseBackups();
			
			database.factory().query("ANALYZE").execute(); //$NON-NLS-1$
		} catch (SQLException | IOException | RuntimeException e) {
			BackupPlugin.getDefault().logError("error while running backup", e); //$NON-NLS-1$
		} finally {
			database.close();
			backupDatabase();
			System.gc();
			
			fireBackupEnded();
			
			BackupPlugin.getDefault().logMessage("Backup done"); //$NON-NLS-1$
		}
	}

	private void backupDatabase() {
		try {
			File outputFolder = new File(settings.getOutputFolder());
			File dbBackupRootFolder = new File(outputFolder, "$db-backup"); //$NON-NLS-1$
			File dbBackupFolder = new File(dbBackupRootFolder, String.valueOf(System.currentTimeMillis()));
			database.backupDatabase(dbBackupFolder);
		} catch (IOException e) {
			BackupPlugin.getDefault().logError("Error while creating database backup", e); //$NON-NLS-1$
		}
	}
	
	private void removeOldDatabaseBackups() {
		File outputFolder = new File(settings.getOutputFolder());
		File dbBackupRootFolder = new File(outputFolder, "$db-backup"); //$NON-NLS-1$
		if (dbBackupRootFolder.isDirectory()) {
			List<Long> timestamps = new ArrayList<>();
			for (File f : dbBackupRootFolder.listFiles()) {
				if (f.isDirectory()) {
					timestamps.add(Long.valueOf(f.getName()));
				}
			}
			if (timestamps.size() > 19) {
				Collections.sort(timestamps);
				Collections.reverse(timestamps);
				for (int i = 19; i < timestamps.size(); i++) {
					long timestamp = timestamps.get(i).longValue();
					File folder = new File(dbBackupRootFolder, String.valueOf(timestamp));
					try {
						FileUtils.forceDelete(folder);
					} catch (IOException e) {
						BackupPlugin.getDefault().logError("error while deleting old database backup folder: " + //$NON-NLS-1$
								folder.getAbsolutePath(), e);
					}
				}
			}
		}
	}

	private int backupFolder(IFolder folder, int parentFolderId, String overrideName) throws SQLException, IOException {
		FileTime creationTime = folder.getCreationTime();
		FileTime lastModificationTime = folder.getLastModificationTime();
		database.factory()
			.insertInto(Entries.ENTRIES)
			.set(Entries.PARENT_ID, (parentFolderId > 0) ? Integer.valueOf(parentFolderId) : null)
			.set(Entries.BACKUP_ID, Integer.valueOf(backupId))
			.set(Entries.TYPE, Integer.valueOf(EntryType.FOLDER.getValue()))
			.set(Entries.CREATION_TIME, (creationTime != null) ? new Timestamp(creationTime.toMillis()) : null)
			.set(Entries.MODIFICATION_TIME, (lastModificationTime != null) ? new Timestamp(lastModificationTime.toMillis()) : null)
			.set(Entries.HIDDEN, Boolean.valueOf(folder.isHidden()))
			.set(Entries.NAME, StringUtils.isNotBlank(overrideName) ? overrideName : folder.getName())
			.execute();
		int id = database.factory().lastID().intValue();
		List<IFileSystemEntry> entries = new ArrayList<>(folder.list());
		Collections.sort(entries, new Comparator<IFileSystemEntry>() {
			@Override
			public int compare(IFileSystemEntry e1, IFileSystemEntry e2) {
				return e1.getName().compareTo(e2.getName());
			}
		});
		for (IFileSystemEntry entry : entries) {
			if (!running) {
				break;
			}
			
			try {
				if (entry.isFolder()) {
					backupFolder((IFolder) entry, id, null);
				} else {
					backupFile((IFile) entry, id);
				}
			} catch (IOException e) {
				BackupPlugin.getDefault().logError("error while backing up folder: " + //$NON-NLS-1$
						folder.getAbsolutePath(), e);
			}
		}
		
		return id;
	}
	
	private void backupFile(IFile file, int parentFolderId) throws SQLException, IOException {
		if ((numEntries % 50) == 0) {
			checkDiskSpaceAndRemoveOldBackups();
		}

		fireBackupStatusChanged(new BackupStatus(file.getAbsolutePath()));
		
		FileTime creationTime = file.getCreationTime();
		FileTime lastModificationTime = file.getLastModificationTime();

		int fileId = -1;
		if (settings.isUseChecksums()) {
			String checksum = getChecksum(file);
			fileId = findOldFileViaChecksum(file, checksum);
		} else {
			fileId = findOldFileViaTimestamp(file);
		}
		if (fileId <= 0) {
			String backupFilePath = Utils.createBackupFilePath();
			File backupFile = Utils.toBackupFile(backupFilePath, settings.getOutputFolder());
			fileId = backupFileContents(file, backupFile, backupFilePath);
		}

		database.factory()
			.insertInto(Entries.ENTRIES)
			.set(Entries.PARENT_ID, Integer.valueOf(parentFolderId))
			.set(Entries.BACKUP_ID, Integer.valueOf(backupId))
			.set(Entries.TYPE, Integer.valueOf(EntryType.FILE.getValue()))
			.set(Entries.CREATION_TIME, (creationTime != null) ? new Timestamp(creationTime.toMillis()) : null)
			.set(Entries.MODIFICATION_TIME, (lastModificationTime != null) ? new Timestamp(lastModificationTime.toMillis()) : null)
			.set(Entries.HIDDEN, Boolean.valueOf(file.isHidden()))
			.set(Entries.NAME, file.getName())
			.set(Entries.FILE_ID, Integer.valueOf(fileId))
			.execute();
		
		numEntries++;
	}
	
	private int findOldFileViaTimestamp(IFile file) throws SQLException, IOException {
		FileTime lastModificationTime = file.getLastModificationTime();
		long length = file.getLength();
		Cursor<Record> cursor = null;
		try {
			cursor = database.factory()
				.select(Backups.ID)
				.from(Backups.BACKUPS)
				.where(Backups.ID.notEqual(Integer.valueOf(backupId)))
				.orderBy(Backups.RUN_TIME.desc())
				.fetchLazy();
			while (cursor.hasNext()) {
				int backupId = cursor.fetchOne().getValue(Backups.ID).intValue();
				int entryId = findFileOrFolderEntryInBackup(file, backupId);
				if (entryId > 0) {
					Record record = database.factory()
						.select(Entries.MODIFICATION_TIME,
								de.blizzy.backup.database.schema.tables.Files.ID,
								de.blizzy.backup.database.schema.tables.Files.LENGTH)
						.from(Entries.ENTRIES)
						.join(de.blizzy.backup.database.schema.tables.Files.FILES)
							.on(de.blizzy.backup.database.schema.tables.Files.ID.equal(Entries.FILE_ID))
						.where(Entries.ID.equal(Integer.valueOf(entryId)),
								Entries.TYPE.equal(Byte.valueOf((byte) EntryType.FILE.getValue())))
						.fetchAny();
					if (record != null) {
						Timestamp entryModTime = record.getValue(Entries.MODIFICATION_TIME);
						long entryModificationTime = (entryModTime != null) ? entryModTime.getTime() : -1;
						long entryLength = record.getValue(de.blizzy.backup.database.schema.tables.Files.LENGTH).longValue();
						if ((entryModificationTime > 0) &&
							(lastModificationTime != null) && (entryModificationTime == lastModificationTime.toMillis()) &&
							(entryLength == length)) {
							
							return record.getValue(de.blizzy.backup.database.schema.tables.Files.ID).intValue();
						}
					}
				}
			}
		} finally {
			database.closeQuietly(cursor);
		}
		
		return -1;
	}
	
	private int findFileOrFolderEntryInBackup(IFileSystemEntry file, int backupId) throws SQLException, IOException {
		if (file.isFolder()) {
			// try to find folder as root folder
			Record record = database.factory()
				.select(Entries.ID)
				.from(Entries.ENTRIES)
				.where(Entries.NAME.equal(file.getAbsolutePath()), Entries.PARENT_ID.isNull(),
						Entries.BACKUP_ID.equal(Integer.valueOf(backupId)))
				.fetchAny();
			if (record != null) {
				return record.getValue(Entries.ID).intValue();
			}
		}
		
		// find entry in parent folder
		IFolder parentFolder = file.getParentFolder();
		if (parentFolder != null) {
			int parentFolderId = findFileOrFolderEntryInBackup(parentFolder, backupId);
			if (parentFolderId > 0) {
				Record record = database.factory()
					.select(Entries.ID)
					.from(Entries.ENTRIES)
					.where(Entries.NAME.equal(file.getName()), Entries.PARENT_ID.equal(Integer.valueOf(parentFolderId)),
							Entries.BACKUP_ID.equal(Integer.valueOf(backupId)))
					.fetchAny();
				if (record != null) {
					return record.getValue(Entries.ID).intValue();
				}
			}
		}
		
		return -1;
	}

	private int findOldFileViaChecksum(IFile file, String checksum) throws SQLException, IOException {
		Record record = database.factory()
			.select(de.blizzy.backup.database.schema.tables.Files.ID)
			.from(de.blizzy.backup.database.schema.tables.Files.FILES)
			.where(de.blizzy.backup.database.schema.tables.Files.CHECKSUM.equal(checksum),
					de.blizzy.backup.database.schema.tables.Files.LENGTH.equal(Long.valueOf(file.getLength())))
			.fetchAny();
		return (record != null) ?
				record.getValueAsInteger(de.blizzy.backup.database.schema.tables.Files.ID).intValue() :
				-1;
	}

	private int backupFileContents(IFile file, final File backupFile, String backupFilePath)
		throws IOException, SQLException {

		FileUtils.forceMkdir(backupFile.getParentFile());

		final MessageDigest[] digest = new MessageDigest[1];
		IOutputStreamProvider outputStreamProvider = new IOutputStreamProvider() {
			@Override
			public OutputStream getOutputStream() throws IOException {
				try {
					digest[0] = MessageDigest.getInstance("MD5"); //$NON-NLS-1$
					return new DigestOutputStream(
							Compression.BZIP2.getOutputStream(
									new BufferedOutputStream(new FileOutputStream(backupFile))),
							digest[0]);
				} catch (GeneralSecurityException e) {
					throw new RuntimeException(e);
				}
			}
		};
		file.copy(outputStreamProvider);
		String checksum = toHexString(digest[0]);

		database.factory()
			.insertInto(de.blizzy.backup.database.schema.tables.Files.FILES)
			.set(de.blizzy.backup.database.schema.tables.Files.BACKUP_PATH, backupFilePath)
			.set(de.blizzy.backup.database.schema.tables.Files.CHECKSUM, checksum)
			.set(de.blizzy.backup.database.schema.tables.Files.LENGTH, Long.valueOf(file.getLength()))
			.set(de.blizzy.backup.database.schema.tables.Files.COMPRESSION, Byte.valueOf((byte) Compression.BZIP2.getValue()))
			.execute();
		return database.factory().lastID().intValue();
	}
	
	private String getChecksum(IFile file) throws IOException {
		final MessageDigest[] digest = new MessageDigest[1];
		IOutputStreamProvider outputStreamProvider = new IOutputStreamProvider() {
			@Override
			public OutputStream getOutputStream() throws IOException {
				try {
					digest[0] = MessageDigest.getInstance("MD5"); //$NON-NLS-1$
					return new DigestOutputStream(new NullOutputStream(), digest[0]);
				} catch (GeneralSecurityException e) {
					throw new RuntimeException(e);
				}
			}
		};
		file.copy(outputStreamProvider);
		return toHexString(digest[0]);
	}

	private String toHexString(MessageDigest digest) {
		return Hex.encodeHexString(digest.digest());
	}

	public void addListener(IBackupRunListener listener) {
		synchronized (listeners) {
			listeners.add(listener);
		}
	}

	public void removeListener(IBackupRunListener listener) {
		synchronized (listeners) {
			listeners.remove(listener);
		}
	}
	
	private List<IBackupRunListener> getListeners() {
		synchronized (listeners) {
			return new ArrayList<>(listeners);
		}
	}
	
	private void fireBackupStatusChanged(BackupStatus status) {
		final BackupStatusEvent e = new BackupStatusEvent(this, status);
		for (final IBackupRunListener listener : getListeners()) {
			SafeRunner.run(new ISafeRunnable() {
				@Override
				public void run() throws Exception {
					listener.backupStatusChanged(e);
				}
				
				@Override
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
				@Override
				public void run() throws Exception {
					listener.backupEnded(e);
				}
				
				@Override
				public void handleException(Throwable t) {
					// TODO
					t.printStackTrace();
				}
			});
		}
	}

	public void stopBackupAndWait() {
		running = false;
		try {
			thread.join();
		} catch (InterruptedException e) {
			// ignore
		}
	}

	private void removeOldBackups() throws SQLException {
		removeOldBackupsDaily();
		removeOldBackupsWeekly();
		removeFailedBackups();
	}

	private void removeFailedBackups() throws SQLException {
		Set<Integer> ids = new HashSet<>(database.factory()
			.select(Backups.ID)
			.from(Backups.BACKUPS)
			.where(Backups.NUM_ENTRIES.isNull())
			.fetch(Backups.ID));
		removeBackups(ids);
	}
	
	private void removeOldBackupsDaily() throws SQLException {
		// collect IDs of all but the most recent backup each day
		Set<Integer> backupsToRemove = new HashSet<>();
		List<Date> days = getBackupRunsDays();
		Calendar c = Calendar.getInstance();
		for (Date day : days) {
			long start = day.getTime();
			c.setTimeInMillis(start);
			c.add(Calendar.DAY_OF_YEAR, 1);
			long end = c.getTimeInMillis();
			List<Integer> ids = getBackupIds(start, end);
			if (ids.size() >= 2) {
				ids.remove(0);
				backupsToRemove.addAll(ids);
			}
		}

		BackupPlugin.getDefault().logMessage("removing backups (daily): " + backupsToRemove); //$NON-NLS-1$
		if (!backupsToRemove.isEmpty()) {
			removeBackups(backupsToRemove);
		}
	}

	private List<Integer> getBackupIds(long start, long end) throws SQLException {
		return database.factory()
			.select(Backups.ID)
			.from(Backups.BACKUPS)
			.where(Backups.RUN_TIME.greaterOrEqual(new Timestamp(start)),
					Backups.RUN_TIME.lessThan(new Timestamp(end)))
			.orderBy(Backups.RUN_TIME.desc())
			.fetch(Backups.ID);
	}
	
	private void removeOldBackupsWeekly() throws SQLException {
		// collect IDs of all but the most recent backup each day
		Set<Integer> backupsToRemove = new HashSet<>();
		List<Date> days = getBackupRunsDays();
		Calendar c = Calendar.getInstance();
		for (Date day : days) {
			long start = getWeekStart(day).getTime();
			c.setTimeInMillis(start);
			c.add(Calendar.DAY_OF_YEAR, 7);
			long end = c.getTimeInMillis();
			List<Integer> ids = getBackupIds(start, end);
			if (ids.size() >= 2) {
				ids.remove(0);
				backupsToRemove.addAll(ids);
			}
		}

		BackupPlugin.getDefault().logMessage("removing backups (weekly): " + backupsToRemove); //$NON-NLS-1$
		if (!backupsToRemove.isEmpty()) {
			removeBackups(backupsToRemove);
		}
	}

	private Date getWeekStart(Date date) {
		int firstWeekday = Calendar.getInstance().getFirstDayOfWeek();
		Calendar c = Calendar.getInstance();
		c.setTimeInMillis(date.getTime());
		for (;;) {
			int weekday = c.get(Calendar.DAY_OF_WEEK);
			if (weekday == firstWeekday) {
				break;
			}
			c.add(Calendar.DAY_OF_YEAR, -1);
		}
		return new Date(c.getTimeInMillis());
	}

	private List<Date> getBackupRunsDays() throws SQLException {
		Cursor<Record> cursor = null;
		try {
			// get all days where there are backups (and which are older than 14 days)
			Calendar c = Calendar.getInstance();
			c.add(Calendar.DAY_OF_YEAR, -14);
			c.set(Calendar.HOUR_OF_DAY, 0);
			c.set(Calendar.MINUTE, 0);
			c.set(Calendar.SECOND, 0);
			c.set(Calendar.MILLISECOND, 0);
			cursor = database.factory()
				.select(Backups.RUN_TIME)
				.from(Backups.BACKUPS)
				.where(Backups.RUN_TIME.lessThan(new Timestamp(c.getTimeInMillis())))
				.orderBy(Backups.RUN_TIME)
				.fetchLazy();
			List<Date> days = new ArrayList<>();
			while (cursor.hasNext()) {
				Record record = cursor.fetchOne();
				c.setTimeInMillis(record.getValueAsTimestamp(Backups.RUN_TIME).getTime());
				c.set(Calendar.HOUR_OF_DAY, 0);
				c.set(Calendar.MINUTE, 0);
				c.set(Calendar.SECOND, 0);
				c.set(Calendar.MILLISECOND, 0);
				days.add(new Date(c.getTimeInMillis()));
			}
			return days;
		} finally {
			database.closeQuietly(cursor);
		}
	}

	private void removeBackups(Set<Integer> backupsToRemove) throws SQLException {
		for (Integer backupId : backupsToRemove) {
			database.factory()
				.delete(Entries.ENTRIES)
				.where(Entries.BACKUP_ID.equal(backupId))
				.execute();
			database.factory()
				.delete(Backups.BACKUPS)
				.where(Backups.ID.equal(backupId))
				.execute();
		}
	}

	private void removeUnusedFiles() throws SQLException {
		Cursor<Record> cursor = null;
		Set<FileEntry> filesToRemove = new HashSet<>();
		try {
			cursor = database.factory()
				.select(de.blizzy.backup.database.schema.tables.Files.ID,
						de.blizzy.backup.database.schema.tables.Files.BACKUP_PATH)
				.from(de.blizzy.backup.database.schema.tables.Files.FILES)
				.leftOuterJoin(Entries.ENTRIES)
					.on(Entries.FILE_ID.equal(de.blizzy.backup.database.schema.tables.Files.ID))
				.where(Entries.FILE_ID.isNull())
				.fetchLazy();
			while (cursor.hasNext()) {
				Record record = cursor.fetchOne();
				FileEntry file = new FileEntry(
						record.getValueAsInteger(de.blizzy.backup.database.schema.tables.Files.ID).intValue(),
						record.getValueAsString(de.blizzy.backup.database.schema.tables.Files.BACKUP_PATH));
				filesToRemove.add(file);
			}
		} finally {
			database.closeQuietly(cursor);
		}
		
		BackupPlugin.getDefault().logMessage("removing unused files: " + filesToRemove); //$NON-NLS-1$
		if (!filesToRemove.isEmpty()) {
			removeFiles(filesToRemove);
		}
	}

	private void removeFiles(Set<FileEntry> files) throws SQLException {
		for (FileEntry file : files) {
			File f = Utils.toBackupFile(file.backupPath, settings.getOutputFolder());
			Path path = f.toPath();
			try {
				Files.delete(path);
			} catch (IOException e) {
				BackupPlugin.getDefault().logError("error deleting file: " + file.backupPath, e); //$NON-NLS-1$
			}
			
			removeFoldersIfEmpty(f.getParentFile());
			
			database.factory()
				.delete(de.blizzy.backup.database.schema.tables.Files.FILES)
				.where(de.blizzy.backup.database.schema.tables.Files.ID.equal(Integer.valueOf(file.id)))
				.execute();
		}
	}
	
	private void removeFoldersIfEmpty(File folder) {
		File outputFolder = new File(settings.getOutputFolder());
		if (Utils.isParent(new FileSystemFileOrFolder(outputFolder), new FileSystemFileOrFolder(folder)) &&
			(folder.list().length == 0)) {

			try {
				BackupPlugin.getDefault().logMessage("deleting empty folder: " + folder.getAbsolutePath()); //$NON-NLS-1$
				Files.delete(folder.toPath());

				File parentFolder = folder.getParentFile();
				removeFoldersIfEmpty(parentFolder);
			} catch (IOException e) {
				BackupPlugin.getDefault().logError("error deleting folder: " + folder.getAbsolutePath(), e); //$NON-NLS-1$
			}
		}
	}

	private void checkDiskSpaceAndRemoveOldBackups() {
		try {
			FileStore store = Files.getFileStore(new File(settings.getOutputFolder()).toPath());
			long total = store.getTotalSpace();
			if (total > 0) {
				for (;;) {
					long available = store.getUsableSpace();
					if (available <= 0) {
						break;
					}
					
					double avail = (double) available / (double) total * 100d;
					if (avail >= 20d) {
						break;
					}
					
					if (!removeOldestBackup()) {
						break;
					}
				}
			}
		} catch (IOException e) {
			// ignore
		} catch (SQLException e) {
			BackupPlugin.getDefault().logError("error removing oldest backup", e); //$NON-NLS-1$
		}
	}
	
	private boolean removeOldestBackup() throws SQLException {
		Record record = database.factory()
			.select(Backups.ID)
			.from(Backups.BACKUPS)
			.where(Backups.NUM_ENTRIES.isNotNull())
			.orderBy(Backups.RUN_TIME)
			.fetchAny();
		if (record != null) {
			Integer id = record.getValueAsInteger(Backups.ID);
			BackupPlugin.getDefault().logMessage("removing backup: " + id); //$NON-NLS-1$
			removeBackups(Collections.singleton(id));
		}
		return record != null;
	}

	private void consolidateDuplicateFiles() throws SQLException {
		Cursor<Record> cursor = null;
		try {
			cursor = database.factory()
				.select(de.blizzy.backup.database.schema.tables.Files.CHECKSUM,
						de.blizzy.backup.database.schema.tables.Files.LENGTH)
				.from(de.blizzy.backup.database.schema.tables.Files.FILES)
				.groupBy(de.blizzy.backup.database.schema.tables.Files.CHECKSUM,
						de.blizzy.backup.database.schema.tables.Files.LENGTH)
				.having(de.blizzy.backup.database.schema.tables.Files.CHECKSUM.count().greaterThan(Integer.valueOf(1)))
				.fetchLazy();
			while (cursor.hasNext()) {
				Record record = cursor.fetchOne();
				String checksum = record.getValue(de.blizzy.backup.database.schema.tables.Files.CHECKSUM);
				long length = record.getValue(de.blizzy.backup.database.schema.tables.Files.LENGTH).longValue();
				consolidateDuplicateFiles(checksum, length);
			}
		} finally {
			database.closeQuietly(cursor);
		}
	}
	
	private void consolidateDuplicateFiles(String checksum, long length) throws SQLException {
		Cursor<Record> cursor = null;
		try {
			List<Integer> fileIds = database.factory()
				.select(de.blizzy.backup.database.schema.tables.Files.ID)
				.from(de.blizzy.backup.database.schema.tables.Files.FILES)
				.where(de.blizzy.backup.database.schema.tables.Files.CHECKSUM.equal(checksum),
						de.blizzy.backup.database.schema.tables.Files.LENGTH.equal(Long.valueOf(length)))
				.fetch(de.blizzy.backup.database.schema.tables.Files.ID);
			if (fileIds.size() >= 2) {
				int masterFileId = fileIds.get(0).intValue();
				fileIds = fileIds.subList(1, fileIds.size());
				consolidateDuplicateFiles(masterFileId, fileIds);
			}
		} finally {
			database.closeQuietly(cursor);
		}
	}

	private void consolidateDuplicateFiles(int masterFileId, List<Integer> fileIds) throws SQLException {
		BackupPlugin.getDefault().logMessage("consolidating duplicate files: " + masterFileId + " <- " + fileIds); //$NON-NLS-1$ //$NON-NLS-2$

		Long masterId = Long.valueOf(masterFileId);
		while (!fileIds.isEmpty()) {
			int endIdx = Math.min(fileIds.size(), 10);
			List<Integer> chunk = fileIds.subList(0, endIdx);
			database.factory()
				.update(Entries.ENTRIES)
				.set(Entries.FILE_ID, masterId)
				.where(Entries.FILE_ID.in(chunk))
				.execute();
			fileIds = fileIds.subList(endIdx, fileIds.size());
		}
	}
}
