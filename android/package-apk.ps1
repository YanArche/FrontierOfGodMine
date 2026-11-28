param(
    [Parameter(Mandatory)][string]$InputApk,
    [Parameter(Mandatory)][string]$BuildTools,
    [string]$OutputApk = "$PSScriptRoot/../dist/WoidWatch-Android-4G.apk"
)
$ErrorActionPreference = 'Stop'
$InputApk=(Resolve-Path -LiteralPath $InputApk).Path
$BuildTools=(Resolve-Path -LiteralPath $BuildTools).Path
$OutputApk=[IO.Path]::GetFullPath($OutputApk)
$signer=Join-Path $BuildTools 'apksigner.bat'
$aligner=Join-Path $BuildTools 'zipalign.exe'
$identity=Join-Path $env:LOCALAPPDATA 'WoidWatch Android Signing'
$store=Join-Path $identity 'woidwatch.p12'
$sealed=Join-Path $identity 'password.dpapi'
New-Item -ItemType Directory -Force -Path $identity,([IO.Path]::GetDirectoryName($OutputApk)) | Out-Null

if ((Test-Path $store) -ne (Test-Path $sealed)) {
    throw 'The existing Android signing identity is incomplete.'
}
if (!(Test-Path $store)) {
    $secret=[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
    $env:WW_ANDROID_SIGNING_PASSWORD=$secret
    try {
        & "$env:JAVA_HOME/bin/keytool.exe" -genkeypair -storetype PKCS12 -keystore $store -storepass:env WW_ANDROID_SIGNING_PASSWORD -keypass:env WW_ANDROID_SIGNING_PASSWORD -alias woidwatch -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=WoidWatch Android'
        if ($LASTEXITCODE) { throw 'Android signing key creation failed.' }
        $secret | ConvertTo-SecureString -AsPlainText -Force | ConvertFrom-SecureString | Set-Content -LiteralPath $sealed
    } finally { Remove-Item Env:WW_ANDROID_SIGNING_PASSWORD -ErrorAction SilentlyContinue }
}
$password=Get-Content -LiteralPath $sealed -Raw | ForEach-Object { ConvertTo-SecureString $_.Trim() }
$env:WW_ANDROID_SIGNING_PASSWORD=[Net.NetworkCredential]::new('', $password).Password
$aligned=$OutputApk+'.aligned'
$staged=$OutputApk+'.staged'
try {
    & $aligner -f -P 16 4 $InputApk $aligned
    if ($LASTEXITCODE) { throw 'zipalign failed.' }
    & $signer sign --ks $store --ks-key-alias woidwatch --ks-pass env:WW_ANDROID_SIGNING_PASSWORD --v4-signing-enabled false --out $staged $aligned
    if ($LASTEXITCODE) { throw 'APK signing failed.' }
    & $signer verify --verbose --print-certs $staged
    if ($LASTEXITCODE) { throw 'APK signature verification failed.' }
    Move-Item -LiteralPath $staged -Destination $OutputApk -Force
    $sha=(Get-FileHash -LiteralPath $OutputApk -Algorithm SHA256).Hash.ToLowerInvariant()
    Set-Content -LiteralPath ($OutputApk+'.sha256') -Value "$sha  $([IO.Path]::GetFileName($OutputApk))" -Encoding ascii
} finally {
    Remove-Item Env:WW_ANDROID_SIGNING_PASSWORD -ErrorAction SilentlyContinue
    foreach($temporary in @($aligned,$staged)){if(Test-Path -LiteralPath $temporary){Remove-Item -LiteralPath $temporary}}
}
