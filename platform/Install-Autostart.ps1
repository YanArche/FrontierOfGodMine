param([Parameter(Mandatory)][PSCredential]$Credential)
$ErrorActionPreference='Stop'
$root=(Resolve-Path -LiteralPath $PSScriptRoot).Path
if([IO.Path]::GetPathRoot($root) -ne 'D:\'){throw 'Extract the server to D:\FrontierServer first.'}
if(!(Test-Path "$root/server/libraries/net/neoforged/neoforge/21.1.267/win_args.txt") -or
   !(Test-Path "$root/data/production/accounts.sqlite")){
    throw 'Run Prepare-server.cmd under the designated server user first.'
}
if(!(Test-Path "$root/server/eula.txt") -or !(Select-String -LiteralPath "$root/server/eula.txt" -Pattern '^\s*eula\s*=\s*true\s*$' -Quiet)){
    throw 'The owner must accept the EULA before registering the scheduled task.'
}
$action=New-ScheduledTaskAction -Execute "$env:SystemRoot/System32/WindowsPowerShell/v1.0/powershell.exe" -Argument ('-NoProfile -NonInteractive -ExecutionPolicy RemoteSigned -File "'+$root+'\Run-server.ps1" -Background') -WorkingDirectory $root
$trigger=New-ScheduledTaskTrigger -AtStartup
$trigger.Delay='PT30S'
$settings=New-ScheduledTaskSettingsSet -MultipleInstances IgnoreNew -ExecutionTimeLimit ([TimeSpan]::Zero) -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 2) -StartWhenAvailable -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries
Register-ScheduledTask -TaskName 'Frontier of God Server' -Action $action -Trigger $trigger -Settings $settings -User $Credential.UserName -Password $Credential.GetNetworkCredential().Password -RunLevel Limited -Force | Out-Null
Write-Host 'Boot task registered. It has not been started.'
