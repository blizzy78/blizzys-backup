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
package de.blizzy.backup.database;

public enum EntryType {
	FOLDER(1),
	FILE(2),
	FAILED_FILE(3);
	
	private int value;

	private EntryType(int value) {
		this.value = value;
	}
	
	public int getValue() {
		return value;
	}
	
	public static EntryType fromValue(int value) {
		if (value == FOLDER.value) {
			return FOLDER;
		}
		if (value == FILE.value) {
			return FILE;
		}
		if (value == FAILED_FILE.value) {
			return FAILED_FILE;
		}
		throw new IllegalArgumentException("unknown value: " + value); //$NON-NLS-1$
	}
}
