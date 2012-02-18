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

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.resource.ImageDescriptor;
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
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.ToolBar;

import de.blizzy.backup.backup.BackupEndedEvent;
import de.blizzy.backup.backup.BackupErrorEvent;
import de.blizzy.backup.backup.BackupRun;
import de.blizzy.backup.backup.BackupStatus;
import de.blizzy.backup.backup.BackupStatusEvent;
import de.blizzy.backup.backup.IBackupRunListener;
import de.blizzy.backup.restore.RestoreDialog;
import de.blizzy.backup.settings.ISettingsListener;
import de.blizzy.backup.settings.Settings;
import de.blizzy.backup.settings.SettingsDialog;

class BackupShell {
	private Shell shell;
	private Button restoreButton;
	private Button backupNowButton;
	private Button checkButton;
	private Composite progressComposite;
	private ProgressBar progressBar;
	private Link statusLabel;
	private BackupRun backupRun;
	private IBackupRunListener backupRunListener = new IBackupRunListener() {
		@Override
		public void backupStatusChanged(BackupStatusEvent e) {
			BackupStatus status = e.getStatus();
			updateStatusLabel(status);
			if (status.isCleanup() || status.isFinalize()) {
				pauseAction.setEnabled(false);
				stopAction.setEnabled(false);
			}
		}
		
		@Override
		public void backupEnded(BackupEndedEvent e) {
			backupRun.removeListener(this);
			backupRun = null;
			updateStatusLabel(null);
			updateRestoreButton();
			updateBackupNowButton();
			updateCheckButton();
			updateProgressVisibility();
			pauseAction.setChecked(false);
			pauseAction.setEnabled(true);
			stopAction.setEnabled(true);
		}
		
		@Override
		public void backupErrorOccurred(BackupErrorEvent e) {
			synchronized (backupErrors) {
				backupErrors.add(new BackupError(e));
			}
		}
	};
	private ISettingsListener settingsListener = new ISettingsListener() {
		@Override
		public void settingsChanged() {
			updateStatusLabel(null);
			updateRestoreButton();
			updateBackupNowButton();
			updateCheckButton();
		}
	};
	private IAction pauseAction;
	private IAction stopAction;
	private List<BackupError> backupErrors = new ArrayList<>();

