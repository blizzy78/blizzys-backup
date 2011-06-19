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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.IOpenListener;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
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
import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;

class RestoreDialog extends Dialog {
	private static final class Backup {
		int id;
		Date runTime;
		
		Backup(int id, Date runTime) {
			this.id = id;
			this.runTime = runTime;
		}
	}
	
	private static final class Entry {
		int id;
		int parentId;
		String name;
		EntryType type;
		Date creationTime;
		Date modificationTime;
		boolean hidden;
		int length;
		String backupPath;

		Entry(int id, int parentId, String name, EntryType type, Date creationTime, Date modificationTime, boolean hidden,
				int length, String backupPath) {

			this.id = id;
			this.parentId = parentId;
			this.name = name;
			this.type = type;
			this.creationTime = creationTime;
			this.modificationTime = modificationTime;
			this.hidden = hidden;
			this.length = length;
			this.backupPath = backupPath;
		}
	}

	private static final class BackupLabelProvider extends LabelProvider {
		private static final DateFormat DATE_FORMAT = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
		
		@Override
		public String getText(Object element) {
			Backup backup = (Backup) element;
			return DATE_FORMAT.format(backup.runTime);
		}
	}

	private static final class EntryLabelProvider implements ITableLabelProvider {
		private static final DateFormat DATE_FORMAT = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);

		private Image rootFolderImage;
		private Image folderImage;
		private Image fileImage;

		EntryLabelProvider(Device device) {
			rootFolderImage = BackupPlugin.getDefault().getImageDescriptor("etc/icons/rootFolder.gif").createImage(device); //$NON-NLS-1$
			folderImage = BackupPlugin.getDefault().getImageDescriptor("etc/icons/folder.gif").createImage(device); //$NON-NLS-1$
			fileImage = BackupPlugin.getDefault().getImageDescriptor("etc/icons/file.gif").createImage(device); //$NON-NLS-1$
		}
		
		public void dispose() {
			rootFolderImage.dispose();
			folderImage.dispose();
			fileImage.dispose();
		}

		public String getColumnText(Object element, int columnIndex) {
			Entry entry = (Entry) element;
			switch (columnIndex) {
				case 0:
					return entry.name;
				case 1:
					if (entry.length >= 0) {
						return FileUtils.byteCountToDisplaySize(entry.length);
					}
					break;
				case 2:
					if (entry.modificationTime != null) {
						return DATE_FORMAT.format(entry.modificationTime);
					}
					break;
			}
			return null;
		}

		public Image getColumnImage(Object element, int columnIndex) {
			if (columnIndex == 0) {
				Entry entry = (Entry) element;
				if (entry.parentId <= 0) {
					return rootFolderImage;
				}
				if (entry.type == EntryType.FOLDER) {
					return folderImage;
				}
				return fileImage;
			}
			return null;
		}

		public boolean isLabelProperty(Object element, String property) {
			return true;
		}

		public void addListener(ILabelProviderListener listener) {
		}

