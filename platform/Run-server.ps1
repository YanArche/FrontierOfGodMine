param([switch]$Background,[switch]$Offline)
$ErrorActionPreference='Stop'
$root=$PSScriptRoot
$game=Join-Path $root 'server'
if(!(Test-Path (Join-Path $game 'eula.txt'))){
    throw 'Run Prepare-server.cmd, read the Minecraft EULA and set server/eula.txt to eula=true before starting.'
}
if(!(Select-String -LiteralPath (Join-Path $game 'eula.txt') -Pattern '^\s*eula\s*=\s*true\s*$' -Quiet)){
    throw 'The server owner must accept the Minecraft EULA before starting.'
}
$jvm=Join-Path $game 'user_jvm_args.txt'
if(!(Test-Path $jvm) -or !(Select-String -LiteralPath $jvm -Pattern '^\s*-Xmx\d+[mMgG]\s*$' -Quiet)){
    if(Test-Path $jvm){Copy-Item -LiteralPath $jvm -Destination "$jvm.neoforge-default" -ErrorAction Stop}
    Add-Content -LiteralPath $jvm -Value (Get-Content (Join-Path $root 'server-jvm-defaults.txt') -Raw)
}
$flags=@{Server=$true;Offline=$Offline}
if($Background){
    $logs=Join-Path $root 'logs'
    New-Item -ItemType Directory -Force $logs | Out-Null
    $log=Join-Path $logs ((Get-Date -Format 'yyyyMMdd-HHmmss-fff')+'.log')
    & "$root/frontier.ps1" @flags *>> $log
}else{& "$root/frontier.ps1" @flags}
exit $LASTEXITCODE
