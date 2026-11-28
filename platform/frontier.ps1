param([switch]$Server,[switch]$PrepareOnly,[switch]$Offline)
$ErrorActionPreference="Stop"
try {
  $res=Join-Path $PSScriptRoot resources
  $java=Join-Path $res 'runtime/bin/java.exe'
  $flags=@()
  if($Server){
    & "$PSScriptRoot/accounts/setup.ps1" -Mode prepare
    $flags+= '--server' 
    if($PrepareOnly){$flags+='--prepare'}
    if($Offline){$flags+='--offline'}
    $flags+=(Join-Path $PSScriptRoot server)
  }
  & $java "-Dfrontier.resources=$res" -cp "$res/frontier.jar;$res/gson.jar" Frontier @flags
  if($LASTEXITCODE){throw "Frontier exited with code $LASTEXITCODE"}
} catch {[Console]::Error.WriteLine($_.ToString()); exit 1}
