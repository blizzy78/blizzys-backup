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

import org.eclipse.jface.dialogs.IDialogSettings;

final class Utils {
	private static final String DIALOG_SECTION = BackupPlugin.ID + ".dialog"; //$NON-NLS-1$

	private Utils() {}

	static IDialogSettings getSection(String sectionId) {
		IDialogSettings root = BackupPlugin.getDefault().getDialogSettings();
		return getChildSection(getChildSection(root, DIALOG_SECTION), sectionId);
	}

	static IDialogSettings getChildSection(IDialogSettings parent, String name) {
		IDialogSettings section = parent.getSection(name);
		if (section == null) {
			section = parent.addNewSection(name);
		}
		return section;
	}

	static File toBackupFile(String backupFilePath, String outputFolder) {
		String[] parts = backupFilePath.split("/"); //$NON-NLS-1$
		File result = new File(outputFolder);
		for (String part : parts) {
			result = new File(result, part);
		}
		return result;
	}
}
