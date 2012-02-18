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
import java.util.List;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.jface.viewers.ColumnPixelData;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.TableLayout;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;

class ErrorsDialog extends Dialog {
	public class ErrorsContentProvider implements IStructuredContentProvider {
		@Override
		public Object[] getElements(Object inputElement) {
			@SuppressWarnings("unchecked")
			List<BackupError> errors = (List<BackupError>) inputElement;
			return errors.toArray();
		}

		@Override
		public void dispose() {
		}

		@Override
		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		}
	}

	public class ErrorsLabelProvider implements ITableLabelProvider {
		private ImageRegistry imageRegistry;

		ErrorsLabelProvider(Display display) {
			imageRegistry = new ImageRegistry(display);
			
			imageRegistry.put("warning", BackupPlugin.getDefault().getImageDescriptor("etc/icons/warning.gif")); //$NON-NLS-1$ //$NON-NLS-2$
			imageRegistry.put("error", BackupPlugin.getDefault().getImageDescriptor("etc/icons/error.gif")); //$NON-NLS-1$ //$NON-NLS-2$
		}

		@Override
		public String getColumnText(Object element, int columnIndex) {
			BackupError error = (BackupError) element;
			switch (columnIndex) {
				case 0:
					return DATE_FORMAT.format(error.getDate());
				case 1:
					return error.getFileOrFolderPath();
				case 2:
					{
						Throwable t = error.getError();
						String msg = t.getLocalizedMessage();
						if (msg == null) {
							msg = t.getMessage();
						}
						return msg;
					}
			}
			return null;
		}
		
		@Override
		public Image getColumnImage(Object element, int columnIndex) {
			if (columnIndex == 0) {
				BackupError error = (BackupError) element;
				switch (error.getSeverity()) {
					case WARNING:
						return imageRegistry.get("warning"); //$NON-NLS-1$
					case ERROR:
						return imageRegistry.get("error"); //$NON-NLS-1$
				}
			}
			return null;
		}
		
		@Override
		public boolean isLabelProperty(Object element, String property) {
			return false;
		}

		@Override
		public void dispose() {
			imageRegistry.dispose();
		}

		@Override
		public void addListener(ILabelProviderListener listener) {
		}

		@Override
		public void removeListener(ILabelProviderListener listener) {
		}
	}
	
	private static final DateFormat DATE_FORMAT = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
	private static final int CLEAR_ID = IDialogConstants.CLIENT_ID + 1;

	private List<BackupError> errors;
	private boolean clearErrors;

	protected ErrorsDialog(Shell parentShell, List<BackupError> errors) {
		super(parentShell);
		
		this.errors = errors;
	}
	
	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText(Messages.Title_Errors);
	}
	
	@Override
	protected boolean isResizable() {
		return true;
	}
	
	@Override
	protected Control createDialogArea(Composite parent) {
		Composite composite = (Composite) super.createDialogArea(parent);
		((GridLayout) composite.getLayout()).numColumns = 1;
		
		TableViewer viewer = new TableViewer(composite, SWT.SINGLE | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		viewer.setContentProvider(new ErrorsContentProvider());
		viewer.setLabelProvider(new ErrorsLabelProvider(parent.getDisplay()));
		
		Table table = viewer.getTable();
		TableLayout layout = new TableLayout();
		table.setLayout(layout);
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
		GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
		gd.widthHint = convertWidthInCharsToPixels(80);
		gd.heightHint = convertHeightInCharsToPixels(15);
		table.setLayoutData(gd);
		
		TableColumn col = new TableColumn(table, SWT.LEFT);
		col.setText(Messages.Title_Date);
		layout.addColumnData(new ColumnPixelData(convertWidthInCharsToPixels(16) + 20, true));
		
		col = new TableColumn(table, SWT.LEFT);
		col.setText(Messages.Title_FileOrFolder);
		layout.addColumnData(new ColumnWeightData(40, true));

		col = new TableColumn(table, SWT.LEFT);
		col.setText(Messages.Title_Error);
		layout.addColumnData(new ColumnWeightData(40, true));
		
		viewer.setInput(errors);
		
		return composite;
	}
	
	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		createButton(parent, CLEAR_ID, Messages.Button_ClearErrors, false);
		createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CLOSE_LABEL, true);
	}
	
	@Override
	protected void buttonPressed(int buttonId) {
		if (buttonId == CLEAR_ID) {
			clearErrors = true;
			buttonId = IDialogConstants.CANCEL_ID;
		}
		super.buttonPressed(buttonId);
	}
	
	boolean isClearErrors() {
		return clearErrors;
	}
}
