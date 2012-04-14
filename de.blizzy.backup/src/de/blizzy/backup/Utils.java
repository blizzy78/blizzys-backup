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
package de.blizzy.backup;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.swt.widgets.Display;
import org.jooq.Record;

import de.blizzy.backup.database.Database;
import de.blizzy.backup.database.schema.Tables;
import de.blizzy.backup.vfs.IFolder;

public final class Utils {
	public static interface IFileOrFolderEntry {
		boolean isFolder() throws IOException;
		IFileOrFolderEntry getParentFolder() throws IOException;
		String getAbsolutePath();
		String getName();
	}

	private static final String DIALOG_SECTION = BackupPlugin.ID + ".dialog"; //$NON-NLS-1$
	private static final DateFormat BACKUP_PATH_FORMAT =
		new SimpleDateFormat("yyyy'/'MM'/'dd'/'HHmm"); //$NON-NLS-1$

	private Utils() {}

	public static IDialogSettings getSection(String sectionId) {
		IDialogSettings root = BackupPlugin.getDefault().getDialogSettings();
		return getChildSection(getChildSection(root, DIALOG_SECTION), sectionId);
	}

	public static IDialogSettings getChildSection(IDialogSettings parent, String name) {
		IDialogSettings section = parent.getSection(name);
		if (section == null) {
			section = parent.addNewSection(name);
		}
		return section;
	}

	public static String createBackupFilePath(String outputFolder) {
		String datePath = BACKUP_PATH_FORMAT.format(new Date());
		File folder = toBackupFile(datePath + "/dummy", outputFolder).getParentFile(); //$NON-NLS-1$
		int maxIdx = getMaxBackupFileIndex(folder);
		int newIdx = maxIdx + 1;
		return datePath + "/" + toBackupFileName(newIdx); //$NON-NLS-1$
	}
	
	public static String createSampleBackupFilePath() {
		return BACKUP_PATH_FORMAT.format(new Date()) + "/" + toBackupFileName(Integer.MAX_VALUE); //$NON-NLS-1$
	}

	public static File toBackupFile(String backupFilePath, String outputFolder) {
		String[] parts = backupFilePath.split("/"); //$NON-NLS-1$
		File result = new File(outputFolder);
		for (String part : parts) {
			result = new File(result, part);
		}
		return result;
	}
	
	public static boolean isBackupFolder(String folder) {
		return StringUtils.isNotBlank(folder) && Database.containsDatabaseFolder(new File(folder));
	}
	
	static void runAsync(Display display, Runnable runnable) {
		if (Display.findDisplay(Thread.currentThread()) != null) {
			runnable.run();
		} else if (!display.isDisposed()) {
			display.asyncExec(runnable);
		}
	}
	
	public static String getSimpleName(IFolder folder) {
		String result = folder.getName();
		if (StringUtils.isBlank(result)) {
			result = folder.getAbsolutePath();
		}
		return result;
	}
	
	public static boolean isParent(IFolder parent, IFolder child) {
		for (; child != null; child = child.getParentFolder()) {
			if (parent.equals(child.getParentFolder())) {
				return true;
			}
		}
		return false;
	}
	
	public static void zipFile(File source, File target) throws IOException {
		ZipOutputStream out = null;
		try {
			out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(target)));
			out.setLevel(Deflater.BEST_COMPRESSION);
			
			ZipEntry entry = new ZipEntry(source.getName());
			entry.setTime(source.lastModified());
			out.putNextEntry(entry);
			
			Files.copy(source.toPath(), out);
		} finally {
			IOUtils.closeQuietly(out);
		}
	}
	
	public static File toCanonicalFile(File file) {
		try {
			return file.getCanonicalFile();
		} catch (IOException e) {
			// ignore
		}
		return file.getAbsoluteFile();
	}

	public static int findFileOrFolderEntryInBackup(IFileOrFolderEntry fileOrFolder, int backupId, Database database)
			throws IOException {
		
		if (fileOrFolder.isFolder()) {
			// try to find folder as root folder
			Record record = database.factory()
				.select(Tables.ENTRIES.ID)
				.from(Tables.ENTRIES)
				.where(Tables.ENTRIES.NAME.equal(fileOrFolder.getAbsolutePath()), Tables.ENTRIES.PARENT_ID.isNull(),
						Tables.ENTRIES.BACKUP_ID.equal(Integer.valueOf(backupId)))
				.fetchAny();
			if (record != null) {
				return record.getValue(Tables.ENTRIES.ID).intValue();
			}
		}
		
		// find entry in parent folder
		IFileOrFolderEntry parentFolder = fileOrFolder.getParentFolder();
		if (parentFolder != null) {
			int parentFolderId = findFileOrFolderEntryInBackup(parentFolder, backupId, database);
			if (parentFolderId > 0) {
				Record record = database.factory()
					.select(Tables.ENTRIES.ID)
					.from(Tables.ENTRIES)
					.where(Tables.ENTRIES.NAME.equal(fileOrFolder.getName()),
							Tables.ENTRIES.PARENT_ID.equal(Integer.valueOf(parentFolderId)),
							Tables.ENTRIES.BACKUP_ID.equal(Integer.valueOf(backupId)))
					.fetchAny();
				if (record != null) {
					return record.getValue(Tables.ENTRIES.ID).intValue();
				}
			}
		}
		
		return -1;
	}

	public static int getMaxBackupFileIndex(File folder) {
		if (folder.isDirectory()) {
			List<String> files = Arrays.asList(folder.list(new FilenameFilter() {
				@Override
				public boolean accept(File dir, String name) {
					return name.indexOf('-') < 0;
				}
			}));
			if (!files.isEmpty()) {
				Collections.sort(files, new Comparator<String>() {
					@Override
					public int compare(String f1, String f2) {
						int idx1 = toBackupFileIndex(f1);
						int idx2 = toBackupFileIndex(f2);
						if (idx1 < idx2) {
							return -1;
						}
						if (idx1 > idx2) {
							return 1;
						}
						return 0;
					}
				});
				return toBackupFileIndex(files.get(files.size() - 1));
			}
		}
		return 0;
	}
	
	public static String toBackupFileName(int index) {
		return Integer.toString(index, 36);
	}
	
	public static int toBackupFileIndex(String fileName) {
		return Integer.parseInt(fileName, 36);
	}
}
