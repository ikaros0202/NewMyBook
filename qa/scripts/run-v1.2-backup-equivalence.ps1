param(
    [string]$DeviceSerial = "",
    [Parameter(Mandatory = $true)]
    [string]$PrivateSamplePath,
    [Parameter(Mandatory = $true)]
    [long]$ExpectedSampleSizeBytes,
    [Parameter(Mandatory = $true)]
    [ValidatePattern("^[A-Fa-f0-9]{64}$")]
    [string]$ExpectedSampleSha256
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$localProperties = Get-Content (Join-Path $repoRoot "local.properties") -Encoding utf8
$sdkLine = $localProperties | Where-Object { $_ -like "sdk.dir=*" } | Select-Object -First 1
if (-not $sdkLine) { throw "local.properties does not define sdk.dir" }
$sdkRoot = $sdkLine.Substring("sdk.dir=".Length).Replace("\:", ":").Replace("/", "\")
$adb = Join-Path $sdkRoot "platform-tools\adb.exe"
if (-not (Test-Path -LiteralPath $adb)) { throw "Android platform tools are unavailable" }

$devices = & $adb devices
$serials = @($devices | Select-Object -Skip 1 | ForEach-Object {
    if ($_ -match "^(\S+)\s+device$") { $Matches[1] }
} | Where-Object { $_ })
if ($DeviceSerial) {
    if ($DeviceSerial -notin $serials) { throw "Requested emulator is not connected" }
    $serial = $DeviceSerial
} elseif ($serials.Count -eq 1) {
    $serial = $serials[0]
} else {
    throw "Connect exactly one emulator or pass -DeviceSerial"
}
if ($serial -notlike "emulator-*") { throw "Phase D device evidence is emulator-only" }

$sample = Get-Item -LiteralPath $PrivateSamplePath -ErrorAction Stop
if ($sample.Length -ne $ExpectedSampleSizeBytes) {
    throw "Private sample size does not match the caller-provided value"
}
$sampleHash = (Get-FileHash -LiteralPath $sample.FullName -Algorithm SHA256).Hash
if ($sampleHash -ne $ExpectedSampleSha256.ToUpperInvariant()) {
    throw "Private sample hash does not match the caller-provided value"
}
$deviceSampleName = "xinyue-private-sample.txt"
$deviceSample = "/sdcard/Download/$deviceSampleName"
Push-Location $repoRoot
try {
    $previousErrorPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & $adb -s $serial push $sample.FullName $deviceSample *> $null
        $pushExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorPreference
    }
    if ($pushExitCode -ne 0) { throw "Private sample staging failed" }
    & $adb -s $serial shell touch $deviceSample
    if ($LASTEXITCODE -ne 0) { throw "Private sample timestamp normalization failed" }
    & $adb -s $serial shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file://$deviceSample" *> $null

    $api = (& $adb -s $serial shell getprop ro.build.version.sdk).Trim()
    $pageSize = (& $adb -s $serial shell getconf PAGE_SIZE).Trim()
    Write-Output "Phase D backup/restore verification: emulator, API $api, page size $pageSize"

    & .\gradlew.bat :core:data:testDebugUnitTest --tests "*Backup*" --tests "*Restore*" --no-daemon --no-configuration-cache
    if ($LASTEXITCODE -ne 0) { throw "Backup/restore JVM gate failed" }

    & .\gradlew.bat :app:connectedAcceptanceAndroidTest `
        "-Pandroid.testInstrumentationRunnerArguments.class=com.xinyue.reader.BackupRestoreInstrumentedTest" `
        "-Pandroid.testInstrumentationRunnerArguments.privateSampleFile=$deviceSampleName" `
        --no-daemon --no-configuration-cache
    if ($LASTEXITCODE -ne 0) { throw "DocumentsUI clear/restore equivalence gate failed" }

    # Gradle uninstalls the test package after connected tests; reinstall both generated APKs
    # without clearing target data so the two-step force-stop orchestration can run directly.
    $appApk = Join-Path $repoRoot "app\build\outputs\apk\acceptance\app-acceptance.apk"
    $testApk = Join-Path $repoRoot "app\build\outputs\apk\androidTest\acceptance\app-acceptance-androidTest.apk"
    $appInstall = & $adb -s $serial install -r -t $appApk 2>&1
    if (($appInstall -join "`n") -notmatch "Success") { throw "Acceptance APK reinstall failed" }
    $testInstall = & $adb -s $serial install -r -t $testApk 2>&1
    if (($testInstall -join "`n") -notmatch "Success") { throw "Instrumentation APK reinstall failed" }

    $instrumentationLine = & $adb -s $serial shell pm list instrumentation | Where-Object {
        $_ -match "target=com\.xinyue\.reader"
    } | Select-Object -First 1
    if ($instrumentationLine -notmatch "^instrumentation:([^ ]+)") {
        throw "Acceptance instrumentation runner is unavailable"
    }
    $runner = $Matches[1]
    $recoveryClass = "com.xinyue.reader.BackupRecoveryForceStopInstrumentedTest"

    $prepare = & $adb -s $serial shell am instrument -w -r `
        -e recoveryPhase prepare `
        -e class "$recoveryClass#prepareFilesPublishedRecoveryBoundary" `
        $runner 2>&1
    if (($prepare -join "`n") -notmatch "OK \(") { throw "Recovery boundary preparation failed" }

    & $adb -s $serial shell am force-stop com.xinyue.reader
    & $adb -s $serial shell am start -W `
        -n com.xinyue.reader/com.xinyue.reader.MainActivity *> $null
    Start-Sleep -Seconds 2

    $verify = & $adb -s $serial shell am instrument -w -r `
        -e recoveryPhase verify `
        -e class "$recoveryClass#verifyStartupRecoveryAfterForceStop" `
        $runner 2>&1
    if (($verify -join "`n") -notmatch "OK \(") { throw "Force-stop startup recovery verification failed" }
} finally {
    Pop-Location
    & $adb -s $serial shell rm -f $deviceSample *> $null
}

Write-Output "PASS: hostile archives were isolated and the DocumentsUI export-clear-overwrite-restore result was equivalent."
