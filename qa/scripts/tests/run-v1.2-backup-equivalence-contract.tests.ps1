param(
    [string]$ScriptUnderTest = (Join-Path $PSScriptRoot "..\run-v1.2-backup-equivalence.ps1")
)

$ErrorActionPreference = "Stop"
$content = Get-Content -Raw -Encoding UTF8 -LiteralPath $ScriptUnderTest
$failures = [System.Collections.Generic.List[string]]::new()

if ($content -notmatch '\[long\]\$ExpectedSampleSizeBytes') {
    $failures.Add("ExpectedSampleSizeBytes must be an explicit long parameter")
}
if ($content -notmatch '\[string\]\$ExpectedSampleSha256') {
    $failures.Add("ExpectedSampleSha256 must be an explicit string parameter")
}
if ($content -match '(?<![A-Fa-f0-9])[A-Fa-f0-9]{64}(?![A-Fa-f0-9])') {
    $failures.Add("The public script must not embed a fixed SHA-256 fingerprint")
}
if ($content -notmatch '\$sample\.Length\s+-ne\s+\$ExpectedSampleSizeBytes') {
    $failures.Add("Sample length must be checked against the caller-provided value")
}
if ($content -notmatch '\$sampleHash\s+-ne\s+\$ExpectedSampleSha256\.ToUpperInvariant\(\)') {
    $failures.Add("Sample hash must be checked against the caller-provided value")
}

if ($failures.Count -gt 0) {
    $failures | ForEach-Object { Write-Error $_ }
    exit 1
}

Write-Output "PASS: backup equivalence script accepts private fingerprints only through parameters."
