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

import java.util.Set;

class Settings {
	private Set<String> folders;
	private String outputFolder;
	private boolean runHourly;
	private int dailyHours;
	private int dailyMinutes;

	Settings(Set<String> folders, String outputFolder, boolean runHourly, int dailyHours, int dailyMinutes) {
		this.folders = folders;
		this.outputFolder = outputFolder;
		this.runHourly = runHourly;
		this.dailyHours = dailyHours;
		this.dailyMinutes = dailyMinutes;
	}
	
	Set<String> getFolders() {
		return folders;
	}
	
	String getOutputFolder() {
		return outputFolder;
	}
	
	boolean isRunHourly() {
		return runHourly;
	}
	
	int getDailyHours() {
		return dailyHours;
	}
	
	int getDailyMinutes() {
		return dailyMinutes;
	}
}
