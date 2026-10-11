$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot '..\assert-empty-acceptance-device.ps1')

foreach ($scenario in @('clean', 'owner-installed', 'stopped-user-retained', 'test-package', 'probe-failed')) {
    $script:probeCalls = @()
    $script:safetyScenario = $scenario
    $failed = $false
    try {
        Assert-EmptyAcceptanceDevice -InvokeAdb {
            param($AdbArguments)
            $script:probeCalls += ($AdbArguments -join ' ')
            if ($AdbArguments[-1] -eq 'users') {
                return [pscustomobject]@{ ExitCode = 0; Lines = @('Users:', 'UserInfo{0:Owner:13} running', 'UserInfo{17:NewMyBook_Public_QA:10}') }
            }
            if ($script:safetyScenario -eq 'probe-failed') { return [pscustomobject]@{ ExitCode = 1; Lines = @() } }
            $blocked = ($script:safetyScenario -eq 'owner-installed' -and $AdbArguments[-2] -eq '0') -or
                ($script:safetyScenario -eq 'stopped-user-retained' -and $AdbArguments[-2] -eq '17') -or
                ($script:safetyScenario -eq 'test-package')
            $lines = if ($blocked) { @($(if ($script:safetyScenario -eq 'test-package') { 'package:com.xinyue.reader.test' } else { 'package:com.xinyue.reader' })) } else { @('package:com.xinyue.reader.debug') }
            [pscustomobject]@{ ExitCode = 0; Lines = $lines }
        }
    } catch { $failed = $true }
    if ($failed -ne ($scenario -ne 'clean')) { throw "Wrong safety result: $scenario" }
    if (($script:probeCalls -join "`n") -match 'uninstall|clear|install') { throw 'Safety checks must be read-only' }
}
foreach ($name in @('run-v1.2-device-matrix.ps1', 'run-v1.2-accessibility-matrix.ps1', 'run-v1.6-api37-structured-txt.ps1')) {
    $content = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot "..\$name")
    if ($content -notmatch 'Assert-EmptyAcceptanceDevice -InvokeAdb') { throw "Missing safety check: $name" }
    if ($content -match "@\('install', '-r'") { throw "Replacing install remains: $name" }
}
$selection = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot '..\..\..\benchmark\src\main\kotlin\com\xinyue\reader\benchmark\ReleaseSelectionJourney.kt')
if ($selection -match 'pm clear|uninstall') { throw 'Direct selection verification must preserve existing libraries' }
if ($selection -notmatch 'NewMyBook_Public_QA' -or $selection -notmatch 'user != 0') { throw 'Direct selection verification must require the reusable public user' }
Write-Output 'PASS: legacy acceptance refuses installed or retained packages in every user before mutation.'
