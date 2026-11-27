param([switch]$Server,[switch]$PrepareOnly)
$ErrorActionPreference="Stop"
try {
  $res=Join-Path $PSScriptRoot resources
  $java=Join-Path $res 'runtime/bin/java.exe'
  $flags=@()
  if($Server){
    $flags+= '--server'
    if($PrepareOnly){$flags+='--prepare'}
    $flags+=(Join-Path $PSScriptRoot server)
  }
  & $java "-Dfrontier.resources=$res" -cp "$res/frontier.jar;$res/gson.jar" Frontier @flags
  if($LASTEXITCODE){throw "Frontier exited with code $LASTEXITCODE"}
} catch {Write-Host $_; Read-Host 'Press Enter'; exit 1}
