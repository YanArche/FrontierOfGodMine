param([string]$Store=(Join-Path $env:ProgramData 'WoidWatchAccounts'))
#Requires -RunAsAdministrator
$ErrorActionPreference='Stop'
$task='WoidWatch Accounts'
$Store=[IO.Path]::GetFullPath($Store)
if(!(Test-Path "$PSScriptRoot/service.py")){throw 'Распакуйте весь архив перед установкой.'}
New-Item -Force -ItemType Directory "$Store/app","$Store/data" | Out-Null
# Only administrators, SYSTEM and LocalService can access the account database.
& icacls.exe $Store /inheritance:r /grant:r '*S-1-5-32-544:(OI)(CI)F' '*S-1-5-18:(OI)(CI)F' '*S-1-5-19:(OI)(CI)RX' | Out-Null
if($LASTEXITCODE){throw 'Ошибка ACL'}
& icacls.exe "$Store/data" /grant:r '*S-1-5-19:(OI)(CI)M' | Out-Null
if($LASTEXITCODE){throw 'Ошибка ACL базы'}
foreach($name in 'service.py','index.html','account.js','requirements.txt','run.ps1'){Copy-Item -LiteralPath "$PSScriptRoot/$name" -Destination "$Store/app/$name" -Force}
# Use an all-users installation of Python, readable by LocalService.
$python=(Get-Command python.exe -ErrorAction Stop).Source
if($python.StartsWith($env:USERPROFILE,[StringComparison]::OrdinalIgnoreCase)){throw 'Установите Python 3.12+ для всех пользователей, затем выберите его в PATH.'}
& $python -m venv "$Store/venv"
if($LASTEXITCODE){throw 'Ошибка установки venv'}
& "$Store/venv/Scripts/python.exe" -m pip install -r "$Store/app/requirements.txt"
if($LASTEXITCODE){throw 'Ошибка установки зависимостей'}
if(!(Test-Path "$Store/data/cookie.key")){
 $key=New-Object byte[] 48
 $rng=[Security.Cryptography.RandomNumberGenerator]::Create()
 try{$rng.GetBytes($key)}finally{$rng.Dispose()}
 [IO.File]::WriteAllText("$Store/data/cookie.key",[Convert]::ToBase64String($key))
}
$action=New-ScheduledTaskAction -Execute 'powershell.exe' -Argument ('-NoProfile -NonInteractive -ExecutionPolicy Bypass -File "'+$Store+'\app\run.ps1"')
$principal=New-ScheduledTaskPrincipal -UserId 'S-1-5-19' -LogonType ServiceAccount
$settings=New-ScheduledTaskSettingsSet -StartWhenAvailable -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1) -ExecutionTimeLimit ([TimeSpan]::Zero) -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries
Register-ScheduledTask -TaskName $task -Action $action -Trigger (New-ScheduledTaskTrigger -AtStartup) -Settings $settings -Principal $principal -Force | Out-Null
Write-Host "Готово. Для первого запуска выполните Start-ScheduledTask -TaskName 'WoidWatch Accounts'"
