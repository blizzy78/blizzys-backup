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
package de.blizzy.backup.vfs;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.attribute.FileTime;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.vfs2.FileContent;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileType;

public abstract class RemoteFileOrFolder<L extends RemoteLocation> implements IFolder, IFile {
	private static final int BUFFER_SIZE = 128 * 1024;
	
	private String file;
	private L location;
	private FileObject fileObject;
	private FileContent fileContent;
	private Boolean isFolder;

	public RemoteFileOrFolder(String file, L location) {
		this.file = file;
		this.location = location;
	}

	@Override
	public String getName() {
		return StringUtils.substringAfterLast(file, "/"); //$NON-NLS-1$
	}
	
	@Override
	public String getAbsolutePath() {
		return location.getProtocol() + "://" + location.getHost() + file; //$NON-NLS-1$
	}

	@Override
	public IFolder getParentFolder() {
		if (!file.equals("/")) { //$NON-NLS-1$
			String parentFolder = StringUtils.substringBeforeLast(file, "/"); //$NON-NLS-1$
			if (StringUtils.isBlank(parentFolder)) {
				parentFolder = "/"; //$NON-NLS-1$
			}
			return getFolder(parentFolder);
		}
		return null;
	}

	protected abstract IFolder getFolder(String folder);

	protected L getLocation() {
		return location;
	}
	
	@Override
	public boolean isHidden() {
		return getName().startsWith("."); //$NON-NLS-1$
	}

	@Override
	public FileTime getCreationTime() {
		return null;
	}

	@Override
	public FileTime getLastModificationTime() throws IOException {
		FileContent content = getFileContent();
		try {
			long lastModifiedTime = content.getLastModifiedTime();
			return FileTime.fromMillis(lastModifiedTime);
		} catch (FileSystemException e) {
			return null;
		}
	}

	private FileObject getFileObject() throws IOException {
		if (fileObject == null) {
			IAction<FileObject> action = new IAction<FileObject>() {
				@Override
				public FileObject run() throws IOException {
					return location.resolveFile(file);
				}
				
				@Override
				public boolean canRetry(IOException e) {
					return location.canRetryAction(e);
				}
			};
			fileObject = new ActionRunner<>(action, RemoteLocation.MAX_TRIES, location).run();
		}
		return fileObject;
	}

	private FileContent getFileContent() throws IOException {
		if (fileContent == null) {
			final FileObject fileObject = getFileObject();
			IAction<FileContent> action = new IAction<FileContent>() {
				@Override
				public FileContent run() throws IOException {
					return fileObject.getContent();
				}
				
				@Override
				public boolean canRetry(IOException e) {
					return location.canRetryAction(e);
				}
			};
			fileContent = new ActionRunner<>(action, RemoteLocation.MAX_TRIES, location).run();
		}
		return fileContent;
	}

	@Override
	public boolean isFolder() throws IOException {
		if (isFolder == null) {
			isFolder = Boolean.valueOf(getFileObject().getType().equals(FileType.FOLDER));
		}
		return isFolder.booleanValue();
	}

	public void setIsFolder() {
		isFolder = Boolean.TRUE;
	}

	@Override
	public void copy(final IOutputStreamProvider outputStreamProvider) throws IOException {
		final FileContent fileContent = getFileContent();
		IAction<Void> action = new IAction<Void>() {
			@Override
			public Void run() throws IOException {
				InputStream in = null;
				OutputStream out = null;
				try {
					in = new BufferedInputStream(fileContent.getInputStream(), BUFFER_SIZE);
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
		new ActionRunner<>(action, RemoteLocation.MAX_TRIES, location).run();
	}

	@Override
	public long getLength() throws IOException {
		return getFileContent().getSize();
	}

	@Override
	public Set<IFileSystemEntry> list() throws IOException {
		final FileObject fileObject = getFileObject();
		IAction<Set<IFileSystemEntry>> action = new IAction<Set<IFileSystemEntry>>() {
			@Override
			public Set<IFileSystemEntry> run() throws IOException {
				Set<IFileSystemEntry> result = new HashSet<>();
				for (FileObject child : fileObject.getChildren()) {
					result.add(getFileOrFolder(file + "/" + child.getName().getBaseName())); //$NON-NLS-1$
				}
				return result;
			}
			
			@Override
			public boolean canRetry(IOException e) {
				return location.canRetryAction(e);
			}
		};
		return new ActionRunner<>(action, RemoteLocation.MAX_TRIES, location).run();
	}
	
	protected abstract IFolder getFileOrFolder(String file);
	
	@Override
	public int hashCode() {
		return file.hashCode();
	}
	
	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		} else if ((o != null) && o.getClass().equals(getClass())) {
			RemoteFileOrFolder<?> other = (RemoteFileOrFolder<?>) o;
			return other.file.equals(file);
		}
		return false;
	}
}
