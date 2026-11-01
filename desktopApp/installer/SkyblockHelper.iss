; SkyblockHelper Windows 설치 프로그램 (Inno Setup 6)
; CI: iscc /DMyAppVersion=1.2.0 desktopApp\installer\SkyblockHelper.iss
; 앱 파일은 ./gradlew :desktopApp:createDistributable 결과물(build\compose\binaries\main\app\SkyblockHelper)을 쓴다.

#ifndef MyAppVersion
  #define MyAppVersion "0.0.0"
#endif
#define MyAppName "SkyblockHelper"
#define MyAppExe "SkyblockHelper.exe"

[Setup]
AppId={{3C2B9E41-6A7D-4F0B-9E5C-1D8A7B6C5E42}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} {#MyAppVersion}
AppPublisher=SkyblockHelper
AppPublisherURL=https://github.com/pkgw07/SkyblockHelper
AppSupportURL=https://github.com/pkgw07/SkyblockHelper/issues
AppUpdatesURL=https://github.com/pkgw07/SkyblockHelper/releases
AppCopyright=SkyblockHelper (open source, https://github.com/pkgw07/SkyblockHelper)
VersionInfoVersion={#MyAppVersion}
VersionInfoCompany=SkyblockHelper
VersionInfoDescription=SkyblockHelper Setup - Hypixel SkyBlock helper
VersionInfoProductName=SkyblockHelper
VersionInfoProductVersion={#MyAppVersion}
DefaultDirName={localappdata}\Programs\{#MyAppName}
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes
DisableDirPage=auto
PrivilegesRequired=lowest
OutputDir=..\build\installer
OutputBaseFilename={#MyAppName}-{#MyAppVersion}
SetupIconFile=..\icons\app.ico
UninstallDisplayIcon={app}\{#MyAppExe}
UninstallDisplayName={#MyAppName}
WizardStyle=modern
WizardImageFile=wizard_large.bmp,wizard_large_2x.bmp
WizardSmallImageFile=wizard_small.bmp,wizard_small_2x.bmp
Compression=lzma2/ultra64
SolidCompression=yes
CloseApplications=yes
RestartApplications=no
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible

[Languages]
#if FileExists(AddBackslash(CompilerPath) + "Languages\Korean.isl")
Name: "korean"; MessagesFile: "compiler:Languages\Korean.isl"
#endif
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Files]
Source: "..\build\compose\binaries\main\app\{#MyAppName}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[InstallDelete]
; 업데이트 때 이전 버전의 라이브러리가 섞이지 않도록 정리
Type: filesandordirs; Name: "{app}\app"
Type: filesandordirs; Name: "{app}\runtime"

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExe}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExe}"; Tasks: desktopicon

[Run]
; 조용한 업데이트(/SILENT)일 때도 설치가 끝나면 다시 실행된다
Filename: "{app}\{#MyAppExe}"; Description: "{cm:LaunchProgram,{#MyAppName}}"; Flags: nowait postinstall
