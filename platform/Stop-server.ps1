$ErrorActionPreference='Stop'
$state=Join-Path $PSScriptRoot 'server/.frontier'
if(!(Test-Path -LiteralPath $state)){throw 'Prepare the server first.'}
Set-Content -LiteralPath (Join-Path $state 'stop-request') -Value 'stop' -Encoding ascii
Write-Host 'Graceful stop requested. Wait for the task to exit and check the server log before copying worlds or the database.'
