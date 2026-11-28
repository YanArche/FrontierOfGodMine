param([ValidateSet('prepare','server','stub','invite')][string]$Mode='prepare')
$ErrorActionPreference='Stop'
$serverRoot=Split-Path $PSScriptRoot
$isBundle=Test-Path (Join-Path $serverRoot 'Start-server.cmd')
$store=if($isBundle -and $Mode -ne 'stub'){Join-Path $serverRoot 'data'}else{Join-Path $env:LOCALAPPDATA 'WoidWatchAccountHost'}
$python=Join-Path $store 'venv/Scripts/python.exe'
if(!(Test-Path $python)){
  New-Item -ItemType Directory -Force $store | Out-Null
  & python -m venv "$store/venv"
  if($LASTEXITCODE){throw 'Install Python 3.12+ and enable it in PATH.'}
}
$requirements=Join-Path $PSScriptRoot requirements.txt
$hash=(Get-FileHash $requirements).Hash
$stamp=Join-Path $store dependencies.txt
if(!(Test-Path $stamp) -or (Get-Content $stamp -Raw).Trim() -ne $hash){
  & $python -m pip install -r $requirements
  if($LASTEXITCODE){throw 'Account service dependencies failed to install'}
  Set-Content $stamp $hash
}
$data=Join-Path $store 'production'
$old=Join-Path $env:ProgramData 'WoidWatchAccounts/data'
if(!$isBundle -and (Test-Path "$old/accounts.sqlite")){$data=$old}
if($Mode -eq 'stub'){$data=Join-Path $store 'test'}
if($Mode -eq 'server'){throw 'Use Start-server.cmd to start production accounts and Minecraft together.'}
& $python "$PSScriptRoot/host.py" --mode $Mode --data $data
if($LASTEXITCODE){throw 'Account host failed'}
$env:WOIDWATCH_HOST_PYTHON=$python
$env:WOIDWATCH_HOST_SCRIPT=Join-Path $PSScriptRoot 'host.py'
$env:WOIDWATCH_HOST_DATA=$data
