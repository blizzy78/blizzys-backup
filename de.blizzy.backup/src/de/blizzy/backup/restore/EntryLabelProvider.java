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
package de.blizzy.backup.restore;

import java.io.File;
import java.text.DateFormat;

import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.Image;

import de.blizzy.backup.BackupPlugin;
import de.blizzy.backup.FileLengthFormat;
import de.blizzy.backup.database.EntryType;

class EntryLabelProvider implements ITableLabelProvider {
	private static final DateFormat DATE_FORMAT = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
	private static final FileLengthFormat FILE_LENGTH_FORMAT = new FileLengthFormat();

	private Image rootFolderImage;
	private Image folderImage;
	private Image fileImage;
	private boolean showFullPath;

	EntryLabelProvider(Device device) {
		rootFolderImage = BackupPlugin.getDefault().getImageDescriptor("etc/icons/rootFolder.gif").createImage(device); //$NON-NLS-1$
		folderImage = BackupPlugin.getDefault().getImageDescriptor("etc/icons/folder.gif").createImage(device); //$NON-NLS-1$
		fileImage = BackupPlugin.getDefault().getImageDescriptor("etc/icons/file.gif").createImage(device); //$NON-NLS-1$
	}
	
	@Override
	public void dispose() {
		rootFolderImage.dispose();
		folderImage.dispose();
		fileImage.dispose();
	}

	@Override
	public String getColumnText(Object element, int columnIndex) {
		Entry entry = (Entry) element;
		switch (columnIndex) {
			case 0:
				return (showFullPath && (entry.fullPath != null)) ?
						entry.fullPath + File.separator + entry.name :
						entry.name;
			case 1:
				if (entry.length >= 0) {
					return FILE_LENGTH_FORMAT.format(entry.length);
				}
				break;
			case 2:
				if (entry.modificationTime != null) {
					return DATE_FORMAT.format(entry.modificationTime);
				}
				break;
		}
		return null;
	}

	@Override
	public Image getColumnImage(Object element, int columnIndex) {
		if (columnIndex == 0) {
			Entry entry = (Entry) element;
			if (entry.parentId <= 0) {
				return rootFolderImage;
			}
			if (entry.type == EntryType.FOLDER) {
				return folderImage;
			}
			return fileImage;
		}
		return null;
	}

	@Override
	public boolean isLabelProperty(Object element, String property) {
		return true;
	}

	@Override
	public void addListener(ILabelProviderListener listener) {
	}

	@Override
	public void removeListener(ILabelProviderListener listener) {
	}

	void setShowFullPath(boolean showFullPath) {
		this.showFullPath = showFullPath;
	}
}
