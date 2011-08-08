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
package de.blizzy.backup.vfs.ftp;

import de.blizzy.backup.vfs.IFolder;
import de.blizzy.backup.vfs.RemoteFileOrFolder;

class FtpFileOrFolder extends RemoteFileOrFolder<FtpLocation> {
	FtpFileOrFolder(String file, FtpLocation location) {
		super(file, location);
	}
	
	@Override
	protected IFolder getFolder(String folder) {
		FtpFileOrFolder result = new FtpFileOrFolder(folder, getLocation());
		result.setIsFolder();
		return result;
	}
	
	@Override
	protected IFolder getFileOrFolder(String file) {
		return new FtpFileOrFolder(file, getLocation());
	}
}
