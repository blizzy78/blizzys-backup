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
import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.commons.lang.StringUtils;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.plugin.AbstractUIPlugin;

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

	public Object start(IApplicationContext context) throws Exception {
		display = Display.getDefault();
		
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
					scheduleBackupRun();
				}
			}
		});
		
		timer = new Timer();

		TrayIcon trayIcon = new TrayIcon(display);
		
		scheduleBackupRun();

		context.applicationRunning();

		if (!BackupPlugin.getDefault().isHidden()) {
			showShell();
		}
		
		while (running && !display.isDisposed()) {
			try {
				if (!display.readAndDispatch()) {
					display.sleep();
				}
			} catch (RuntimeException e) {
				e.printStackTrace();
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
		
		return EXIT_OK;
	}

	private void setupDefaultPreferences() {
		IDialogSettings backupSection = Utils.getSection("backup"); //$NON-NLS-1$
		if (backupSection.get("lastRun") == null) { //$NON-NLS-1$
			// fake last run as happened 50 min ago
			backupSection.put("lastRun", System.currentTimeMillis() - 50L * 60L * 1000L); //$NON-NLS-1$
		}
	}

	private static void scheduleBackupRun() {
		if (timer != null) {
			if (backupTimerTask != null) {
				backupTimerTask.cancel();
				backupTimerTask = null;
			}
			
			IDialogSettings backupSection = Utils.getSection("backup"); //$NON-NLS-1$
			long lastRun = backupSection.getLong("lastRun"); //$NON-NLS-1$
			Settings settings = settingsManager.getSettings();
			// run hourly: just add 60 min
			if (settings.isRunHourly()) {
				nextBackupRunTime = lastRun + 60L * 60L * 1000L;
			}
			// daily
			else {
				Calendar c = Calendar.getInstance();
				long now = c.getTimeInMillis();
				c.set(Calendar.HOUR_OF_DAY, settings.getDailyHours());
				c.set(Calendar.MINUTE, settings.getDailyMinutes());
				long then = c.getTimeInMillis();
				// scheduled time in settings has not arrived on the current day yet
				if (then > now) {
					nextBackupRunTime = then;
				}
				// scheduled time has passed: schedule timer for next day at that time
				else {
					c.add(Calendar.DAY_OF_YEAR, 1);
					nextBackupRunTime = c.getTimeInMillis();
				}
			}
			
			backupTimerTask = new TimerTask() {
				@Override
				public void run() {
					runBackup();
				}
			};
			timer.schedule(backupTimerTask, Math.max(nextBackupRunTime - System.currentTimeMillis(), 0));
		}
	}

	private static void runBackup() {
		Settings settings = settingsManager.getSettings();
		if (!settings.getFolders().isEmpty() &&
			StringUtils.isNotBlank(settings.getOutputFolder()) &&
			new File(settings.getOutputFolder()).exists()) {

			long now = System.currentTimeMillis();
			IDialogSettings backupSection = Utils.getSection("backup"); //$NON-NLS-1$
			backupSection.put("lastRun", now); //$NON-NLS-1$

			backupRun = new BackupRun(settings);
			backupRun.addListener(new BackupRunAdapter() {
				@Override
				public void backupEnded(BackupEndedEvent e) {
					backupRun = null;
					scheduleBackupRun();
				}
			});
			
			if (backupShell != null) {
				backupShell.setBackupRun(backupRun);
			}
			backupRun.runBackup();
		}
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
	
	static Image[] getWindowImages() {
		return windowImages;
	}
	
	static SettingsManager getSettingsManager() {
		return settingsManager;
	}
}
