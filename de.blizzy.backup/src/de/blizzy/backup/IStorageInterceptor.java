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
import java.io.OutputStream;

import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.swt.widgets.Shell;

public interface IStorageInterceptor {
	boolean initialize(Shell shell, IDialogSettings settings);
	void destroy();
	OutputStream interceptOutputStream(OutputStream out, long fileLength) throws IOException;
	InputStream interceptInputStream(InputStream in, long fileLength) throws IOException;
	boolean showErrorMessage(Throwable t, Shell shell);
}
