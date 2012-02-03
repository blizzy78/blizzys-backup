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
package de.blizzy.backup.restore;

import java.io.File;

import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerSorter;

import de.blizzy.backup.database.EntryType;

class EntrySorter extends ViewerSorter {
	private boolean sortFullPath;

	@Override
	public int compare(Viewer viewer, Object element1, Object element2) {
		Entry e1 = (Entry) element1;
		Entry e2 = (Entry) element2;
		if ((e1.type == EntryType.FOLDER) && (e2.type != EntryType.FOLDER)) {
			return -1;
		}
		if ((e1.type != EntryType.FOLDER) && (e2.type == EntryType.FOLDER)) {
			return 1;
		}
		String name1 = (sortFullPath && (e1.fullPath != null)) ? e1.fullPath + File.separator + e1.name : e1.name;
		String name2 = (sortFullPath && (e2.fullPath != null)) ? e2.fullPath + File.separator + e2.name : e2.name;
		return name1.compareToIgnoreCase(name2);
	}
	
	void setSortFullPath(boolean sortFullPath) {
		this.sortFullPath = sortFullPath;
	}
}
