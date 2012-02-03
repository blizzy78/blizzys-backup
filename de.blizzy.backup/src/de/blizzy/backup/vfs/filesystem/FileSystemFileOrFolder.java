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
package de.blizzy.backup.vfs.filesystem;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.IOUtils;

import de.blizzy.backup.FileAttributes;
import de.blizzy.backup.vfs.IFile;
import de.blizzy.backup.vfs.IFileSystemEntry;
import de.blizzy.backup.vfs.IFolder;
import de.blizzy.backup.vfs.IOutputStreamProvider;

public class FileSystemFileOrFolder implements IFile, IFolder {
	private File file;
	private FileAttributes attrs;

	public FileSystemFileOrFolder(File file) {
		this.file = file;
	}
	
	@Override
	public String getName() {
		return file.getName();
	}

	@Override
	public String getAbsolutePath() {
		return file.getAbsolutePath();
	}
	
	private FileAttributes getAttributes() throws IOException {
		if (attrs == null) {
			attrs = FileAttributes.get(file.toPath());
		}
		return attrs;
	}

	@Override
	public boolean isHidden() throws IOException {
		return getAttributes().isHidden();
	}

	@Override
	public FileTime getCreationTime() throws IOException {
		return getAttributes().getCreationTime();
	}

	@Override
	public FileTime getLastModificationTime() throws IOException {
		return getAttributes().getModificationTime();
	}

	@Override
	public Set<IFileSystemEntry> list() {
		Set<IFileSystemEntry> result = new HashSet<>();
		File[] files = file.listFiles();
		if (files != null) {
			for (File f : files) {
				result.add(new FileSystemFileOrFolder(f));
			}
		}
		return result;
	}

	@Override
	public void copy(IOutputStreamProvider outputStreamProvider) throws IOException {
		OutputStream out = null;
		try {
			out = outputStreamProvider.getOutputStream();
			Files.copy(file.toPath(), out);
		} finally {
			IOUtils.closeQuietly(out);
		}
	}
	
	@Override
	public long getLength() {
		return file.length();
	}
	
	@Override
	public IFolder getParentFolder() {
		File parent = file.getParentFile();
		return (parent != null) ? new FileSystemFileOrFolder(parent) : null;
	}
	
	@Override
	public boolean isFolder() {
		return file.isDirectory();
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
			FileSystemFileOrFolder other = (FileSystemFileOrFolder) o;
			return other.file.equals(file);
		}
		return false;
	}
}
