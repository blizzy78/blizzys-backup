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
package de.blizzy.backup.vfs.sftp;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.attribute.FileTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.schmizz.sshj.common.DisconnectReason;
import net.schmizz.sshj.sftp.FileAttributes;
import net.schmizz.sshj.sftp.FileMode;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import de.blizzy.backup.vfs.IFile;
import de.blizzy.backup.vfs.IFileSystemEntry;
import de.blizzy.backup.vfs.IFolder;
import de.blizzy.backup.vfs.IOutputStreamProvider;

class SftpFileOrFolder implements IFile, IFolder {
	private static final int BUFFER_SIZE = 128 * 1024;
	
	private String file;
	private SftpLocation location;
	private FileAttributes fileAttributes;

	SftpFileOrFolder(String file, SftpLocation location) {
		this.file = file;
		this.location = location;
	}

	public String getName() {
		return StringUtils.substringAfterLast(file, "/"); //$NON-NLS-1$
	}

	public String getAbsolutePath() {
		return "sftp://" + location.getHost() + file; //$NON-NLS-1$
	}

	public IFolder getParentFolder() {
		if (!file.equals("/")) { //$NON-NLS-1$
			String parentFolder = StringUtils.substringBeforeLast(file, "/"); //$NON-NLS-1$
			if (StringUtils.isBlank(parentFolder)) {
				parentFolder = "/"; //$NON-NLS-1$
			}
			return new SftpFileOrFolder(parentFolder, location);
		}
		return null;
	}

	public boolean isHidden() throws IOException {
		return getName().startsWith("."); //$NON-NLS-1$
	}

	public FileTime getCreationTime() throws IOException {
		return null;
	}

	public FileTime getLastModificationTime() throws IOException {
		return FileTime.fromMillis(getFileAttributes().getMtime());
	}
	
	private FileAttributes getFileAttributes() throws IOException {
		if (fileAttributes == null) {
			IAction<FileAttributes> action = new IAction<FileAttributes>() {
				public FileAttributes run() throws IOException {
					return location.getSftpClient().lstat(file);
				}
				
				public boolean canRetry(IOException e) {
					return (e instanceof SFTPException) &&
						(((SFTPException) e).getDisconnectReason() == DisconnectReason.UNKNOWN);
				}
			};
			fileAttributes = new ActionRunner<FileAttributes>(
					action, SftpLocation.MAX_TRIES, location).run();
		}
		return fileAttributes;
	}

	public boolean isFolder() throws IOException {
		return getFileAttributes().getType() == FileMode.Type.DIRECTORY;
	}

	public Set<IFileSystemEntry> list() throws IOException {
		IAction<List<RemoteResourceInfo>> action = new IAction<List<RemoteResourceInfo>>() {
			public List<RemoteResourceInfo> run() throws IOException {
				return location.getSftpClient().ls(file);
			}
			
			public boolean canRetry(IOException e) {
				return (e instanceof SFTPException) &&
					(((SFTPException) e).getDisconnectReason() == DisconnectReason.UNKNOWN);
			}
		};
		List<RemoteResourceInfo> files = new ActionRunner<List<RemoteResourceInfo>>(
				action, SftpLocation.MAX_TRIES, location).run();
		Set<IFileSystemEntry> result = new HashSet<IFileSystemEntry>();
		for (RemoteResourceInfo file : files) {
			result.add(new SftpFileOrFolder(file.getPath(), location));
		}
		return result;
	}

	public void copy(final IOutputStreamProvider outputStreamProvider) throws IOException {
		IAction<Void> action = new IAction<Void>() {
			public Void run() throws IOException {
				InputStream in = null;
				OutputStream out = null;
				try {
					in = new BufferedInputStream(location.getSftpClient().open(file).getInputStream(), BUFFER_SIZE);
					out = outputStreamProvider.getOutputStream();
					IOUtils.copy(in, out);
				} finally {
					IOUtils.closeQuietly(in);
					IOUtils.closeQuietly(out);
				}
				return null;
			}
			
			public boolean canRetry(IOException e) {
				return (e instanceof SFTPException) &&
					(((SFTPException) e).getDisconnectReason() == DisconnectReason.UNKNOWN);
			}
		};
		new ActionRunner<Void>(action, SftpLocation.MAX_TRIES, location).run();
	}
	
	public long getLength() throws IOException {
		return getFileAttributes().getSize();
	}
	
	@Override
	public int hashCode() {
		return file.hashCode();
	}
	
	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		} else if ((o != null) && o.getClass().equals(getClass())) {
			SftpFileOrFolder other = (SftpFileOrFolder) o;
			return other.file.equals(file);
		}
		return false;
	}
}
