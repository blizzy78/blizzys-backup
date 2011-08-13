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

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

class LicenseDialog extends Dialog {
	LicenseDialog(Shell parentShell) {
		super(parentShell);
	}

	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setImages(BackupApplication.getWindowImages());
		newShell.setText(Messages.Title_License);
	}
	
	@Override
	protected void setShellStyle(int newShellStyle) {
		super.setShellStyle(newShellStyle | SWT.RESIZE);
	}
	
	@Override
	protected Control createDialogArea(Composite parent) {
		Composite composite = (Composite) super.createDialogArea(parent);
		
		Text text = new Text(composite, SWT.BORDER | SWT.READ_ONLY | SWT.MULTI | SWT.V_SCROLL);
		InputStream in = null;
		try {
			in = BackupPlugin.getDefault().openBundleFile("etc/license.txt"); //$NON-NLS-1$
			String licenseText = StringUtils.join(IOUtils.readLines(in, "ISO-8859-1"), '\n'); //$NON-NLS-1$
			text.setText(licenseText);
		} catch (IOException e) {
			IOUtils.closeQuietly(in);
		}
		GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
		gd.widthHint = convertWidthInCharsToPixels(80);
		gd.heightHint = convertWidthInCharsToPixels(40);
		text.setLayoutData(gd);
		
		return composite;
	}
	
	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
	}
}
