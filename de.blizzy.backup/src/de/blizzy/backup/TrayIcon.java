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
package de.blizzy.backup;

import java.text.DateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Tray;
import org.eclipse.swt.widgets.TrayItem;

import de.blizzy.backup.backup.BackupEndedEvent;
import de.blizzy.backup.backup.BackupErrorEvent;
import de.blizzy.backup.backup.BackupRun;
import de.blizzy.backup.backup.BackupStatus;
import de.blizzy.backup.backup.BackupStatusEvent;
import de.blizzy.backup.backup.IBackupRunListener;
import de.blizzy.backup.settings.ISettingsListener;

class TrayIcon implements IBackupRunListener, ISettingsListener {
	private TrayItem trayItem;
	private Image idleImage;
	private Image progressImage;
	private Image warningImage;
	private Image errorImage;
	private BackupRun backupRun;
	private Image currentImage;
	private Timer blinkTimer;

	TrayIcon(Display display) {
		Tray systemTray = display.getSystemTray();
		if (systemTray != null) {
			trayItem = new TrayItem(systemTray, SWT.NONE);
			idleImage = BackupPlugin.getDefault().getImageDescriptor("etc/logo/logo_16.png").createImage(display); //$NON-NLS-1$
			progressImage = BackupPlugin.getDefault().getImageDescriptor("etc/logo/logo_progress_16.png").createImage(display); //$NON-NLS-1$
			warningImage = BackupPlugin.getDefault().getImageDescriptor("etc/logo/logo_warning_16.png").createImage(display); //$NON-NLS-1$
			errorImage = BackupPlugin.getDefault().getImageDescriptor("etc/logo/logo_error_16.png").createImage(display); //$NON-NLS-1$
			updateStatus(null);
			trayItem.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					BackupApplication.showShell();
				}
			});
			
			BackupApplication.getSettingsManager().addListener(this);
		}
	}

	private void updateStatus(BackupStatus status) {
		if (trayItem != null) {
			if (blinkTimer == null) {
				Image newImage = null;
				if (status == null) {
					newImage = idleImage;
				} else if (status.isInitialize()) {
					newImage = progressImage;
				}
				if ((newImage != null) && (newImage != currentImage)) {
					currentImage = newImage;
					trayItem.setImage(currentImage);
				}
			}

			Date nextRunDate = new Date(BackupApplication.getNextScheduledBackupRunTime());
			int numEntries = -1;
			int totalEntries = -1;
			if (status != null) {
				numEntries = status.getNumEntries();
				totalEntries = status.getTotalEntries();
			}
			trayItem.setToolTipText(Messages.Title_BlizzysBackup + " - " + //$NON-NLS-1$
					((backupRun != null) ?
							(Messages.Running +
								(((numEntries >= 0) && (totalEntries >= 0)) ?
										(" (" + (int) Math.round(numEntries * 100d / totalEntries) + "%)") : "")) : //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
							Messages.Label_NextRun + ": " + //$NON-NLS-1$
								DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(nextRunDate)));
		}
	}
	
	void dispose() {
		if (blinkTimer != null) {
			blinkTimer.cancel();
		}
		if (idleImage != null) {
			idleImage.dispose();
		}
		if (progressImage != null) {
			progressImage.dispose();
		}
		if (warningImage != null) {
			warningImage.dispose();
		}
		if (errorImage != null) {
			errorImage.dispose();
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
	public void backupStatusChanged(final BackupStatusEvent e) {
		if (trayItem != null) {
			Utils.runAsync(trayItem.getDisplay(), new Runnable() {
				@Override
				public void run() {
					if (!trayItem.isDisposed()) {
						updateStatus(e.getStatus());
					}
				}
			});
		}
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
						updateStatus(null);
					}
				}
			});
		}
	}
	
	@Override
	public void backupErrorOccurred(BackupErrorEvent e) {
		switch (e.getSeverity()) {
			case WARNING:
				currentImage = warningImage;
				break;
			case ERROR:
				currentImage = errorImage;
				break;
		}
		setBlink(true);
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
						updateStatus(null);
					}
				}
			});
		}
	}
	
	@Override
	public void settingsChanged() {
		updateStatus(null);
	}
	
	void resetImage() {
		if (trayItem != null) {
			final Display display = trayItem.getDisplay();
			Utils.runAsync(display, new Runnable() {
				@Override
				public void run() {
					if (!display.isDisposed() && !trayItem.isDisposed()) {
						currentImage = (backupRun != null) ? progressImage : idleImage;
						setBlink(false);
					}
				}
			});
		}
	}
	
	private void setBlink(final boolean blink) {
		if (trayItem != null) {
			final Display display = trayItem.getDisplay();
			display.syncExec(new Runnable() {
				@Override
				public void run() {
					if (!display.isDisposed() && !trayItem.isDisposed()) {
						if (blink && (blinkTimer == null)) {
							blinkTimer = new Timer();
							final boolean[] visible = { true };
							final Runnable imageRunnable = new Runnable() {
								@Override
								public void run() {
									trayItem.setImage(visible[0] ? null : currentImage);
									visible[0] = !visible[0];
								}
							};
							TimerTask task = new TimerTask() {
								@Override
								public void run() {
									Utils.runAsync(display, imageRunnable);
								}
							};
							blinkTimer.schedule(task, 800, 800);
						} else if (!blink && (blinkTimer != null)) {
							blinkTimer.cancel();
							blinkTimer = null;
							trayItem.setImage(currentImage);
						}
					}
				}
			});
		}
	}
}
