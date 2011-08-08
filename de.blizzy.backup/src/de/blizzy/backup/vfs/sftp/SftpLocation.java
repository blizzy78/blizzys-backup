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

import org.apache.commons.vfs.FileSystemException;
import org.apache.commons.vfs.FileSystemOptions;
import org.apache.commons.vfs.provider.sftp.SftpFileSystemConfigBuilder;
import org.eclipse.jface.dialogs.IDialogSettings;

import de.blizzy.backup.vfs.IFolder;
import de.blizzy.backup.vfs.RemoteLocation;

class SftpLocation extends RemoteLocation {
	public SftpLocation(String host, int port, String login, String password, String folder,
			SftpLocationProvider provider) {

		super(host, port, login, password, folder, provider);
	}

	@Override
	protected String getProtocol() {
		return "sftp"; //$NON-NLS-1$
	}

	@Override
	public IFolder getRootFolder() {
		SftpFileOrFolder root = new SftpFileOrFolder(getFolder(), this);
		root.setIsFolder();
		return root;
	}

	@Override
	protected void setFileSystemOptions(FileSystemOptions options) throws FileSystemException {
		SftpFileSystemConfigBuilder builder = SftpFileSystemConfigBuilder.getInstance();
		builder.setStrictHostKeyChecking(options, "no"); //$NON-NLS-1$
		builder.setCompression(options, "zlib,none"); //$NON-NLS-1$
	}
	
	static SftpLocation getLocation(IDialogSettings section, SftpLocationProvider provider) {
		return new SftpLocation(section.get("host"), Integer.parseInt(section.get("port")), //$NON-NLS-1$ //$NON-NLS-2$
				section.get("login"), section.get("password"), section.get("folder"), provider); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}
}
