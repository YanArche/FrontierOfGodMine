param([Parameter(Mandatory=$true)][string]$Profile,[Parameter(Mandatory=$true)][string]$Baseline)
$ErrorActionPreference='Stop'
Set-Location (Split-Path $PSScriptRoot)
function Check-Exit { if($LASTEXITCODE -ne 0){throw "Build tool exited with code $LASTEXITCODE"} }
& python tools/export.py $Profile $Baseline
Check-Exit
& ./tools/build-launcher.ps1
& "$env:WINDIR/Microsoft.NET/Framework64/v4.0.30319/csc.exe" /nologo /target:winexe /reference:System.Windows.Forms.dll '/win32icon:platform\frontier.ico' '/out:dist\Frontier.exe' '.\platform\Bootstrap.cs'
Check-Exit
if(!(Test-Path dist/loader.jar)){
    Invoke-WebRequest 'https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.267/neoforge-21.1.267-installer.jar' -OutFile dist/loader.jar
}
if((Get-FileHash dist/loader.jar).Hash -ne '25bbe66e82205abeccdfd23bdac61e6a8e9ff1333885f3c9f31d392a5ec97e60'){throw 'NeoForge hash mismatch'}
& python tools/release.py
Check-Exit