		public void removeListener(ILabelProviderListener listener) {
		}
	}

	private String outputFolder;
	private Database database;
	private Connection conn;
	private ComboViewer backupsViewer;
	private TableViewer entriesViewer;
	private Button moveUpButton;
	private PreparedStatement psEntries;
	private PreparedStatement psRootEntries;
	private PreparedStatement psParent;
	private PreparedStatement psNumEntries;

	RestoreDialog(Shell parentShell) {
		super(parentShell);

		IDialogSettings settingsSection = Utils.getChildSection(Utils.getSection("backup"), "settings"); //$NON-NLS-1$ //$NON-NLS-2$
		outputFolder = settingsSection.get("outputFolder"); //$NON-NLS-1$
		database = new Database(new File(outputFolder, "$blizzysbackup")); //$NON-NLS-1$
	}
	
	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setImages(BackupApplication.getWindowImages());
	}
	
	@Override
	protected boolean isResizable() {
		return true;
	}

	@Override
	public int open() {
		try {
			conn = database.openDatabaseConnection();
			
			psEntries = conn.prepareStatement("SELECT entries.id AS id, parent_id, name, type, creation_time, " + //$NON-NLS-1$
					"modification_time, hidden, files.length AS length, files.backup_path AS backup_path " + //$NON-NLS-1$
					"FROM entries " + //$NON-NLS-1$
					"LEFT JOIN files ON files.id = entries.file_id " + //$NON-NLS-1$
					"WHERE (backup_id = ?) AND (parent_id = ?) " + //$NON-NLS-1$
					"ORDER BY name"); //$NON-NLS-1$
			psRootEntries = conn.prepareStatement("SELECT entries.id AS id, parent_id, name, type, creation_time, " + //$NON-NLS-1$
					"modification_time, hidden, files.length AS length, files.backup_path AS backup_path " + //$NON-NLS-1$
					"FROM entries " + //$NON-NLS-1$
					"LEFT JOIN files ON files.id = entries.file_id " + //$NON-NLS-1$
					"WHERE (backup_id = ?) AND (parent_id IS NULL) " + //$NON-NLS-1$
					"ORDER BY name"); //$NON-NLS-1$
			psParent = conn.prepareStatement("SELECT parent_id FROM entries WHERE id = ?"); //$NON-NLS-1$
			psNumEntries = conn.prepareStatement("SELECT COUNT(*) FROM entries " + //$NON-NLS-1$
					"WHERE (backup_id = ?) AND (parent_id = ?) AND (type != " + EntryType.FOLDER.getValue() + ")"); //$NON-NLS-1$ //$NON-NLS-2$
		} catch (SQLException e) {
			// TODO
			e.printStackTrace();
		}
		
		return super.open();
	}
	
	@Override
	public boolean close() {
		database.closeQuietly(psEntries, psRootEntries, psParent, psNumEntries);
		database.releaseDatabaseConnection(conn);
		
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
		label.setText("Show backup contents at:"); //$NON-NLS-1$
		label.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		
		backupsViewer = new ComboViewer(composite);
		backupsViewer.getCombo().setVisibleItemCount(10);
		backupsViewer.getControl().setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		
		List<Backup> backups = new ArrayList<Backup>();
		try {
			PreparedStatement ps = conn.prepareStatement("SELECT id, run_time FROM backups ORDER BY run_time DESC"); //$NON-NLS-1$
			ResultSet rs = ps.executeQuery();
			while (rs.next()) {
				int id = rs.getInt("id"); //$NON-NLS-1$
				Date runTime = new Date(rs.getTimestamp("run_time").getTime()); //$NON-NLS-1$
				Backup backup = new Backup(id, runTime);
				backups.add(backup);
			}
		} catch (SQLException e) {
			// TODO
			e.printStackTrace();
		}
		
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
		col.setText("Name"); //$NON-NLS-1$
		tableLayout.addColumnData(new ColumnWeightData(50, true));
		col = new TableColumn(table, SWT.LEFT);
		col.setText("Size"); //$NON-NLS-1$
		tableLayout.addColumnData(new ColumnWeightData(20, true));
		col = new TableColumn(table, SWT.LEFT);
		col.setText("Modification Date"); //$NON-NLS-1$
		tableLayout.addColumnData(new ColumnWeightData(30, true));

		Composite entriesButtonsComposite = new Composite(entriesComposite, SWT.NONE);
		layout = new GridLayout(1, false);
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		entriesButtonsComposite.setLayout(layout);
		entriesButtonsComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, true));
		
		moveUpButton = new Button(entriesButtonsComposite, SWT.PUSH);
		moveUpButton.setText("Move Up"); //$NON-NLS-1$
		moveUpButton.setEnabled(false);
		moveUpButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		
		final Button restoreButton = new Button(entriesButtonsComposite, SWT.PUSH);
		restoreButton.setText("Restore..."); //$NON-NLS-1$
		restoreButton.setEnabled(false);
		restoreButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		
		backupsViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent e) {
				Backup backup = (Backup) ((IStructuredSelection) e.getSelection()).getFirstElement();
				showBackup(backup);
			}
		});
		
		entriesViewer.addOpenListener(new IOpenListener() {
			public void open(OpenEvent e) {
				IStructuredSelection selection = (IStructuredSelection) e.getSelection();
				if (selection.size() == 1) {
					Entry entry = (Entry) selection.getFirstElement();
					if (entry.type == EntryType.FOLDER) {
						Backup backup = (Backup) ((IStructuredSelection) backupsViewer.getSelection()).getFirstElement();
						showFolder(backup, entry);
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
				moveUp();
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
		
		if (!backups.isEmpty()) {
			backupsViewer.setSelection(new StructuredSelection(backups.get(0)), true);
		}
		
		return composite;
	}

	protected void showBackup(Backup backup) {
		showEntries(backup, -1);
		moveUpButton.setEnabled(false);
	}

	private void showFolder(Backup backup, Entry entry) {
		showEntries(backup, entry.id);
		moveUpButton.setEnabled(true);
		moveUpButton.setData((entry.parentId > 0) ? Integer.valueOf(entry.parentId) : backup);
	}

	private void moveUp() {
		Object data = moveUpButton.getData();
		if (data instanceof Backup) {
			showBackup((Backup) data);
		} else {
			int folderId = ((Integer) moveUpButton.getData()).intValue();
			Backup backup = (Backup) ((IStructuredSelection) backupsViewer.getSelection()).getFirstElement();
			ResultSet rs = null;
			try {
				psParent.setInt(1, folderId);
				rs = psParent.executeQuery();
				rs.next();
				int parentId = rs.getInt("parent_id"); //$NON-NLS-1$
				if (rs.wasNull()) {
					parentId = -1;
				}
				showEntries(backup, folderId);
				moveUpButton.setEnabled(true);
				moveUpButton.setData((parentId > 0) ? Integer.valueOf(parentId) : backup);
			} catch (SQLException e) {
				// TODO
				e.printStackTrace();
			} finally {
				database.closeQuietly(rs);
			}
		}
	}

	private void showEntries(Backup backup, int parentFolderId) {
		List<Entry> entries = Collections.emptyList();
		ResultSet rs = null;
		try {
			if (parentFolderId > 0) {
				psEntries.setInt(1, backup.id);
				psEntries.setInt(2, parentFolderId);
				rs = psEntries.executeQuery();
			} else {
				psRootEntries.setInt(1, backup.id);
				rs = psRootEntries.executeQuery();
			}
			entries = getEntries(rs);
		} catch (SQLException e) {
			// TODO
			e.printStackTrace();
		} finally {
			database.closeQuietly(rs);
		}
		
		entriesViewer.setInput(entries);
		entriesViewer.getControl().setData(Integer.valueOf(parentFolderId));
	}
	
	private List<Entry> getEntries(ResultSet rs) throws SQLException {
		List<Entry> entries = new ArrayList<Entry>();
		while (rs.next()) {
			int id = rs.getInt("id"); //$NON-NLS-1$
			int parentId = rs.getInt("parent_id"); //$NON-NLS-1$
			if (rs.wasNull()) {
				parentId = -1;
			}
			String name = rs.getString("name"); //$NON-NLS-1$
			EntryType type = EntryType.fromValue(rs.getInt("type")); //$NON-NLS-1$
			Timestamp createTime = rs.getTimestamp("creation_time"); //$NON-NLS-1$
			Date creationTime = null;
			if (!rs.wasNull()) {
				creationTime = new Date(createTime.getTime());
			}
			Timestamp modTime = rs.getTimestamp("modification_time"); //$NON-NLS-1$
			Date modificationTime = null;
			if (!rs.wasNull()) {
				modificationTime = new Date(modTime.getTime());
			}
			int hiddenInt = rs.getInt("hidden"); //$NON-NLS-1$
			boolean hidden = (hiddenInt == 1);
			if (rs.wasNull()) {
				hidden = false;
			}
			int length = rs.getInt("length"); //$NON-NLS-1$
			if (rs.wasNull()) {
				length = -1;
			}
			String backupPath = rs.getString("backup_path"); //$NON-NLS-1$
			if (rs.wasNull()) {
				backupPath = null;
			}
			Entry entry = new Entry(id, parentId, name, type, creationTime, modificationTime, hidden, length, backupPath);
			entries.add(entry);
		}
		return entries;
	}
	
	private void restore(final Collection<Entry> entries) {
		String folder = null;
		for (;;) {
			DirectoryDialog dlg = new DirectoryDialog(getShell(), SWT.SAVE);
			dlg.setText("Select Output Folder"); //$NON-NLS-1$
			dlg.setFilterPath(folder);
			folder = dlg.open();
			if (folder == null) {
				break;
			}
			
			if (new File(folder).list().length > 0) {
				MessageDialog.openError(getShell(), "Folder Not Empty", //$NON-NLS-1$
						NLS.bind("Folder ''{0}'' is not empty. Please select a different folder.", //$NON-NLS-1$
								new File(folder).getName()));
				continue;
			}

			break;
		}
		
		if (folder != null) {
			final String myFolder = folder;
			Backup backup = (Backup) ((IStructuredSelection) backupsViewer.getSelection()).getFirstElement();
			final int backupId = backup.id;
			IRunnableWithProgress runnable = new IRunnableWithProgress() {
				public void run(IProgressMonitor monitor) throws InvocationTargetException,
						InterruptedException {

					try {
						int totalNumEntries = getTotalNumEntries(entries, backupId);
						monitor.beginTask("Restore from backup", totalNumEntries); //$NON-NLS-1$
						for (Entry entry : entries) {
							restoreEntry(entry, new File(myFolder), outputFolder, backupId, monitor);
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

	private int getTotalNumEntries(Collection<Entry> entries, int backupId) throws SQLException {
		int num = 0;
		for (Entry entry : entries) {
			if (entry.type == EntryType.FOLDER) {
				num += getTotalNumEntries(entry, backupId);
			} else {
				num++;
			}
		}
		return num;
	}

	private int getTotalNumEntries(Entry folderEntry, int backupId) throws SQLException {
		psEntries.setInt(1, backupId);
		psEntries.setInt(2, folderEntry.id);
		ResultSet rs = null;
		try {
			rs = psEntries.executeQuery();
			List<Entry> entries = getEntries(rs);
			return getTotalNumEntries(entries, backupId);
		} finally {
			database.closeQuietly(rs);
		}
	}

	private void restoreEntry(Entry entry, File parentFolder, String outputFolder, int backupId, IProgressMonitor monitor)
		throws IOException, SQLException, InterruptedException {

		if (monitor.isCanceled()) {
			throw new InterruptedException();
		}
		
		if (entry.type == EntryType.FOLDER) {
			File newFolder = new File(parentFolder, escapeFileName(entry.name));
			FileUtils.forceMkdir(newFolder);
			
			psEntries.setInt(1, backupId);
			psEntries.setInt(2, entry.id);
			ResultSet rs = null;
			try {
				rs = psEntries.executeQuery();
				for (Entry e : getEntries(rs)) {
					restoreEntry(e, newFolder, outputFolder, backupId, monitor);
				}
			} finally {
				database.closeQuietly(rs);
			}
		} else {
			File inputFile = Utils.toBackupFile(entry.backupPath, outputFolder);
			File outputFile = new File(parentFolder, escapeFileName(entry.name));
			Path outputPath = outputFile.toPath();
			InputStream in = null;
			try {
				in = new GZIPInputStream(new BufferedInputStream(new FileInputStream(inputFile)));
				Files.copy(in, outputPath);
			} finally {
				IOUtils.closeQuietly(in);
				monitor.worked(1);
			}
			DosFileAttributeView view = Files.getFileAttributeView(outputPath, DosFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
			if (view != null) {
				if (entry.hidden) {
					view.setHidden(entry.hidden);
				}
				view.setTimes(FileTime.fromMillis(entry.modificationTime.getTime()), null,
						FileTime.fromMillis(entry.creationTime.getTime()));
			}
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
