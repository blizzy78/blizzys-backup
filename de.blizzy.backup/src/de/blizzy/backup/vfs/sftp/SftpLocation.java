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

import org.apache.commons.vfs.FileObject;
import org.apache.commons.vfs.FileSystemException;
import org.apache.commons.vfs.FileSystemManager;
import org.apache.commons.vfs.FileSystemOptions;
import org.apache.commons.vfs.VFS;
import org.apache.commons.vfs.auth.StaticUserAuthenticator;
import org.apache.commons.vfs.impl.DefaultFileSystemConfigBuilder;
import org.apache.commons.vfs.provider.sftp.SftpFileSystemConfigBuilder;
import org.eclipse.jface.dialogs.IDialogSettings;

import de.blizzy.backup.vfs.IFolder;
import de.blizzy.backup.vfs.ILocation;
import de.blizzy.backup.vfs.ILocationProvider;

class SftpLocation implements ILocation {
	private String host;
	private int port;
	private String login;
	private String password;
	private String folder;
	private SftpLocationProvider provider;

	public SftpLocation(String host, int port, String login, String password, String folder,
			SftpLocationProvider provider) {

		this.host = host;
		this.port = port;
		this.login = login;
		this.password = password;
		this.folder = folder;
		this.provider = provider;
	}

	public String getDisplayName() {
		return "sftp://" + host + folder; //$NON-NLS-1$
	}

	public IFolder getRootFolder() {
		SftpFileOrFolder root = new SftpFileOrFolder(folder, this);
		root.setIsFolder();
		return root;
	}

	public ILocationProvider getProvider() {
		return provider;
	}

	static ILocation getL(IDialogSettings section, SftpLocationProvider provider) {
		return new SftpLocation(section.get("host"), Integer.parseInt(section.get("port")), //$NON-NLS-1$ //$NON-NLS-2$
				section.get("login"), section.get("password"), section.get("folder"), provider); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	public void saveSettings(IDialogSettings section) {
		section.put("host", host); //$NON-NLS-1$
		section.put("port", String.valueOf(port)); //$NON-NLS-1$
		section.put("login", login); //$NON-NLS-1$
		section.put("password", password); //$NON-NLS-1$
		section.put("folder", folder); //$NON-NLS-1$
	}

	String getHost() {
		return host;
	}
	
	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if ((o != null) && o.getClass().equals(getClass())) {
			SftpLocation other = (SftpLocation) o;
			return other.host.equals(host) && (other.port == port) &&
				other.login.equals(login) && other.password.equals(password) &&
				other.folder.equals(folder);
		}
		return false;
	}
	
	FileObject resolveFile(String file) throws FileSystemException {
		FileSystemOptions opts = new FileSystemOptions();
		DefaultFileSystemConfigBuilder.getInstance().setUserAuthenticator(opts, new StaticUserAuthenticator(null, login, password));
		SftpFileSystemConfigBuilder.getInstance().setStrictHostKeyChecking(opts, "no"); //$NON-NLS-1$
		FileSystemManager manager = VFS.getManager();
		return manager.resolveFile("sftp://" + host + ":" + String.valueOf(port) + file, opts); //$NON-NLS-1$ //$NON-NLS-2$
	}
}
