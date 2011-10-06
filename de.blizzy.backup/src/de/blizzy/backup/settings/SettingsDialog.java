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
import java.text.DateFormat;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
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
import de.blizzy.backup.BackupPlugin;
import de.blizzy.backup.Messages;
import de.blizzy.backup.Utils;
import de.blizzy.backup.vfs.ILocation;
import de.blizzy.backup.vfs.ILocationProvider;
import de.blizzy.backup.vfs.LocationProviderDescriptor;
import de.blizzy.backup.vfs.filesystem.FileSystemFileOrFolder;
import de.blizzy.backup.vfs.filesystem.FileSystemLocationProvider;

public class SettingsDialog extends Dialog {
	private ListViewer foldersViewer;
	private Text outputFolderText;
	private Button runHourlyRadio;
	private DateTime dailyTime;
	private Button fileCompareMetadataRadio;
	private Button fileCompareChecksumRadio;
	private Label scheduleExplanationLabel;

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
		foldersViewer.setLabelProvider(new FoldersLabelProvider());
		foldersViewer.setSorter(new ViewerSorter() {
			@Override
			public int compare(Viewer viewer, Object e1, Object e2) {
				return ((ILocation) e1).getDisplayName().compareToIgnoreCase(((ILocation) e2).getDisplayName());
			}
		});
		GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
		gd.widthHint = convertWidthInCharsToPixels(60);
		gd.heightHint = convertHeightInCharsToPixels(10);
		foldersViewer.getControl().setLayoutData(gd);
		foldersViewer.setInput(new HashSet<>(settings.getLocations()));
		
