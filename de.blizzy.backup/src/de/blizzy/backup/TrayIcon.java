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

import java.text.DateFormat;
import java.util.Date;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Tray;
import org.eclipse.swt.widgets.TrayItem;

import de.blizzy.backup.backup.BackupEndedEvent;
import de.blizzy.backup.backup.BackupRun;
import de.blizzy.backup.backup.BackupStatusEvent;
import de.blizzy.backup.backup.IBackupRunListener;
import de.blizzy.backup.settings.ISettingsListener;

class TrayIcon implements IBackupRunListener, ISettingsListener {
	private TrayItem trayItem;
	private Image image;
	private Image progressImage;
	private BackupRun backupRun;

	TrayIcon(Display display) {
		Tray systemTray = display.getSystemTray();
		if (systemTray != null) {
			trayItem = new TrayItem(systemTray, SWT.NONE);
			image = BackupPlugin.getDefault().getImageDescriptor("etc/logo/logo_16.png").createImage(display); //$NON-NLS-1$
			progressImage = BackupPlugin.getDefault().getImageDescriptor("etc/logo/logo_progress_16.png").createImage(display); //$NON-NLS-1$
			updateStatus();
			trayItem.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					BackupApplication.showShell();
				}
			});
			
			BackupApplication.getSettingsManager().addListener(this);
		}
	}

	private void updateStatus() {
		if (trayItem != null) {
			trayItem.setImage((backupRun != null) ? progressImage : image);
			Date nextRunDate = new Date(BackupApplication.getNextScheduledBackupRunTime());
			trayItem.setToolTipText(Messages.Title_BlizzysBackup + " - " + //$NON-NLS-1$
					((backupRun != null) ? Messages.Running :
					Messages.Label_NextRun + ": " + //$NON-NLS-1$
						DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(nextRunDate)));
		}
	}
	
	void dispose() {
		if (image != null) {
			image.dispose();
		}
		if (progressImage != null) {
			progressImage.dispose();
		}
		if (trayItem != null) {
			trayItem.dispose();
		}
		if (backupRun != null) {
			backupRun.removeListener(this);
		}
		BackupApplication.getSettingsManager().removeListener(this);
	}

	@Override
	public void backupStatusChanged(BackupStatusEvent e) {
	}

	@Override
	public void backupEnded(BackupEndedEvent e) {
		backupRun.removeListener(this);
		backupRun = null;
		if (trayItem != null) {
			Utils.runAsync(trayItem.getDisplay(), new Runnable() {
				@Override
				public void run() {
					if (!trayItem.isDisposed()) {
						updateStatus();
					}
				}
			});
		}
	}
	
	void setBackupRun(BackupRun backupRun) {
		this.backupRun = backupRun;
		if (backupRun != null) {
			backupRun.addListener(this);
		}
		if (trayItem != null) {
			Utils.runAsync(trayItem.getDisplay(), new Runnable() {
				@Override
				public void run() {
					if (!trayItem.isDisposed()) {
						updateStatus();
					}
				}
			});
		}
	}
	
	@Override
	public void settingsChanged() {
		updateStatus();
	}
}
