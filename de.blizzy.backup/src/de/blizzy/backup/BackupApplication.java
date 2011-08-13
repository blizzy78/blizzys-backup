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

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.plugin.AbstractUIPlugin;

import de.blizzy.backup.backup.BackupEndedEvent;
import de.blizzy.backup.backup.BackupRun;
import de.blizzy.backup.backup.BackupRunAdapter;
import de.blizzy.backup.settings.ISettingsListener;
import de.blizzy.backup.settings.Settings;
import de.blizzy.backup.settings.SettingsManager;

public class BackupApplication implements IApplication {
	private static boolean running = true;
	private static Display display;
	private static BackupShell backupShell;
	private static Timer timer;
	private static TimerTask backupTimerTask;
	private static BackupRun backupRun;
	private static long nextBackupRunTime;
	private static Image[] windowImages;
	private static SettingsManager settingsManager;
	private static TrayIcon trayIcon;

	public Object start(IApplicationContext context) throws IOException {
		display = Display.getDefault();

		boolean restartNecessary = false;

		File runLockFile = new File(BackupPlugin.getDefault().getStateLocation().toFile(), "runLock"); //$NON-NLS-1$
		RunLock runLock = new RunLock(runLockFile);
		if (runLock.tryLock()) {
			setupDefaultPreferences();
			
			Image image16 = AbstractUIPlugin.imageDescriptorFromPlugin(
					BackupPlugin.ID, "etc/logo/logo_16.png").createImage(display); //$NON-NLS-1$
			Image image32 = AbstractUIPlugin.imageDescriptorFromPlugin(
					BackupPlugin.ID, "etc/logo/logo_32.png").createImage(display); //$NON-NLS-1$
			Image image48 = AbstractUIPlugin.imageDescriptorFromPlugin(
					BackupPlugin.ID, "etc/logo/logo_48.png").createImage(display); //$NON-NLS-1$
			windowImages = new Image[] { image16, image32, image48 };
	
			settingsManager = new SettingsManager();
			settingsManager.addListener(new ISettingsListener() {
				public void settingsChanged() {
					if (backupRun == null) {
						scheduleBackupRun(false);
					}
				}
			});
			
			timer = new Timer();
	
			scheduleBackupRun(false);
	
			trayIcon = new TrayIcon(display);
	
			context.applicationRunning();
	
			if (!BackupPlugin.getDefault().isHidden()) {
				showShell();
			}
			
			try {
				Shell shell = (backupShell != null) ? backupShell.getShell() : null;
				if (new Updater(false, false).update(shell)) {
					running = false;
					restartNecessary = true;
				}
			} catch (Throwable e) {
				BackupPlugin.getDefault().logError("error while updating application", e); //$NON-NLS-1$
			}
			
			while (running && !display.isDisposed()) {
				try {
					if (!display.readAndDispatch()) {
						display.sleep();
					}
				} catch (RuntimeException e) {
					BackupPlugin.getDefault().logError("error in event loop", e); //$NON-NLS-1$
				}
			}
	
			trayIcon.dispose();
			
			timer.cancel();
			timer = null;
	
			image16.dispose();
			image32.dispose();
			image48.dispose();
	
			if (backupRun != null) {
				backupRun.stopBackupAndWait();
			}
		} else {
			new MessageDialog(null, Messages.Title_ProgramRunning, null, Messages.ProgramRunning,
					MessageDialog.ERROR, new String[] { IDialogConstants.CLOSE_LABEL }, 0).open();
		}
		
		return restartNecessary ? EXIT_RESTART : EXIT_OK;
	}

	private void setupDefaultPreferences() {
		IDialogSettings backupSection = Utils.getSection("backup"); //$NON-NLS-1$
		if (backupSection.get("lastRun") == null) { //$NON-NLS-1$
			// fake last run as happened 50 min ago
			backupSection.put("lastRun", System.currentTimeMillis() - 50L * 60L * 1000L); //$NON-NLS-1$
		}
	}

	static void scheduleBackupRun(boolean forceRunNow) {
		if (timer != null) {
			if (backupTimerTask != null) {
				backupTimerTask.cancel();
				backupTimerTask = null;
			}
			
			backupTimerTask = new TimerTask() {
				@Override
				public void run() {
					runBackup();
				}
			};

			if (forceRunNow) {
				timer.schedule(backupTimerTask, 0);
			} else {
				IDialogSettings backupSection = Utils.getSection("backup"); //$NON-NLS-1$
				long lastRun = backupSection.getLong("lastRun"); //$NON-NLS-1$
				Settings settings = settingsManager.getSettings();
				if (settings.isRunHourly()) {
					nextBackupRunTime = lastRun;
				} else {
					Calendar c = Calendar.getInstance();
					c.setTimeInMillis(lastRun);
					c.set(Calendar.HOUR_OF_DAY, settings.getDailyHours());
					c.set(Calendar.MINUTE, settings.getDailyMinutes());
					nextBackupRunTime = c.getTimeInMillis();
				}
				long now = System.currentTimeMillis();
				for (;;) {
					nextBackupRunTime = clearSeconds(nextBackupRunTime);
					if (nextBackupRunTime > now) {
						break;
					}
					nextBackupRunTime += settings.isRunHourly() ? 60L * 60L * 1000L : 24L * 60L * 60L * 1000L;
				}
				timer.schedule(backupTimerTask, Math.max(nextBackupRunTime - now, 0));
			}
		}
	}

	private static long clearSeconds(long time) {
		return (time / 1000L / 60L) * 60L * 1000L;
	}

	private static void runBackup() {
		if (areSettingsOkayToRunBackup()) {
			Settings settings = settingsManager.getSettings();
			long now = System.currentTimeMillis();
			IDialogSettings backupSection = Utils.getSection("backup"); //$NON-NLS-1$
			backupSection.put("lastRun", now); //$NON-NLS-1$

			backupRun = new BackupRun(settings);
			backupRun.addListener(new BackupRunAdapter() {
				@Override
				public void backupEnded(BackupEndedEvent e) {
					backupRun = null;
					scheduleBackupRun(false);
				}
			});
			
			if (backupShell != null) {
				backupShell.setBackupRun(backupRun);
			}
			trayIcon.setBackupRun(backupRun);
			backupRun.runBackup();
		}
	}

	static boolean areSettingsOkayToRunBackup() {
		Settings settings = settingsManager.getSettings();
		return !settings.getLocations().isEmpty() &&
			StringUtils.isNotBlank(settings.getOutputFolder()) &&
			new File(settings.getOutputFolder()).exists();
	}
	
	public void stop() {
		quit();
	}
	
	public static void quit() {
		running = false;
		display.wake();
	}

	static void showShell() {
		if (backupShell == null) {
			backupShell = new BackupShell(display);
			if (backupRun != null) {
				backupShell.setBackupRun(backupRun);
			}
			backupShell.open();
		} else {
			backupShell.setVisible(true);
			backupShell.forceActive();
		}
	}

	static void hideShell() {
		if (backupShell != null) {
			backupShell.setVisible(false);
		}
	}
	
	static long getNextScheduledBackupRunTime() {
		return nextBackupRunTime;
	}
	
	public static Image[] getWindowImages() {
		return windowImages;
	}
	
	public static SettingsManager getSettingsManager() {
		return settingsManager;
	}
}
