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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.provider.ftp.FtpFileSystemConfigBuilder;
import org.eclipse.jface.dialogs.IDialogSettings;

import de.blizzy.backup.vfs.IFolder;
import de.blizzy.backup.vfs.RemoteLocation;

class FtpLocation extends RemoteLocation {
	public FtpLocation(String host, int port, String login, String password, String folder,
			FtpLocationProvider provider) {

		super(host, port, login, password, folder, provider);
	}

	@Override
	protected String getProtocol() {
		return "ftp"; //$NON-NLS-1$
	}

	@Override
	public IFolder getRootFolder() {
		FtpFileOrFolder root = new FtpFileOrFolder(getFolder(), this);
		root.setIsFolder();
		return root;
	}

	@Override
	protected void setFileSystemOptions(FileSystemOptions options) throws FileSystemException {
		FtpFileSystemConfigBuilder.getInstance().setPassiveMode(options, true);
	}
	
	static FtpLocation getLocation(IDialogSettings section, FtpLocationProvider provider) {
		String login = StringUtils.defaultIfBlank(section.get("login"), null); //$NON-NLS-1$
		String password = StringUtils.defaultIfBlank(section.get("password"), null); //$NON-NLS-1$
		return new FtpLocation(section.get("host"), Integer.parseInt(section.get("port")), //$NON-NLS-1$ //$NON-NLS-2$
				login, password, section.get("folder"), provider); //$NON-NLS-1$
	}
}
