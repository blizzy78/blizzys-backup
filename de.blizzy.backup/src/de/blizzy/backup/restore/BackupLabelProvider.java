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
package de.blizzy.backup.restore;

import java.text.DateFormat;
import java.text.NumberFormat;

import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.osgi.util.NLS;

import de.blizzy.backup.Messages;

class BackupLabelProvider extends LabelProvider {
	private static final DateFormat DATE_FORMAT = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
	private static final NumberFormat NUMBER_FORMAT = NumberFormat.getIntegerInstance();
	
	@Override
	public String getText(Object element) {
		Backup backup = (Backup) element;
		return DATE_FORMAT.format(backup.runTime) +
			" (" + NLS.bind(Messages.XEntries, NUMBER_FORMAT.format(backup.numEntries)) + ")"; //$NON-NLS-1$ //$NON-NLS-2$
	}
}
