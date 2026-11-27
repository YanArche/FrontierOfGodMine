param([Parameter(Mandatory=$true)][int]$Build,[Parameter(Mandatory=$true)][string]$Version)
$ErrorActionPreference='Stop'
Set-Location (Split-Path $PSScriptRoot)
$public=Get-PfxCertificate -FilePath launcher/release.cer
$cert=Get-Item "Cert:/CurrentUser/My/$($public.Thumbprint)"
$file=Get-Item dist/woidwatch.jar
$manifest=[ordered]@{product='woidwatch-launcher';build=$Build;version=$Version;sha256=(Get-FileHash $file.FullName).Hash.ToLowerInvariant();bytes=$file.Length}
$bytes=[Text.Encoding]::UTF8.GetBytes(($manifest|ConvertTo-Json -Compress))
$key=[Security.Cryptography.X509Certificates.RSACertificateExtensions]::GetRSAPrivateKey($cert)
try{
 $sig=$key.SignData($bytes,[Security.Cryptography.HashAlgorithmName]::SHA256,[Security.Cryptography.RSASignaturePadding]::Pkcs1)
 $out=@{manifest=[Convert]::ToBase64String($bytes);signature=[Convert]::ToBase64String($sig)}|ConvertTo-Json
 [IO.File]::WriteAllText("$pwd/launcher/stable.json",$out,[Text.UTF8Encoding]::new($false))
}finally{$key.Dispose()}
