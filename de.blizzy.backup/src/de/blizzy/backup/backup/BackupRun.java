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
import java.util.Collection;
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
import org.eclipse.jface.dialogs.IDialogSettings;
import org.jooq.Cursor;
import org.jooq.Record;
import org.jooq.exception.DataAccessException;

import de.blizzy.backup.BackupApplication;
import de.blizzy.backup.BackupPlugin;
import de.blizzy.backup.Compression;
import de.blizzy.backup.IStorageInterceptor;
import de.blizzy.backup.StorageInterceptorDescriptor;
import de.blizzy.backup.Utils;
import de.blizzy.backup.Utils.IFileOrFolderEntry;
import de.blizzy.backup.backup.BackupErrorEvent.Severity;
import de.blizzy.backup.database.Database;
import de.blizzy.backup.database.EntryType;
import de.blizzy.backup.database.schema.Tables;
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
	private Thread entriesCounterThread;
	private Database database;
	private int backupId;
	private List<IBackupRunListener> listeners = new ArrayList<>();
	private boolean running = true;
	private boolean paused;
	private int numEntries;
	private int totalEntries;
	private List<IStorageInterceptor> storageInterceptors = new ArrayList<>();
	private List<IFileSystemEntry> currentFileOrFolder = new ArrayList<>();

	public BackupRun(Settings settings) {
		this.settings = settings;
	}

	public void runBackup() {
		thread = new Thread(this, "Backup"); //$NON-NLS-1$
		thread.start();
		
		Runnable runnable = new Runnable() {
			@Override
			public void run() {
				try {
					countEntries();
				} catch (RuntimeException e) {
					BackupPlugin.getDefault().logError("error while counting entries", e); //$NON-NLS-1$
				}
			}
		};
		entriesCounterThread = new Thread(runnable, "Entries Counter"); //$NON-NLS-1$
		entriesCounterThread.start();
	}

	@Override
	public void run() {
		BackupPlugin.getDefault().logMessage("Starting backup"); //$NON-NLS-1$
		
		fireBackupStatusChanged(BackupStatus.INITIALIZE);
		
		final boolean[] ok = { true };
		List<StorageInterceptorDescriptor> descs = BackupPlugin.getDefault().getStorageInterceptors();
		for (final StorageInterceptorDescriptor desc : descs) {
			final IStorageInterceptor interceptor = desc.getStorageInterceptor();
			SafeRunner.run(new ISafeRunnable() {
				@Override
				public void run() throws Exception {
					IDialogSettings settings = Utils.getChildSection(
							Utils.getSection("storageInterceptors"), desc.getId()); //$NON-NLS-1$
					if (!interceptor.initialize(BackupApplication.getBackupShellWindow(), settings)) {
						ok[0] = false;
					}
				}
				
				@Override
				public void handleException(Throwable t) {
					ok[0] = false;
					interceptor.showErrorMessage(t, BackupApplication.getBackupShellWindow());
					BackupPlugin.getDefault().logError(
							"error while initializing storage interceptor '" + desc.getName() + "'", t); //$NON-NLS-1$ //$NON-NLS-2$
				}
			});
			storageInterceptors.add(interceptor);
		}

		try {
			if (ok[0]) {
				database = new Database(settings, true);
				try {
					database.open(storageInterceptors);
					database.initialize();
					
					database.factory()
						.insertInto(Tables.BACKUPS)
						.set(Tables.BACKUPS.RUN_TIME, new Timestamp(System.currentTimeMillis()))
						.execute();
					backupId = database.factory().lastID().intValue();
					
					for (ILocation location : settings.getLocations()) {
						if (!running) {
							break;
						}
						doPause();
		
						try {
							backupFolder(location.getRootFolder(), -1, location.getRootFolder().getAbsolutePath());
						} catch (IOException | RuntimeException e) {
							BackupPlugin.getDefault().logError("error while running backup", e); //$NON-NLS-1$
							fireBackupErrorOccurred(e, BackupErrorEvent.Severity.ERROR);
						} finally {
							location.close();
						}
					}
					
					database.factory()
						.update(Tables.BACKUPS)
						.set(Tables.BACKUPS.NUM_ENTRIES, Integer.valueOf(numEntries))
						.where(Tables.BACKUPS.ID.equal(Integer.valueOf(backupId)))
						.execute();
					
					fireBackupStatusChanged(BackupStatus.CLEANUP);
					removeOldBackups();
					consolidateDuplicateFiles();
					removeUnusedFiles();
					removeOldDatabaseBackups();
					
					database.factory().query("ANALYZE").execute(); //$NON-NLS-1$
				} catch (SQLException | IOException | RuntimeException e) {
					for (IStorageInterceptor interceptor : storageInterceptors) {
						interceptor.showErrorMessage(e, BackupApplication.getBackupShellWindow());
					}
					BackupPlugin.getDefault().logError("error while running backup", e); //$NON-NLS-1$
					fireBackupErrorOccurred(e, BackupErrorEvent.Severity.ERROR);
				} finally {
					fireBackupStatusChanged(BackupStatus.FINALIZE);
					database.close();
					backupDatabase();
					for (final IStorageInterceptor interceptor : storageInterceptors) {
						SafeRunner.run(new ISafeRunnable() {
							@Override
							public void run() throws Exception {
								interceptor.destroy();
							}
							
							@Override
							public void handleException(Throwable t) {
								BackupPlugin.getDefault().logError("error while destroying storage interceptor", t); //$NON-NLS-1$
							}
						});
					}
				}
			}
		} finally {
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
			fireBackupErrorOccurred(e, BackupErrorEvent.Severity.ERROR);
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
						fireBackupErrorOccurred(e, BackupErrorEvent.Severity.WARNING);
					}
				}
			}
		}
	}

	private int backupFolder(IFolder folder, int parentFolderId, String overrideName) throws IOException {
		currentFileOrFolder.add(folder);
		try {
			FileTime creationTime = folder.getCreationTime();
			FileTime lastModificationTime = folder.getLastModificationTime();
			database.factory()
				.insertInto(Tables.ENTRIES)
				.set(Tables.ENTRIES.PARENT_ID, (parentFolderId > 0) ? Integer.valueOf(parentFolderId) : null)
				.set(Tables.ENTRIES.BACKUP_ID, Integer.valueOf(backupId))
				.set(Tables.ENTRIES.TYPE, Byte.valueOf((byte) EntryType.FOLDER.getValue()))
				.set(Tables.ENTRIES.CREATION_TIME, (creationTime != null) ? new Timestamp(creationTime.toMillis()) : null)
				.set(Tables.ENTRIES.MODIFICATION_TIME, (lastModificationTime != null) ? new Timestamp(lastModificationTime.toMillis()) : null)
				.set(Tables.ENTRIES.HIDDEN, Boolean.valueOf(folder.isHidden()))
				.set(Tables.ENTRIES.NAME, StringUtils.isNotBlank(overrideName) ? overrideName : folder.getName())
				.set(Tables.ENTRIES.NAME_LOWER, StringUtils.isNotBlank(overrideName) ? overrideName.toLowerCase() : folder.getName().toLowerCase())
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
				doPause();
				
				if (entry.isFolder()) {
					try {
						backupFolder((IFolder) entry, id, null);
					} catch (IOException e) {
						BackupPlugin.getDefault().logError("error while backing up folder: " + //$NON-NLS-1$
								entry.getAbsolutePath(), e);
						fireBackupErrorOccurred(e, BackupErrorEvent.Severity.ERROR);
					}
				} else {
					try {
						backupFile((IFile) entry, id);
					} catch (IOException e) {
						BackupPlugin.getDefault().logError("error while backing up file: " + //$NON-NLS-1$
								entry.getAbsolutePath(), e);
						fireBackupErrorOccurred(e, BackupErrorEvent.Severity.ERROR);
					}
				}
			}
			
			return id;
		} finally {
			currentFileOrFolder.remove(currentFileOrFolder.size() - 1);
		}
	}
	
	private void backupFile(IFile file, int parentFolderId) throws IOException {
		currentFileOrFolder.add(file);
		try {
			if ((numEntries % 50) == 0) {
				checkDiskSpaceAndRemoveOldBackups();
			}
	
			fireBackupStatusChanged(new BackupStatus(file.getAbsolutePath(), numEntries, totalEntries));
			
			FileTime creationTime = file.getCreationTime();
			FileTime lastModificationTime = file.getLastModificationTime();
	
			int fileId = -1;
			if (settings.isUseChecksums()) {
				String checksum = getChecksum(file);
				fileId = findOldFileViaChecksum(file, checksum);
			} else {
				fileId = findOldFileViaTimestamp(file);
			}
			EntryType type = EntryType.FILE;
			if (fileId <= 0) {
				try {
					String backupFilePath = Utils.createBackupFilePath(settings.getOutputFolder());
					File backupFile = Utils.toBackupFile(backupFilePath, settings.getOutputFolder());
					fileId = backupFileContents(file, backupFile, backupFilePath);
				} catch (IOException e) {
					BackupPlugin.getDefault().logError("error while backing up file: " + //$NON-NLS-1$
							file.getAbsolutePath(), e);
					// file might be in use this time so only show a warning instead of an error
					fireBackupErrorOccurred(e, Severity.WARNING);
					type = EntryType.FAILED_FILE;
				}
			}
	
			database.factory()
				.insertInto(Tables.ENTRIES)
				.set(Tables.ENTRIES.PARENT_ID, Integer.valueOf(parentFolderId))
				.set(Tables.ENTRIES.BACKUP_ID, Integer.valueOf(backupId))
				.set(Tables.ENTRIES.TYPE, Byte.valueOf((byte) type.getValue()))
				.set(Tables.ENTRIES.CREATION_TIME, (creationTime != null) ? new Timestamp(creationTime.toMillis()) : null)
				.set(Tables.ENTRIES.MODIFICATION_TIME, (lastModificationTime != null) ? new Timestamp(lastModificationTime.toMillis()) : null)
				.set(Tables.ENTRIES.HIDDEN, Boolean.valueOf(file.isHidden()))
				.set(Tables.ENTRIES.NAME, file.getName())
				.set(Tables.ENTRIES.NAME_LOWER, file.getName().toLowerCase())
				.set(Tables.ENTRIES.FILE_ID, (fileId > 0) ? Integer.valueOf(fileId) : null)
				.execute();
			
			numEntries++;
		} finally {
			currentFileOrFolder.remove(currentFileOrFolder.size() - 1);
		}
	}
	
	private int findOldFileViaTimestamp(IFile file) throws IOException {
		FileTime lastModificationTime = file.getLastModificationTime();
		long length = file.getLength();
		Cursor<Record> cursor = null;
		try {
			cursor = database.factory()
				.select(Tables.BACKUPS.ID)
				.from(Tables.BACKUPS)
				.where(Tables.BACKUPS.ID.notEqual(Integer.valueOf(backupId)))
				.orderBy(Tables.BACKUPS.RUN_TIME.desc())
				.fetchLazy();
			while (cursor.hasNext()) {
				int backupId = cursor.fetchOne().getValue(Tables.BACKUPS.ID).intValue();
				int entryId = findFileOrFolderEntryInBackup(file, backupId);
				if (entryId > 0) {
					Record record = database.factory()
						.select(Tables.ENTRIES.MODIFICATION_TIME,
								Tables.FILES.ID,
								Tables.FILES.LENGTH)
						.from(Tables.ENTRIES)
						.join(Tables.FILES)
							.on(Tables.FILES.ID.equal(Tables.ENTRIES.FILE_ID))
						.where(Tables.ENTRIES.ID.equal(Integer.valueOf(entryId)),
								Tables.ENTRIES.TYPE.equal(Byte.valueOf((byte) EntryType.FILE.getValue())))
						.fetchAny();
					if (record != null) {
						Timestamp entryModTime = record.getValue(Tables.ENTRIES.MODIFICATION_TIME);
						long entryModificationTime = (entryModTime != null) ? entryModTime.getTime() : -1;
						long entryLength = record.getValue(Tables.FILES.LENGTH).longValue();
						if ((entryModificationTime > 0) &&
							(lastModificationTime != null) && (entryModificationTime == lastModificationTime.toMillis()) &&
							(entryLength == length)) {
							
							return record.getValue(Tables.FILES.ID).intValue();
						}
					}
				}
			}
		} finally {
			database.closeQuietly(cursor);
		}
		
		return -1;
	}
	
	private int findFileOrFolderEntryInBackup(final IFileSystemEntry file, int backupId) throws IOException {
		IFileOrFolderEntry entry = toFileOrFolderEntry(file);
		return Utils.findFileOrFolderEntryInBackup(entry, backupId, database);
	}
	
	private Utils.IFileOrFolderEntry toFileOrFolderEntry(final IFileSystemEntry fileOrFolder) {
		return new Utils.IFileOrFolderEntry() {
			@Override
			public boolean isFolder() throws IOException {
				return fileOrFolder.isFolder();
			}
			
			@Override
			public IFileOrFolderEntry getParentFolder() {
				IFolder parentFolder = fileOrFolder.getParentFolder();
				return (parentFolder != null) ? toFileOrFolderEntry(parentFolder) : null;
			}
			
			@Override
			public String getName() {
				return fileOrFolder.getName();
			}
			
			@Override
			public String getAbsolutePath() {
				return fileOrFolder.getAbsolutePath();
			}
		};
	}

	private int findOldFileViaChecksum(IFile file, String checksum) throws IOException {
		Record record = database.factory()
			.select(Tables.FILES.ID)
			.from(Tables.FILES)
			.where(Tables.FILES.CHECKSUM.equal(checksum),
					Tables.FILES.LENGTH.equal(Long.valueOf(file.getLength())))
			.fetchAny();
		return (record != null) ?
				record.getValue(Tables.FILES.ID).intValue() :
				-1;
	}

	private int backupFileContents(final IFile file, final File backupFile, String backupFilePath)
		throws IOException {

		FileUtils.forceMkdir(backupFile.getParentFile());

		final MessageDigest[] digest = new MessageDigest[1];
		IOutputStreamProvider outputStreamProvider = new IOutputStreamProvider() {
			@Override
			public OutputStream getOutputStream() throws IOException {
				try {
					digest[0] = MessageDigest.getInstance("SHA-256"); //$NON-NLS-1$
					OutputStream fileOut = new BufferedOutputStream(new FileOutputStream(backupFile));
					OutputStream interceptOut = fileOut;
					for (IStorageInterceptor interceptor : storageInterceptors) {
						interceptOut = interceptor.interceptOutputStream(interceptOut, file.getLength());
					}
					OutputStream compressOut = Compression.BZIP2.getOutputStream(interceptOut);
					OutputStream digestOut = new DigestOutputStream(compressOut, digest[0]);
					return digestOut;
				} catch (GeneralSecurityException e) {
					throw new RuntimeException(e);
				}
			}
		};
		boolean fileCopied = false;
		try {
			file.copy(outputStreamProvider);
			fileCopied = true;
		} finally {
			if (!fileCopied) {
				try {
					Files.delete(backupFile.toPath());
				} catch (IOException e) {
					BackupPlugin.getDefault().logError("error while deleting file: " + //$NON-NLS-1$
							backupFile.getAbsolutePath(), e);
					fireBackupErrorOccurred(e, BackupErrorEvent.Severity.WARNING);
				}
				removeFoldersIfEmpty(backupFile.getParentFile());
			}
		}
		
		String checksum = toHexString(digest[0]);

		database.factory()
			.insertInto(Tables.FILES)
			.set(Tables.FILES.BACKUP_PATH, backupFilePath)
			.set(Tables.FILES.CHECKSUM, checksum)
			.set(Tables.FILES.LENGTH, Long.valueOf(file.getLength()))
			.set(Tables.FILES.COMPRESSION, Byte.valueOf((byte) Compression.BZIP2.getValue()))
			.execute();
		return database.factory().lastID().intValue();
	}
	
	private String getChecksum(IFile file) throws IOException {
		final MessageDigest[] digest = new MessageDigest[1];
		IOutputStreamProvider outputStreamProvider = new IOutputStreamProvider() {
			@Override
			public OutputStream getOutputStream() {
				try {
					digest[0] = MessageDigest.getInstance("SHA-256"); //$NON-NLS-1$
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
					BackupPlugin.getDefault().logError("error while notifying listener", t); //$NON-NLS-1$
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
					BackupPlugin.getDefault().logError("error while notifying listener", t); //$NON-NLS-1$
				}
			});
		}
	}

	private void fireBackupErrorOccurred(Throwable error, BackupErrorEvent.Severity severity) {
		IFileSystemEntry fileOrFolder = !currentFileOrFolder.isEmpty() ?
				currentFileOrFolder.get(currentFileOrFolder.size() - 1) :
				null;
		final BackupErrorEvent e = new BackupErrorEvent(this, fileOrFolder, new Date(), error, severity);
		for (final IBackupRunListener listener : getListeners()) {
			SafeRunner.run(new ISafeRunnable() {
				@Override
				public void run() throws Exception {
					listener.backupErrorOccurred(e);
				}
				
				@Override
				public void handleException(Throwable t) {
					BackupPlugin.getDefault().logError("error while notifying listener", t); //$NON-NLS-1$
				}
			});
		}
	}

	public void stopBackupAndWait() {
		running = false;
		try {
			entriesCounterThread.join();
		} catch (InterruptedException e) {
			// ignore
		}
		try {
			thread.join();
		} catch (InterruptedException e) {
			// ignore
		}
	}
	
	public void stopBackup() {
		running = false;
		setPaused(false);
	}
	
	public void setPaused(boolean paused) {
		synchronized (this) {
			this.paused = paused;
			notify();
		}
	}
	

	private void removeOldBackups() {
		removeOldBackupsMaxAge();
		removeOldBackupsDaily();
		removeOldBackupsWeekly();
		removeFailedBackups();
	}

	private void removeFailedBackups() {
		Set<Integer> ids = new HashSet<>(database.factory()
			.select(Tables.BACKUPS.ID)
			.from(Tables.BACKUPS)
			.where(Tables.BACKUPS.NUM_ENTRIES.isNull())
			.fetch(Tables.BACKUPS.ID));
		removeBackups(ids);
	}
	
	private void removeOldBackupsMaxAge() {
		if (settings.getMaxAgeDays() > 0) {
			Calendar c = Calendar.getInstance();
			c.add(Calendar.DAY_OF_MONTH, -settings.getMaxAgeDays());
			List<Integer> backupIds = getBackupIds(0, c.getTimeInMillis());
			
			BackupPlugin.getDefault().logMessage("removing backups (age): " + backupIds); //$NON-NLS-1$
			if (!backupIds.isEmpty()) {
				removeBackups(backupIds);
			}
		}
	}
	
	private void removeOldBackupsDaily() {
		// collect IDs of all but the most recent backup each day
		Set<Integer> backupsToRemove = new HashSet<>();
		List<Date> days = getBackupRunsDays(BackupPlugin.KEEP_HOURLIES_DAYS);
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

	private List<Integer> getBackupIds(long start, long end) {
		return database.factory()
			.select(Tables.BACKUPS.ID)
			.from(Tables.BACKUPS)
			.where(Tables.BACKUPS.RUN_TIME.greaterOrEqual(new Timestamp(start)),
					Tables.BACKUPS.RUN_TIME.lessThan(new Timestamp(end)))
			.orderBy(Tables.BACKUPS.RUN_TIME.desc())
			.fetch(Tables.BACKUPS.ID);
	}
	
	private void removeOldBackupsWeekly() {
		// collect IDs of all but the most recent backup each week
		Set<Integer> backupsToRemove = new HashSet<>();
		List<Date> days = getBackupRunsDays(BackupPlugin.KEEP_DAILIES_DAYS);
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

	private List<Date> getBackupRunsDays(int skipDays) {
		Cursor<Record> cursor = null;
		try {
			// get all days where there are backups (and which are older than skipDays days)
			Calendar c = Calendar.getInstance();
			c.add(Calendar.DAY_OF_YEAR, -skipDays);
			c.set(Calendar.HOUR_OF_DAY, 0);
			c.set(Calendar.MINUTE, 0);
			c.set(Calendar.SECOND, 0);
			c.set(Calendar.MILLISECOND, 0);
			cursor = database.factory()
				.select(Tables.BACKUPS.RUN_TIME)
				.from(Tables.BACKUPS)
				.where(Tables.BACKUPS.RUN_TIME.lessThan(new Timestamp(c.getTimeInMillis())))
				.orderBy(Tables.BACKUPS.RUN_TIME)
				.fetchLazy();
			List<Date> days = new ArrayList<>();
			while (cursor.hasNext()) {
				Record record = cursor.fetchOne();
				c.setTimeInMillis(record.getValue(Tables.BACKUPS.RUN_TIME).getTime());
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

	private void removeBackups(Collection<Integer> backupsToRemove) {
		for (Integer backupId : backupsToRemove) {
			database.factory()
				.delete(Tables.ENTRIES)
				.where(Tables.ENTRIES.BACKUP_ID.equal(backupId))
				.execute();
			database.factory()
				.delete(Tables.BACKUPS)
				.where(Tables.BACKUPS.ID.equal(backupId))
				.execute();
		}
	}

	private void removeUnusedFiles() {
		Cursor<Record> cursor = null;
		Set<FileEntry> filesToRemove = new HashSet<>();
		try {
			cursor = database.factory()
				.select(Tables.FILES.ID,
						Tables.FILES.BACKUP_PATH)
				.from(Tables.FILES)
				.leftOuterJoin(Tables.ENTRIES)
					.on(Tables.ENTRIES.FILE_ID.equal(Tables.FILES.ID))
				.where(Tables.ENTRIES.FILE_ID.isNull())
				.fetchLazy();
			while (cursor.hasNext()) {
				Record record = cursor.fetchOne();
				FileEntry file = new FileEntry(
						record.getValue(Tables.FILES.ID).intValue(),
						record.getValue(Tables.FILES.BACKUP_PATH));
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

	private void removeFiles(Set<FileEntry> files) {
		for (FileEntry file : files) {
			File f = Utils.toBackupFile(file.backupPath, settings.getOutputFolder());
			Path path = f.toPath();
			try {
				Files.delete(path);
			} catch (IOException e) {
				BackupPlugin.getDefault().logError("error deleting file: " + file.backupPath, e); //$NON-NLS-1$
				fireBackupErrorOccurred(e, BackupErrorEvent.Severity.WARNING);
			}
			
			removeFoldersIfEmpty(f.getParentFile());
			
			database.factory()
				.delete(Tables.FILES)
				.where(Tables.FILES.ID.equal(Integer.valueOf(file.id)))
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
				fireBackupErrorOccurred(e, BackupErrorEvent.Severity.WARNING);
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
					
					double avail = available * 100d / total;
					if (avail >= (100d - settings.getMaxDiskFillRate())) {
						break;
					}
					
					if (!removeOldestBackup()) {
						break;
					}
					
					removeUnusedFiles();
				}
			}
		} catch (IOException e) {
			// ignore
		} catch (DataAccessException e) {
			BackupPlugin.getDefault().logError("error removing oldest backup", e); //$NON-NLS-1$
			fireBackupErrorOccurred(e, BackupErrorEvent.Severity.WARNING);
		}
	}
	
	private boolean removeOldestBackup() {
		Record record = database.factory()
			.select(Tables.BACKUPS.ID)
			.from(Tables.BACKUPS)
			.where(Tables.BACKUPS.NUM_ENTRIES.isNotNull())
			.orderBy(Tables.BACKUPS.RUN_TIME)
			.fetchAny();
		if (record != null) {
			Integer id = record.getValue(Tables.BACKUPS.ID);
			BackupPlugin.getDefault().logMessage("removing backup: " + id); //$NON-NLS-1$
			removeBackups(Collections.singleton(id));
		}
		return record != null;
	}

	private void consolidateDuplicateFiles() {
		Cursor<Record> cursor = null;
		try {
			cursor = database.factory()
				.select(Tables.FILES.CHECKSUM,
						Tables.FILES.LENGTH)
				.from(Tables.FILES)
				.groupBy(Tables.FILES.CHECKSUM,
						Tables.FILES.LENGTH)
				.having(Tables.FILES.CHECKSUM.count().greaterThan(Integer.valueOf(1)))
				.fetchLazy();
			while (cursor.hasNext()) {
				Record record = cursor.fetchOne();
				String checksum = record.getValue(Tables.FILES.CHECKSUM);
				long length = record.getValue(Tables.FILES.LENGTH).longValue();
				consolidateDuplicateFiles(checksum, length);
			}
		} finally {
			database.closeQuietly(cursor);
		}
	}
	
	private void consolidateDuplicateFiles(String checksum, long length) {
		Cursor<Record> cursor = null;
		try {
			List<Integer> fileIds = database.factory()
				.select(Tables.FILES.ID)
				.from(Tables.FILES)
				.where(Tables.FILES.CHECKSUM.equal(checksum),
						Tables.FILES.LENGTH.equal(Long.valueOf(length)))
				.fetch(Tables.FILES.ID);
			if (fileIds.size() >= 2) {
				int masterFileId = fileIds.get(0).intValue();
				fileIds = fileIds.subList(1, fileIds.size());
				consolidateDuplicateFiles(masterFileId, fileIds);
			}
		} finally {
			database.closeQuietly(cursor);
		}
	}

	private void consolidateDuplicateFiles(int masterFileId, List<Integer> fileIds) {
		BackupPlugin.getDefault().logMessage("consolidating duplicate files: " + masterFileId + " <- " + fileIds); //$NON-NLS-1$ //$NON-NLS-2$

		Integer masterId = Integer.valueOf(masterFileId);
		while (!fileIds.isEmpty()) {
			int endIdx = Math.min(fileIds.size(), 10);
			List<Integer> chunk = fileIds.subList(0, endIdx);
			database.factory()
				.update(Tables.ENTRIES)
				.set(Tables.ENTRIES.FILE_ID, masterId)
				.where(Tables.ENTRIES.FILE_ID.in(chunk))
				.execute();
			fileIds = fileIds.subList(endIdx, fileIds.size());
		}
	}

	private void countEntries() {
		for (ILocation location : settings.getLocations()) {
			countEntries(location.getRootFolder());
		}
	}

	private void countEntries(IFolder folder) {
		try {
			for (IFileSystemEntry entry : folder.list()) {
				if (!running) {
					break;
				}

				if (entry.isFolder()) {
					countEntries((IFolder) entry);
				} else {
					totalEntries++;
				}
			}
		} catch (IOException e) {
			BackupPlugin.getDefault().logError("error while counting entries in folder: " + folder.getAbsolutePath(), e); //$NON-NLS-1$
			fireBackupErrorOccurred(e, BackupErrorEvent.Severity.WARNING);
		}
	}
	
	private void doPause() {
		synchronized (this) {
			while (paused) {
				try {
					wait();
				} catch (InterruptedException e) {
					// ignore
				}
			}
		}
	}
}