	BackupShell(Display display) {
		shell = new Shell(display, SWT.SHELL_TRIM ^ SWT.MAX);
		shell.setText(Messages.Title_BlizzysBackup);
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
			@Override
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
		headerText.setText(NLS.bind(Messages.Version,
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

		Point extent = getMaxTextExtent(display, bigFont,
				Messages.Button_Settings, Messages.Button_Restore, Messages.Button_BackupNow);
		
		Button settingsButton = new Button(buttonsComposite, SWT.PUSH);
		settingsButton.setText(Messages.Button_Settings);
		settingsButton.setFont(bigFont);
		gd = new GridData(SWT.FILL, SWT.FILL, false, true);
		gd.widthHint = (int) (extent.x * 1.6d);
		gd.heightHint = extent.y * 2;
		settingsButton.setLayoutData(gd);
		
		Label label = new Label(buttonsComposite, SWT.NONE);
		label.setText(Messages.ModifyBackupSettings);
		label.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));
		
		restoreButton = new Button(buttonsComposite, SWT.PUSH);
		restoreButton.setText(Messages.Button_Restore);
		restoreButton.setFont(bigFont);
		gd = new GridData(SWT.FILL, SWT.FILL, false, true);
		gd.widthHint = (int) (extent.x * 1.6d);
		gd.heightHint = extent.y * 2;
		restoreButton.setLayoutData(gd);
		updateRestoreButton();

		label = new Label(buttonsComposite, SWT.NONE);
		label.setText(Messages.RestoreFromBackup);
		label.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));
		
		backupNowButton = new Button(buttonsComposite, SWT.PUSH);
		backupNowButton.setText(Messages.Button_BackupNow);
		backupNowButton.setFont(bigFont);
		gd = new GridData(SWT.FILL, SWT.FILL, false, true);
		gd.widthHint = (int) (extent.x * 1.6d);
		gd.heightHint = extent.y * 2;
		backupNowButton.setLayoutData(gd);
		updateBackupNowButton();

		label = new Label(buttonsComposite, SWT.NONE);
		label.setText(Messages.RunBackupNow);
		label.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));

		if (BackupPlugin.getDefault().isCheckGui()) {
			checkButton = new Button(buttonsComposite, SWT.PUSH);
			checkButton.setText(Messages.Button_Check);
			checkButton.setFont(bigFont);
			gd = new GridData(SWT.FILL, SWT.FILL, false, true);
			gd.widthHint = (int) (extent.x * 1.6d);
			gd.heightHint = extent.y * 2;
			checkButton.setLayoutData(gd);
			updateCheckButton();

			label = new Label(buttonsComposite, SWT.NONE);
			label.setText(Messages.CheckBackup);
			label.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));
		}
		
		Composite progressStatusComposite = new Composite(shell, SWT.NONE);
		layout = new GridLayout(1, false);
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		progressStatusComposite.setLayout(layout);
		progressStatusComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		
		progressComposite = new Composite(progressStatusComposite, SWT.NONE);
		layout = new GridLayout(3, false);
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		progressComposite.setLayout(layout);
		progressComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		progressBar = new ProgressBar(progressComposite, SWT.HORIZONTAL | SWT.SMOOTH);
		progressBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		progressBar.setMinimum(0);
		updateProgressVisibility();

		ToolBarManager toolBarManager = new ToolBarManager(SWT.FLAT);

		pauseAction = new Action(Messages.Button_PauseBackup, IAction.AS_CHECK_BOX) {
			@Override
			public void run() {
				if (backupRun != null) {
					backupRun.setPaused(pauseAction.isChecked());
				}
			}
		};
		ImageDescriptor imgDesc = BackupPlugin.getDefault().getImageDescriptor("etc/icons/pause.gif"); //$NON-NLS-1$
		pauseAction.setImageDescriptor(imgDesc);
		pauseAction.setToolTipText(Messages.Button_PauseBackup);
		toolBarManager.add(pauseAction);

		stopAction = new Action() {
			@Override
			public void run() {
				if (backupRun != null) {
					pauseAction.setChecked(false);
					pauseAction.setEnabled(false);
					stopAction.setEnabled(false);
					backupRun.stopBackup();
				}
			}
		};
		imgDesc = BackupPlugin.getDefault().getImageDescriptor("etc/icons/stop.gif"); //$NON-NLS-1$
		stopAction.setImageDescriptor(imgDesc);
		stopAction.setToolTipText(Messages.Button_StopBackup);
		toolBarManager.add(stopAction);

		ToolBar toolBar = toolBarManager.createControl(progressComposite);
		toolBar.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
		
		statusLabel = new Link(progressStatusComposite, SWT.NONE);
		statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		updateStatusLabel(null);

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
		
		backupNowButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				BackupApplication.scheduleBackupRun(true);
			}
		});
		
		if (checkButton != null) {
			checkButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					BackupApplication.runCheck();
				}
			});
		}
		
		statusLabel.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (e.text.equals("errors")) { //$NON-NLS-1$
					showErrors();
				}
			}
		});
		
		shell.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				bigFont.dispose();
			}
		});
		
		shell.addShellListener(new ShellAdapter() {
			@Override
			public void shellClosed(ShellEvent e) {
				MessageDialog dlg = new MessageDialog(shell, Messages.Title_ExitApplication, null,
						Messages.ExitApplication, MessageDialog.CONFIRM,
						new String[] { Messages.Button_Exit, Messages.Button_MinimizeOnly }, 1);
				if (dlg.open() == 0) {
					BackupApplication.quit();
				} else {
					e.doit = false;
					BackupApplication.hideShell();
				}
			}
			
			@Override
			public void shellIconified(ShellEvent e) {
				e.doit = false;
				BackupApplication.hideShell();
			}
		});
		
		shell.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				handleDispose();
			}
		});
		
		BackupApplication.getSettingsManager().addListener(settingsListener);
		
		settingsButton.forceFocus();
	}

	private void handleDispose() {
		if (backupRun != null) {
			backupRun.removeListener(backupRunListener);
		}
		BackupApplication.getSettingsManager().removeListener(settingsListener);
	}

	private void updateStatusLabel(final BackupStatus status) {
		Utils.runAsync(shell.getDisplay(), new Runnable() {
			@Override
			public void run() {
				if (!statusLabel.isDisposed()) {
					String text;
					if (status != null) {
						int numEntries = status.getNumEntries();
						int totalEntries = status.getTotalEntries();
						synchronized (backupErrors) {
							text = Messages.Label_Status + ": " + Messages.Running + " " + //$NON-NLS-1$ //$NON-NLS-2$
									(((numEntries >= 0) && (totalEntries >= 0)) ?
											("(" + (int) Math.round(numEntries * 100d / totalEntries) + "%) ") : "") + //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
									(!backupErrors.isEmpty() ? "(<a href=\"errors\">" + Messages.Errors + "</a>) " : "") + //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
									"- " + //$NON-NLS-1$
									status.getText();
						}
						if ((numEntries >= 0) && (totalEntries >= 0)) {
							progressBar.setMaximum(totalEntries);
							progressBar.setSelection(numEntries);
						}
					} else {
						Date nextRunDate = new Date(BackupApplication.getNextScheduledBackupRunTime());
						synchronized (backupErrors) {
							text = Messages.Label_Status + ": " + Messages.Idle + " " + //$NON-NLS-1$ //$NON-NLS-2$
									(!backupErrors.isEmpty() ? "(<a href=\"errors\">" + Messages.Errors + "</a>) " : "") + //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
									"- " + Messages.Label_NextRun + ": " + //$NON-NLS-1$ //$NON-NLS-2$
									DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(nextRunDate);
						}
						progressBar.setMaximum(0);
						progressBar.setSelection(0);
					}
					statusLabel.setText(text);
				}
			}
		});
	}
	
	private void updateRestoreButton() {
		Utils.runAsync(shell.getDisplay(), new Runnable() {
			@Override
			public void run() {
				if (!restoreButton.isDisposed()) {
					Settings settings = BackupApplication.getSettingsManager().getSettings();
					restoreButton.setEnabled((backupRun == null) && Utils.isBackupFolder(settings.getOutputFolder()));
				}
			}
		});
	}

	private void updateBackupNowButton() {
		Utils.runAsync(shell.getDisplay(), new Runnable() {
			@Override
			public void run() {
				if (!backupNowButton.isDisposed()) {
					backupNowButton.setEnabled((backupRun == null) && BackupApplication.areSettingsOkayToRunBackup());
				}
			}
		});
	}

	private void updateCheckButton() {
		if (checkButton != null) {
			Utils.runAsync(shell.getDisplay(), new Runnable() {
				@Override
				public void run() {
					if (!checkButton.isDisposed()) {
						checkButton.setEnabled((backupRun == null) && BackupApplication.areSettingsOkayToRunBackup());
					}
				}
			});
		}
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
		shell.setMinimized(false);
		shell.setActive();
	}

	void setVisible(boolean visible) {
		shell.setVisible(visible);
	}

	void setBackupRun(BackupRun backupRun) {
		this.backupRun = backupRun;
		backupRun.addListener(backupRunListener);
		updateRestoreButton();
		updateBackupNowButton();
		updateCheckButton();
		updateProgressVisibility();
	}
	
	private void updateProgressVisibility() {
		Utils.runAsync(shell.getDisplay(), new Runnable() {
			@Override
			public void run() {
				if (!progressComposite.isDisposed()) {
					progressComposite.setVisible(backupRun != null);
				}
			}
		});
	}

	private void showErrors() {
		List<BackupError> errors;
		synchronized (backupErrors) {
			errors = new ArrayList<>(backupErrors);
		}
		ErrorsDialog dlg = new ErrorsDialog(shell, errors);
		dlg.open();
		if (dlg.isClearErrors()) {
			synchronized (backupErrors) {
				backupErrors.clear();
			}
			BackupApplication.resetTrayIconErrors();
			if (backupRun == null) {
				updateStatusLabel(null);
			}
		}
	}
	
	Shell getShell() {
		return shell;
	}
}
