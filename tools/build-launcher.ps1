$ErrorActionPreference='Stop'
Set-Location (Split-Path $PSScriptRoot)
New-Item -Force -ItemType Directory dist | Out-Null
$classes=Join-Path (Resolve-Path dist).Path ('classes-'+[guid]::NewGuid())
New-Item -ItemType Directory $classes | Out-Null
try{
 & javac --release 21 -encoding UTF-8 -cp lib/gson.jar -d $classes wardrobe/ww/Choice.java (Get-ChildItem src/*.java).FullName
 if($LASTEXITCODE){throw 'javac failed'}
 & jar cf dist/woidwatch.jar -C $classes . -C launcher release.cer -C launcher art -C launcher woidwatch-wardrobe.jar
 if($LASTEXITCODE){throw 'jar failed'}
 Copy-Item dist/woidwatch.jar dist/frontier.jar -Force
}finally{
 $p=(Resolve-Path -LiteralPath $classes).Path
 if(([IO.Path]::GetDirectoryName($p) -eq (Resolve-Path dist).Path) -and ([IO.Path]::GetFileName($p) -match '^classes-[a-f0-9-]{36}$')){
    Remove-Item -LiteralPath $p -Recurse -Force
 }
}
