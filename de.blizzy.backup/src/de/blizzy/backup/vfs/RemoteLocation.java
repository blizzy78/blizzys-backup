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
package de.blizzy.backup.vfs;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystem;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.UserAuthenticator;
import org.apache.commons.vfs2.VFS;
import org.apache.commons.vfs2.auth.StaticUserAuthenticator;
import org.apache.commons.vfs2.impl.DefaultFileSystemConfigBuilder;
import org.eclipse.jface.dialogs.IDialogSettings;

import de.blizzy.backup.BackupPlugin;

public abstract class RemoteLocation implements ILocation {
	static final int MAX_TRIES = 5;
	
	private String host;
	private int port;
	private String login;
	private String password;
	private String folder;
	private ILocationProvider provider;
	private FileSystem fileSystem;

	protected RemoteLocation(String host, int port, String login, String password, String folder,
			ILocationProvider provider) {

		this.host = host;
		this.port = port;
		this.login = login;
		this.password = password;
		this.folder = folder;
		this.provider = provider;
	}

	protected abstract String getProtocol();

	@Override
	public String getDisplayName() {
		return getProtocol() + "://" + host + folder; //$NON-NLS-1$
	}

	@Override
	public abstract IFolder getRootFolder();

	public String getFolder() {
		return folder;
	}
	
	@Override
	public ILocationProvider getProvider() {
		return provider;
	}

	public void saveSettings(IDialogSettings section) {
		section.put("host", host); //$NON-NLS-1$
		section.put("port", String.valueOf(port)); //$NON-NLS-1$
		section.put("login", StringUtils.isNotBlank(login) ? login : StringUtils.EMPTY); //$NON-NLS-1$
		section.put("password", StringUtils.isNotBlank(password) ? password : StringUtils.EMPTY); //$NON-NLS-1$
		section.put("folder", folder); //$NON-NLS-1$
	}

	String getHost() {
		return host;
	}

	@Override
	public int hashCode() {
		return host.hashCode() ^ port ^
			((login != null) ? login.hashCode() : StringUtils.EMPTY.hashCode()) ^
			((password != null) ? password.hashCode() : StringUtils.EMPTY.hashCode()) ^
			folder.hashCode();
	}
	
	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		} else if ((o != null) && o.getClass().equals(getClass())) {
			RemoteLocation other = (RemoteLocation) o;
			return other.host.equals(host) && (other.port == port) &&
				StringUtils.equals(other.login, login) &&
				StringUtils.equals(other.password, password) &&
				other.folder.equals(folder);
		}
		return false;
	}

	FileObject resolveFile(String file) throws FileSystemException {
		FileSystem fileSystem = getFileSystem();
		return fileSystem.resolveFile(file);
	}
	
	private FileSystem getFileSystem() throws FileSystemException {
		if (fileSystem == null) {
			FileSystemOptions opts = getFileSystemOptions();
			FileObject fileObject = VFS.getManager().resolveFile(
					getProtocol() + "://" + host + ":" + String.valueOf(port), //$NON-NLS-1$ //$NON-NLS-2$
					opts);
			fileSystem = fileObject.getFileSystem();
		}
		return fileSystem;
	}
	
	private FileSystemOptions getFileSystemOptions() throws FileSystemException {
		FileSystemOptions opts = new FileSystemOptions();
		setFileSystemOptions(opts);
		if (StringUtils.isNotBlank(login) && StringUtils.isNotBlank(password)) {
			UserAuthenticator authenticator = new StaticUserAuthenticator(null, login, password);
			DefaultFileSystemConfigBuilder.getInstance().setUserAuthenticator(
					opts, authenticator);
		}
		return opts;
	}

	/**
	 * @throws FileSystemException  
	 */
	protected void setFileSystemOptions(@SuppressWarnings("unused") FileSystemOptions options) throws FileSystemException {
	}
	
	@Override
	public void close() {
		disconnect();
	}
	
	@Override
	public void reconnect() {
		disconnect();
	}

	private void disconnect() {
		if (fileSystem != null) {
			try {
				VFS.getManager().closeFileSystem(fileSystem);
			} catch (FileSystemException e) {
				BackupPlugin.getDefault().logError("error while closing filesystem", e); //$NON-NLS-1$
			} finally {
				fileSystem = null;
			}
		}
	}
	
	boolean canRetryAction(IOException e) {
		return e instanceof FileSystemException;
	}
}
