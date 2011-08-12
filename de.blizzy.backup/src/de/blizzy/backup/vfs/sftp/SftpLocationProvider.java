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

import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;

import de.blizzy.backup.BackupPlugin;
import de.blizzy.backup.Messages;
import de.blizzy.backup.vfs.ILocation;
import de.blizzy.backup.vfs.ILocationProvider;
import de.blizzy.backup.vfs.RemoteLocationDialog;

public class SftpLocationProvider implements ILocationProvider {
	public String getId() {
		return BackupPlugin.ID + ".locationProvider.sftp"; //$NON-NLS-1$
	}
	
	public ILocation promptLocation(Shell parentShell) {
		RemoteLocationDialog dlg = new RemoteLocationDialog(parentShell) {
			@Override
			protected ILocation createLocation() {
				return new SftpLocation(hostText.getText(), Integer.parseInt(portText.getText()),
						loginText.getText(), passwordText.getText(), folderText.getText(), SftpLocationProvider.this);
			}
		};
		dlg.setTitle(Messages.Title_SFTP);
		dlg.setPort(22);
		return (dlg.open() == Window.OK) ? dlg.getLocation() : null;
	}
	
	public ILocation getLocation(IDialogSettings section) {
		return SftpLocation.getLocation(section, this);
	}

	public void saveSettings(ILocation location, IDialogSettings section) {
		((SftpLocation) location).saveSettings(section);
	}
}
