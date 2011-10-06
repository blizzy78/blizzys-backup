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
package de.blizzy.backup.backup;

import de.blizzy.backup.Messages;

public class BackupStatus {
	public static final BackupStatus INITIALIZE = new BackupStatus(true, null, -1, -1, false, false);
	public static final BackupStatus FINALIZE = new BackupStatus(false, null, -1, -1, true, false);
	public static final BackupStatus CLEANUP = new BackupStatus(false, null, -1, -1, false, true);

	private boolean initialize;
	private int numEntries;
	private int totalEntries;
	private String currentFile;
	private boolean finalize;
	private boolean cleanup;

	public BackupStatus(String currentFile, int numEntries, int totalEntries) {
		this(false, currentFile, numEntries, totalEntries, false, false);
	}
	
	private BackupStatus(boolean initialize, String currentFile, int numEntries, int totalEntries, boolean finalize, boolean cleanup) {
		this.initialize = initialize;
		this.currentFile = currentFile;
		this.numEntries = numEntries;
		this.totalEntries = totalEntries;
		this.finalize = finalize;
		this.cleanup = cleanup;
	}

	public int getNumEntries() {
		return numEntries;
	}
	
	public int getTotalEntries() {
		return totalEntries;
	}
	
	public String getText() {
		if (initialize) {
			return Messages.Initializing;
		}
		if (finalize) {
			return Messages.Finalizing;
		}
		if (cleanup) {
			return Messages.CleaningUp;
		}
		return currentFile;
	}
}
