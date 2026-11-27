param([switch]$Invite)
$ErrorActionPreference='Stop'
$store=Split-Path $PSScriptRoot
Set-Location $PSScriptRoot
$env:WOIDWATCH_DATABASE="$store/data/accounts.sqlite"
$env:WOIDWATCH_COOKIE_KEY=[IO.File]::ReadAllText("$store/data/cookie.key")
if($Invite){& "$store/venv/Scripts/python.exe" -m flask --app service invite}else{
 & "$store/venv/Scripts/python.exe" -m waitress --listen=127.0.0.1:8787 service:app
}
exit $LASTEXITCODE
