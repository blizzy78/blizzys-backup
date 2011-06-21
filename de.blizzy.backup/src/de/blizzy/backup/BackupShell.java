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

import java.text.DateFormat;
import java.util.Date;

import org.apache.commons.lang.StringUtils;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.ShellAdapter;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;

class BackupShell {
	private Shell shell;
	private Label statusLabel;
	private BackupRun backupRun;
	private IBackupRunListener backupRunListener = new IBackupRunListener() {
		public void backupStatusChanged(BackupStatusEvent e) {
			updateStatusLabel();
		}
		
		public void backupEnded(BackupEndedEvent e) {
			backupRun.removeListener(this);
			backupRun = null;
			updateStatusLabel();
		}
	};

	BackupShell(Display display) {
		shell = new Shell(display);
		shell.setText("blizzy's Backup"); //$NON-NLS-1$
		shell.setImages(BackupApplication.getWindowImages());
		
		GridLayout layout = new GridLayout(1, false);
		layout.marginWidth = 20;
		layout.marginHeight = 20;
		layout.verticalSpacing = 15;
		shell.setLayout(layout);

		Composite logoAndHeaderComposite = new Composite(shell, SWT.NONE);
		layout = new GridLayout(2, false);
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		layout.horizontalSpacing = 15;
		logoAndHeaderComposite.setLayout(layout);
		
		Canvas logoCanvas = new Canvas(logoAndHeaderComposite, SWT.DOUBLE_BUFFERED);
		logoCanvas.addPaintListener(new PaintListener() {
			public void paintControl(PaintEvent e) {
				Image image = BackupPlugin.getDefault().getImageDescriptor("etc/logo/logo_48.png") //$NON-NLS-1$
					.createImage(e.display);
				e.gc.drawImage(image, 0, 0);
				image.dispose();
			}
		});
		GridData gd = new GridData(SWT.CENTER, SWT.CENTER, false, false);
		gd.widthHint = 48;
		gd.heightHint = 48;
		logoCanvas.setLayoutData(gd);
		
		Link headerText = new Link(logoAndHeaderComposite, SWT.NONE);
		headerText.setText(NLS.bind("blizzy's Backup - Version {0}\nCopyright {1} by Maik Schreiber\n" + //$NON-NLS-1$
				"Licensed under the <a href=\"license\">GNU GPL v3</a>", //$NON-NLS-1$
				BackupPlugin.VERSION, BackupPlugin.COPYRIGHT_YEARS));
		
		Composite buttonsComposite = new Composite(shell, SWT.NONE);
		layout = new GridLayout(2, false);
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		layout.horizontalSpacing = 10;
		layout.verticalSpacing = 15;
		buttonsComposite.setLayout(layout);
		buttonsComposite.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

		FontData[] fontDatas = buttonsComposite.getFont().getFontData();
		for (FontData fontData : fontDatas) {
			fontData.setHeight((int) (fontData.getHeight() * 1.5d));
		}
		final Font bigFont = new Font(display, fontDatas);

		Point extent = getMaxTextExtent(display, bigFont, "Settings", "Restore"); //$NON-NLS-1$ //$NON-NLS-2$
		
		Button settingsButton = new Button(buttonsComposite, SWT.PUSH);
		settingsButton.setText("Settings"); //$NON-NLS-1$
		settingsButton.setFont(bigFont);
		gd = new GridData(SWT.FILL, SWT.FILL, false, true);
		gd.widthHint = extent.x * 2;
		gd.heightHint = extent.y * 2;
		settingsButton.setLayoutData(gd);
		
		Label label = new Label(buttonsComposite, SWT.NONE);
		label.setText("Modify backup settings and folders"); //$NON-NLS-1$
		label.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));
		
		Button restoreButton = new Button(buttonsComposite, SWT.PUSH);
		restoreButton.setText("Restore"); //$NON-NLS-1$
		restoreButton.setFont(bigFont);
		gd = new GridData(SWT.FILL, SWT.FILL, false, true);
		gd.widthHint = extent.x * 2;
		gd.heightHint = extent.y * 2;
		restoreButton.setLayoutData(gd);
		
		label = new Label(buttonsComposite, SWT.NONE);
		label.setText("View and restore from previous backups"); //$NON-NLS-1$
		label.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));
		
		statusLabel = new Label(shell, SWT.NONE);
		statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		updateStatusLabel();

		shell.pack();
		
		headerText.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				new LicenseDialog(shell).open();
			}
		});

		settingsButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				editSettings();
			}
		});
		
		restoreButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				restore();
			}
		});
		
		shell.addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e) {
				bigFont.dispose();
			}
		});
		
		shell.addShellListener(new ShellAdapter() {
			@Override
			public void shellClosed(ShellEvent e) {
				if (backupRun != null) {
					backupRun.removeListener(backupRunListener);
				}
				BackupApplication.quit();
			}
			
			@Override
			public void shellIconified(ShellEvent e) {
				BackupApplication.hideShell();
			}
		});
		
		settingsButton.forceFocus();
	}

	private void updateStatusLabel() {
		Display display = shell.getDisplay();
		display.asyncExec(new Runnable() {
			public void run() {
				if (backupRun != null) {
					String currentFile = backupRun.getCurrentFile();
					if (StringUtils.isNotBlank(currentFile)) {
						statusLabel.setText("Status" + ": " + "Running" + " - " + currentFile);
					}
				} else {
					Date nextRunDate = new Date(BackupApplication.getNextScheduledBackupRunTime());
					statusLabel.setText("Status" + ": " + "Idle" + " - " + "Next run" + ": " +
							DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(nextRunDate));
				}
			}
		});
	}

	private void editSettings() {
		new SettingsDialog(shell).open();
	}
	
	private void restore() {
		new RestoreDialog(shell).open();
	}

	private Point getMaxTextExtent(Device device, Font font, String... strings) {
		Image buf = new Image(device, 1, 1);
		GC gc = new GC(buf);
		gc.setFont(font);
		Point result = new Point(0, 0);
		for (String string : strings) {
			Point extent = gc.textExtent(string);
			result.x = Math.max(result.x, extent.x);
			result.y = Math.max(result.y, extent.y);
		}
		gc.dispose();
		buf.dispose();
		return result;
	}
	
	void open() {
		shell.open();
	}

	void forceActive() {
		shell.forceActive();
	}

	void setVisible(boolean visible) {
		shell.setVisible(visible);
	}

	void setBackupRun(BackupRun backupRun) {
		this.backupRun = backupRun;
		backupRun.addListener(backupRunListener);
	}
}
