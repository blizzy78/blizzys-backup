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
	public static final BackupStatus INITIALIZE = new BackupStatus(true, null, false);
	public static final BackupStatus CLEANUP = new BackupStatus(false, null, true);

	private boolean initialize;
	private String currentFile;
	private boolean cleanup;

	public BackupStatus(String currentFile) {
		this(false, currentFile, false);
	}
	
	private BackupStatus(boolean initialize, String currentFile, boolean cleanup) {
		this.initialize = initialize;
		this.currentFile = currentFile;
		this.cleanup = cleanup;
	}

	public String getText() {
		if (initialize) {
			return Messages.Initializing;
		}
		if (cleanup) {
			return Messages.CleaningUp;
		}
		return currentFile;
	}
}
