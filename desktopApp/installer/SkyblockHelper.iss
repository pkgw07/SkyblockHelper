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
CloseApplications=force
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

[Code]
// 예전 설치 방식(v1.1.0, MSI 기반)으로 설치된 SkyblockHelper 가 있으면 조용히 제거한다
procedure RemoveOldMsiInstall();
var
  Keys: TArrayOfString;
  I, Code: Integer;
  Base, Name, Uninst: String;
begin
  Base := 'Software\Microsoft\Windows\CurrentVersion\Uninstall';
  if RegGetSubkeyNames(HKCU, Base, Keys) then
    for I := 0 to GetArrayLength(Keys) - 1 do
    begin
      if RegQueryStringValue(HKCU, Base + '\' + Keys[I], 'DisplayName', Name) and (Name = 'SkyblockHelper') and
         RegQueryStringValue(HKCU, Base + '\' + Keys[I], 'UninstallString', Uninst) and (Pos('MsiExec', Uninst) > 0) then
        Exec('msiexec.exe', '/x ' + Keys[I] + ' /qn', '', SW_HIDE, ewWaitUntilTerminated, Code);
    end;
end;

procedure CurStepChanged(CurStep: TSetupStep);
begin
  if CurStep = ssInstall then
    RemoveOldMsiInstall();
end;
