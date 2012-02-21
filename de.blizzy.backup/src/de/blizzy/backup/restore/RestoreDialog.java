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
import java.util.Timer;
import java.util.TimerTask;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.ISafeRunnable;
import org.eclipse.core.runtime.SafeRunner;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IDialogSettings;
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
import org.eclipse.jface.window.Window;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;
import org.jooq.Condition;
import org.jooq.Cursor;
import org.jooq.Record;
import org.jooq.exception.DataAccessException;

import de.blizzy.backup.BackupApplication;
import de.blizzy.backup.BackupPlugin;
import de.blizzy.backup.Compression;
import de.blizzy.backup.FileAttributes;
import de.blizzy.backup.IStorageInterceptor;
import de.blizzy.backup.Messages;
import de.blizzy.backup.StorageInterceptorDescriptor;
import de.blizzy.backup.Utils;
import de.blizzy.backup.Utils.IFileOrFolderEntry;
import de.blizzy.backup.database.Database;
import de.blizzy.backup.database.EntryType;
import de.blizzy.backup.database.schema.Tables;
import de.blizzy.backup.database.schema.tables.records.BackupsRecord;
import de.blizzy.backup.settings.Settings;
import de.blizzy.backup.vfs.filesystem.FileSystemFileOrFolder;

public class RestoreDialog extends Dialog {
	private Settings settings;
	private List<Backup> backups = new ArrayList<>();
	private Database database;
	private ComboViewer backupsViewer;
	private Text searchText;
	private boolean listenForSearchText = true;
	private TableViewer entriesViewer;
	private Button moveUpButton;
	private Link currentFolderLink;
	private Timer timer = new Timer();
	private TimerTask searchTimerTask;
	private List<IStorageInterceptor> storageInterceptors = new ArrayList<>();
	private Boolean alwaysRestoreFromOlderBackups;

	public RestoreDialog(Shell parentShell) {
		super(parentShell);

		settings = BackupApplication.getSettingsManager().getSettings();
		database = new Database(settings, false);
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
		final ProgressMonitorDialog dlg = new ProgressMonitorDialog(getParentShell());
		IRunnableWithProgress runnable = new IRunnableWithProgress() {
			@Override
			public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
				monitor.beginTask(Messages.Title_OpenBackupDatabase, IProgressMonitor.UNKNOWN);
				
				final boolean[] ok = { true };
				List<StorageInterceptorDescriptor> descs = BackupPlugin.getDefault().getStorageInterceptors();
				for (final StorageInterceptorDescriptor desc : descs) {
					final IStorageInterceptor interceptor = desc.getStorageInterceptor();
					SafeRunner.run(new ISafeRunnable() {
						@Override
						public void run() throws Exception {
							IDialogSettings settings = Utils.getChildSection(
									Utils.getSection("storageInterceptors"), desc.getId()); //$NON-NLS-1$
							if (!interceptor.initialize(dlg.getShell(), settings)) {
								ok[0] = false;
							}
						}
						
						@Override
						public void handleException(Throwable t) {
							ok[0] = false;
							interceptor.showErrorMessage(t, dlg.getShell());
							BackupPlugin.getDefault().logError(
									"error while initializing storage interceptor '" + desc.getName() + "'", t); //$NON-NLS-1$ //$NON-NLS-2$
						}
					});
					storageInterceptors.add(interceptor);
				}
				
				if (!ok[0]) {
					monitor.done();
					throw new InterruptedException();
				}
				
				Cursor<BackupsRecord> cursor = null;
				try {
					database.open(storageInterceptors);
					database.initialize();
					
					cursor = database.factory()
						.selectFrom(Tables.BACKUPS).where(Tables.BACKUPS.NUM_ENTRIES.isNotNull()).orderBy(Tables.BACKUPS.RUN_TIME.desc())
						.fetchLazy();
					while (cursor.hasNext()) {
						BackupsRecord record = cursor.fetchOne();
						Backup backup = new Backup(record.getId().intValue(), new Date(record.getRunTime().getTime()), record.getNumEntries().intValue());
						backups.add(backup);
					}
				} catch (SQLException | IOException e) {
					boolean handled = false;
					for (IStorageInterceptor interceptor : storageInterceptors) {
						if (interceptor.showErrorMessage(e, dlg.getShell())) {
							handled = true;
						}
					}
					if (handled) {
						throw new InterruptedException();
					}
					throw new InvocationTargetException(e);
				} finally {
					database.closeQuietly(cursor);
					monitor.done();
				}
			}
		};
		try {
			dlg.run(true, false, runnable);
		} catch (InvocationTargetException e) {
			// TODO
			BackupPlugin.getDefault().logError("Error while opening backup database", e); //$NON-NLS-1$
		} catch (InterruptedException e) {
			return Window.CANCEL;
		}
		
