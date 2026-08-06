; ==============================================================
; installer.nsi — Instalador NSIS para Recetas Familiares
; NSIS 3.x + Modern UI 2
; ==============================================================

; La version la inyecta build-installer.ps1 con /DAPP_VERSION=x.y para que
; exista una sola fuente de verdad. El valor de aqui solo actua de red de
; seguridad si alguien compila el .nsi a mano.
!ifndef APP_VERSION
    !define APP_VERSION "1.2"
!endif

!include "MUI2.nsh"
!include "FileFunc.nsh"
!include "LogicLib.nsh"

; ── Configuracion general ──────────────────────────────────────
Name              "Recetas Familiares"
OutFile           "output\RecetasFamiliares-Instalador-v${APP_VERSION}.exe"
InstallDir        "$LOCALAPPDATA\RecetasFamiliares"
InstallDirRegKey  HKCU "Software\RecetasFamiliares" "InstallDir"
RequestExecutionLevel user
Unicode True
SetCompressor /SOLID lzma
SetCompressorDictSize 32

; ── Iconos ─────────────────────────────────────────────────────
!define MUI_ICON   "installer\recetas.ico"
!define MUI_UNICON "installer\recetas.ico"

; ── Panel izquierdo bienvenida (164x314 px) ────────────────────
!define MUI_WELCOMEFINISHPAGE_BITMAP   "installer\nsis-welcome.bmp"
!define MUI_UNWELCOMEFINISHPAGE_BITMAP "installer\nsis-welcome.bmp"

; ── Cabecera paginas interiores (150x57 px) ───────────────────
!define MUI_HEADERIMAGE
!define MUI_HEADERIMAGE_BITMAP         "installer\nsis-header.bmp"
!define MUI_HEADERIMAGE_BITMAP_NOSTRETCH
!define MUI_HEADERIMAGE_RIGHT

; ── Textos ─────────────────────────────────────────────────────
!define MUI_WELCOMEPAGE_TITLE "Recetas Familiares ${APP_VERSION}"
!define MUI_WELCOMEPAGE_TEXT "Bienvenido al instalador de Recetas Familiares.$\r$\n$\r$\nTu recetario familiar digital: guarda recetas, gestiona ingredientes, planifica menus y comparte la memoria culinaria de tu familia.$\r$\n$\r$\nNo necesitas instalar Java. La aplicacion incluye todo lo necesario.$\r$\n$\r$\nHaz clic en Siguiente para continuar."

!define MUI_FINISHPAGE_TITLE "Instalacion completada"
!define MUI_FINISHPAGE_TEXT "Recetas Familiares v${APP_VERSION} se ha instalado correctamente.$\r$\n$\r$\nSe han creado accesos directos en el escritorio y el menu de inicio.$\r$\n$\r$\nNecesitas el backend en ejecucion para conectarte (ver documentacion).$\r$\n$\r$\nHaz clic en Finalizar para cerrar el instalador."
!define MUI_FINISHPAGE_RUN      "$INSTDIR\RecetasFamiliares.exe"
!define MUI_FINISHPAGE_RUN_TEXT "Abrir Recetas Familiares ahora"

!define MUI_ABORTWARNING
!define MUI_ABORTWARNING_TEXT "La instalacion no ha terminado. Si cierras ahora, la aplicacion no quedara instalada.$\r$\n$\r$\n¿Deseas salir del instalador?"

; ── Paginas ────────────────────────────────────────────────────
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH

!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

!insertmacro MUI_LANGUAGE "Spanish"

; ── Version info embebida en el .exe ──────────────────────────
VIProductVersion "${APP_VERSION}.0.0"
VIAddVersionKey /LANG=0 "ProductName"     "Recetas Familiares"
VIAddVersionKey /LANG=0 "ProductVersion"  "${APP_VERSION}"
VIAddVersionKey /LANG=0 "CompanyName"     "Gipsybuho"
VIAddVersionKey /LANG=0 "FileDescription" "Instalador de Recetas Familiares"
VIAddVersionKey /LANG=0 "FileVersion"     "${APP_VERSION}.0.0"
VIAddVersionKey /LANG=0 "LegalCopyright"  "(C) 2026 Gipsybuho"

