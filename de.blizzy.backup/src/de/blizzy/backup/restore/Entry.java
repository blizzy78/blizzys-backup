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

import java.util.Date;

import de.blizzy.backup.Compression;
import de.blizzy.backup.database.EntryType;

class Entry {
	int id;
	int parentId;
	String name;
	EntryType type;
	Date creationTime;
	Date modificationTime;
	boolean hidden;
	long length;
	String backupPath;
	Compression compression;

	Entry(int id, int parentId, String name, EntryType type, Date creationTime, Date modificationTime, boolean hidden,
			long length, String backupPath, Compression compression) {

		this.id = id;
		this.parentId = parentId;
		this.name = name;
		this.type = type;
		this.creationTime = creationTime;
		this.modificationTime = modificationTime;
		this.hidden = hidden;
		this.length = length;
		this.backupPath = backupPath;
		this.compression = compression;
	}
}
