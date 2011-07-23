!include MUI2.nsh

Name "blizzy's Backup"
OutFile "setup.exe"
InstallDir "$PROGRAMFILES\blizzysbackup"
RequestExecutionLevel admin

XPStyle on
BrandingText " "
ShowInstDetails nevershow
ShowUninstDetails nevershow

!define MUI_ABORTWARNING

!insertmacro MUI_PAGE_LICENSE "license.txt"
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

!insertmacro MUI_LANGUAGE "English"
!insertmacro MUI_LANGUAGE "German"

Function .onInit
	!insertmacro MUI_LANGDLL_DISPLAY
FunctionEnd

Section "-blizzy's Backup"
	SetRegView 64
	InitPluginsDir
	
	Var /global arch
	GetVersion::WindowsPlatformArchitecture
	Pop $arch
	
	CreateDirectory "$LOCALAPPDATA\blizzysbackup"
	NSISunz::Unzip "data-win$arch.zip" "$LOCALAPPDATA\blizzysbackup"
	Pop $0
	StrCmp $0 success unzip_success
		SetDetailsView show
		Abort "unzip failed: $0"
	unzip_success:
		CopyFiles /silent "$LOCALAPPDATA\blizzysbackup\data-win$arch\*" "$LOCALAPPDATA\blizzysbackup"
		RMDir /r "$LOCALAPPDATA\blizzysbackup\data-win$arch"

		CreateDirectory "$INSTDIR"
		SetOutPath "$LOCALAPPDATA\blizzysbackup"
		CreateShortcut "$INSTDIR\blizzy's Backup.lnk" "$LOCALAPPDATA\blizzysbackup\blizzysbackup.exe"
		CreateShortcut "$SMSTARTUP\blizzy's Backup.lnk" "$LOCALAPPDATA\blizzysbackup\blizzysbackup.exe" "-hidden=true"

		WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\blizzy's Backup" "DisplayName" "blizzy's Backup"
		WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\blizzy's Backup" "UninstallString" '"$INSTDIR\uninstall.exe"'
		WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\blizzy's Backup" "NoModify" 1
		WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\blizzy's Backup" "NoRepair" 1
		WriteUninstaller "$INSTDIR\uninstall.exe"
SectionEnd

Section "Uninstall"
	SetRegView 64

	Delete "$SMSTARTUP\blizzy's Backup.lnk"
	RMDir /r "$INSTDIR"
	RMDir /r "$LOCALAPPDATA\blizzysbackup"
	DeleteRegKey HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\blizzy's Backup"
SectionEnd
