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
package de.blizzy.backup;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Tray;
import org.eclipse.swt.widgets.TrayItem;

class TrayIcon {
	private TrayItem trayItem;
	private Image image;

	TrayIcon(Display display) {
		Tray systemTray = display.getSystemTray();
		if (systemTray != null) {
			trayItem = new TrayItem(systemTray, SWT.NONE);
			image = BackupPlugin.getDefault().getImageDescriptor("etc/logo/logo_16.png").createImage(display); //$NON-NLS-1$
			trayItem.setImage(image);
			trayItem.setToolTipText(Messages.Title_BlizzysBackup);
			trayItem.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					BackupApplication.showShell();
				}
			});
		}
	}
	
	void dispose() {
		if (image != null) {
			image.dispose();
		}
		if (trayItem != null) {
			trayItem.dispose();
		}
	}
}
