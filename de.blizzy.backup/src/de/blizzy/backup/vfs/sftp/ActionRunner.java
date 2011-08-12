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
package de.blizzy.backup.vfs.sftp;

import java.io.IOException;

class ActionRunner<T> {
	private IAction<T> action;
	private int maxTries;
	private SftpLocation location;

	ActionRunner(IAction<T> action, int maxTries, SftpLocation location) {
		this.action = action;
		this.maxTries = maxTries;
		this.location = location;
	}
	
	T run() throws IOException {
		T result;
		IOException ex = null;
		for (int tries = 0; tries < maxTries; tries++) {
			try {
				result = action.run();
				return result;
			} catch (IOException e) {
				ex = e;
				location.reconnect();
				if (action.canRetry(e)) {
					continue;
				}
			}
		}
		throw ex;
	}
}