		Composite folderButtonsComposite = new Composite(foldersComposite, SWT.NONE);
		GridLayout layout = new GridLayout(1, false);
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		folderButtonsComposite.setLayout(layout);
		folderButtonsComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false));

		for (final LocationProviderDescriptor descriptor : BackupPlugin.getDefault().getLocationProviders()) {
			Button button = new Button(folderButtonsComposite, SWT.PUSH);
			button.setText(NLS.bind(Messages.Button_AddX, descriptor.getName()));
			button.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			button.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					addFolder(descriptor.getLocationProvider());
				}
			});
		}
		
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
		
		Group fileComparisonComposite = new Group(composite, SWT.NONE);
		fileComparisonComposite.setText(Messages.Title_FileComparison);
		fileComparisonComposite.setLayout(new GridLayout(1, false));
		fileComparisonComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		
		fileCompareMetadataRadio = new Button(fileComparisonComposite, SWT.RADIO);
		fileCompareMetadataRadio.setText(Messages.CompareFilesMetadata);
		fileCompareMetadataRadio.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

		fileCompareChecksumRadio = new Button(fileComparisonComposite, SWT.RADIO);
		fileCompareChecksumRadio.setText(Messages.CompareFilesChecksum);
		fileCompareChecksumRadio.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

		fileCompareMetadataRadio.setSelection(!settings.isUseChecksums());
		fileCompareChecksumRadio.setSelection(settings.isUseChecksums());
		
		Group scheduleExplanationComposite = new Group(composite, SWT.NONE);
		scheduleExplanationComposite.setText(Messages.Title_ScheduleExplanation);
		scheduleExplanationComposite.setLayout(new GridLayout(1, false));
		scheduleExplanationComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

		scheduleExplanationLabel = new Label(scheduleExplanationComposite, SWT.NONE);
		scheduleExplanationLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		updateExplanationLabel();

		foldersViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			@Override
			public void selectionChanged(SelectionChangedEvent e) {
				removeFolderButton.setEnabled(!e.getSelection().isEmpty());
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
				updateExplanationLabel();
			}
		});
		
		dailyTime.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				updateExplanationLabel();
			}
		});
		
		DropTarget dropTarget = new DropTarget(foldersViewer.getControl(), DND.DROP_LINK);
		dropTarget.setTransfer(new Transfer[] { FileTransfer.getInstance() });
		dropTarget.addDropListener(new DropTargetListener() {
			@Override
			public void dragEnter(DropTargetEvent event) {
				if (FileTransfer.getInstance().isSupportedType(event.currentDataType)) {
					event.detail = DND.DROP_LINK;
					event.feedback = DND.FEEDBACK_SCROLL;
				} else {
					event.detail = DND.DROP_NONE;
				}
			}

			@Override
			public void dragLeave(DropTargetEvent event) {
			}

			@Override
			public void dragOver(DropTargetEvent event) {
				if (FileTransfer.getInstance().isSupportedType(event.currentDataType)) {
					event.detail = DND.DROP_LINK;
					event.feedback = DND.FEEDBACK_SCROLL;
				} else {
					event.detail = DND.DROP_NONE;
				}
			}

			@Override
			public void dropAccept(DropTargetEvent event) {
				if (FileTransfer.getInstance().isSupportedType(event.currentDataType)) {
					event.detail = DND.DROP_LINK;
					event.feedback = DND.FEEDBACK_SCROLL;
				} else {
					event.detail = DND.DROP_NONE;
				}
			}
			
			@Override
			public void drop(DropTargetEvent event) {
				if (FileTransfer.getInstance().isSupportedType(event.currentDataType)) {
					if (event.data != null) {
						for (String file : (String[]) event.data) {
							if (new File(file).isDirectory()) {
								addFolder(FileSystemLocationProvider.location(Utils.toCanonicalFile(new File(file))));
							}
						}
					} else {
						event.detail = DND.DROP_NONE;
					}
				} else {
					event.detail = DND.DROP_NONE;
				}
			}
			
			@Override
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

	private void addFolder(ILocationProvider provider) {
		ILocation newLocation = provider.promptLocation(getShell());
		if (newLocation != null) {
			addFolder(newLocation);
		}
	}
	
	private void addFolder(ILocation location) {
		@SuppressWarnings("unchecked")
		Set<ILocation> locations = (Set<ILocation>) foldersViewer.getInput();

		// is the new folder a child of any folder in the backup? if so, display error message
		for (ILocation oldLocation : locations) {
			if (Utils.isParent(oldLocation.getRootFolder(), location.getRootFolder())) {
				MessageDialog.openError(getShell(), Messages.Title_FolderCannotBeAdded,
						NLS.bind(Messages.ParentFolderInBackup, Utils.getSimpleName(location.getRootFolder())));
				return;
			}
		}
		
		// is the new folder the parent of the output folder? if so, display error message
		String outputFolder = StringUtils.defaultString(outputFolderText.getText());
		if (StringUtils.isNotBlank(outputFolder) &&
			Utils.isParent(location.getRootFolder(), new FileSystemFileOrFolder(new File(outputFolder)))) {

			MessageDialog.openError(getShell(), Messages.Title_FolderCannotBeAdded,
					NLS.bind(Messages.FolderIsParentOfBackupFolder, Utils.getSimpleName(location.getRootFolder())));
			return;
		}
		
		// is the new folder the same as the output folder? if so, display error message
		if (StringUtils.isNotBlank(outputFolder) &&
			location.getRootFolder().equals(new FileSystemFileOrFolder(new File(outputFolder)))) {
			
			MessageDialog.openError(getShell(), Messages.Title_FolderCannotBeAdded,
					NLS.bind(Messages.FolderIsOutputFolder, Utils.getSimpleName(location.getRootFolder())));
			return;
		}
		
		// is the new folder a child of the output folder? if so, display error message
		if (StringUtils.isNotBlank(outputFolder) &&
			Utils.isParent(new FileSystemFileOrFolder(new File(outputFolder)), location.getRootFolder())) {

			MessageDialog.openError(getShell(), Messages.Title_FolderCannotBeAdded,
					NLS.bind(Messages.FolderIsChildOfOutputFolder, Utils.getSimpleName(location.getRootFolder())));
			return;
		}
		
		// is the new folder the parent of any folder in the backup? if so, remove those folders
		for (ILocation oldLocation : new HashSet<>(locations)) {
			if (Utils.isParent(location.getRootFolder(), oldLocation.getRootFolder())) {
				locations.remove(oldLocation);
				foldersViewer.remove(oldLocation);
			}
		}
		
		if (locations.add(location)) {
			foldersViewer.add(location);
		}
	}

	private void removeFolder() {
		@SuppressWarnings("unchecked")
		List<String> selectedFolders = ((IStructuredSelection) foldersViewer.getSelection()).toList();
		@SuppressWarnings("unchecked")
		Set<ILocation> folders = (Set<ILocation>) foldersViewer.getInput();
		folders.removeAll(selectedFolders);
		foldersViewer.remove(selectedFolders.toArray(new ILocation[0]));
	}

	private void browseOutputFolder() {
		@SuppressWarnings("unchecked")
		Set<ILocation> locations = (Set<ILocation>) foldersViewer.getInput();
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
						NLS.bind(Messages.FolderContainsExistingBackup,
								Utils.getSimpleName(new FileSystemFileOrFolder(new File(folder)))))) {

					break;
				} else {
					continue;
				}
			}

			// does folder contain files? if so, display error message
			if (new File(folder).list().length > 0) {
				MessageDialog.openError(getShell(), Messages.Title_InvalidFolder,
						NLS.bind(Messages.FolderNotEmpty,
								Utils.getSimpleName(new FileSystemFileOrFolder(new File(folder)))));
				continue;
			}

			// display error message if:
			// - folder is the same as any folder in the backup
			// - folder is a child of any folder in the backup
			for (ILocation oldLocation : locations) {
				if (new FileSystemFileOrFolder(new File(folder)).equals(oldLocation.getRootFolder()) ||
					Utils.isParent(oldLocation.getRootFolder(), new FileSystemFileOrFolder(new File(folder)))) {

					MessageDialog.openError(getShell(), Messages.Title_InvalidFolder,
							NLS.bind(Messages.OutputFolderIsInBackup,
									Utils.getSimpleName(new FileSystemFileOrFolder(new File(folder)))));
					continue dialogLoop;
				}
			}
			
			break;
		}
		if (folder != null) {
			outputFolderText.setText(folder);
		}
	}
	
	private void updateExplanationLabel() {
		DateFormat timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT);
		Calendar c = Calendar.getInstance();
		c.set(Calendar.HOUR_OF_DAY, dailyTime.getHours());
		c.set(Calendar.MINUTE, dailyTime.getMinutes());
		scheduleExplanationLabel.setText(
				"- " + (runHourlyRadio.getSelection() ? //$NON-NLS-1$
						Messages.ScheduleExplanation_HourlyBackups :
						NLS.bind(Messages.ScheduleExplanation_DailyBackups, timeFormat.format(c.getTime()))) +
				"\n" + //$NON-NLS-1$
				(runHourlyRadio.getSelection() ?
						"- " + //$NON-NLS-1$
							NLS.bind(Messages.ScheduleExplanation_HourlyBackupsKeepTime, Integer.valueOf(BackupPlugin.KEEP_HOURLIES_DAYS)) +
							"\n" : //$NON-NLS-1$
						"") + //$NON-NLS-1$
				"- " + //$NON-NLS-1$
				NLS.bind(Messages.ScheduleExplanation_DailyBackupsKeepTime, Integer.valueOf(BackupPlugin.KEEP_DAILIES_DAYS)) +
				"\n" + //$NON-NLS-1$
				"- " + //$NON-NLS-1$
				NLS.bind(Messages.ScheduleExplanation_WeeklyBackupsKeepDisk, Integer.valueOf(BackupPlugin.MAX_DISK_FILL_RATE)) +
				// compensate for missing line
				(!runHourlyRadio.getSelection() ? "\n " : "")); //$NON-NLS-1$ //$NON-NLS-2$
	}
	
	@Override
	protected void buttonPressed(int buttonId) {
		if (buttonId == IDialogConstants.OK_ID) {
			@SuppressWarnings("unchecked")
			Set<ILocation> folders = (Set<ILocation>) foldersViewer.getInput();
			String outputFolder = outputFolderText.getText();
			if (StringUtils.isBlank(outputFolder)) {
				outputFolder = null;
			}
			Settings settings = new Settings(folders, outputFolder, runHourlyRadio.getSelection(),
					dailyTime.getHours(), dailyTime.getMinutes(), fileCompareChecksumRadio.getSelection());
			BackupApplication.getSettingsManager().setSettings(settings);
		}
		
		super.buttonPressed(buttonId);
	}
}
