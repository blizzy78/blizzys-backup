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

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class LengthOutputStream extends FilterOutputStream {
	private long length;
	
	public LengthOutputStream(OutputStream out) {
		super(out);
	}

	@Override
	public void write(int b) throws IOException {
		length++;
		out.write(b);
	}
	
	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		length += len;
		out.write(b, off, len);
	}
	
	public long getLength() {
		return length;
	}
}
