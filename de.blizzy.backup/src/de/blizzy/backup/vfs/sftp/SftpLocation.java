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
package de.blizzy.backup.vfs.sftp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.schmizz.sshj.Config;
import net.schmizz.sshj.DefaultConfig;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.Factory.Named;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.sftp.SFTPException;
import net.schmizz.sshj.transport.compression.Compression;
import net.schmizz.sshj.transport.compression.NoneCompression;
import net.schmizz.sshj.transport.compression.ZlibCompression;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jface.dialogs.IDialogSettings;

import de.blizzy.backup.BackupPlugin;
import de.blizzy.backup.vfs.IFolder;
import de.blizzy.backup.vfs.ILocation;
import de.blizzy.backup.vfs.ILocationProvider;

class SftpLocation implements ILocation {
	static final int MAX_TRIES = 5;
	
	private String host;
	private int port;
	private String login;
	private String password;
	private String folder;
	private SftpLocationProvider provider;
	private SSHClient sshClient;
	private SFTPClient sftpClient;

	public SftpLocation(String host, int port, String login, String password, String folder,
			SftpLocationProvider provider) {
		
		this.host = host;
		this.port = port;
		this.login = login;
		this.password = password;
		this.folder = folder;
		this.provider = provider;
	}

	@Override
	public String getDisplayName() {
		return "sftp://" + host + folder; //$NON-NLS-1$
	}

	@Override
	public IFolder getRootFolder() {
		return new SftpFileOrFolder(folder, this);
	}

	@Override
	public ILocationProvider getProvider() {
		return provider;
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
		if (sftpClient != null) {
			try {
				sftpClient.close();
			} catch (IOException e) {
				BackupPlugin.getDefault().logError("error while closing SFTP client", e); //$NON-NLS-1$
			} finally {
				sftpClient = null;
			}
		}

		if (sshClient != null) {
			try {
				sshClient.close();
			} catch (IOException e) {
				BackupPlugin.getDefault().logError("error while closing SSH client", e); //$NON-NLS-1$
			} finally {
				sshClient = null;
			}
		}
	}

	String getHost() {
		return host;
	}

	SFTPClient getSftpClient() throws IOException {
		if (sftpClient == null) {
			sftpClient = getSshClient().newSFTPClient();
		}
		return sftpClient;
	}
	
	private SSHClient getSshClient() throws IOException {
		if (sshClient == null) {
			Config config = new DefaultConfig();
			List<Named<Compression>> compressions = new ArrayList<>();
			compressions.add(new ZlibCompression.Factory());
			compressions.add(new NoneCompression.Factory());
			config.setCompressionFactories(compressions);
			sshClient = new SSHClient(config);
			sshClient.addHostKeyVerifier(new PromiscuousVerifier());
			sshClient.connect(host, port);
			sshClient.authPassword(login, password);
		}
		return sshClient;
	}
	
	boolean canRetryAction(IOException e) {
		if (e instanceof SFTPException) {
			SFTPException ex = (SFTPException) e;
			switch (ex.getDisconnectReason()) {
				case UNKNOWN:
				case PROTOCOL_ERROR:
					return true;
			}
		}
		return false;
	}

	public void saveSettings(IDialogSettings section) {
		section.put("host", host); //$NON-NLS-1$
		section.put("port", String.valueOf(port)); //$NON-NLS-1$
		section.put("login", StringUtils.isNotBlank(login) ? login : StringUtils.EMPTY); //$NON-NLS-1$
		section.put("password", StringUtils.isNotBlank(password) ? password : StringUtils.EMPTY); //$NON-NLS-1$
		section.put("folder", folder); //$NON-NLS-1$
	}

	static SftpLocation getLocation(IDialogSettings section, SftpLocationProvider provider) {
		return new SftpLocation(section.get("host"), Integer.parseInt(section.get("port")), //$NON-NLS-1$ //$NON-NLS-2$
				section.get("login"), section.get("password"), section.get("folder"), provider); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	@Override
	public int hashCode() {
		return host.hashCode() ^ port ^
			login.hashCode() ^ password.hashCode() ^
			folder.hashCode();
	}
	
	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		} else if ((o != null) && o.getClass().equals(getClass())) {
			SftpLocation other = (SftpLocation) o;
			return other.host.equals(host) && (other.port == port) &&
				other.login.equals(login) && other.password.equals(password) &&
				other.folder.equals(folder);
		}
		return false;
	}
}