; ── Detectar version anterior ─────────────────────────────────
Function .onInit
    ReadRegStr $R0 HKCU "Software\RecetasFamiliares" "InstallDir"
    ${If} $R0 != ""
    ${AndIf} ${FileExists} "$R0\Desinstalar.exe"
        MessageBox MB_YESNO|MB_ICONQUESTION \
            "Se ha detectado una version anterior de Recetas Familiares.$\r$\n$\r$\nSe desinstalara automaticamente antes de instalar la nueva version.$\r$\n$\r$\n¿Deseas continuar?" \
            IDNO abort_install
        ExecWait '"$R0\Desinstalar.exe" /S'
        Goto done_check
        abort_install:
            Abort
        done_check:
    ${EndIf}
FunctionEnd

; ── Instalacion ────────────────────────────────────────────────
Section "Aplicacion" SecMain
    SectionIn RO

    SetOutPath "$INSTDIR"
    SetOverwrite on

    ; Copiar app-image completa (JDK embebido + JavaFX + app)
    File /r "output\RecetasFamiliares\*.*"

    ; Registro
    WriteRegStr HKCU "Software\RecetasFamiliares" "InstallDir" "$INSTDIR"
    WriteRegStr HKCU "Software\RecetasFamiliares" "Version"    "${APP_VERSION}"

    ; Desinstalador
    WriteUninstaller "$INSTDIR\Desinstalar.exe"

    ; Agregar/Quitar programas
    WriteRegStr HKCU \
        "Software\Microsoft\Windows\CurrentVersion\Uninstall\RecetasFamiliares" \
        "DisplayName" "Recetas Familiares"
    WriteRegStr HKCU \
        "Software\Microsoft\Windows\CurrentVersion\Uninstall\RecetasFamiliares" \
        "DisplayVersion" "${APP_VERSION}"
    WriteRegStr HKCU \
        "Software\Microsoft\Windows\CurrentVersion\Uninstall\RecetasFamiliares" \
        "Publisher" "Gipsybuho"
    WriteRegStr HKCU \
        "Software\Microsoft\Windows\CurrentVersion\Uninstall\RecetasFamiliares" \
        "UninstallString" '"$INSTDIR\Desinstalar.exe"'
    WriteRegStr HKCU \
        "Software\Microsoft\Windows\CurrentVersion\Uninstall\RecetasFamiliares" \
        "DisplayIcon" "$INSTDIR\RecetasFamiliares.exe,0"
    WriteRegStr HKCU \
        "Software\Microsoft\Windows\CurrentVersion\Uninstall\RecetasFamiliares" \
        "InstallLocation" "$INSTDIR"
    WriteRegDWORD HKCU \
        "Software\Microsoft\Windows\CurrentVersion\Uninstall\RecetasFamiliares" \
        "NoModify" 1
    WriteRegDWORD HKCU \
        "Software\Microsoft\Windows\CurrentVersion\Uninstall\RecetasFamiliares" \
        "NoRepair" 1

    ${GetSize} "$INSTDIR" "/S=0K" $0 $1 $2
    IntFmt $0 "0x%08X" $0
    WriteRegDWORD HKCU \
        "Software\Microsoft\Windows\CurrentVersion\Uninstall\RecetasFamiliares" \
        "EstimatedSize" "$0"

    ; Menu inicio
    CreateDirectory "$SMPROGRAMS\Recetas Familiares"
    CreateShortcut \
        "$SMPROGRAMS\Recetas Familiares\Recetas Familiares.lnk" \
        "$INSTDIR\RecetasFamiliares.exe" "" "$INSTDIR\RecetasFamiliares.exe" 0
    CreateShortcut \
        "$SMPROGRAMS\Recetas Familiares\Desinstalar.lnk" \
        "$INSTDIR\Desinstalar.exe"

    ; Escritorio
    CreateShortcut \
        "$DESKTOP\Recetas Familiares.lnk" \
        "$INSTDIR\RecetasFamiliares.exe" "" "$INSTDIR\RecetasFamiliares.exe" 0
SectionEnd

; ── Desinstalacion ─────────────────────────────────────────────
Section "Uninstall"
    RMDir /r "$INSTDIR"

    Delete "$SMPROGRAMS\Recetas Familiares\Recetas Familiares.lnk"
    Delete "$SMPROGRAMS\Recetas Familiares\Desinstalar.lnk"
    RMDir  "$SMPROGRAMS\Recetas Familiares"
    Delete "$DESKTOP\Recetas Familiares.lnk"

    DeleteRegKey HKCU \
        "Software\Microsoft\Windows\CurrentVersion\Uninstall\RecetasFamiliares"
    DeleteRegKey HKCU "Software\RecetasFamiliares"
SectionEnd
