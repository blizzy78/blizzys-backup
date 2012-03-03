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
package de.blizzy.backup.settings;

import java.util.Set;

import de.blizzy.backup.vfs.ILocation;

public class Settings {
	private Set<ILocation> locations;
	private String outputFolder;
	private boolean runHourly;
	private int dailyHours;
	private int dailyMinutes;
	private boolean useChecksums;
	private int maxAgeDays;
	private int maxDiskFillRate;

	public Settings(Set<ILocation> locations, String outputFolder, boolean runHourly, int dailyHours, int dailyMinutes,
			boolean useChecksums, int maxAgeDays, int maxDiskFillRate) {
		
		this.locations = locations;
		this.outputFolder = outputFolder;
		this.runHourly = runHourly;
		this.dailyHours = dailyHours;
		this.dailyMinutes = dailyMinutes;
		this.useChecksums = useChecksums;
		this.maxAgeDays = maxAgeDays;
		this.maxDiskFillRate = maxDiskFillRate;
	}
	
	public Set<ILocation> getLocations() {
		return locations;
	}
	
	public String getOutputFolder() {
		return outputFolder;
	}
	
	public boolean isRunHourly() {
		return runHourly;
	}
	
	public int getDailyHours() {
		return dailyHours;
	}
	
	public int getDailyMinutes() {
		return dailyMinutes;
	}
	
	public boolean isUseChecksums() {
		return useChecksums;
	}
	
	public int getMaxAgeDays() {
		return maxAgeDays;
	}
	
	public int getMaxDiskFillRate() {
		return maxDiskFillRate;
	}
}
