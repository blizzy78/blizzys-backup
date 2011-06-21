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

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.lang.StringUtils;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.plugin.AbstractUIPlugin;

public class BackupPlugin extends AbstractUIPlugin {
	public static final String ID = "de.blizzy.backup"; //$NON-NLS-1$
	public static final String VERSION = "1.0.0"; //$NON-NLS-1$
	public static final String COPYRIGHT_YEARS = "2011"; //$NON-NLS-1$

	private static final String ARG_HIDDEN = "-hidden"; //$NON-NLS-1$

	private static BackupPlugin instance;

	public BackupPlugin() {
		instance = this;
	}
	
	public static BackupPlugin getDefault() {
		return instance;
	}

	boolean isHidden() {
		return Boolean.parseBoolean(
				StringUtils.defaultString(getApplicationArg(ARG_HIDDEN), Boolean.FALSE.toString()));
	}
	
	private String getApplicationArg(String argName) {
		String[] args = Platform.getApplicationArgs();
		for (String arg : args) {
			if (arg.equals(argName)) {
				return ""; //$NON-NLS-1$
			}
			if (arg.startsWith(argName + "=")) { //$NON-NLS-1$
				return arg.substring(argName.length() + 1);
			}
		}
		return null;
	}

	void logMessage(String msg) {
		getLog().log(new Status(IStatus.INFO, ID, msg));
	}

	void logError(String msg, Throwable t) {
		getLog().log(new Status(IStatus.ERROR, ID, msg, t));
	}
	
	ImageDescriptor getImageDescriptor(String path) {
		return AbstractUIPlugin.imageDescriptorFromPlugin(ID, path);
	}

	InputStream openBundleFile(String path) throws IOException {
		return getBundle().getEntry(path).openStream();
	}
}
