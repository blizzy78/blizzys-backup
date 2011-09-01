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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;

public enum Compression {
	GZIP(1),
	BZIP2(2);
	
	private int value;

	private Compression(int value) {
		this.value = value;
	}
	
	public int getValue() {
		return value;
	}
	
	public InputStream getInputStream(InputStream in) throws IOException {
		if (this == GZIP) {
			return new GZIPInputStream(in);
		}
		if (this == BZIP2) {
			return new BZip2CompressorInputStream(in);
		}
		throw new RuntimeException();
	}
	
	public OutputStream getOutputStream(OutputStream out) throws IOException {
		if (this == GZIP) {
			return new GZIPOutputStream(out);
		}
		if (this == BZIP2) {
			return new BZip2CompressorOutputStream(out);
		}
		throw new RuntimeException();
	}

	public static Compression fromValue(int value) {
		if (value == GZIP.value) {
			return GZIP;
		}
		if (value == BZIP2.value) {
			return BZIP2;
		}
		throw new IllegalArgumentException("unknown value: " + value); //$NON-NLS-1$
	}
}
