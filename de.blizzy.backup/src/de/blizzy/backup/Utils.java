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
import java.io.IOException;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.swt.widgets.Display;

import de.blizzy.backup.database.Database;
import de.blizzy.backup.vfs.IFolder;

public final class Utils {
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

	public static String createBackupFilePath() {
		return BACKUP_PATH_FORMAT.format(new Date()) + "/" + UUID.randomUUID().toString(); //$NON-NLS-1$
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
}
