#ifndef MyAppVersion
  #define MyAppVersion "1.1"
#endif

#define MyAppName        "Recetas Familiares"
#define MyAppPublisher   "Gipsybuho"
#define MyAppExeName     "RecetasFamiliares.exe"
#define MyAppDir         "RecetasFamiliares"
#define MyAppSourceDir   "output\RecetasFamiliares"
#define MyAppURL         "https://github.com/gipsybuho"

[Setup]
AppId={{5F3A8C2E-1D4B-47F0-9E6A-2C0B5D8E3F1A}
AppName={#MyAppName}
AppVerName={#MyAppName} {#MyAppVersion}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}
DefaultDirName={autopf}\{#MyAppDir}
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes
OutputDir=output
OutputBaseFilename=RecetasFamiliares-Instalador-v{#MyAppVersion}
SetupIconFile=installer\recetas.ico
UninstallDisplayIcon={app}\{#MyAppExeName}
UninstallDisplayName={#MyAppName} v{#MyAppVersion}
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
WizardImageFile=installer\nsis-welcome.bmp
WizardSmallImageFile=installer\recetas-small.bmp
InfoBeforeFile=installer\antes-de-instalar.txt
InfoAfterFile=installer\despues-de-instalar.txt
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0
SetupMutex=RecetasFamiliaresSetup_5F3A8C2E
CloseApplications=yes
CloseApplicationsFilter=*.exe,*.dll
RestartApplications=no
ShowLanguageDialog=auto
SetupLogging=yes
VersionInfoVersion={#MyAppVersion}.0.0
VersionInfoProductVersion={#MyAppVersion}.0.0
VersionInfoCompany={#MyAppPublisher}
VersionInfoDescription={#MyAppName} - Gestion familiar de recetas
VersionInfoProductName={#MyAppName}

[Languages]
Name: "spanish"; MessagesFile: "compiler:Languages\Spanish.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "Crear icono en el escritorio"; GroupDescription: "Iconos adicionales:"; Flags: unchecked

[InstallDelete]
; Limpia mods/runtime de una version anterior antes de copiar los nuevos,
; evita que queden JARs/DLLs de JavaFX huerfanos entre versiones.
Type: filesandordirs; Name: "{app}\mods"
Type: filesandordirs; Name: "{app}\runtime"

[Files]
Source: "{#MyAppSourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#MyAppName}";                          Filename: "{app}\{#MyAppExeName}"
Name: "{group}\{cm:UninstallProgram,{#MyAppName}}";    Filename: "{uninstallexe}"
Name: "{group}\Recetas Familiares en GitHub";          Filename: "{#MyAppURL}"
Name: "{autodesktop}\{#MyAppName}";                    Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
; Barrido final tras desinstalar: no debe quedar nada bajo el directorio de instalacion.
Type: filesandordirs; Name: "{app}"

[Registry]
Root: HKCU; Subkey: "Software\Gipsybuho\RecetasFamiliares"; ValueType: string; ValueName: "InstallDir";  ValueData: "{app}"; Flags: uninsdeletekey
Root: HKCU; Subkey: "Software\Gipsybuho\RecetasFamiliares"; ValueType: string; ValueName: "Version";     ValueData: "{#MyAppVersion}"
Root: HKCU; Subkey: "Software\Gipsybuho\RecetasFamiliares"; ValueType: string; ValueName: "InstallDate"; ValueData: "{code:CurrentDateTimeString}"

[Code]
function CurrentDateTimeString(Param: String): String;
begin
  Result := GetDateTimeString('yyyy/mm/dd hh:nn:ss', '-', ':');
end;

function GetUninstallString(): String;
var
  sUnInstPath:      String;
  sUnInstallString: String;
begin
  sUnInstPath      := 'Software\Microsoft\Windows\CurrentVersion\Uninstall\{5F3A8C2E-1D4B-47F0-9E6A-2C0B5D8E3F1A}_is1';
  sUnInstallString := '';
  if not RegQueryStringValue(HKCU, sUnInstPath, 'UninstallString', sUnInstallString) then
    RegQueryStringValue(HKLM, sUnInstPath, 'UninstallString', sUnInstallString);
  Result := sUnInstallString;
end;

function IsUpgrade(): Boolean;
begin
  Result := (GetUninstallString() <> '');
end;

procedure UnInstallOldVersion();
var
  sUnInstallString: String;
  iResultCode:      Integer;
begin
  sUnInstallString := GetUninstallString();
  if sUnInstallString <> '' then
  begin
    sUnInstallString := RemoveQuotes(sUnInstallString);
    Exec(sUnInstallString, '/SILENT /NORESTART /SUPPRESSMSGBOXES', '', SW_HIDE,
         ewWaitUntilTerminated, iResultCode);
  end;
end;

procedure CurStepChanged(CurStep: TSetupStep);
begin
  if (CurStep = ssInstall) then
    if (IsUpgrade()) then
      UnInstallOldVersion();
end;
