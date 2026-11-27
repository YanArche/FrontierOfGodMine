param([switch]$Invite)
if($Invite){& "$PSScriptRoot/setup.ps1" -Mode invite}else{throw 'Используйте Start-server.cmd для рабочей службы или Test-accounts.cmd для проверки.'}
