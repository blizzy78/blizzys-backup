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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.attribute.FileTime;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.vfs.FileContent;
import org.apache.commons.vfs.FileObject;
import org.apache.commons.vfs.FileSystemException;
import org.apache.commons.vfs.FileType;

import de.blizzy.backup.vfs.IFile;
import de.blizzy.backup.vfs.IFileSystemEntry;
import de.blizzy.backup.vfs.IFolder;

class SftpFileOrFolder implements IFolder, IFile {
	private String file;
	private SftpLocation location;
	private FileObject fileObject;
	private FileContent fileContent;
	private Boolean isFolder;

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
			SftpFileOrFolder parent = new SftpFileOrFolder(parentFolder, location);
			parent.setIsFolder();
			return parent;
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
		FileContent content = getFileContent();
		try {
			long lastModifiedTime = content.getLastModifiedTime();
			return FileTime.fromMillis(lastModifiedTime);
		} catch (FileSystemException e) {
			return null;
		}
	}

	private FileObject getFileObject() throws FileSystemException {
		if (fileObject == null) {
			fileObject = location.resolveFile(file);
		}
		return fileObject;
	}
	
	private FileContent getFileContent() throws FileSystemException {
		if (fileContent == null) {
			fileContent = getFileObject().getContent();
		}
		return fileContent;
	}

	public boolean isFolder() throws IOException {
		if (isFolder == null) {
			isFolder = Boolean.valueOf(getFileObject().getType().equals(FileType.FOLDER));
		}
		return isFolder.booleanValue();
	}
	
	void setIsFolder() {
		isFolder = Boolean.TRUE;
	}

	public InputStream getInputStream() throws IOException {
		return getFileContent().getInputStream();
	}

	public long getLength() throws IOException {
		return getFileContent().getSize();
	}

	public Set<IFileSystemEntry> list() throws IOException {
		Set<IFileSystemEntry> result = new HashSet<IFileSystemEntry>();
		for (FileObject child : getFileObject().getChildren()) {
			result.add(new SftpFileOrFolder(file + "/" + child.getName().getBaseName(), location)); //$NON-NLS-1$
		}
		return result;
	}
}
