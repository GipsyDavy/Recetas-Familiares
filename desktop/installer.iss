#define MyAppName        "Recetas Familiares"
#define MyAppVersion     "1.0.0"
#define MyAppPublisher   "Gipsybuho"
#define MyAppExeName     "RecetasFamiliares.exe"
#define MyAppDir         "RecetasFamiliares"
#define MyAppSourceDir   "output\RecetasFamiliares"

[Setup]
AppId={{5F3A8C2E-1D4B-47F0-9E6A-2C0B5D8E3F1A}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL=https://github.com/gipsybuho
AppSupportURL=https://github.com/gipsybuho
AppUpdatesURL=https://github.com/gipsybuho
DefaultDirName={autopf}\{#MyAppDir}
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes
OutputDir=output
OutputBaseFilename=RecetasFamiliares-Instalador-v{#MyAppVersion}
SetupIconFile=installer\recetas.ico
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
UninstallDisplayIcon={app}\{#MyAppExeName}
UninstallDisplayName={#MyAppName} v{#MyAppVersion}
VersionInfoVersion={#MyAppVersion}
VersionInfoCompany={#MyAppPublisher}
VersionInfoDescription={#MyAppName} - Gestion familiar de recetas

[Languages]
Name: "spanish"; MessagesFile: "compiler:Languages\Spanish.isl"

[Tasks]
Name: "desktopicon"; Description: "Crear icono en el escritorio"; GroupDescription: "Iconos adicionales:"; Flags: unchecked

[Files]
Source: "{#MyAppSourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#MyAppName}";                          Filename: "{app}\{#MyAppExeName}"
Name: "{group}\{cm:UninstallProgram,{#MyAppName}}";    Filename: "{uninstallexe}"
Name: "{autodesktop}\{#MyAppName}";                    Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent

[Registry]
Root: HKCU; Subkey: "Software\Gipsybuho\RecetasFamiliares"; ValueType: string; ValueName: "InstallDir"; ValueData: "{app}"; Flags: uninsdeletesubkey
Root: HKCU; Subkey: "Software\Gipsybuho\RecetasFamiliares"; ValueType: string; ValueName: "Version";    ValueData: "{#MyAppVersion}"

[Code]
function GetUninstallString(): String;
var
  sUnInstPath:      String;
  sUnInstallString: String;
begin
  sUnInstPath      := ExpandConstant('Software\Microsoft\Windows\CurrentVersion\Uninstall\{5F3A8C2E-1D4B-47F0-9E6A-2C0B5D8E3F1A}_is1');
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
