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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
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
import org.jooq.Cursor;
import org.jooq.Record;

import de.blizzy.backup.BackupPlugin;
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
import de.blizzy.backup.vfs.filesystem.FileSystemFileOrFolder;

public class BackupRun implements Runnable {
	private static final DateFormat BACKUP_PATH_FORMAT =
		new SimpleDateFormat("yyyy'/'MM'/'dd'/'HHmm"); //$NON-NLS-1$
	
	private Settings settings;
	private Thread thread;
	private Database database;
	private int previousBackupId = -1;
	private int backupId;
	private List<IBackupRunListener> listeners = new ArrayList<IBackupRunListener>();
	private String currentFile;
	private boolean running = true;
	private boolean cleaningUp;
	private int numEntries;

	public BackupRun(Settings settings) {
		this.settings = settings;
	}

	public void runBackup() {
		thread = new Thread(this, "Backup"); //$NON-NLS-1$
		thread.start();
	}

	public void run() {
		BackupPlugin.getDefault().logMessage("Starting backup"); //$NON-NLS-1$
		
		database = new Database(settings);
		try {
			database.open();
			database.initialize(createBackupFilePath());
			
			Record record = database.factory()
				.select(Backups.ID)
				.from(Backups.BACKUPS)
				.orderBy(Backups.RUN_TIME.desc())
				.fetchAny();
			if (record != null) {
				previousBackupId = record.getValue(Backups.ID).intValue();
			}
			
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
			
			cleaningUp = true;
			fireBackupStatusChanged();
			try {
				removeOldBackups();
				removeUnusedFiles();
				removeOldDatabaseBackups();
			} finally {
				cleaningUp = false;
			}
			
			database.factory().query("ANALYZE").execute(); //$NON-NLS-1$
		} catch (SQLException e) {
			BackupPlugin.getDefault().logError("error while running backup", e); //$NON-NLS-1$
		} catch (RuntimeException e) {
			BackupPlugin.getDefault().logError("error while running backup", e); //$NON-NLS-1$
		} finally {
			database.close();

			backupDatabase();
			
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
			List<Long> timestamps = new ArrayList<Long>();
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
		for (IFileSystemEntry entry : folder.list()) {
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

		currentFile = file.getAbsolutePath();
		fireBackupStatusChanged();
		
		FileTime creationTime = file.getCreationTime();
		FileTime lastModificationTime = file.getLastModificationTime();

		int fileId = -1;
		String checksum = null;
		if (settings.isUseChecksums()) {
			checksum = getChecksum(file);
			fileId = findOldFile(file, checksum);
		} else {
			int entryId = findFileOrFolderEntryInPreviousBackup(file);
			if (entryId > 0) {
				Record record = database.factory()
					.select(Entries.MODIFICATION_TIME,
							de.blizzy.backup.database.schema.tables.Files.ID,
							de.blizzy.backup.database.schema.tables.Files.LENGTH,
							de.blizzy.backup.database.schema.tables.Files.CHECKSUM)
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
						(entryLength == file.getLength())) {
						
						fileId = record.getValue(de.blizzy.backup.database.schema.tables.Files.ID).intValue();
						checksum = record.getValue(de.blizzy.backup.database.schema.tables.Files.CHECKSUM);
					}
				}
			}
		}
		if (fileId <= 0) {
			if (checksum == null) {
				checksum = getChecksum(file);
			}
			String backupFilePath = createBackupFilePath();
			File backupFile = Utils.toBackupFile(backupFilePath, settings.getOutputFolder());
			fileId = backupFileContents(file, backupFile, backupFilePath, checksum);
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
	
	private int findFileOrFolderEntryInPreviousBackup(IFileSystemEntry file) throws SQLException, IOException {
		if (previousBackupId > 0) {
			if (file.isFolder()) {
				// try to find folder as root folder
				Record record = database.factory()
					.select(Entries.ID)
					.from(Entries.ENTRIES)
					.where(Entries.NAME.equal(file.getAbsolutePath()), Entries.PARENT_ID.isNull(),
							Entries.BACKUP_ID.equal(Integer.valueOf(previousBackupId)))
					.fetchAny();
				if (record != null) {
					return record.getValue(Entries.ID).intValue();
				}
			}
			
			// find folder in parent folder
			IFolder parentFolder = file.getParentFolder();
			if (parentFolder != null) {
				int parentFolderId = findFileOrFolderEntryInPreviousBackup(parentFolder);
				if (parentFolderId > 0) {
					Record record = database.factory()
						.select(Entries.ID)
						.from(Entries.ENTRIES)
						.where(Entries.NAME.equal(file.getName()), Entries.PARENT_ID.equal(Integer.valueOf(parentFolderId)),
								Entries.BACKUP_ID.equal(Integer.valueOf(previousBackupId)))
						.fetchAny();
					if (record != null) {
						return record.getValue(Entries.ID).intValue();
					}
				}
			}
		}
		
		return -1;
	}

	private int findOldFile(IFile file, String checksum) throws SQLException, IOException {
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

	private int backupFileContents(IFile file, File backupFile, String backupFilePath, String checksum)
		throws IOException, SQLException {

		FileUtils.forceMkdir(backupFile.getParentFile());
		InputStream in = null;
		OutputStream out = null;
		try {
			in = new BufferedInputStream(file.getInputStream());
			out = new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(backupFile)));
			IOUtils.copy(in, out);
			out.flush();
		} finally {
			IOUtils.closeQuietly(in);
			IOUtils.closeQuietly(out);
		}
		
		database.factory()
			.insertInto(de.blizzy.backup.database.schema.tables.Files.FILES)
			.set(de.blizzy.backup.database.schema.tables.Files.BACKUP_PATH, backupFilePath)
			.set(de.blizzy.backup.database.schema.tables.Files.CHECKSUM, checksum)
			.set(de.blizzy.backup.database.schema.tables.Files.LENGTH, Long.valueOf(file.getLength()))
			.execute();
		return database.factory().lastID().intValue();
	}
	
	private String getChecksum(IFile file) throws IOException {
		InputStream in = null;
		try {
			in = new BufferedInputStream(file.getInputStream());
			return DigestUtils.md5Hex(in);
		} finally {
			IOUtils.closeQuietly(in);
		}
	}

	private String createBackupFilePath() {
		return BACKUP_PATH_FORMAT.format(new Date()) + "/" + UUID.randomUUID().toString(); //$NON-NLS-1$
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

	public String getCurrentFile() {
		return currentFile;
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
	}
	
	private void removeOldBackupsDaily() throws SQLException {
		// collect IDs of all but the most recent backup each day
		Set<Integer> backupsToRemove = new HashSet<Integer>();
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
		Set<Integer> backupsToRemove = new HashSet<Integer>();
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
			List<Date> days = new ArrayList<Date>();
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
		Set<FileEntry> filesToRemove = new HashSet<FileEntry>();
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
		
		BackupPlugin.getDefault().logMessage("Removing unused files: " + filesToRemove); //$NON-NLS-1$
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

	public boolean isCleaningUp() {
		return cleaningUp;
	}
}
