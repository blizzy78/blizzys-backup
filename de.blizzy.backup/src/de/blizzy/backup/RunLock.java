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

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;

class RunLock {
	private File lockFile;
	private FileChannel channel;
	private FileLock lock;

	RunLock(File lockFile) {
		this.lockFile = lockFile;
	}
	
	boolean tryLock() {
		try {
			channel = FileChannel.open(lockFile.toPath(), StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.DELETE_ON_CLOSE);
			lock = channel.tryLock();
			if (lock != null) {
				addShutdownHook();
				return true;
			}
		} catch (IOException e) {
		}
		return false;
	}
	
	private void addShutdownHook() {
		Thread thread = new Thread("Run Lock File Cleanup") { //$NON-NLS-1$
			@Override
			public void run() {
				release();
			}
		};
		Runtime.getRuntime().addShutdownHook(thread);
	}

	private void release() {
		if (lock != null) {
			try {
				lock.release();
			} catch (IOException e) {
				// ignore
			}
		}
		if (channel != null) {
			try {
				channel.close();
			} catch (IOException e) {
				// ignore
			}
		}
		lockFile.delete();
	}
}
