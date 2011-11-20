package de.blizzy.backup;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

import de.blizzy.backup.vfs.IFolder;

public class UtilsTest {
	@Test
	public void createBackupFilePath() {
		String path = Utils.createBackupFilePath();
		assertTrue(Integer.parseInt(path.substring(0, 4)) >= 1);
		assertEquals("/", path.substring(4, 5)); //$NON-NLS-1$
		assertTrue(Integer.parseInt(path.substring(5, 7)) >= 1);
		assertEquals("/", path.substring(7, 8)); //$NON-NLS-1$
		assertTrue(Integer.parseInt(path.substring(8, 10)) >= 1);
		assertEquals("/", path.substring(10, 11)); //$NON-NLS-1$
		assertTrue(path.substring(11).length() > 0);
	}
	
	@Test
	public void toBackupFile() {
		File tempDir = new File(System.getProperty("java.io.tmpdir")); //$NON-NLS-1$
		File backupFile = Utils.toBackupFile("2011/09/24/foo", tempDir.getAbsolutePath()); //$NON-NLS-1$
		File expected = new File(new File(new File(new File(tempDir, "2011"), "09"), "24"), "foo"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		assertEquals(expected.getAbsoluteFile(), backupFile.getAbsoluteFile());
	}
	
	@Test
	public void isBackupFolder() throws IOException {
		File tempDir = null;
		File dbDir = null;
		try {
			tempDir = Files.createTempDirectory(Paths.get(System.getProperty("java.io.tmpdir")), null).toFile(); //$NON-NLS-1$
			dbDir = new File(tempDir, "$blizzysbackup"); //$NON-NLS-1$
			dbDir.mkdir();
			assertTrue(Utils.isBackupFolder(tempDir.getAbsolutePath()));
		} finally {
			if (tempDir != null) {
				tempDir.delete();
			}
			if (dbDir != null) {
				dbDir.delete();
			}
		}
	}
	
	@Test
	public void getSimpleName() {
		IFolder folder = mock(IFolder.class);
		when(folder.getName()).thenReturn("foo"); //$NON-NLS-1$
		assertEquals("foo", Utils.getSimpleName(folder)); //$NON-NLS-1$

		folder = mock(IFolder.class);
		when(folder.getName()).thenReturn(""); //$NON-NLS-1$
		when(folder.getAbsolutePath()).thenReturn("C:"); //$NON-NLS-1$
		assertEquals("C:", Utils.getSimpleName(folder)); //$NON-NLS-1$
	}
	
	@Test
	public void isParent() {
		IFolder folder1 = mock(IFolder.class);
		when(folder1.getParentFolder()).thenReturn(null);
		IFolder folder2 = mock(IFolder.class);
		when(folder2.getParentFolder()).thenReturn(folder1);
		IFolder folder3 = mock(IFolder.class);
		when(folder3.getParentFolder()).thenReturn(folder2);
		
		// direct parent
		assertTrue(Utils.isParent(folder1, folder2));
		assertTrue(Utils.isParent(folder2, folder3));
		// indirect parent
		assertTrue(Utils.isParent(folder1, folder3));
		// same folder
		assertFalse(Utils.isParent(folder2, folder2));
		// wrong order
		assertFalse(Utils.isParent(folder2, folder1));
	}
}
