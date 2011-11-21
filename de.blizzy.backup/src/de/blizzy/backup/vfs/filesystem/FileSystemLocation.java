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
package de.blizzy.backup.vfs.filesystem;

import java.io.File;

import org.eclipse.jface.dialogs.IDialogSettings;

import de.blizzy.backup.vfs.IFolder;
import de.blizzy.backup.vfs.ILocation;
import de.blizzy.backup.vfs.ILocationProvider;

public class FileSystemLocation implements ILocation {
	private IFolder root;
	private FileSystemLocationProvider provider;

	FileSystemLocation(File folder, FileSystemLocationProvider provider) {
		root = new FileSystemFileOrFolder(folder);
		this.provider = provider;
	}
	
	@Override
	public String getDisplayName() {
		return root.getAbsolutePath();
	}

	@Override
	public IFolder getRootFolder() {
		return root;
	}
	
	public static ILocation getLocation(IDialogSettings section, FileSystemLocationProvider provider) {
		return new FileSystemLocation(new File(section.get("path")), provider); //$NON-NLS-1$
	}

	public void saveSettings(IDialogSettings section) {
		section.put("path", root.getAbsolutePath()); //$NON-NLS-1$
	}
	
	@Override
	public ILocationProvider getProvider() {
		return provider;
	}
	
	@Override
	public void close() {
	}
	
	@Override
	public void reconnect() {
	}

	@Override
	public int hashCode() {
		return root.hashCode();
	}
	
	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		} else if ((o != null) && o.getClass().equals(getClass())) {
			FileSystemLocation other = (FileSystemLocation) o;
			return other.root.equals(root);
		}
		return false;
	}
}
