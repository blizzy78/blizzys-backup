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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ListViewer;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.DropTargetListener;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DateTime;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import de.blizzy.backup.BackupApplication;
import de.blizzy.backup.Messages;
import de.blizzy.backup.Utils;

public class SettingsDialog extends Dialog {
	private ListViewer foldersViewer;
	private Text outputFolderText;
	private Button runHourlyRadio;
	private DateTime dailyTime;

	public SettingsDialog(Shell parentShell) {
		super(parentShell);
	}
	
	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setImages(BackupApplication.getWindowImages());
		newShell.setText(Messages.Title_Settings);
	}
	
	@Override
	protected boolean isResizable() {
		return true;
	}
	
	@Override
	protected Control createDialogArea(Composite parent) {
		Settings settings = BackupApplication.getSettingsManager().getSettings();

		Composite composite = (Composite) super.createDialogArea(parent);
		((GridLayout) composite.getLayout()).numColumns = 1;
		((GridLayout) composite.getLayout()).verticalSpacing = 10;

		Group foldersComposite = new Group(composite, SWT.NONE);
		foldersComposite.setText(Messages.Title_FoldersToBackup);
		foldersComposite.setLayout(new GridLayout(2, false));
		foldersComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		
		foldersViewer = new ListViewer(foldersComposite, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		foldersViewer.setContentProvider(new ArrayContentProvider());
		foldersViewer.setSorter(new ViewerSorter() {
			@Override
			public int compare(Viewer viewer, Object e1, Object e2) {
				return ((String) e1).compareToIgnoreCase((String) e2);
			}
		});
		GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
		gd.widthHint = convertWidthInCharsToPixels(60);
		gd.heightHint = convertHeightInCharsToPixels(10);
		foldersViewer.getControl().setLayoutData(gd);
		foldersViewer.setInput(new HashSet<String>(settings.getFolders()));
		
		Composite folderButtonsComposite = new Composite(foldersComposite, SWT.NONE);
		GridLayout layout = new GridLayout(1, false);
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		folderButtonsComposite.setLayout(layout);
		folderButtonsComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false));

		Button addFolderButton = new Button(folderButtonsComposite, SWT.PUSH);
		addFolderButton.setText(Messages.Button_Add);
		addFolderButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		
		final Button removeFolderButton = new Button(folderButtonsComposite, SWT.PUSH);
		removeFolderButton.setText(Messages.Button_Remove);
		removeFolderButton.setEnabled(false);
		removeFolderButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		
		Label label = new Label(foldersComposite, SWT.NONE);
		label.setText(Messages.DropFoldersHelp);
		gd = new GridData(SWT.LEFT, SWT.CENTER, false, false);
		gd.horizontalSpan = 2;
		label.setLayoutData(gd);
		
		Group outputFolderComposite = new Group(composite, SWT.NONE);
		outputFolderComposite.setText(Messages.Title_OutputFolder);
		outputFolderComposite.setLayout(new GridLayout(3, false));
		outputFolderComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

		label = new Label(outputFolderComposite, SWT.NONE);
		label.setText(Messages.Label_BackupOutputFolder + ":"); //$NON-NLS-1$
		label.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

		outputFolderText = new Text(outputFolderComposite, SWT.BORDER | SWT.READ_ONLY);
		outputFolderText.setText(StringUtils.defaultString(settings.getOutputFolder()));
		outputFolderText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		
		Button browseOutputFolderButton = new Button(outputFolderComposite, SWT.PUSH);
		browseOutputFolderButton.setText(Messages.Button_Browse + "..."); //$NON-NLS-1$
		browseOutputFolderButton.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
		
		Group scheduleComposite = new Group(composite, SWT.NONE);
		scheduleComposite.setText(Messages.Title_WhenToBackup);
		scheduleComposite.setLayout(new GridLayout(2, false));
		scheduleComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

		runHourlyRadio = new Button(scheduleComposite, SWT.RADIO);
		runHourlyRadio.setText(Messages.Label_RunHourly);
		gd = new GridData(SWT.LEFT, SWT.CENTER, false, false);
		gd.horizontalSpan = 2;
		runHourlyRadio.setLayoutData(gd);

		final Button runDailyRadio = new Button(scheduleComposite, SWT.RADIO);
		runDailyRadio.setText(Messages.Label_RunDaily + ":"); //$NON-NLS-1$
		runDailyRadio.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		
		dailyTime = new DateTime(scheduleComposite, SWT.TIME | SWT.SHORT);

		runHourlyRadio.setSelection(settings.isRunHourly());
		runDailyRadio.setSelection(!settings.isRunHourly());
		dailyTime.setHours(settings.getDailyHours());
		dailyTime.setMinutes(settings.getDailyMinutes());
		dailyTime.setEnabled(!settings.isRunHourly());
		
		foldersViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent e) {
				removeFolderButton.setEnabled(!e.getSelection().isEmpty());
			}
		});
		
		addFolderButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				addFolder();
			}
		});
		
		removeFolderButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				removeFolder();
			}
		});
		
		browseOutputFolderButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				browseOutputFolder();
			}
		});
		
		runDailyRadio.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				dailyTime.setEnabled(runDailyRadio.getSelection());
			}
		});
		
		DropTarget dropTarget = new DropTarget(foldersViewer.getControl(), DND.DROP_LINK);
		dropTarget.setTransfer(new Transfer[] { FileTransfer.getInstance() });
		dropTarget.addDropListener(new DropTargetListener() {
			public void dragEnter(DropTargetEvent event) {
				if (FileTransfer.getInstance().isSupportedType(event.currentDataType)) {
					event.detail = DND.DROP_LINK;
					event.feedback = DND.FEEDBACK_SCROLL;
				} else {
					event.detail = DND.DROP_NONE;
				}
			}

			public void dragLeave(DropTargetEvent event) {
			}

			public void dragOver(DropTargetEvent event) {
				if (FileTransfer.getInstance().isSupportedType(event.currentDataType)) {
					event.detail = DND.DROP_LINK;
					event.feedback = DND.FEEDBACK_SCROLL;
				} else {
					event.detail = DND.DROP_NONE;
				}
			}

			public void dropAccept(DropTargetEvent event) {
				if (FileTransfer.getInstance().isSupportedType(event.currentDataType)) {
					event.detail = DND.DROP_LINK;
					event.feedback = DND.FEEDBACK_SCROLL;
				} else {
					event.detail = DND.DROP_NONE;
				}
			}
			
			public void drop(DropTargetEvent event) {
				if (FileTransfer.getInstance().isSupportedType(event.currentDataType)) {
					if (event.data != null) {
						for (String file : (String[]) event.data) {
							if (new File(file).isDirectory()) {
								addFolder(file);
							}
						}
					} else {
						event.detail = DND.DROP_NONE;
					}
				} else {
					event.detail = DND.DROP_NONE;
				}
			}
			
			public void dragOperationChanged(DropTargetEvent event) {
				if (FileTransfer.getInstance().isSupportedType(event.currentDataType)) {
					event.detail = DND.DROP_LINK;
					event.feedback = DND.FEEDBACK_SCROLL;
				} else {
					event.detail = DND.DROP_NONE;
				}
			}
		});
		
		return composite;
	}

	private void addFolder() {
		DirectoryDialog dlg = new DirectoryDialog(getShell(), SWT.OPEN);
		dlg.setText(Messages.Title_SelectFolder);
		String folder = dlg.open();
		if (folder != null) {
			addFolder(folder);
		}
	}

	private void addFolder(String folder) {
		@SuppressWarnings("unchecked")
		Set<String> folders = (Set<String>) foldersViewer.getInput();

		// is the new folder a child of any folder in the backup? if so, display error message
		for (String oldFolder : folders) {
			if (Utils.isParent(new File(oldFolder), new File(folder))) {
				MessageDialog.openError(getShell(), Messages.Title_FolderCannotBeAdded,
						NLS.bind(Messages.ParentFolderInBackup, Utils.getSimpleName(new File(folder))));
				return;
			}
		}
		
		// is the new folder the parent of the output folder? if so, display error message
		String outputFolder = StringUtils.defaultString(outputFolderText.getText());
		if (StringUtils.isNotBlank(outputFolder) && Utils.isParent(new File(folder), new File(outputFolder))) {
			MessageDialog.openError(getShell(), Messages.Title_FolderCannotBeAdded,
					NLS.bind(Messages.FolderIsParentOfBackupFolder, Utils.getSimpleName(new File(folder))));
			return;
		}
		
		// is the new folder the same as the output folder? if so, display error message
		if (StringUtils.isNotBlank(outputFolder) && new File(folder).equals(new File(outputFolder))) {
			MessageDialog.openError(getShell(), Messages.Title_FolderCannotBeAdded,
					NLS.bind(Messages.FolderIsOutputFolder, Utils.getSimpleName(new File(folder))));
			return;
		}
		
		// is the new folder a child of the output folder? if so, display error message
		if (StringUtils.isNotBlank(outputFolder) && Utils.isParent(new File(outputFolder), new File(folder))) {
			MessageDialog.openError(getShell(), Messages.Title_FolderCannotBeAdded,
					NLS.bind(Messages.FolderIsChildOfOutputFolder, Utils.getSimpleName(new File(folder))));
			return;
		}
		
		// is the new folder the parent of any folder in the backup? if so, remove those folders
		for (String oldFolder : new HashSet<String>(folders)) {
			if (Utils.isParent(new File(folder), new File(oldFolder))) {
				folders.remove(oldFolder);
				foldersViewer.remove(oldFolder);
			}
		}
		
		if (folders.add(folder)) {
			foldersViewer.add(folder);
		}
	}

	private void removeFolder() {
		@SuppressWarnings("unchecked")
		List<String> selectedFolders = ((IStructuredSelection) foldersViewer.getSelection()).toList();
		@SuppressWarnings("unchecked")
		Set<String> folders = (Set<String>) foldersViewer.getInput();
		folders.removeAll(selectedFolders);
		foldersViewer.remove(selectedFolders.toArray(ArrayUtils.EMPTY_STRING_ARRAY));
	}

	private void browseOutputFolder() {
		@SuppressWarnings("unchecked")
		Set<String> folders = (Set<String>) foldersViewer.getInput();
		String folder = outputFolderText.getText();
		if (StringUtils.isEmpty(folder)) {
			folder = null;
		}
		dialogLoop: for (;;) {
			DirectoryDialog dlg = new DirectoryDialog(getShell(), SWT.SAVE);
			dlg.setText(Messages.Title_SelectOutputFolder);
			dlg.setFilterPath(folder);
			folder = dlg.open();
			if (folder == null) {
				break;
			}
			
			if (Utils.isBackupFolder(folder)) {
				if (MessageDialog.openConfirm(getShell(), Messages.Title_ExistingBackup,
						NLS.bind(Messages.FolderContainsExistingBackup, Utils.getSimpleName(new File(folder))))) {

					break;
				} else {
					continue;
				}
			}

			// does folder contain files? if so, display error message
			if (new File(folder).list().length > 0) {
				MessageDialog.openError(getShell(), Messages.Title_InvalidFolder,
						NLS.bind(Messages.FolderNotEmpty, Utils.getSimpleName(new File(folder))));
				continue;
			}

			// display error message if:
			// - folder is the same as any folder in the backup
			// - folder is a child of any folder in the backup
			for (String oldFolder : folders) {
				// display error
				if (new File(folder).equals(new File(oldFolder)) ||
					Utils.isParent(new File(oldFolder), new File(folder))) {

					MessageDialog.openError(getShell(), Messages.Title_InvalidFolder,
							NLS.bind(Messages.OutputFolderIsInBackup, Utils.getSimpleName(new File(folder))));
					continue dialogLoop;
				}
			}
			
			break;
		}
		if (folder != null) {
			outputFolderText.setText(folder);
		}
	}
	
	@Override
	protected void buttonPressed(int buttonId) {
		if (buttonId == IDialogConstants.OK_ID) {
			@SuppressWarnings("unchecked")
			Set<String> folders = (Set<String>) foldersViewer.getInput();
			String outputFolder = outputFolderText.getText();
			if (StringUtils.isBlank(outputFolder)) {
				outputFolder = null;
			}
			Settings settings = new Settings(folders, outputFolder, runHourlyRadio.getSelection(),
					dailyTime.getHours(), dailyTime.getMinutes());
			BackupApplication.getSettingsManager().setSettings(settings);
		}
		
		super.buttonPressed(buttonId);
	}
}
