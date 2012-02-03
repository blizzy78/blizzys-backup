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
package de.blizzy.backup.vfs.sftp;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.attribute.FileTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.schmizz.sshj.sftp.FileAttributes;
import net.schmizz.sshj.sftp.FileMode;
import net.schmizz.sshj.sftp.RemoteResourceInfo;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import de.blizzy.backup.vfs.ActionRunner;
import de.blizzy.backup.vfs.IAction;
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

	@Override
	public String getName() {
		return StringUtils.substringAfterLast(file, "/"); //$NON-NLS-1$
	}

	@Override
	public String getAbsolutePath() {
		return "sftp://" + location.getHost() + file; //$NON-NLS-1$
	}

	@Override
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

	@Override
	public boolean isHidden() throws IOException {
		return getName().startsWith("."); //$NON-NLS-1$
	}

	@Override
	public FileTime getCreationTime() throws IOException {
		return null;
	}

	@Override
	public FileTime getLastModificationTime() throws IOException {
		return FileTime.fromMillis(getFileAttributes().getMtime() * 1000L);
	}
	
	private FileAttributes getFileAttributes() throws IOException {
		if (fileAttributes == null) {
			IAction<FileAttributes> action = new IAction<FileAttributes>() {
				@Override
				public FileAttributes run() throws IOException {
					return location.getSftpClient().lstat(file);
				}
				
				@Override
				public boolean canRetry(IOException e) {
					return location.canRetryAction(e);
				}
			};
			fileAttributes = new ActionRunner<>(
					action, SftpLocation.MAX_TRIES, location).run();
		}
		return fileAttributes;
	}

	@Override
	public boolean isFolder() throws IOException {
		return getFileAttributes().getType() == FileMode.Type.DIRECTORY;
	}

	@Override
	public Set<IFileSystemEntry> list() throws IOException {
		IAction<List<RemoteResourceInfo>> action = new IAction<List<RemoteResourceInfo>>() {
			@Override
			public List<RemoteResourceInfo> run() throws IOException {
				return location.getSftpClient().ls(file);
			}
			
			@Override
			public boolean canRetry(IOException e) {
				return location.canRetryAction(e);
			}
		};
		List<RemoteResourceInfo> files = new ActionRunner<>(
				action, SftpLocation.MAX_TRIES, location).run();
		Set<IFileSystemEntry> result = new HashSet<>();
		for (RemoteResourceInfo file : files) {
			result.add(new SftpFileOrFolder(file.getPath(), location));
		}
		return result;
	}

	@Override
	public void copy(final IOutputStreamProvider outputStreamProvider) throws IOException {
		IAction<Void> action = new IAction<Void>() {
			@Override
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
			
			@Override
			public boolean canRetry(IOException e) {
				return location.canRetryAction(e);
			}
		};
		new ActionRunner<>(action, SftpLocation.MAX_TRIES, location).run();
	}
	
	@Override
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
