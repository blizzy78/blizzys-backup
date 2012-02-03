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
package de.blizzy.backup.check;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.security.DigestOutputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.sql.SQLException;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.swt.widgets.Shell;
import org.jooq.Cursor;
import org.jooq.Record;
import org.jooq.impl.Factory;

import de.blizzy.backup.BackupPlugin;
import de.blizzy.backup.Compression;
import de.blizzy.backup.LengthOutputStream;
import de.blizzy.backup.Messages;
import de.blizzy.backup.Utils;
import de.blizzy.backup.database.Database;
import de.blizzy.backup.database.schema.Tables;
import de.blizzy.backup.settings.Settings;

public class CheckRun implements IRunnableWithProgress {
	private static final class FileCheckResult {
		static final FileCheckResult BROKEN = new FileCheckResult(false, null);
		
		boolean ok;
		String checksumSHA256;

		FileCheckResult(boolean ok, String checksumSHA256) {
			this.ok = ok;
			this.checksumSHA256 = checksumSHA256;
		}
	}
	
	private static final int SHA256_LENGTH = DigestUtils.sha256Hex(StringUtils.EMPTY).length();
	
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

	@Override
	public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
		database = new Database(settings, false);
		
		try {
			database.open();
			database.initialize();
			
			int numFiles = database.factory()
				.select(Factory.count())
				.from(Tables.FILES)
				.fetchOne(Factory.count())
				.intValue();
			monitor.beginTask(Messages.Title_CheckBackupIntegrity, numFiles);

			Cursor<Record> cursor = null;
			try {
				cursor = database.factory()
					.select(Tables.FILES.ID, Tables.FILES.BACKUP_PATH, Tables.FILES.CHECKSUM, Tables.FILES.LENGTH, Tables.FILES.COMPRESSION)
					.from(Tables.FILES)
					.fetchLazy();
				while (cursor.hasNext()) {
					if (monitor.isCanceled()) {
						throw new InterruptedException();
					}
					
					Record record = cursor.fetchOne();
					String backupPath = record.getValue(Tables.FILES.BACKUP_PATH);
					String checksum = record.getValue(Tables.FILES.CHECKSUM);
					long length = record.getValue(Tables.FILES.LENGTH).longValue();
					Compression compression = Compression.fromValue(record.getValue(Tables.FILES.COMPRESSION).intValue());
					FileCheckResult checkResult = checkFile(backupPath, checksum, length, compression);
					if (!checkResult.ok) {
						backupOk = false;
						break;
					}
					if (checksum.length() != SHA256_LENGTH) {
						Integer id = record.getValue(Tables.FILES.ID);
						database.factory()
							.update(Tables.FILES)
							.set(Tables.FILES.CHECKSUM, checkResult.checksumSHA256)
							.where(Tables.FILES.ID.equal(id))
							.execute();
					}
					monitor.worked(1);
				}
			} finally {
				database.closeQuietly(cursor);
			}
		} catch (SQLException | IOException e) {
			throw new InvocationTargetException(e);
		} finally {
			database.close();
			System.gc();
			monitor.done();
		}
	}

	private FileCheckResult checkFile(String backupPath, String checksum,
			long length, Compression compression) throws IOException {

		File backupFile = Utils.toBackupFile(backupPath, outputFolder);
		if (backupFile.isFile()) {
			InputStream in = null;
			OutputStream out = null;
			try {
				in = compression.getInputStream(new BufferedInputStream(new FileInputStream(backupFile)));
				LengthOutputStream lengthOut = new LengthOutputStream(new NullOutputStream());
				MessageDigest digest = MessageDigest.getInstance("SHA-256"); //$NON-NLS-1$
				out = new DigestOutputStream(lengthOut, digest);
				MessageDigest md5Digest = null;
				if (checksum.length() != SHA256_LENGTH) {
					md5Digest = MessageDigest.getInstance("MD5"); //$NON-NLS-1$
					out = new DigestOutputStream(out, md5Digest);
				}
				IOUtils.copy(in, out);
				out.flush();
				
				String fileChecksum = Hex.encodeHexString(digest.digest());
				String fileChecksumMD5 = (md5Digest != null) ? Hex.encodeHexString(md5Digest.digest()) : null;
				long fileLength = lengthOut.getLength();
				boolean ok = (fileLength == length) &&
						checksum.equals((checksum.length() == SHA256_LENGTH) ? fileChecksum : fileChecksumMD5);
				return new FileCheckResult(ok, fileChecksum);
			} catch (GeneralSecurityException e) {
				throw new RuntimeException(e);
			} finally {
				IOUtils.closeQuietly(in);
				IOUtils.closeQuietly(out);
			}
		}
		return FileCheckResult.BROKEN;
	}
}
