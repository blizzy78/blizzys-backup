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
package de.blizzy.backup.restore;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.IOpenListener;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.OpenEvent;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableLayout;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.jooq.Cursor;
import org.jooq.Record;

import de.blizzy.backup.BackupApplication;
import de.blizzy.backup.BackupPlugin;
import de.blizzy.backup.Compression;
import de.blizzy.backup.FileAttributes;
import de.blizzy.backup.Messages;
import de.blizzy.backup.Utils;
import de.blizzy.backup.database.Database;
import de.blizzy.backup.database.EntryType;
import de.blizzy.backup.database.schema.tables.Backups;
import de.blizzy.backup.database.schema.tables.Entries;
import de.blizzy.backup.database.schema.tables.records.BackupsRecord;
import de.blizzy.backup.settings.Settings;
import de.blizzy.backup.vfs.filesystem.FileSystemFileOrFolder;

public class RestoreDialog extends Dialog {
	private Settings settings;
	private List<Backup> backups = new ArrayList<Backup>();
	private Database database;
	private ComboViewer backupsViewer;
	private TableViewer entriesViewer;
	private Button moveUpButton;
	private Link currentFolderLink;

	public RestoreDialog(Shell parentShell) {
		super(parentShell);

		settings = BackupApplication.getSettingsManager().getSettings();
		database = new Database(settings);
	}
	
	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setImages(BackupApplication.getWindowImages());
		newShell.setText(Messages.Title_Restore);
	}
	
	@Override
	protected boolean isResizable() {
		return true;
	}

	@Override
	public int open() {
		IRunnableWithProgress runnable = new IRunnableWithProgress() {
			public void run(IProgressMonitor monitor) throws InvocationTargetException {
				monitor.beginTask(Messages.Title_OpenBackupDatabase, IProgressMonitor.UNKNOWN);
				Cursor<BackupsRecord> cursor = null;
				try {
					database.open();
					database.initialize();
					
					cursor = database.factory()
						.selectFrom(Backups.BACKUPS).where(Backups.NUM_ENTRIES.isNotNull()).orderBy(Backups.RUN_TIME.desc())
						.fetchLazy();
					while (cursor.hasNext()) {
						BackupsRecord record = cursor.fetchOne();
						Backup backup = new Backup(record.getId().intValue(), new Date(record.getRunTime().getTime()), record.getNumEntries().intValue());
						backups.add(backup);
					}
				} catch (SQLException e) {
					throw new InvocationTargetException(e);
				} finally {
					database.closeQuietly(cursor);
					monitor.done();
				}
			}
		};
		ProgressMonitorDialog dlg = new ProgressMonitorDialog(getParentShell());
		try {
			dlg.run(true, false, runnable);
		} catch (InvocationTargetException e) {
			// TODO
			BackupPlugin.getDefault().logError("Error while opening backup database", e); //$NON-NLS-1$
		} catch (InterruptedException e) {
			// not cancelable
		}
		
		return super.open();
	}
	
	@Override
	public boolean close() {
		IRunnableWithProgress runnable = new IRunnableWithProgress() {
			public void run(IProgressMonitor monitor) throws InvocationTargetException {
				monitor.beginTask(Messages.Title_CloseBackupDatabase, IProgressMonitor.UNKNOWN);
				try {
					database.close();
				} finally {
					monitor.done();
				}
			}
		};
		ProgressMonitorDialog dlg = new ProgressMonitorDialog(getShell());
		try {
			dlg.run(true, false, runnable);
		} catch (InvocationTargetException e) {
			// TODO
			BackupPlugin.getDefault().logError("Error while closing backup database", e); //$NON-NLS-1$
		} catch (InterruptedException e) {
			// not cancelable
		}
		
		System.gc();
		
		return super.close();
	}
	
	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CLOSE_LABEL, false);
	}
	
	@Override
	protected Control createDialogArea(Composite parent) {
		Composite composite = (Composite) super.createDialogArea(parent);
		((GridLayout) composite.getLayout()).numColumns = 2;
		((GridLayout) composite.getLayout()).makeColumnsEqualWidth = false;
		
		Label label = new Label(composite, SWT.NONE);
		label.setText(Messages.Label_ShowBackupContentsAt + ":"); //$NON-NLS-1$
		label.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		
		backupsViewer = new ComboViewer(composite);
		backupsViewer.getCombo().setVisibleItemCount(10);
		backupsViewer.getControl().setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		
		backupsViewer.setContentProvider(new ArrayContentProvider());
		backupsViewer.setLabelProvider(new BackupLabelProvider());
		backupsViewer.setSorter(new ViewerSorter() {
			@Override
			public int compare(Viewer viewer, Object e1, Object e2) {
				return ((Backup) e2).runTime.compareTo(((Backup) e1).runTime);
			}
		});
		backupsViewer.setInput(backups);
		
		Composite entriesComposite = new Composite(composite, SWT.NONE);
		GridLayout layout = new GridLayout(2, false);
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		entriesComposite.setLayout(layout);
		GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
		gd.horizontalSpan = 2;
		entriesComposite.setLayoutData(gd);
		
		entriesViewer = new TableViewer(entriesComposite, SWT.BORDER | SWT.MULTI | SWT.FULL_SELECTION |
				SWT.V_SCROLL | SWT.H_SCROLL);
		gd = new GridData(SWT.FILL, SWT.FILL, true, true);
		gd.widthHint = convertWidthInCharsToPixels(100);
		gd.heightHint = convertHeightInCharsToPixels(20);
		entriesViewer.getControl().setLayoutData(gd);
		entriesViewer.setContentProvider(new ArrayContentProvider());
		entriesViewer.setLabelProvider(new EntryLabelProvider(parent.getDisplay()));
		entriesViewer.setSorter(new ViewerSorter() {
			@Override
			public int compare(Viewer viewer, Object element1, Object element2) {
				Entry e1 = (Entry) element1;
				Entry e2 = (Entry) element2;
				if ((e1.type == EntryType.FOLDER) && (e2.type != EntryType.FOLDER)) {
					return -1;
				}
				if ((e1.type != EntryType.FOLDER) && (e2.type == EntryType.FOLDER)) {
					return 1;
				}
				return e1.name.compareToIgnoreCase(e2.name);
			}
		});

		Table table = entriesViewer.getTable();
		TableLayout tableLayout = new TableLayout();
		table.setLayout(tableLayout);
		table.setHeaderVisible(true);
		table.setLinesVisible(false);
		
		TableColumn col = new TableColumn(table, SWT.LEFT);
		col.setText(Messages.Label_Name);
		tableLayout.addColumnData(new ColumnWeightData(50, true));
		col = new TableColumn(table, SWT.LEFT);
		col.setText(Messages.Label_Size);
		tableLayout.addColumnData(new ColumnWeightData(20, true));
		col = new TableColumn(table, SWT.LEFT);
		col.setText(Messages.Label_ModificationDate);
		tableLayout.addColumnData(new ColumnWeightData(30, true));

		Composite entriesButtonsComposite = new Composite(entriesComposite, SWT.NONE);
		layout = new GridLayout(1, false);
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		entriesButtonsComposite.setLayout(layout);
		entriesButtonsComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, true));
		
		moveUpButton = new Button(entriesButtonsComposite, SWT.PUSH);
		moveUpButton.setText(Messages.Button_MoveUp);
		moveUpButton.setEnabled(false);
		moveUpButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		
		final Button restoreButton = new Button(entriesButtonsComposite, SWT.PUSH);
		restoreButton.setText(Messages.Button_Restore + "..."); //$NON-NLS-1$
		restoreButton.setEnabled(false);
		restoreButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		
		currentFolderLink = new Link(composite, SWT.NONE);
		gd = new GridData(SWT.FILL, SWT.CENTER, true, false);
		gd.horizontalSpan = 2;
		currentFolderLink.setLayoutData(gd);
		
		backupsViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent e) {
				try {
					Backup backup = (Backup) ((IStructuredSelection) e.getSelection()).getFirstElement();
					showBackup(backup);
				} catch (SQLException ex) {
					BackupPlugin.getDefault().logError("error while showing backup", ex); //$NON-NLS-1$
				}
			}
		});
		
		entriesViewer.addOpenListener(new IOpenListener() {
			public void open(OpenEvent e) {
				IStructuredSelection selection = (IStructuredSelection) e.getSelection();
				if (selection.size() == 1) {
					Entry entry = (Entry) selection.getFirstElement();
					if (entry.type == EntryType.FOLDER) {
						try {
							Backup backup = (Backup) ((IStructuredSelection) backupsViewer.getSelection()).getFirstElement();
							showFolder(backup, entry);
						} catch (SQLException ex) {
							BackupPlugin.getDefault().logError("error while showing folder", ex); //$NON-NLS-1$
						}
					}
				}
			}
		});
		
		entriesViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent e) {
				restoreButton.setEnabled(!e.getSelection().isEmpty());
			}
		});
		
		moveUpButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				try {
					moveUp();
				} catch (SQLException ex) {
					BackupPlugin.getDefault().logError("error while moving up", ex); //$NON-NLS-1$
				}
			}
		});
		
		restoreButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				@SuppressWarnings("unchecked")
				List<Entry> entries = ((IStructuredSelection) entriesViewer.getSelection()).toList();
				restore(entries);
			}
		});
		
		currentFolderLink.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				try {
					Backup backup = (Backup) ((IStructuredSelection) backupsViewer.getSelection()).getFirstElement();
					int pos = e.text.indexOf('_');
					int folderId = Integer.parseInt(e.text.substring(0, pos));
					int parentFolderId = Integer.parseInt(e.text.substring(pos + 1));
					showFolder(backup, folderId, parentFolderId);
				} catch (SQLException ex) {
					BackupPlugin.getDefault().logError("error while showing folder", ex); //$NON-NLS-1$
				}
			}
		});
		
		if (!backups.isEmpty()) {
			backupsViewer.setSelection(new StructuredSelection(backups.get(0)), true);
		}
		
		return composite;
	}

	protected void showBackup(Backup backup) throws SQLException {
		showEntries(backup, -1);
		moveUpButton.setEnabled(false);
	}

	private void showFolder(Backup backup, Entry entry) throws SQLException {
		showFolder(backup, entry.id, entry.parentId);
	}

	private void showFolder(Backup backup, int entryId, int parentFolderId) throws SQLException {
		showEntries(backup, entryId);
		moveUpButton.setEnabled(true);
		moveUpButton.setData((parentFolderId > 0) ? Integer.valueOf(parentFolderId) : backup);
	}

	private void moveUp() throws SQLException {
		Object data = moveUpButton.getData();
		if (data instanceof Backup) {
			showBackup((Backup) data);
		} else {
			int folderId = ((Integer) moveUpButton.getData()).intValue();
			Backup backup = (Backup) ((IStructuredSelection) backupsViewer.getSelection()).getFirstElement();
			try {
				Integer parentIdInt = database.factory()
					.select(Entries.PARENT_ID).from(Entries.ENTRIES).where(Entries.ID.equal(Integer.valueOf(folderId)))
					.fetchOne(Entries.PARENT_ID);
				showEntries(backup, folderId);
				moveUpButton.setEnabled(true);
				moveUpButton.setData((parentIdInt != null) ? parentIdInt : backup);
			} catch (SQLException e) {
				BackupPlugin.getDefault().logError("error while getting parent ID", e); //$NON-NLS-1$
			}
		}
	}

	private void showEntries(Backup backup, int parentFolderId) throws SQLException {
		List<Entry> entries = Collections.emptyList();
		Cursor<Record> cursor = null;
		try {
			cursor = getEntriesCursor(backup.id, parentFolderId);
			entries = getEntries(cursor);
		} catch (SQLException e) {
			BackupPlugin.getDefault().logError("error while loading entries", e); //$NON-NLS-1$
		} finally {
			database.closeQuietly(cursor);
		}
		
		entriesViewer.setInput(entries);
		entriesViewer.getControl().setData(Integer.valueOf(parentFolderId));
		
		String folder = getFolderLink(backup, parentFolderId);
		currentFolderLink.setText(StringUtils.isNotBlank(folder) ?
				Messages.Label_CurrentFolder + ": " + getFolderLink(backup, parentFolderId) : //$NON-NLS-1$
				StringUtils.EMPTY);
	}

	private String getFolderLink(Backup backup, int folderId) throws SQLException {
		if (folderId <= 0) {
			return null;
		}
		
		Record record = database.factory()
			.select(Entries.NAME, Entries.PARENT_ID)
			.from(Entries.ENTRIES)
			.where(Entries.ID.equal(Integer.valueOf(folderId)))
			.fetchOne();
		String name = record.getValue(Entries.NAME);
		Integer parentFolderId = record.getValueAsInteger(Entries.PARENT_ID);
		String parentFolder = getFolderLink(backup, (parentFolderId != null) ? parentFolderId.intValue() : -1);
		String part = "<a href=\"" + folderId + "_" + ((parentFolderId != null) ? parentFolderId.intValue() : -1) + //$NON-NLS-1$ //$NON-NLS-2$
			"\">" + name + "</a>"; //$NON-NLS-1$ //$NON-NLS-2$
		return (parentFolder != null) ? parentFolder + File.separator + part : part;
	}

	private Cursor<Record> getEntriesCursor(int backupId, int parentFolderId) throws SQLException {
		return database.factory()
			.select(Entries.ID, Entries.PARENT_ID, Entries.NAME, Entries.TYPE, Entries.CREATION_TIME, Entries.MODIFICATION_TIME,
					Entries.HIDDEN, de.blizzy.backup.database.schema.tables.Files.LENGTH,
					de.blizzy.backup.database.schema.tables.Files.BACKUP_PATH,
					de.blizzy.backup.database.schema.tables.Files.COMPRESSION)
			.from(Entries.ENTRIES)
			.leftOuterJoin(de.blizzy.backup.database.schema.tables.Files.FILES)
				.on(de.blizzy.backup.database.schema.tables.Files.ID.equal(Entries.FILE_ID))
			.where(Entries.BACKUP_ID.equal(Integer.valueOf(backupId)),
					(parentFolderId > 0) ? Entries.PARENT_ID.equal(Integer.valueOf(parentFolderId)) :
						Entries.PARENT_ID.isNull())
			.orderBy(Entries.NAME)
			.fetchLazy();
	}
	
	private List<Entry> getEntries(Cursor<Record> cursor) throws SQLException {
		List<Entry> entries = new ArrayList<Entry>();
		while (cursor.hasNext()) {
			Record record = cursor.fetchOne();
			int id = record.getValueAsInteger(Entries.ID).intValue();
			Integer parentIdInt = record.getValueAsInteger(Entries.PARENT_ID);
			int parentId = (parentIdInt != null) ? parentIdInt.intValue() : -1;
			String name = record.getValueAsString(Entries.NAME);
			EntryType type = EntryType.fromValue(record.getValueAsInteger(Entries.TYPE).intValue());
			Timestamp createTime = record.getValueAsTimestamp(Entries.CREATION_TIME);
			Date creationTime = (createTime != null) ? new Date(createTime.getTime()) : null;
			Timestamp modTime = record.getValueAsTimestamp(Entries.MODIFICATION_TIME);
			Date modificationTime = (modTime != null) ? new Date(modTime.getTime()) : null;
			boolean hidden = record.getValueAsBoolean(Entries.HIDDEN).booleanValue();
			Long lengthLong = record.getValueAsLong(de.blizzy.backup.database.schema.tables.Files.LENGTH);
			long length = (lengthLong != null) ? lengthLong.longValue() : -1;
			String backupPath = record.getValueAsString(de.blizzy.backup.database.schema.tables.Files.BACKUP_PATH);
			Integer compressionInt = record.getValueAsInteger(de.blizzy.backup.database.schema.tables.Files.COMPRESSION);
			Compression compression = (compressionInt != null) ? Compression.fromValue(compressionInt.intValue()) : null;
			Entry entry = new Entry(id, parentId, name, type, creationTime, modificationTime, hidden, length, backupPath,
					compression);
			entries.add(entry);
		}
		return entries;
	}
	
	private void restore(final Collection<Entry> entries) {
		String folder = null;
		for (;;) {
			DirectoryDialog dlg = new DirectoryDialog(getShell(), SWT.SAVE);
			dlg.setText(Messages.Title_SelectOutputFolder);
			dlg.setFilterPath(folder);
			folder = dlg.open();
			if (folder == null) {
				break;
			}
			
			if (new File(folder).list().length > 0) {
				MessageDialog.openError(getShell(), Messages.Title_FolderNotEmpty,
						NLS.bind(Messages.FolderNotEmpty,
								Utils.getSimpleName(new FileSystemFileOrFolder(new File(folder)))));
				continue;
			}

			break;
		}
		
		if (folder != null) {
			final String myFolder = folder;
			Backup backup = (Backup) ((IStructuredSelection) backupsViewer.getSelection()).getFirstElement();
			final int backupId = backup.id;
			final int numEntries = backup.numEntries;
			IRunnableWithProgress runnable = new IRunnableWithProgress() {
				public void run(IProgressMonitor monitor) throws InvocationTargetException,
						InterruptedException {

					try {
						monitor.beginTask(Messages.Title_RestoreFromBackup, numEntries);
						for (Entry entry : entries) {
							restoreEntry(entry, new File(myFolder), settings.getOutputFolder(), backupId, monitor);
						}
					} catch (IOException e) {
						throw new InvocationTargetException(e);
					} catch (SQLException e) {
						throw new InvocationTargetException(e);
					} finally {
						monitor.done();
					}
				}
			};
			ProgressMonitorDialog dlg = new ProgressMonitorDialog(getShell());
			try {
				dlg.run(true, true, runnable);
			} catch (InvocationTargetException e) {
				// TODO
				BackupPlugin.getDefault().logError("Error while restoring from backup", e.getCause()); //$NON-NLS-1$
			} catch (InterruptedException e) {
				// okay
			}
		}
	}

	private void restoreEntry(Entry entry, File parentFolder, String outputFolder, int backupId, IProgressMonitor monitor)
		throws IOException, SQLException, InterruptedException {

		if (monitor.isCanceled()) {
			throw new InterruptedException();
		}
		
		Path outputPath;
		if (entry.type == EntryType.FOLDER) {
			File newFolder = new File(parentFolder, escapeFileName(entry.name));
			FileUtils.forceMkdir(newFolder);
			
			Cursor<Record> cursor = getEntriesCursor(backupId, entry.id);
			try {
				for (Entry e : getEntries(cursor)) {
					restoreEntry(e, newFolder, outputFolder, backupId, monitor);
				}
			} finally {
				database.closeQuietly(cursor);
			}

			outputPath = newFolder.toPath();
		} else {
			File inputFile = Utils.toBackupFile(entry.backupPath, outputFolder);
			File outputFile = new File(parentFolder, escapeFileName(entry.name));
			outputPath = outputFile.toPath();
			InputStream in = null;
			try {
				in = entry.compression.getInputStream(new BufferedInputStream(new FileInputStream(inputFile)));
				Files.copy(in, outputPath);
			} finally {
				IOUtils.closeQuietly(in);
			}
		}

		FileAttributes fileAttributes = FileAttributes.get(outputPath);
		if (entry.hidden) {
			fileAttributes.setHidden(entry.hidden);
		}
		FileTime createTime = (entry.creationTime != null) ? FileTime.fromMillis(entry.creationTime.getTime()) : null;
		FileTime modTime = (entry.modificationTime != null) ? FileTime.fromMillis(entry.modificationTime.getTime()) : null;
		fileAttributes.setTimes(createTime, modTime);
		
		if (entry.type != EntryType.FOLDER) {
			monitor.worked(1);
		}
	}

	private String escapeFileName(String name) {
		return name
			.replace('/', '_')
			.replace('\\', '_')
			.replace(':', '_')
			.replace(';', '_')
			.replaceAll("__", "_"); //$NON-NLS-1$ //$NON-NLS-2$
	}
}
