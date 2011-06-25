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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.eclipse.core.runtime.ISafeRunnable;
import org.eclipse.core.runtime.SafeRunner;
import org.eclipse.jface.dialogs.IDialogSettings;

class SettingsManager {
	private List<ISettingsListener> listeners = new ArrayList<ISettingsListener>();
	
	Settings getSettings() {
		IDialogSettings section = getSection();
		Set<String> folders = new HashSet<String>();
		String[] savedFolders = section.getArray("folders"); //$NON-NLS-1$
		if (savedFolders != null) {
			for (String folder : savedFolders) {
				folders.add(folder);
			}
		}
		String outputFolder = section.get("outputFolder"); //$NON-NLS-1$
		boolean runHourly = true;
		if (section.get("runHourly") != null) { //$NON-NLS-1$
			runHourly = section.getBoolean("runHourly"); //$NON-NLS-1$
		}
		int dailyHours = 12;
		if (section.get("dailyHours") != null) { //$NON-NLS-1$
			dailyHours = section.getInt("dailyHours"); //$NON-NLS-1$
		}
		int dailyMinutes = 0;
		if (section.get("dailyMinutes") != null) { //$NON-NLS-1$
			dailyMinutes = section.getInt("dailyMinutes"); //$NON-NLS-1$
		}
		return new Settings(folders, outputFolder, runHourly, dailyHours, dailyMinutes);
	}

	private IDialogSettings getSection() {
		return Utils.getChildSection(Utils.getSection("backup"), "settings"); //$NON-NLS-1$ //$NON-NLS-2$
	}
	
	void setSettings(Settings settings) {
		IDialogSettings section = getSection();
		section.put("folders", settings.getFolders().toArray(ArrayUtils.EMPTY_STRING_ARRAY)); //$NON-NLS-1$
		section.put("outputFolder", settings.getOutputFolder()); //$NON-NLS-1$
		section.put("runHourly", settings.isRunHourly()); //$NON-NLS-1$
		section.put("dailyHours", settings.getDailyHours()); //$NON-NLS-1$
		section.put("dailyMinutes", settings.getDailyMinutes()); //$NON-NLS-1$
		
		fireSettingsChanged();
	}
	
	void addListener(ISettingsListener listener) {
		synchronized (listeners) {
			listeners.add(listener);
		}
	}

	void removeListener(ISettingsListener listener) {
		synchronized (listeners) {
			listeners.remove(listener);
		}
	}
	
	private void fireSettingsChanged() {
		List<ISettingsListener> ls;
		synchronized (listeners) {
			ls = new ArrayList<ISettingsListener>(listeners);
		}
		for (final ISettingsListener l : ls) {
			SafeRunner.run(new ISafeRunnable() {
				public void run() throws Exception {
					l.settingsChanged();
				}
				
				public void handleException(Throwable t) {
					BackupPlugin.getDefault().logError(StringUtils.EMPTY, t);
				}
			});
		}
	}
}
