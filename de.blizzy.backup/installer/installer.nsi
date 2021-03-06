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


SetCompressor /SOLID lzma

!include MUI2.nsh

Name "blizzy's Backup"

OutFile "blizzysbackup-setup.exe"
InstallDir "$PROGRAMFILES\blizzysbackup"
RequestExecutionLevel admin

XPStyle on
BrandingText " "
ShowInstDetails nevershow
ShowUninstDetails nevershow

!define MUI_ABORTWARNING
!define MUI_UNABORTWARNING
!define MUI_LICENSEPAGE_RADIOBUTTONS
!define MUI_ICON "logo.ico"
!define MUI_UNICON "logo.ico"
!define MUI_HEADERIMAGE
!define MUI_HEADERIMAGE_BITMAP "logo.bmp"
!define MUI_HEADERIMAGE_BITMAP_NOSTRETCH
!define MUI_HEADERIMAGE_RIGHT

!insertmacro MUI_PAGE_LICENSE "license.txt"
!insertmacro MUI_PAGE_COMPONENTS
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES

!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

!insertmacro MUI_LANGUAGE "English"
!insertmacro MUI_LANGUAGE "German"

LangString TXT_COMPONENT_TITLE_MAIN ${LANG_GERMAN} "blizzy's Backup"
LangString TXT_COMPONENT_DESC_MAIN ${LANG_GERMAN} "Das Hauptprogramm."
LangString TXT_COMPONENT_TITLE_AUTOSTART ${LANG_GERMAN} "Autostart-Verkn�pfung"
LangString TXT_COMPONENT_DESC_AUTOSTART ${LANG_GERMAN} "Eine Verkn�pfung, um das Programm beim Windows-Start zu starten."

LangString TXT_COMPONENT_TITLE_MAIN ${LANG_ENGLISH} "blizzy's Backup"
LangString TXT_COMPONENT_DESC_MAIN ${LANG_ENGLISH} "The main program."
LangString TXT_COMPONENT_TITLE_AUTOSTART ${LANG_ENGLISH} "Auto-start Shortcut"
LangString TXT_COMPONENT_DESC_AUTOSTART ${LANG_ENGLISH} "A shortcut to start the program when Windows starts."

VIAddVersionKey /LANG=${LANG_ENGLISH} "ProductName" "blizzy's Backup"
VIAddVersionKey /LANG=${LANG_ENGLISH} "FileDescription" "blizzy's Backup Installer"
VIAddVersionKey /LANG=${LANG_ENGLISH} "FileVersion" "1.0.0.0"
VIAddVersionKey /LANG=${LANG_ENGLISH} "LegalCopyright" "Copyright (C) 2011-2012 Maik Schreiber"
VIProductVersion "1.0.0.0"

Function .onInit
	!insertmacro MUI_LANGDLL_DISPLAY

	Var /global arch
	GetVersion::WindowsPlatformArchitecture
	Pop $arch
	SetRegView 64
	InitPluginsDir
FunctionEnd

Section "$(TXT_COMPONENT_TITLE_MAIN)" SEC_MAIN
	SetOutPath "$PLUGINSDIR"
	File /r "data-win32"
	File /r "data-win64"
	File "license.txt"

	CreateDirectory "$LOCALAPPDATA\blizzysbackup"
	CopyFiles "$PLUGINSDIR\data-win$arch\*" "$LOCALAPPDATA\blizzysbackup"
	CopyFiles "$PLUGINSDIR\license.txt" "$INSTDIR\license.txt"
	CreateDirectory "$INSTDIR"

	SetOutPath "$LOCALAPPDATA\blizzysbackup"
	CreateShortcut "$INSTDIR\blizzy's Backup.lnk" "$LOCALAPPDATA\blizzysbackup\blizzysbackup.exe"
	CreateShortcut "$SMPROGRAMS\blizzy's Backup.lnk" "$LOCALAPPDATA\blizzysbackup\blizzysbackup.exe"

	WriteUninstaller "$INSTDIR\uninstall.exe"

	WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\blizzy's Backup" "DisplayName" "blizzy's Backup"
	WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\blizzy's Backup" "UninstallString" '"$INSTDIR\uninstall.exe"'
	WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\blizzy's Backup" "NoModify" 1
	WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\blizzy's Backup" "NoRepair" 1
SectionEnd

Section "$(TXT_COMPONENT_TITLE_AUTOSTART)" SEC_AUTOSTART
		CreateShortcut "$SMSTARTUP\blizzy's Backup.lnk" "$LOCALAPPDATA\blizzysbackup\blizzysbackup.exe" "-hidden=true"
SectionEnd

Section "Uninstall"
	Delete "$SMSTARTUP\blizzy's Backup.lnk"
	Delete "$SMPROGRAMS\blizzy's Backup.lnk"
	RMDir /r "$INSTDIR"
	RMDir /r "$LOCALAPPDATA\blizzysbackup"
	DeleteRegKey HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\blizzy's Backup"
SectionEnd

!insertmacro MUI_FUNCTION_DESCRIPTION_BEGIN
	!insertmacro MUI_DESCRIPTION_TEXT ${SEC_MAIN} "$(TXT_COMPONENT_DESC_MAIN)"
	!insertmacro MUI_DESCRIPTION_TEXT ${SEC_AUTOSTART} "$(TXT_COMPONENT_DESC_AUTOSTART)"
!insertmacro MUI_FUNCTION_DESCRIPTION_END
