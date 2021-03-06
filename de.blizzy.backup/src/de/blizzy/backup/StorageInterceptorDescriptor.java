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

public class StorageInterceptorDescriptor {
	private String id;
	private String name;
	private IStorageInterceptor storageInterceptor;

	StorageInterceptorDescriptor(String id, String name, IStorageInterceptor storageInterceptor) {
		this.id = id;
		this.name = name;
		this.storageInterceptor = storageInterceptor;
	}

	public String getId() {
		return id;
	}
	
	public String getName() {
		return name;
	}
	
	public IStorageInterceptor getStorageInterceptor() {
		return storageInterceptor;
	}
}
