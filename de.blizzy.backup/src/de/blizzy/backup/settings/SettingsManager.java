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
package de.blizzy.backup.settings;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.eclipse.core.runtime.ISafeRunnable;
import org.eclipse.core.runtime.SafeRunner;
import org.eclipse.jface.dialogs.IDialogSettings;

import de.blizzy.backup.BackupPlugin;
import de.blizzy.backup.Utils;
import de.blizzy.backup.vfs.ILocation;
import de.blizzy.backup.vfs.LocationProviderDescriptor;
import de.blizzy.backup.vfs.filesystem.FileSystemLocationProvider;

public class SettingsManager {
	private List<ISettingsListener> listeners = new ArrayList<ISettingsListener>();
	
	public Settings getSettings() {
		IDialogSettings section = getSection();
		Set<ILocation> locations = new HashSet<ILocation>();

		IDialogSettings locationsSection = section.getSection("locations"); //$NON-NLS-1$
		if (locationsSection != null) {
			IDialogSettings[] locationSections = locationsSection.getSections();
			if (locationSections != null) {
				List<LocationProviderDescriptor> descriptors = BackupPlugin.getDefault().getLocationProviders();
				for (IDialogSettings locationSection : locationSections) {
					String type = locationSection.get("__type"); //$NON-NLS-1$
					for (LocationProviderDescriptor desc : descriptors) {
						if (desc.getLocationProvider().getId().equals(type)) {
							locations.add(desc.getLocationProvider().getLocation(locationSection));
							break;
						}
					}
				}
			}
		} else {
			// old folders
			String[] savedFolders = section.getArray("folders"); //$NON-NLS-1$
			if (savedFolders != null) {
				for (String folder : savedFolders) {
					locations.add(FileSystemLocationProvider.location(new File(folder)));
				}
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
		boolean useChecksums = false;
		if (section.get("useChecksums") != null) { //$NON-NLS-1$
			useChecksums = section.getBoolean("useChecksums"); //$NON-NLS-1$
		}
		return new Settings(locations, outputFolder, runHourly, dailyHours, dailyMinutes, useChecksums);
	}

	private IDialogSettings getSection() {
		return Utils.getChildSection(Utils.getSection("backup"), "settings"); //$NON-NLS-1$ //$NON-NLS-2$
	}
	
	public void setSettings(Settings settings) {
		IDialogSettings section = getSection();

		IDialogSettings locationsSection = section.addNewSection("locations"); //$NON-NLS-1$
		int idx = 1;
		for (ILocation location : settings.getLocations()) {
			IDialogSettings locationSection = locationsSection.addNewSection("location." + idx++); //$NON-NLS-1$
			locationSection.put("__type", location.getProvider().getId()); //$NON-NLS-1$
			location.getProvider().saveSettings(location, locationSection);
		}
		
		// clean out old folders section
		section.put("folders", ArrayUtils.EMPTY_STRING_ARRAY); //$NON-NLS-1$
		
		section.put("outputFolder", settings.getOutputFolder()); //$NON-NLS-1$
		section.put("runHourly", settings.isRunHourly()); //$NON-NLS-1$
		section.put("dailyHours", settings.getDailyHours()); //$NON-NLS-1$
		section.put("dailyMinutes", settings.getDailyMinutes()); //$NON-NLS-1$
		section.put("useChecksums", settings.isUseChecksums()); //$NON-NLS-1$
		
		fireSettingsChanged();
	}
	
	public void addListener(ISettingsListener listener) {
		synchronized (listeners) {
			listeners.add(listener);
		}
	}

	public void removeListener(ISettingsListener listener) {
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
