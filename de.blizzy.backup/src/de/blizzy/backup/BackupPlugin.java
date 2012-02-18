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

import java.io.IOException;
import java.io.InputStream;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.plugin.AbstractUIPlugin;

import de.blizzy.backup.vfs.ILocationProvider;
import de.blizzy.backup.vfs.LocationProviderDescriptor;
import de.blizzy.backup.vfs.filesystem.FileSystemLocationProvider;

public class BackupPlugin extends AbstractUIPlugin {
	public static final String ID = "de.blizzy.backup"; //$NON-NLS-1$
	public static final String VERSION = "1.5.0"; //$NON-NLS-1$
	public static final String COPYRIGHT_YEARS = "2011-2012"; //$NON-NLS-1$

	public static final int KEEP_HOURLIES_DAYS = 7;
	public static final int KEEP_DAILIES_DAYS = 30;
	public static final int MAX_DISK_FILL_RATE = 80;

	private static final String ARG_HIDDEN = "-hidden"; //$NON-NLS-1$
	private static final String ARG_CHECK_GUI = "-checkGui"; //$NON-NLS-1$

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
	
	boolean isCheckGui() {
		return Boolean.parseBoolean(
				StringUtils.defaultString(getApplicationArg(ARG_CHECK_GUI), Boolean.FALSE.toString()));
	}
	
	private String getApplicationArg(String argName) {
		String[] args = Platform.getApplicationArgs();
		for (String arg : args) {
			if (arg.equals(argName)) {
				return StringUtils.EMPTY;
			}
			if (arg.startsWith(argName + "=")) { //$NON-NLS-1$
				return arg.substring(argName.length() + 1);
			}
		}
		return null;
	}

	public void logMessage(String msg) {
		getLog().log(new Status(IStatus.INFO, ID, msg));
	}

	public void logError(String msg, Throwable t) {
		getLog().log(new Status(IStatus.ERROR, ID, msg, t));
	}
	
	public ImageDescriptor getImageDescriptor(String path) {
		return AbstractUIPlugin.imageDescriptorFromPlugin(ID, path);
	}

	InputStream openBundleFile(String path) throws IOException {
		return getBundle().getEntry(path).openStream();
	}
	
	public List<LocationProviderDescriptor> getLocationProviders() {
		List<LocationProviderDescriptor> result = new ArrayList<>();
		IExtensionPoint point = Platform.getExtensionRegistry().getExtensionPoint(ID + ".locationProviders"); //$NON-NLS-1$
		IExtension[] extensions = point.getExtensions();
		for (IExtension extension : extensions) {
			for (IConfigurationElement element : extension.getConfigurationElements()) {
				try {
					String name = element.getAttribute("name"); //$NON-NLS-1$
					ILocationProvider locationProvider =
						(ILocationProvider) element.createExecutableExtension("class"); //$NON-NLS-1$
					result.add(new LocationProviderDescriptor(name, locationProvider));
				} catch (CoreException e) {
					logError("error while creating location provider", e); //$NON-NLS-1$
				}
			}
		}
		Collections.sort(result, new Comparator<LocationProviderDescriptor>() {
			@Override
			public int compare(LocationProviderDescriptor d1, LocationProviderDescriptor d2) {
				if (d1.getLocationProvider() instanceof FileSystemLocationProvider) {
					return -1;
				}
				if (d2.getLocationProvider() instanceof FileSystemLocationProvider) {
					return 1;
				}
				return Collator.getInstance().compare(d1.getName(), d2.getName());
			}
		});
		return result;
	}

	public List<StorageInterceptorDescriptor> getStorageInterceptors() {
		List<StorageInterceptorDescriptor> result = new ArrayList<>();
		IExtensionPoint point = Platform.getExtensionRegistry().getExtensionPoint(ID + ".storageInterceptors"); //$NON-NLS-1$
		IExtension[] extensions = point.getExtensions();
		for (IExtension extension : extensions) {
			for (IConfigurationElement element : extension.getConfigurationElements()) {
				try {
					String id = extension.getUniqueIdentifier();
					String name = element.getAttribute("name"); //$NON-NLS-1$
					IStorageInterceptor storageInterceptor =
							(IStorageInterceptor) element.createExecutableExtension("class"); //$NON-NLS-1$
					result.add(new StorageInterceptorDescriptor(id, name, storageInterceptor));
				} catch (CoreException e) {
					logError("error while creating storage interceptor", e); //$NON-NLS-1$
				}
			}
		}
		Collections.sort(result, new Comparator<StorageInterceptorDescriptor>() {
			@Override
			public int compare(StorageInterceptorDescriptor d1, StorageInterceptorDescriptor d2) {
				return Collator.getInstance().compare(d1.getName(), d2.getName());
			}
		});
		return result;
	}
}