		return super.open();
	}
	
	@Override
	public boolean close() {
		IRunnableWithProgress runnable = new IRunnableWithProgress() {
			@Override
			public void run(IProgressMonitor monitor) throws InvocationTargetException {
				monitor.beginTask(Messages.Title_CloseBackupDatabase, IProgressMonitor.UNKNOWN);
				try {
					database.close();
					for (final IStorageInterceptor interceptor : storageInterceptors) {
						SafeRunner.run(new ISafeRunnable() {
							@Override
							public void run() throws Exception {
								interceptor.destroy();
							}
							
							@Override
							public void handleException(Throwable t) {
								BackupPlugin.getDefault().logError("error while destroying storage interceptor", t); //$NON-NLS-1$
							}
						});
					}
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

		Composite backupsAndSearchComposite = new Composite(composite, SWT.NONE);
		GridLayout layout = new GridLayout(2, false);
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		backupsAndSearchComposite.setLayout(layout);
		backupsAndSearchComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		
		Label label = new Label(backupsAndSearchComposite, SWT.NONE);
		label.setText(Messages.Label_ShowBackupContentsAt + ":"); //$NON-NLS-1$
		label.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		
		backupsViewer = new ComboViewer(backupsAndSearchComposite);
		backupsViewer.getCombo().setVisibleItemCount(10);
		backupsViewer.getControl().setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		
		label = new Label(backupsAndSearchComposite, SWT.NONE);
		label.setText(Messages.Label_SearchFileFolder + ":"); //$NON-NLS-1$
		label.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

		searchText = new Text(backupsAndSearchComposite, SWT.BORDER);
		searchText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		
		new Label(composite, SWT.NONE);
		
		backupsViewer.setContentProvider(new ArrayContentProvider());
		backupsViewer.setLabelProvider(new BackupLabelProvider());
		backupsViewer.setSorter(new ViewerSorter() {
			@Override
			public int compare(Viewer viewer, Object e1, Object e2) {
				return ((Backup) e2).runTime.compareTo(((Backup) e1).runTime);
			}
		});
		backupsViewer.setInput(backups);

		entriesViewer = new TableViewer(composite, SWT.BORDER | SWT.MULTI | SWT.FULL_SELECTION |
				SWT.V_SCROLL | SWT.H_SCROLL);
		GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
		gd.widthHint = convertWidthInCharsToPixels(100);
		gd.heightHint = convertHeightInCharsToPixels(20);
		entriesViewer.getControl().setLayoutData(gd);
		entriesViewer.setContentProvider(new ArrayContentProvider());
		entriesViewer.setLabelProvider(new EntryLabelProvider(parent.getDisplay()));
		entriesViewer.setSorter(new EntrySorter());

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

		Composite entriesButtonsComposite = new Composite(composite, SWT.NONE);
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
			@Override
			public void selectionChanged(SelectionChangedEvent e) {
				try {
					Backup backup = (Backup) ((IStructuredSelection) e.getSelection()).getFirstElement();
					showBackup(backup);
				} catch (DataAccessException ex) {
					BackupPlugin.getDefault().logError("error while showing backup", ex); //$NON-NLS-1$
				}
			}
		});
		
		searchText.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				if (listenForSearchText) {
					startSearchTimer(searchText.getText());
				}
			}
		});
		
		entriesViewer.addOpenListener(new IOpenListener() {
			@Override
			public void open(OpenEvent e) {
				IStructuredSelection selection = (IStructuredSelection) e.getSelection();
				if (selection.size() == 1) {
					Entry entry = (Entry) selection.getFirstElement();
					if (entry.type == EntryType.FOLDER) {
						listenForSearchText = false;
						try {
							searchText.setText(StringUtils.EMPTY);
						} finally {
							listenForSearchText = true;
						}
						try {
							Backup backup = (Backup) ((IStructuredSelection) backupsViewer.getSelection()).getFirstElement();
							showFolder(backup, entry);
						} catch (DataAccessException ex) {
							BackupPlugin.getDefault().logError("error while showing folder", ex); //$NON-NLS-1$
						}
					}
				}
			}
		});
		
		entriesViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			@Override
			public void selectionChanged(SelectionChangedEvent e) {
				restoreButton.setEnabled(!e.getSelection().isEmpty());
			}
		});
		
		moveUpButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				try {
					moveUp();
				} catch (DataAccessException ex) {
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
				} catch (DataAccessException ex) {
					BackupPlugin.getDefault().logError("error while showing folder", ex); //$NON-NLS-1$
				}
			}
		});
		
		if (!backups.isEmpty()) {
			backupsViewer.setSelection(new StructuredSelection(backups.get(0)), true);
		}
		
		return composite;
	}

	private void showBackup(Backup backup) {
		showEntries(backup, -1);
		moveUpButton.setEnabled(false);
	}

	private void showFolder(Backup backup, Entry entry) {
		showFolder(backup, entry.id, entry.parentId);
	}

	private void showFolder(Backup backup, int entryId, int parentFolderId) {
		showEntries(backup, entryId);
		moveUpButton.setEnabled(true);
		moveUpButton.setData((parentFolderId > 0) ? Integer.valueOf(parentFolderId) : backup);
	}

	private void moveUp() {
		Object data = moveUpButton.getData();
		if (data instanceof Backup) {
			showBackup((Backup) data);
		} else {
			int folderId = ((Integer) moveUpButton.getData()).intValue();
			Backup backup = (Backup) ((IStructuredSelection) backupsViewer.getSelection()).getFirstElement();
			try {
				Integer parentIdInt = database.factory()
					.select(Tables.ENTRIES.PARENT_ID).from(Tables.ENTRIES).where(Tables.ENTRIES.ID.equal(Integer.valueOf(folderId)))
					.fetchOne(Tables.ENTRIES.PARENT_ID);
				showEntries(backup, folderId);
				moveUpButton.setEnabled(true);
				moveUpButton.setData((parentIdInt != null) ? parentIdInt : backup);
			} catch (DataAccessException e) {
				BackupPlugin.getDefault().logError("error while getting parent ID", e); //$NON-NLS-1$
			}
		}
	}

	private void showEntries(Backup backup, int parentFolderId) {
		List<Entry> entries = Collections.emptyList();
		Cursor<Record> cursor = null;
		try {
			cursor = getEntriesCursor(backup.id, parentFolderId, null, -1);
			entries = getEntries(cursor, false);
		} catch (DataAccessException e) {
			BackupPlugin.getDefault().logError("error while loading entries", e); //$NON-NLS-1$
		} finally {
			database.closeQuietly(cursor);
		}
		
		((EntryLabelProvider) entriesViewer.getLabelProvider()).setShowFullPath(false);
		((EntrySorter) entriesViewer.getSorter()).setSortFullPath(false);
		entriesViewer.setInput(entries);
		entriesViewer.getControl().setData(Integer.valueOf(parentFolderId));
		listenForSearchText = false;
		try {
			searchText.setText(StringUtils.EMPTY);
		} finally {
			listenForSearchText = true;
		}
		
		String folder = getFolderLink(parentFolderId);
		currentFolderLink.setText(StringUtils.isNotBlank(folder) ?
				Messages.Label_CurrentFolder + ": " + getFolderLink(parentFolderId) : //$NON-NLS-1$
				StringUtils.EMPTY);
	}

	private String getFolderLink(int folderId) {
		if (folderId <= 0) {
			return null;
		}
		
		Record record = database.factory()
			.select(Tables.ENTRIES.NAME, Tables.ENTRIES.PARENT_ID)
			.from(Tables.ENTRIES)
			.where(Tables.ENTRIES.ID.equal(Integer.valueOf(folderId)))
			.fetchOne();
		String name = record.getValue(Tables.ENTRIES.NAME);
		Integer parentFolderId = record.getValue(Tables.ENTRIES.PARENT_ID);
		String parentFolder = getFolderLink((parentFolderId != null) ? parentFolderId.intValue() : -1);
		String part = "<a href=\"" + folderId + "_" + ((parentFolderId != null) ? parentFolderId.intValue() : -1) + //$NON-NLS-1$ //$NON-NLS-2$
			"\">" + name + "</a>"; //$NON-NLS-1$ //$NON-NLS-2$
		return (parentFolder != null) ? parentFolder + File.separator + part : part;
	}
	
	private String getFolderPath(int folderId) {
		if (folderId <= 0) {
			return null;
		}
		
		Record record = database.factory()
			.select(Tables.ENTRIES.NAME, Tables.ENTRIES.PARENT_ID)
			.from(Tables.ENTRIES)
			.where(Tables.ENTRIES.ID.equal(Integer.valueOf(folderId)))
			.fetchOne();
		String name = record.getValue(Tables.ENTRIES.NAME);
		Integer parentFolderId = record.getValue(Tables.ENTRIES.PARENT_ID);
		String parentPath = getFolderPath((parentFolderId != null) ? parentFolderId.intValue() : -1);
		return (parentPath != null) ? parentPath + File.separator + name : name;
	}

	private Cursor<Record> getEntriesCursor(int backupId, int parentFolderId, String searchText, int entryId) {
		Condition searchCondition;
		if (entryId > 0) {
			searchCondition = Tables.ENTRIES.ID.equal(Integer.valueOf(entryId));
		} else if (StringUtils.isNotBlank(searchText)) {
			searchCondition = Tables.ENTRIES.NAME_LOWER.like("%" + searchText.toLowerCase() + "%"); //$NON-NLS-1$ //$NON-NLS-2$
		} else if (parentFolderId > 0) {
			searchCondition = Tables.ENTRIES.PARENT_ID.equal(Integer.valueOf(parentFolderId));
		} else {
			searchCondition = Tables.ENTRIES.PARENT_ID.isNull();
		}
		return database.factory()
			.select(Tables.ENTRIES.ID, Tables.ENTRIES.PARENT_ID, Tables.ENTRIES.NAME, Tables.ENTRIES.TYPE,
					Tables.ENTRIES.CREATION_TIME, Tables.ENTRIES.MODIFICATION_TIME, Tables.ENTRIES.HIDDEN,
					Tables.FILES.LENGTH, Tables.FILES.BACKUP_PATH, Tables.FILES.COMPRESSION)
			.from(Tables.ENTRIES)
			.leftOuterJoin(Tables.FILES)
				.on(Tables.FILES.ID.equal(Tables.ENTRIES.FILE_ID))
			.where(Tables.ENTRIES.BACKUP_ID.equal(Integer.valueOf(backupId)),
					searchCondition)
			.orderBy(Tables.ENTRIES.NAME.asc())
			.fetchLazy();
	}
	
	private List<Entry> getEntries(Cursor<Record> cursor, boolean fullPaths) {
		List<Entry> entries = new ArrayList<>();
		while (cursor.hasNext()) {
			Record record = cursor.fetchOne();
			Entry entry = toEntry(record, fullPaths);
			entries.add(entry);
		}
		return entries;
	}

	private Entry toEntry(Record record, boolean fullPaths) {
		int id = record.getValue(Tables.ENTRIES.ID).intValue();
		Integer parentIdInt = record.getValue(Tables.ENTRIES.PARENT_ID);
		int parentId = (parentIdInt != null) ? parentIdInt.intValue() : -1;
		String name = record.getValue(Tables.ENTRIES.NAME);
		EntryType type = EntryType.fromValue(record.getValue(Tables.ENTRIES.TYPE).intValue());
		Timestamp createTime = record.getValue(Tables.ENTRIES.CREATION_TIME);
		Date creationTime = (createTime != null) ? new Date(createTime.getTime()) : null;
		Timestamp modTime = record.getValue(Tables.ENTRIES.MODIFICATION_TIME);
		Date modificationTime = (modTime != null) ? new Date(modTime.getTime()) : null;
		boolean hidden = record.getValue(Tables.ENTRIES.HIDDEN).booleanValue();
		Long lengthLong = record.getValue(Tables.FILES.LENGTH);
		long length = (lengthLong != null) ? lengthLong.longValue() : -1;
		String backupPath = record.getValue(Tables.FILES.BACKUP_PATH);
		Byte compressionByte = record.getValue(Tables.FILES.COMPRESSION);
		Compression compression = (compressionByte != null) ? Compression.fromValue(compressionByte.intValue()) : null;
		Entry entry = new Entry(id, parentId, name, type, creationTime, modificationTime, hidden, length, backupPath,
				compression);
		if (fullPaths) {
			entry.fullPath = getFolderPath(parentId);
		}
		return entry;
	}
	
	private Entry findInOlderBackups(Entry entry) throws IOException {
		Date runTime = new Date(database.factory()
			.select(Tables.BACKUPS.RUN_TIME)
			.from(Tables.ENTRIES)
			.join(Tables.BACKUPS)
				.on(Tables.BACKUPS.ID.equal(Tables.ENTRIES.BACKUP_ID))
			.where(Tables.ENTRIES.ID.equal(Integer.valueOf(entry.id)))
			.fetchOne()
			.getValue(Tables.BACKUPS.RUN_TIME).getTime());
		Cursor<Record> cursor = database.factory()
			.select(Tables.BACKUPS.ID)
			.from(Tables.BACKUPS)
			.where(Tables.BACKUPS.RUN_TIME.lessThan(new Timestamp(runTime.getTime())))
			.orderBy(Tables.BACKUPS.RUN_TIME.desc())
			.fetchLazy();
		try {
			while (cursor.hasNext()) {
				Record record = cursor.fetchOne();
				int backupId = record.getValue(Tables.BACKUPS.ID).intValue();
				int oldEntryId = findInBackup(entry.id, backupId);
				if (oldEntryId > 0) {
					Cursor<Record> entriesCursor = getEntriesCursor(backupId, -1, null, oldEntryId);
					try {
						List<Entry> entries = getEntries(entriesCursor, false);
						if (!entries.isEmpty()) {
							Entry oldEntry = entries.get(0);
							if (oldEntry.type == EntryType.FILE) {
								return oldEntry;
							}
						}
					} finally {
						database.closeQuietly(entriesCursor);
					}
				}
			}
		} finally {
			database.closeQuietly(cursor);
		}
		
		return null;
	}
	
	private int findInBackup(int entryId, int backupId) throws IOException {
		IFileOrFolderEntry entry = toFileOrFolderEntry(entryId);
		return Utils.findFileOrFolderEntryInBackup(entry, backupId, database);
	}
	
	private Utils.IFileOrFolderEntry toFileOrFolderEntry(final int entryId) {
		Record record = database.factory()
				.select(Tables.ENTRIES.NAME, Tables.ENTRIES.PARENT_ID, Tables.ENTRIES.TYPE)
				.from(Tables.ENTRIES)
				.where(Tables.ENTRIES.ID.equal(Integer.valueOf(entryId)))
				.fetchOne();
		final String name = record.getValue(Tables.ENTRIES.NAME);
		final Integer parentId = record.getValue(Tables.ENTRIES.PARENT_ID);
		final EntryType type = EntryType.fromValue(record.getValue(Tables.ENTRIES.TYPE).intValue());
		return new Utils.IFileOrFolderEntry() {
			@Override
			public boolean isFolder() throws IOException {
				return type == EntryType.FOLDER;
			}
			
			@Override
			public IFileOrFolderEntry getParentFolder() throws IOException {
				return (parentId != null) ? toFileOrFolderEntry(parentId.intValue()) : null;
			}
			
			@Override
			public String getName() {
				return name;
			}
			
			@Override
			public String getAbsolutePath() {
				return RestoreDialog.this.getAbsolutePath(entryId);
			}
		};
	}
	
	private String getAbsolutePath(int entryId) {
		Record record = database.factory()
			.select(Tables.ENTRIES.NAME, Tables.ENTRIES.PARENT_ID)
			.from(Tables.ENTRIES)
			.where(Tables.ENTRIES.ID.equal(Integer.valueOf(entryId)))
			.fetchOne();
		String name = record.getValue(Tables.ENTRIES.NAME);
		Integer parentId = record.getValue(Tables.ENTRIES.PARENT_ID);
		return (parentId != null) ? getAbsolutePath(parentId.intValue()) + File.separator + name : name;
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
			alwaysRestoreFromOlderBackups = null;
			
			final String myFolder = folder;
			Backup backup = (Backup) ((IStructuredSelection) backupsViewer.getSelection()).getFirstElement();
			final int backupId = backup.id;
			final int numEntries = backup.numEntries;
			final ProgressMonitorDialog dlg = new ProgressMonitorDialog(getShell());
			IRunnableWithProgress runnable = new IRunnableWithProgress() {
				@Override
				public void run(IProgressMonitor monitor) throws InvocationTargetException,
						InterruptedException {

					try {
						monitor.beginTask(Messages.Title_RestoreFromBackup, numEntries);
						for (Entry entry : entries) {
							restoreEntry(entry, new File(myFolder), settings.getOutputFolder(), backupId,
									monitor, dlg.getShell());
						}
					} catch (IOException e) {
						throw new InvocationTargetException(e);
					} finally {
						monitor.done();
					}
				}
			};
			try {
				dlg.run(true, true, runnable);
			} catch (InvocationTargetException e) {
				// TODO
				BackupPlugin.getDefault().logError("error while restoring from backup", e.getCause()); //$NON-NLS-1$
			} catch (InterruptedException e) {
				// okay
			}
		}
	}

	private void restoreEntry(Entry entry, File parentFolder, String outputFolder, int backupId,
			IProgressMonitor monitor, Shell shell) throws IOException, InterruptedException {

		if (monitor.isCanceled()) {
			throw new InterruptedException();
		}

		boolean isFolder = entry.type == EntryType.FOLDER;
		
		if (entry.type == EntryType.FAILED_FILE) {
			if (alwaysRestoreFromOlderBackups == null) {
				alwaysRestoreFromOlderBackups = Boolean.valueOf(promptRestoreFromOlderBackups(shell));
			}
			if (alwaysRestoreFromOlderBackups.booleanValue()) {
				entry = findInOlderBackups(entry);
			}
		}

		if ((entry != null) && (entry.type != EntryType.FAILED_FILE)) {
			Path outputPath;
			if (entry.type == EntryType.FOLDER) {
				File newFolder = new File(parentFolder, escapeFileName(entry.name));
				FileUtils.forceMkdir(newFolder);
				
				Cursor<Record> cursor = getEntriesCursor(backupId, entry.id, null, -1);
				try {
					for (Entry e : getEntries(cursor, false)) {
						restoreEntry(e, newFolder, outputFolder, backupId, monitor, shell);
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
					InputStream fileIn = new BufferedInputStream(new FileInputStream(inputFile));
					InputStream interceptIn = fileIn;
					for (IStorageInterceptor interceptor : storageInterceptors) {
						interceptIn = interceptor.interceptInputStream(interceptIn, entry.length);
					}
					InputStream compressIn = entry.compression.getInputStream(interceptIn);
					in = compressIn;
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
		}

		if (!isFolder) {
			monitor.worked(1);
		}
	}

	private boolean promptRestoreFromOlderBackups(final Shell shell) {
		Display display = shell.getDisplay();
		final boolean[] result = new boolean[1];
		display.syncExec(new Runnable() {
			@Override
			public void run() {
				if (!shell.isDisposed()) {
					result[0] = MessageDialog.openQuestion(shell, Messages.Title_FailedFiles, Messages.RestoreFailedFilesFromOlderBackups);
				}
			}
		});
		return result[0];
	}

	private String escapeFileName(String name) {
		return name
			.replace('/', '_')
			.replace('\\', '_')
			.replace(':', '_')
			.replace(';', '_')
			.replaceAll("__", "_"); //$NON-NLS-1$ //$NON-NLS-2$
	}
	
	private void startSearchTimer(final String text) {
		synchronized (this) {
			if (searchTimerTask != null) {
				searchTimerTask.cancel();
			}
			
			searchTimerTask = new TimerTask() {
				@Override
				public void run() {
					if (StringUtils.isNotBlank(text)) {
						runSearchAsync(text);
					} else {
						final Shell shell = getShell();
						final Display display = shell.getDisplay();
						if (!display.isDisposed()) {
							display.asyncExec(new Runnable() {
								@Override
								public void run() {
									if (!display.isDisposed() && !shell.isDisposed()) {
										Backup backup = (Backup) ((IStructuredSelection) backupsViewer.getSelection()).getFirstElement();
										try {
											showBackup(backup);
										} catch (DataAccessException e) {
											BackupPlugin.getDefault().logError("error while showing backup", e); //$NON-NLS-1$
										}
									}
								}
							});
						}
					}
				}
			};
			timer.schedule(searchTimerTask, 1000L);
		}
	}

	private void runSearchAsync(String text) {
		final Shell shell = getShell();
		final Display display = shell.getDisplay();
		final org.eclipse.swt.graphics.Cursor[] oldCursor = new org.eclipse.swt.graphics.Cursor[1];
		final int[] backupId = { -1 };
		if (!display.isDisposed()) {
			display.syncExec(new Runnable() {
				@Override
				public void run() {
					if (!display.isDisposed() && !shell.isDisposed()) {
						oldCursor[0] = shell.getCursor();
						shell.setCursor(display.getSystemCursor(SWT.CURSOR_WAIT));
						shell.setEnabled(false);
						Backup backup = (Backup) ((IStructuredSelection) backupsViewer.getSelection()).getFirstElement();
						if (backup != null) {
							backupId[0] = backup.id;
						}
					}
				}
			});
		}
		Cursor<Record> cursor = null;
		try {
			if (backupId[0] > 0) {
				cursor = getEntriesCursor(backupId[0], -1, text, -1);
				final List<Entry> entries = getEntries(cursor, true);
				if (!display.isDisposed()) {
					display.syncExec(new Runnable() {
						@Override
						public void run() {
							if (!display.isDisposed() && !shell.isDisposed()) {
								((EntryLabelProvider) entriesViewer.getLabelProvider()).setShowFullPath(true);
								((EntrySorter) entriesViewer.getSorter()).setSortFullPath(true);
								entriesViewer.setInput(entries);
								currentFolderLink.setText(StringUtils.EMPTY);
								moveUpButton.setEnabled(false);
							}
						}
					});
				}
			}
		} catch (DataAccessException e) {
			BackupPlugin.getDefault().logError("error while searching for entries", e); //$NON-NLS-1$
		} finally {
			database.closeQuietly(cursor);
			
			if (!display.isDisposed()) {
				display.syncExec(new Runnable() {
					@Override
					public void run() {
						if (!display.isDisposed() && !shell.isDisposed()) {
							shell.setEnabled(true);
							shell.setCursor(oldCursor[0]);
						}
					}
				});
			}
		}
	}
}
