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
package de.blizzy.backup.vfs.sftp;

import org.apache.commons.lang.StringUtils;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.events.VerifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import de.blizzy.backup.BackupApplication;
import de.blizzy.backup.Messages;
import de.blizzy.backup.vfs.ILocation;

class SftpLocationDialog extends Dialog {
	private Text hostText;
	private Text portText;
	private Text loginText;
	private Text passwordText;
	private Text folderText;
	private SftpLocationProvider provider;
	private ILocation location;

	SftpLocationDialog(Shell parentShell, SftpLocationProvider provider) {
		super(parentShell);

		this.provider = provider;
	}

	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setImages(BackupApplication.getWindowImages());
		newShell.setText(Messages.Title_SFTP);
	}
	
	@Override
	protected boolean isResizable() {
		return true;
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite composite = (Composite) super.createDialogArea(parent);
		((GridLayout) composite.getLayout()).numColumns = 4;
		((GridLayout) composite.getLayout()).makeColumnsEqualWidth = false;
		
		Label label = new Label(composite, SWT.NONE);
		label.setText(Messages.Label_Host + ":"); //$NON-NLS-1$
		label.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

		hostText = new Text(composite, SWT.BORDER);
		GridData gd = new GridData(SWT.FILL, SWT.CENTER, true, false);
		gd.widthHint = convertWidthInCharsToPixels(40);
		hostText.setLayoutData(gd);

		label = new Label(composite, SWT.NONE);
		label.setText(Messages.Label_Port + ":"); //$NON-NLS-1$
		label.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

		portText = new Text(composite, SWT.BORDER);
		gd = new GridData(SWT.FILL, SWT.CENTER, false, false);
		gd.widthHint = convertWidthInCharsToPixels(4);
		portText.setLayoutData(gd);
		
		label = new Label(composite, SWT.NONE);
		label.setText(Messages.Label_Login + ":"); //$NON-NLS-1$
		label.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

		loginText = new Text(composite, SWT.BORDER);
		gd = new GridData(SWT.FILL, SWT.CENTER, true, false);
		gd.horizontalSpan = 3;
		loginText.setLayoutData(gd);

		label = new Label(composite, SWT.NONE);
		label.setText(Messages.Label_Password + ":"); //$NON-NLS-1$
		label.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		
		passwordText = new Text(composite, SWT.BORDER | SWT.PASSWORD);
		gd = new GridData(SWT.FILL, SWT.CENTER, true, false);
		gd.horizontalSpan = 3;
		passwordText.setLayoutData(gd);
		
		label = new Label(composite, SWT.NONE);
		label.setText(Messages.Label_Folder + ":"); //$NON-NLS-1$
		label.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		
		folderText = new Text(composite, SWT.BORDER);
		gd = new GridData(SWT.FILL, SWT.CENTER, true, false);
		gd.horizontalSpan = 3;
		folderText.setLayoutData(gd);
		
		hostText.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				updateOkButton();
			}
		});

		portText.addVerifyListener(new VerifyListener() {
			public void verifyText(VerifyEvent e) {
				if (StringUtils.isNotBlank(e.text) && !StringUtils.containsOnly(e.text, "0123456789")) { //$NON-NLS-1$
					e.doit = false;
				}
			}
		});
		
		portText.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				updateOkButton();
			}
		});
		
		loginText.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				updateOkButton();
			}
		});
		
		passwordText.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				updateOkButton();
			}
		});
		
		folderText.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				updateOkButton();
			}
		});
		
		return composite;
	}

	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		super.createButtonsForButtonBar(parent);
		updateOkButton();
	}
	
	private void updateOkButton() {
		String portStr = portText.getText();
		boolean ok = StringUtils.isNotBlank(hostText.getText()) &&
			StringUtils.isNotBlank(portStr) &&
			StringUtils.isNotBlank(loginText.getText()) &&
			StringUtils.isNotBlank(passwordText.getText()) &&
			StringUtils.isNotBlank(folderText.getText());
		if (ok) {
			try {
				long port = Long.parseLong(portStr);
				ok = (port > 0) && (port <= 65535);
			} catch (NumberFormatException e) {
				// ignore
			}
		}
		getButton(IDialogConstants.OK_ID).setEnabled(ok);
	}
	
	@Override
	protected void buttonPressed(int buttonId) {
		if (buttonId == IDialogConstants.OK_ID) {
			location = new SftpLocation(hostText.getText(), Integer.parseInt(portText.getText()),
					loginText.getText(), passwordText.getText(), folderText.getText(), provider);
		}
		super.buttonPressed(buttonId);
	}

	ILocation getLocation() {
		return location;
	}
}
