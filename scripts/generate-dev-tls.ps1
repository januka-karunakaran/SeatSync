param([string]$OutputDirectory = (Join-Path $PSScriptRoot '../.local/tls'))
$ErrorActionPreference = 'Stop'
# Password values come from the environment, never command-line literals or committed files.
foreach ($setting in @('SEATSYNC_TLS_KEYSTORE_PASSWORD', 'SEATSYNC_TLS_TRUSTSTORE_PASSWORD')) {
    $secretValue = [Environment]::GetEnvironmentVariable($setting)
    if ([string]::IsNullOrWhiteSpace($secretValue) -or $secretValue.Length -lt 12) {
        throw "Set $setting to a password of at least 12 characters."
    }
}
$tlsDirectory = [IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Path $tlsDirectory -Force | Out-Null
$keyFile = Join-Path $tlsDirectory 'server.p12'
$trustFile = Join-Path $tlsDirectory 'trust.p12'
$certificateFile = Join-Path $tlsDirectory 'localhost.cer'
foreach ($artifact in @($keyFile, $trustFile, $certificateFile)) {
    if (Test-Path -LiteralPath $artifact) { throw "Refusing to overwrite existing TLS material: $artifact" }
}
$keytoolCommand = 'keytool'
if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/keytool.exe'))) {
    $keytoolCommand = Join-Path $env:JAVA_HOME 'bin/keytool.exe'
}
function Invoke-SeatSyncKeytool([string[]]$Arguments) {
    # Windows PowerShell 5 treats keytool's informational stderr as ErrorRecords.
    # Capture it as text and decide success using the native exit code.
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $nativeOutput = & $keytoolCommand @Arguments 2>&1
        $toolExitCode = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previousPreference }
    $nativeOutput | ForEach-Object { Write-Output ([string]$_) }
    if ($toolExitCode -ne 0) { throw 'Certificate generation/inspection command failed.' }
}
Invoke-SeatSyncKeytool @('-genkeypair', '-alias', 'seatsync-local', '-keyalg', 'RSA', '-keysize', '3072', '-validity', '30', '-storetype', 'PKCS12', '-keystore', $keyFile, '-storepass:env', 'SEATSYNC_TLS_KEYSTORE_PASSWORD', '-keypass:env', 'SEATSYNC_TLS_KEYSTORE_PASSWORD', '-dname', 'CN=localhost, O=SeatSync Development, C=LK', '-ext', 'SAN=dns:localhost,ip:127.0.0.1')
Invoke-SeatSyncKeytool @('-exportcert', '-alias', 'seatsync-local', '-keystore', $keyFile, '-storepass:env', 'SEATSYNC_TLS_KEYSTORE_PASSWORD', '-file', $certificateFile)
Invoke-SeatSyncKeytool @('-importcert', '-noprompt', '-alias', 'seatsync-local', '-storetype', 'PKCS12', '-keystore', $trustFile, '-storepass:env', 'SEATSYNC_TLS_TRUSTSTORE_PASSWORD', '-file', $certificateFile)
Invoke-SeatSyncKeytool @('-printcert', '-file', $certificateFile)
Write-Output "Server key material: $keyFile (keep private)."
Write-Output "Client public truststore: $trustFile"
Write-Output 'Verify the SHA-256 certificate fingerprint over a trusted channel before sharing the truststore.'
