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
; 실행 중인 프로그램은 아래 [Code] 에서 꺼질 때까지 기다린다 (Restart Manager 는 Java 앱을 잘 못 닫음)
CloseApplications=no
RestartApplications=no
ShowLanguageDialog=no
LanguageDetectionMethod=none
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible

[Languages]
; 한국어를 먼저 (설치·업데이트 진행 화면이 한국어로 나오게). 언어 파일은 저장소에 함께 둔다
Name: "korean"; MessagesFile: "Korean.isl"
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
{ 프로그램 파일이 아직 쓰이는 중인지: 폴더 이름을 잠깐 바꿔 본다 (쓰는 중이면 바뀌지 않음) }
function FolderInUse(const Dir: String): Boolean;
var
  Tmp: String;
begin
  Result := False;
  if not DirExists(Dir) then Exit;
  Tmp := Dir + '.check';
  if RenameFile(Dir, Tmp) then
    RenameFile(Tmp, Dir)
  else
    Result := True;
end;

function AppInUse(): Boolean;
begin
  Result := FolderInUse(ExpandConstant('{app}\app')) or FolderInUse(ExpandConstant('{app}\runtime'));
end;

{ 앱 안에서 업데이트를 시작하면 앱이 곧 꺼진다. 완전히 꺼질 때까지 기다리고, 안 꺼지면 종료시킨다 }
function PrepareToInstall(var NeedsRestart: Boolean): String;
var
  I, Code: Integer;
begin
  Result := '';
  for I := 1 to 60 do
  begin
    if not AppInUse() then Exit;
    Sleep(500);
  end;
  Exec(ExpandConstant('{sys}\taskkill.exe'), '/F /IM {#MyAppExe}', '', SW_HIDE, ewWaitUntilTerminated, Code);
  for I := 1 to 20 do
  begin
    if not AppInUse() then Exit;
    Sleep(500);
  end;
  Result := 'SkyblockHelper 가 아직 실행 중이에요. 프로그램을 끈 뒤 다시 설치해 주세요.';
end;
