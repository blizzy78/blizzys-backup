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

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS {
	private static final String BUNDLE_NAME = "de.blizzy.backup.messages"; //$NON-NLS-1$
	public static String BackupIntegrityIntact;
	public static String BackupIntegrityNotIntact;
	public static String Button_Add;
	public static String Button_AddX;
	public static String Button_BackupNow;
	public static String Button_Browse;
	public static String Button_Check;
	public static String Button_Exit;
	public static String Button_MinimizeOnly;
	public static String Button_MoveUp;
	public static String Button_PauseBackup;
	public static String Button_Remove;
	public static String Button_Restore;
	public static String Button_Settings;
	public static String Button_StopBackup;
	public static String CheckBackup;
	public static String CheckingForNewVersion;
	public static String CleaningUp;
	public static String CompareFilesChecksum;
	public static String CompareFilesMetadata;
	public static String DropFoldersHelp;
	public static String ErrorsWhileCheckingBackup;
	public static String ExitApplication;
	public static String Finalizing;
	public static String FolderContainsExistingBackup;
	public static String FolderIsChildOfOutputFolder;
	public static String FolderIsOutputFolder;
	public static String FolderIsParentOfBackupFolder;
	public static String FolderNotEmpty;
	public static String Format_Bytes;
	public static String Format_GB;
	public static String Format_KB;
	public static String Format_MB;
	public static String Idle;
	public static String Initializing;
	public static String Label_BackupOutputFolder;
	public static String Label_CurrentFolder;
	public static String Label_Folder;
	public static String Label_Host;
	public static String Label_Login;
	public static String Label_ModificationDate;
	public static String Label_Name;
	public static String Label_NextRun;
	public static String Label_Password;
	public static String Label_Port;
	public static String Label_RunDaily;
	public static String Label_RunHourly;
	public static String Label_SearchFileFolder;
	public static String Label_ShowBackupContentsAt;
	public static String Label_Size;
	public static String Label_Status;
	public static String ModifyBackupSettings;
	public static String NewVersionAvailable;
	public static String NoNewVersionAvailable;
	public static String OutputFolderIsInBackup;
	public static String ParentFolderInBackup;
	public static String ProgramRunning;
	public static String RestoreFromBackup;
	public static String RunBackupNow;
	public static String Running;
	public static String ScheduleExplanation_DailyBackups;
	public static String ScheduleExplanation_DailyBackupsKeepTime;
	public static String ScheduleExplanation_HourlyBackups;
	public static String ScheduleExplanation_HourlyBackupsKeepTime;
	public static String ScheduleExplanation_WeeklyBackupsKeepDisk;
	public static String Title_BackupIntegrityCheck;
	public static String Title_BlizzysBackup;
	public static String Title_CheckBackupIntegrity;
	public static String Title_CloseBackupDatabase;
	public static String Title_Error;
	public static String Title_ExistingBackup;
	public static String Title_ExitApplication;
	public static String Title_FileComparison;
	public static String Title_FolderCannotBeAdded;
	public static String Title_FolderNotEmpty;
	public static String Title_FoldersToBackup;
	public static String Title_FTP;
	public static String Title_Information;
	public static String Title_InvalidFolder;
	public static String Title_License;
	public static String Title_NewVersionAvailable;
	public static String Title_NoNewVersionAvailable;
	public static String Title_OpenBackupDatabase;
	public static String Title_OutputFolder;
	public static String Title_ProgramRunning;
	public static String Title_Restore;
	public static String Title_RestoreFromBackup;
	public static String Title_ScheduleExplanation;
	public static String Title_SelectFolder;
	public static String Title_SelectOutputFolder;
	public static String Title_Settings;
	public static String Title_SFTP;
	public static String Title_WhenToBackup;
	public static String Version;
	public static String Warning_CompareChecksum;
	public static String XEntries;
	static {
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, Messages.class);
	}

	private Messages() {
	}
}
