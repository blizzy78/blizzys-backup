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

import java.util.EventObject;

public class BackupErrorEvent extends EventObject {
	public static enum Severity {
		WARNING, ERROR;
	}
	
	private Throwable error;
	private Severity severity;

	BackupErrorEvent(Object source, Throwable error, Severity severity) {
		super(source);

		this.error = error;
		this.severity = severity;
	}
	
	public Throwable getError() {
		return error;
	}
	
	public Severity getSeverity() {
		return severity;
	}
}
