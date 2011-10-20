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

import java.text.FieldPosition;
import java.text.Format;
import java.text.NumberFormat;
import java.text.ParsePosition;

import org.eclipse.osgi.util.NLS;

public class FileLengthFormat extends Format {
	@Override
	public StringBuffer format(Object obj, StringBuffer toAppendTo, FieldPosition pos) {
		if (!(obj instanceof Long)) {
			throw new IllegalArgumentException("object must be Long"); //$NON-NLS-1$
		}

		long length = ((Number) obj).longValue();
		if (length < 1024L) {
			toAppendTo.append(NLS.bind(Messages.Format_Bytes, formatNumber(length)));
		} else if (length < 1024000L) {
			toAppendTo.append(NLS.bind(Messages.Format_KB, formatNumber(length / 1024d)));
		} else if (length < 1048576000L) {
			toAppendTo.append(NLS.bind(Messages.Format_MB, formatNumber(length / 1024d / 1024d)));
		} else {
			toAppendTo.append(NLS.bind(Messages.Format_GB, formatNumber(length / 1024d / 1024d / 1024d)));
		}

		return toAppendTo;
	}

	public String format(long length) {
		return format(Long.valueOf(length), new StringBuffer(), null).toString();
	}
	
	private String formatNumber(double d) {
		NumberFormat format = NumberFormat.getNumberInstance();
		int fractionDigits;
		if (d >= 100d) {
			fractionDigits = 0;
		} else if (d >= 10d) {
			fractionDigits = 1;
		} else {
			fractionDigits = 2;
		}
		format.setMaximumFractionDigits(fractionDigits);
		return format.format(d);
	}
	
	private String formatNumber(long l) {
		return NumberFormat.getNumberInstance().format(l);
	}
	
	@Override
	public Object parseObject(String source, ParsePosition pos) {
		return null;
	}
}
