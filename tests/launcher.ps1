# Run from a JDK 21 environment after building dist/woidwatch.jar.
$ErrorActionPreference='Stop'
Set-Location (Split-Path $PSScriptRoot)
$tempRoot=Join-Path ([IO.Path]::GetTempPath()) ('woidwatch-test-'+[guid]::NewGuid())
New-Item -ItemType Directory -Path "$tempRoot/resources","$tempRoot/stub","$tempRoot/test" | Out-Null
$oldLocal=$env:LOCALAPPDATA
function Check { if($LASTEXITCODE){throw "Tool failed ($LASTEXITCODE)"} }
try{
 & keytool -genkeypair -alias test -keyalg RSA -keysize 2048 -validity 1 -dname CN=test -keystore "$tempRoot/qa.p12" -storepass testing -keypass testing
 Check
 & keytool -exportcert -alias test -keystore "$tempRoot/qa.p12" -storepass testing -file "$tempRoot/test/release.cer"
 Check
 @"
import java.nio.file.*;
class Frontier {
 public static void main(String[] args)throws Exception {
   Path ready=Path.of(args[1]);
   Files.createFile(ready);
   Files.createFile(ready.resolveSibling("fallback-ran"));
   Thread.sleep(500);
 }
}
"@ | Set-Content "$tempRoot/stub/Frontier.java" -Encoding ascii
 & javac -d "$tempRoot/stub" "$tempRoot/stub/Frontier.java"
 Check
 & jar cf "$tempRoot/resources/frontier.jar" -C "$tempRoot/stub" Frontier.class
 Check
 Copy-Item lib/gson.jar "$tempRoot/resources"
 & javac -d "$tempRoot/test" -cp 'dist/woidwatch.jar;lib/gson.jar' tests/LauncherChecks.java
 Check
 $env:LOCALAPPDATA=$tempRoot
 & java "-Dfrontier.resources=$tempRoot/resources" -cp "$tempRoot/test;dist/woidwatch.jar;lib/gson.jar" LauncherChecks $tempRoot
 Check
}finally{
 $env:LOCALAPPDATA=$oldLocal
 $resolved=(Resolve-Path -LiteralPath $tempRoot).Path
 if(([IO.Path]::GetDirectoryName($resolved) -eq [IO.Path]::GetTempPath().TrimEnd('\')) -and ([IO.Path]::GetFileName($resolved) -match '^woidwatch-test-[a-f0-9-]{36}$')){
   Remove-Item -LiteralPath $resolved -Recurse -Force
 }
}
