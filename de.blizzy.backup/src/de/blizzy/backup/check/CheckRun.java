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
package de.blizzy.backup.check;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.security.DigestOutputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.sql.SQLException;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.swt.widgets.Shell;
import org.jooq.Cursor;
import org.jooq.Record;

import de.blizzy.backup.BackupPlugin;
import de.blizzy.backup.Compression;
import de.blizzy.backup.LengthOutputStream;
import de.blizzy.backup.Messages;
import de.blizzy.backup.Utils;
import de.blizzy.backup.database.Database;
import de.blizzy.backup.database.schema.tables.Files;
import de.blizzy.backup.settings.Settings;

public class CheckRun implements IRunnableWithProgress {
	private Settings settings;
	private String outputFolder;
	private Shell parentShell;
	private Database database;
	private boolean backupOk = true;

	public CheckRun(Settings settings, Shell parentShell) {
		this.settings = settings;
		this.parentShell = parentShell;
		
		outputFolder = settings.getOutputFolder();
	}

	public void runCheck() {
		boolean canceled = false;
		boolean errors = false;
		try {
			ProgressMonitorDialog dlg = new ProgressMonitorDialog(parentShell);
			dlg.run(true, true, this);
		} catch (InvocationTargetException e) {
			BackupPlugin.getDefault().logError("error while checking backup integrity", e.getCause()); //$NON-NLS-1$
			errors = true;
		} catch (RuntimeException e) {
			BackupPlugin.getDefault().logError("error while checking backup integrity", e); //$NON-NLS-1$
			errors = true;
		} catch (InterruptedException e) {
			canceled = true;
		}
		
		if (!canceled) {
			if (errors) {
				MessageDialog.openError(parentShell, Messages.Title_BackupIntegrityCheck, Messages.ErrorsWhileCheckingBackup);
			} else {
				if (backupOk) {
					MessageDialog.openInformation(parentShell, Messages.Title_BackupIntegrityCheck, Messages.BackupIntegrityIntact);
				} else {
					MessageDialog.openError(parentShell, Messages.Title_BackupIntegrityCheck, Messages.BackupIntegrityNotIntact);
				}
			}
		}
	}

	public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
		database = new Database(settings);
		
		try {
			database.open();
			database.initialize();
			
			int numFiles = database.factory()
				.select(database.factory().count())
				.from(Files.FILES)
				.fetchOne(database.factory().count())
				.intValue();
			monitor.beginTask(Messages.Title_CheckBackupIntegrity, numFiles);

			Cursor<Record> cursor = null;
			try {
				cursor = database.factory()
					.select(Files.BACKUP_PATH, Files.CHECKSUM, Files.LENGTH, Files.COMPRESSION)
					.from(Files.FILES)
					.fetchLazy();
				while (cursor.hasNext()) {
					Record record = cursor.fetchOne();
					String backupPath = record.getValue(Files.BACKUP_PATH);
					String checksum = record.getValue(Files.CHECKSUM);
					long length = record.getValue(Files.LENGTH).longValue();
					Compression compression = Compression.fromValue(record.getValue(Files.COMPRESSION).intValue());
					if (!checkFile(backupPath, checksum, length, compression)) {
						backupOk = false;
						break;
					}
					monitor.worked(1);
				}
			} finally {
				database.closeQuietly(cursor);
			}
		} catch (SQLException e) {
			throw new InvocationTargetException(e);
		} catch (IOException e) {
			throw new InvocationTargetException(e);
		} finally {
			database.close();
			System.gc();
			monitor.done();
		}
	}

	private boolean checkFile(String backupPath, String checksum, long length, Compression compression) throws IOException {
		File backupFile = Utils.toBackupFile(backupPath, outputFolder);
		if (backupFile.isFile()) {
			InputStream in = null;
			DigestOutputStream out = null;
			try {
				in = compression.getInputStream(new BufferedInputStream(new FileInputStream(backupFile)));
				MessageDigest digest = MessageDigest.getInstance("MD5"); //$NON-NLS-1$
				LengthOutputStream lengthOut = new LengthOutputStream(new NullOutputStream());
				out = new DigestOutputStream(lengthOut, digest);
				IOUtils.copy(in, out);
				out.flush();
				
				String fileChecksum = Hex.encodeHexString(digest.digest());
				long fileLength = lengthOut.getLength();
				return (fileLength == length) && fileChecksum.equals(checksum);
			} catch (GeneralSecurityException e) {
				throw new RuntimeException(e);
			} finally {
				IOUtils.closeQuietly(in);
				IOUtils.closeQuietly(out);
			}
		}
		return false;
	}
}
