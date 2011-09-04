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
package de.blizzy.backup.vfs.filesystem;

import java.io.File;

import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Shell;

import de.blizzy.backup.BackupPlugin;
import de.blizzy.backup.Messages;
import de.blizzy.backup.Utils;
import de.blizzy.backup.vfs.ILocation;
import de.blizzy.backup.vfs.ILocationProvider;

public class FileSystemLocationProvider implements ILocationProvider {
	public String getId() {
		return BackupPlugin.ID + ".locationProvider.filesystem"; //$NON-NLS-1$
	}
	
	public ILocation promptLocation(Shell parentShell) {
		DirectoryDialog dlg = new DirectoryDialog(parentShell, SWT.OPEN);
		dlg.setText(Messages.Title_SelectFolder);
		String folder = dlg.open();
		return (folder != null) ? new FileSystemLocation(Utils.toCanonicalFile(new File(folder)), this) : null;
	}

	public ILocation getLocation(IDialogSettings section) {
		return FileSystemLocation.getLocation(section, this);
	}

	public void saveSettings(ILocation location, IDialogSettings section) {
		((FileSystemLocation) location).saveSettings(section);
	}
	
	public static FileSystemLocation location(File file) {
		return new FileSystemLocation(file, new FileSystemLocationProvider());
	}
}
