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
package de.blizzy.backup;

import java.util.Date;

import de.blizzy.backup.backup.BackupErrorEvent;
import de.blizzy.backup.backup.BackupErrorEvent.Severity;
import de.blizzy.backup.vfs.IFileSystemEntry;

class BackupError {
	private String fileOrFolderPath;
	private Date date;
	private Throwable error;
	private Severity severity;

	BackupError(BackupErrorEvent event) {
		IFileSystemEntry fileOrFolder = event.getFileOrFolder();
		this.fileOrFolderPath = (fileOrFolder != null) ? fileOrFolder.getAbsolutePath() : null;
		this.date = event.getDate();
		this.error = event.getError();
		this.severity = event.getSeverity();
	}

	String getFileOrFolderPath() {
		return fileOrFolderPath;
	}
	
	Date getDate() {
		return date;
	}
	
	Throwable getError() {
		return error;
	}
	
	Severity getSeverity() {
		return severity;
	}
}
