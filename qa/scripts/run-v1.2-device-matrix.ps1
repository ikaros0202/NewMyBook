[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [AllowEmptyString()]
    [string]$Serial,

    [Parameter(Mandatory = $true)]
    [int]$ExpectedApi,

    [Parameter(Mandatory = $true)]
    [ValidateSet('standard', '16kb')]
    [string]$EndpointKind,

    [Parameter(Mandatory = $true)]
    [string]$Apk,

    [string]$CandidateMetadata,
    [string]$EvidenceRoot = 'qa\v1.2\device-matrix',
    [string]$InstrumentationFilter = 'com.xinyue.reader.ReleaseCriticalJourneyTest',

    [ValidateSet('gesture', 'three-button')]
    [string]$NavigationMode = 'gesture',

    [ValidateSet('phone-portrait', 'phone-landscape', 'tablet', 'split-screen', 'cutout')]
    [string]$WindowMode = 'phone-portrait',

    [string]$DeviceProbeJson,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$qaRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$repoRoot = (Resolve-Path -LiteralPath (Join-Path $qaRoot '..')).Path
$fixture = Join-Path $qaRoot 'sample-novel.txt'
$gradle = Join-Path $repoRoot 'gradlew.bat'
$packageName = 'com.xinyue.reader'
$testPackageName = 'com.xinyue.reader.test'
$instrumentationRunner = "$testPackageName/androidx.test.runner.AndroidJUnitRunner"
$remoteFixture = '/sdcard/Download/sample-novel.txt'
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

function Assert-Matrix {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw "Device matrix contract failed: $Message" }
}

function Invoke-Native {
    param(
        [Parameter(Mandatory = $true)] [string]$Executable,
        [Parameter(Mandatory = $true)] [string[]]$Arguments,
        [switch]$AllowFailure
    )
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = @(& $Executable @Arguments 2>&1)
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if (-not $AllowFailure -and $exitCode -ne 0) {
        $text = ($output | ForEach-Object { $_.ToString() }) -join ' '
        throw "$Executable $($Arguments -join ' ') failed ($exitCode): $text"
    }
    return [pscustomobject]@{
        ExitCode = $exitCode
        Lines = @($output | ForEach-Object { $_.ToString() })
    }
}

function Resolve-SdkRoot {
    foreach ($value in @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME)) {
        if (-not [string]::IsNullOrWhiteSpace($value) -and (Test-Path -LiteralPath $value -PathType Container)) {
            return (Resolve-Path -LiteralPath $value).Path
        }
    }
    $localProperties = Join-Path $repoRoot 'local.properties'
    Assert-Matrix (Test-Path -LiteralPath $localProperties -PathType Leaf) 'Android SDK location is unavailable'
    $line = Get-Content -LiteralPath $localProperties | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
    Assert-Matrix ($null -ne $line) 'local.properties does not define sdk.dir'
    $path = $line.Substring('sdk.dir='.Length).Replace('\:', ':').Replace('/', [IO.Path]::DirectorySeparatorChar)
    Assert-Matrix (Test-Path -LiteralPath $path -PathType Container) "Android SDK directory does not exist: $path"
    return (Resolve-Path -LiteralPath $path).Path
}

function Resolve-Aapt2 {
    param([string]$SdkRoot)
    $candidate = Get-ChildItem -LiteralPath (Join-Path $SdkRoot 'build-tools') -Recurse -File -Filter 'aapt2.exe' |
        Sort-Object { [version]$_.Directory.Name } -Descending |
        Select-Object -First 1
    Assert-Matrix ($null -ne $candidate) 'aapt2.exe was not found'
    return $candidate.FullName
}

function Get-ApkPackageName {
    param([string]$Aapt2, [string]$Path)
    $result = Invoke-Native -Executable $Aapt2 -Arguments @('dump', 'badging', $Path)
    $packageLine = $result.Lines | Where-Object { $_ -match "^package: name='([^']+)'" } | Select-Object -First 1
    Assert-Matrix ($null -ne $packageLine) 'aapt2 did not report an APK package name'
    [void]($packageLine -match "^package: name='([^']+)'")
    return $Matches[1]
}

function Resolve-CandidateContract {
    param([string]$MetadataPath, [string]$ApkPath, [string]$Aapt2)
    Assert-Matrix (-not [string]::IsNullOrWhiteSpace($MetadataPath)) 'candidate metadata path is empty'
    Assert-Matrix (Test-Path -LiteralPath $MetadataPath -PathType Leaf) "candidate metadata is missing: $MetadataPath"
    Assert-Matrix (Test-Path -LiteralPath $ApkPath -PathType Leaf) "APK does not exist: $ApkPath"

    $metadata = Get-Content -LiteralPath $MetadataPath -Raw -Encoding UTF8 | ConvertFrom-Json
    Assert-Matrix ($metadata.versionCode -eq 3 -and $metadata.versionName -eq '0.1.2') 'candidate metadata version is not 0.1.2/code 3'
    $inventoryPath = if ([IO.Path]::IsPathRooted($metadata.inventory)) {
        $metadata.inventory
    } else {
        Join-Path $repoRoot ($metadata.inventory.Replace('/', [IO.Path]::DirectorySeparatorChar))
    }
    Assert-Matrix (Test-Path -LiteralPath $inventoryPath -PathType Leaf) "candidate inventory is missing: $inventoryPath"
    $inventoryHash = (Get-FileHash -LiteralPath $inventoryPath -Algorithm SHA256).Hash.ToUpperInvariant()
    Assert-Matrix ($inventoryHash -eq $metadata.candidateId.ToUpperInvariant()) 'candidateId does not match the canonical inventory'

    $apkHash = (Get-FileHash -LiteralPath $ApkPath -Algorithm SHA256).Hash.ToUpperInvariant()
    Assert-Matrix ($apkHash -eq $metadata.artifacts.acceptanceApk.sha256.ToUpperInvariant()) 'APK hash does not match candidate metadata'
    $applicationId = Get-ApkPackageName -Aapt2 $Aapt2 -Path $ApkPath
    Assert-Matrix ($applicationId -eq $packageName) "APK applicationId/package must be $packageName but was $applicationId"

    $testApkPath = if ([IO.Path]::IsPathRooted($metadata.artifacts.acceptanceTestApk.path)) {
        $metadata.artifacts.acceptanceTestApk.path
    } else {
        Join-Path $repoRoot ($metadata.artifacts.acceptanceTestApk.path.Replace('/', [IO.Path]::DirectorySeparatorChar))
    }
    Assert-Matrix (Test-Path -LiteralPath $testApkPath -PathType Leaf) "candidate test APK is missing: $testApkPath"
    $testApkHash = (Get-FileHash -LiteralPath $testApkPath -Algorithm SHA256).Hash.ToUpperInvariant()
    Assert-Matrix ($testApkHash -eq $metadata.artifacts.acceptanceTestApk.sha256.ToUpperInvariant()) 'test APK hash does not match candidate metadata'
    $testApplicationId = Get-ApkPackageName -Aapt2 $Aapt2 -Path $testApkPath
    Assert-Matrix ($testApplicationId -eq $testPackageName) "test APK applicationId/package must be $testPackageName but was $testApplicationId"

    return [pscustomobject]@{
        Metadata = $metadata
        CandidateId = $inventoryHash
        ApkHash = $apkHash
        ApkPath = (Resolve-Path -LiteralPath $ApkPath).Path
        TestApkHash = $testApkHash
        TestApkPath = (Resolve-Path -LiteralPath $testApkPath).Path
        InventoryPath = (Resolve-Path -LiteralPath $inventoryPath).Path
    }
}

function Resolve-AdbPath {
    param([string]$SdkRoot)
    $command = Get-Command adb -ErrorAction SilentlyContinue
    if ($null -ne $command) { return $command.Source }
    $candidate = Join-Path $SdkRoot 'platform-tools\adb.exe'
    Assert-Matrix (Test-Path -LiteralPath $candidate -PathType Leaf) 'adb.exe was not found'
    return $candidate
}

function Get-LiveProbe {
    param([string]$AdbPath)
    $devicesResult = Invoke-Native -Executable $AdbPath -Arguments @('devices')
    $devices = @()
    foreach ($line in $devicesResult.Lines) {
        if ($line -match '^([^\s]+)\s+([^\s]+)$' -and $Matches[1] -ne 'List') {
            $deviceSerial = $Matches[1]
            $state = $Matches[2]
            $apiResult = Invoke-Native -Executable $AdbPath -Arguments @('-s', $deviceSerial, 'shell', 'getprop', 'ro.build.version.sdk') -AllowFailure
            $pageSizeResult = Invoke-Native -Executable $AdbPath -Arguments @('-s', $deviceSerial, 'shell', 'getconf', 'PAGE_SIZE') -AllowFailure
            $pageSize = if ($pageSizeResult.ExitCode -eq 0 -and $pageSizeResult.Lines.Count -gt 0 -and $pageSizeResult.Lines[0].Trim() -match '^\d+$') {
                $pageSizeResult.Lines[0].Trim()
            } else {
                $smapsResult = Invoke-Native -Executable $AdbPath -Arguments @('-s', $deviceSerial, 'shell', 'grep', '-m', '1', 'KernelPageSize', '/proc/self/smaps') -AllowFailure
                $smapsText = $smapsResult.Lines -join ' '
                if ($smapsResult.ExitCode -eq 0 -and $smapsText -match 'KernelPageSize:\s*(\d+)\s*kB') {
                    ([int]$Matches[1] * 1024).ToString()
                } else {
                    ''
                }
            }
            $devices += [pscustomobject]@{
                serial = $deviceSerial
                state = $state
                api = if ($apiResult.Lines.Count -gt 0) { $apiResult.Lines[0].Trim() } else { '' }
                pageSize = $pageSize
            }
        }
    }
    return [pscustomobject]@{ devices = $devices }
}

function Resolve-DeviceProbe {
    param([string]$ProbePath, [string]$AdbPath)
    if (-not [string]::IsNullOrWhiteSpace($ProbePath)) {
        Assert-Matrix ($DryRun.IsPresent) 'DeviceProbeJson is allowed only with DryRun'
        Assert-Matrix (Test-Path -LiteralPath $ProbePath -PathType Leaf) "device probe fixture is missing: $ProbePath"
        return (Get-Content -LiteralPath $ProbePath -Raw -Encoding UTF8 | ConvertFrom-Json)
    }
    return Get-LiveProbe -AdbPath $AdbPath
}

function Select-Device {
    param([object]$Probe)
    Assert-Matrix (-not [string]::IsNullOrWhiteSpace($Serial)) 'serial is empty'
    $matches = @($Probe.devices | Where-Object { $_.serial -eq $Serial })
    Assert-Matrix ($matches.Count -eq 1) "serial must identify exactly one non-ambiguous device; matches=$($matches.Count)"
    $device = $matches[0]
    Assert-Matrix ($device.state -eq 'device') "device $Serial is unavailable: state=$($device.state)"
    $actualApi = 0
    Assert-Matrix ([int]::TryParse("$($device.api)", [ref]$actualApi)) "device API is invalid: $($device.api)"
    Assert-Matrix ($actualApi -eq $ExpectedApi) "device $Serial API mismatch: expected=$ExpectedApi actual=$actualApi"
    $pageSize = 0
    Assert-Matrix ([int]::TryParse("$($device.pageSize)", [ref]$pageSize)) "device page size is invalid: $($device.pageSize)"
    if ($EndpointKind -eq '16kb') {
        Assert-Matrix ($pageSize -eq 16384) "16 KB endpoint must report page size 16384 but reported $pageSize"
    } else {
        Assert-Matrix ($pageSize -eq 4096) "standard endpoint must report page size 4096 but reported $pageSize"
    }
    return [pscustomobject]@{ Serial = $Serial; Api = $actualApi; PageSize = $pageSize; State = $device.state }
}

function Invoke-SerialAdb {
    param([string[]]$Arguments, [switch]$AllowFailure)
    return Invoke-Native -Executable $script:AdbPath -Arguments (@('-s', $Serial) + $Arguments) -AllowFailure:$AllowFailure
}

function Get-FirstLine {
    param([object]$Result)
    if ($Result.Lines.Count -eq 0) { return '' }
    return $Result.Lines[0].Trim()
}

function Get-ActiveNavigationOverlay {
    $lines = (Invoke-SerialAdb -Arguments @('shell', 'cmd', 'overlay', 'list', '--user', '0') -AllowFailure).Lines
    $match = $lines | Where-Object { $_ -match '^\[x\].*navigationbar' } | Select-Object -First 1
    if ($match -match '([A-Za-z0-9._]+)$') { return $Matches[1] }
    return $null
}

if ([string]::IsNullOrWhiteSpace($CandidateMetadata)) {
    $CandidateMetadata = Join-Path $repoRoot 'qa\staging\v0.1.2\candidate-metadata.json'
}
if (-not [IO.Path]::IsPathRooted($Apk)) { $Apk = Join-Path $repoRoot $Apk }
if (-not [IO.Path]::IsPathRooted($CandidateMetadata)) { $CandidateMetadata = Join-Path $repoRoot $CandidateMetadata }
if (-not [IO.Path]::IsPathRooted($EvidenceRoot)) { $EvidenceRoot = Join-Path $repoRoot $EvidenceRoot }

Assert-Matrix (Test-Path -LiteralPath $fixture -PathType Leaf) 'public fixture is missing'
Assert-Matrix (Test-Path -LiteralPath $gradle -PathType Leaf) 'Gradle wrapper is missing'
$sdkRoot = Resolve-SdkRoot
$aapt2 = Resolve-Aapt2 -SdkRoot $sdkRoot
$script:AdbPath = Resolve-AdbPath -SdkRoot $sdkRoot
$candidate = Resolve-CandidateContract -MetadataPath $CandidateMetadata -ApkPath $Apk -Aapt2 $aapt2
$probe = Resolve-DeviceProbe -ProbePath $DeviceProbeJson -AdbPath $script:AdbPath
$device = Select-Device -Probe $probe

$safeSerial = $Serial -replace '[^A-Za-z0-9._-]', '_'
$scenarioIdentity = "$WindowMode|$NavigationMode|$InstrumentationFilter"
$scenarioAlgorithm = [Security.Cryptography.SHA256]::Create()
try {
    $scenarioHash = ([BitConverter]::ToString(
        $scenarioAlgorithm.ComputeHash([Text.Encoding]::UTF8.GetBytes($scenarioIdentity))
    )).Replace('-', '').Substring(0, 12)
} finally {
    $scenarioAlgorithm.Dispose()
}
$scenario = "$WindowMode-$NavigationMode-s-$scenarioHash"
$candidateDirectory = "candidate-$($candidate.CandidateId.Substring(0, 16))"
$apkDirectory = "apk-$($candidate.ApkHash.Substring(0, 16))"
$endpointRoot = Join-Path $EvidenceRoot (Join-Path "$safeSerial-$EndpointKind" (Join-Path $candidateDirectory (Join-Path $apkDirectory $scenario)))
$testApk = $candidate.TestApkPath
$metadataPath = Join-Path $endpointRoot 'device-metadata.json'
$instrumentationLog = Join-Path $endpointRoot 'instrumentation.log'
$logcatPath = Join-Path $endpointRoot 'logcat.txt'
$fatalScanPath = Join-Path $endpointRoot 'fatal-anr-scan.txt'
$windowFilter = 'com.xinyue.reader.ReaderWindowMatrixTest'
$selectionFilter = 'com.xinyue.reader.ReaderSelectionInstrumentedTest'
$processPrepareFilter = 'com.xinyue.reader.ReaderProcessRecoveryTest#prepareStableProgressForProcessDeath'
$processVerifyFilter = 'com.xinyue.reader.ReaderProcessRecoveryTest#verifyStableProgressAfterProcessDeath'

$commands = @(
    "verify candidateId=$($candidate.CandidateId) apkSha256=$($candidate.ApkHash) testApkSha256=$($candidate.TestApkHash)",
    "adb -s $Serial get-state",
    "adb -s $Serial shell getprop ro.build.version.sdk",
    "adb -s $Serial shell getconf PAGE_SIZE or grep -m 1 KernelPageSize /proc/self/smaps",
    "adb -s $Serial uninstall $packageName",
    "adb -s $Serial uninstall $testPackageName",
    "adb -s $Serial install -r `"$($candidate.ApkPath)`"",
    "adb -s $Serial push `"$fixture`" $remoteFixture",
    "adb -s $Serial shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file://$remoteFixture",
    "adb -s $Serial install -r `"$testApk`"",
    "configure navigationMode=$NavigationMode windowMode=$WindowMode",
    "adb -s $Serial shell am instrument -w -r -e class $InstrumentationFilter $instrumentationRunner",
    "adb -s $Serial shell am instrument -w -r -e class $windowFilter $instrumentationRunner",
    $(if ($EndpointKind -eq '16kb') { "adb -s $Serial shell pm clear $packageName then am instrument -w -r -e class $selectionFilter $instrumentationRunner" } else { "skip selection debt on non-16kb endpoint" }),
    "adb -s $Serial shell am instrument -w -r -e class $processPrepareFilter $instrumentationRunner",
    "adb -s $Serial shell am force-stop $packageName",
    "adb -s $Serial shell am instrument -w -r -e class $processVerifyFilter $instrumentationRunner",
    "adb -s $Serial shell am force-stop $packageName",
    "adb -s $Serial shell monkey -p $packageName 1",
    "scan logcat and write `"$fatalScanPath`""
)
$finallyCommands = @(
    "adb -s $Serial shell settings put system font_scale <original-font-scale>",
    "adb -s $Serial shell settings put system accelerometer_rotation <original-auto-rotation>",
    "adb -s $Serial shell settings put system user_rotation <original-user-rotation>",
    "adb -s $Serial shell cmd overlay enable-exclusive --user 0 --category <original-navigation-overlay>",
    "adb -s $Serial shell settings put global enable_freeform_support <original-multi-window-setting>",
    "adb -s $Serial shell wm size <original-size> or adb -s $Serial shell wm size reset",
    "adb -s $Serial shell wm density <original-density> or adb -s $Serial shell wm density reset",
    "adb -s $Serial shell rm -f $remoteFixture",
    "verify restored font_scale, rotation, freeform, wm size, and wm density"
)

if ($DryRun) {
    [ordered]@{
        serial = $Serial
        expectedApi = $ExpectedApi
        endpointKind = $EndpointKind
        pageSize = $device.PageSize
        navigationMode = $NavigationMode
        windowMode = $WindowMode
        apk = $candidate.ApkPath
        apkSha256 = $candidate.ApkHash
        testApkSha256 = $candidate.TestApkHash
        candidateId = $candidate.CandidateId
        candidateMetadata = (Resolve-Path -LiteralPath $CandidateMetadata).Path
        evidenceRoot = $endpointRoot
        instrumentationFilter = $InstrumentationFilter
        commands = $commands -join "`n"
        finallyCommands = $finallyCommands -join "`n"
    } | ConvertTo-Json -Depth 5
    return
}

New-Item -ItemType Directory -Path $endpointRoot -Force | Out-Null
$status = 'FAIL'
$failure = $null
$startedAt = [DateTimeOffset]::UtcNow.ToString('o')
$originalFontScale = $null
$originalAutoRotation = $null
$originalUserRotation = $null
$originalNavigationOverlay = $null
$originalFreeform = $null
$originalWmSize = $null
$originalWmDensity = $null
$restorationFailure = $null
$restoration = $null

try {
    $originalFontScale = Get-FirstLine (Invoke-SerialAdb -Arguments @('shell', 'settings', 'get', 'system', 'font_scale'))
    $originalAutoRotation = Get-FirstLine (Invoke-SerialAdb -Arguments @('shell', 'settings', 'get', 'system', 'accelerometer_rotation'))
    $originalUserRotation = Get-FirstLine (Invoke-SerialAdb -Arguments @('shell', 'settings', 'get', 'system', 'user_rotation'))
    $originalFreeform = Get-FirstLine (Invoke-SerialAdb -Arguments @('shell', 'settings', 'get', 'global', 'enable_freeform_support'))
    $originalWmSize = (Invoke-SerialAdb -Arguments @('shell', 'wm', 'size')).Lines -join '; '
    $originalWmDensity = (Invoke-SerialAdb -Arguments @('shell', 'wm', 'density')).Lines -join '; '
    $originalNavigationOverlay = Get-ActiveNavigationOverlay

    Invoke-SerialAdb -Arguments @('uninstall', $packageName) -AllowFailure | Out-Null
    Invoke-SerialAdb -Arguments @('uninstall', $testPackageName) -AllowFailure | Out-Null
    Invoke-SerialAdb -Arguments @('install', '-r', $candidate.ApkPath) | Out-Null
    Invoke-SerialAdb -Arguments @('push', $fixture, $remoteFixture) | Out-Null
    Invoke-SerialAdb -Arguments @('shell', 'touch', $remoteFixture) | Out-Null
    Invoke-SerialAdb -Arguments @(
        'shell', 'am', 'broadcast',
        '-a', 'android.intent.action.MEDIA_SCANNER_SCAN_FILE',
        '-d', "file://$remoteFixture"
    ) | Out-Null
    Start-Sleep -Seconds 3
    Invoke-SerialAdb -Arguments @('logcat', '-c') -AllowFailure | Out-Null

    Assert-Matrix (Test-Path -LiteralPath $testApk -PathType Leaf) "acceptance test APK is missing: $testApk"
    Invoke-SerialAdb -Arguments @('install', '-r', $testApk) | Out-Null

    $navigationOverlay = if ($NavigationMode -eq 'gesture') {
        'com.android.internal.systemui.navbar.gestural'
    } else {
        'com.android.internal.systemui.navbar.threebutton'
    }
    Invoke-SerialAdb -Arguments @('shell', 'cmd', 'overlay', 'enable-exclusive', '--user', '0', '--category', $navigationOverlay) -AllowFailure | Out-Null
    Invoke-SerialAdb -Arguments @('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0') | Out-Null
    switch ($WindowMode) {
        'phone-portrait' { Invoke-SerialAdb -Arguments @('shell', 'settings', 'put', 'system', 'user_rotation', '0') | Out-Null }
        'phone-landscape' { Invoke-SerialAdb -Arguments @('shell', 'settings', 'put', 'system', 'user_rotation', '1') | Out-Null }
        'tablet' {
            Invoke-SerialAdb -Arguments @('shell', 'wm', 'size', '1280x800') | Out-Null
            Invoke-SerialAdb -Arguments @('shell', 'wm', 'density', '240') | Out-Null
        }
        'split-screen' { Invoke-SerialAdb -Arguments @('shell', 'settings', 'put', 'global', 'enable_freeform_support', '1') | Out-Null }
        'cutout' { }
    }

    $instrumentation = Invoke-SerialAdb -Arguments @('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', $InstrumentationFilter, $instrumentationRunner)
    [IO.File]::WriteAllLines(
        $instrumentationLog,
        @("=== critical journey: $InstrumentationFilter ===") + $instrumentation.Lines,
        $utf8NoBom
    )
    Assert-Matrix (($instrumentation.Lines -join "`n") -match 'OK \(') 'critical-journey instrumentation did not report OK'

    $window = Invoke-SerialAdb -Arguments @('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', $windowFilter, $instrumentationRunner)
    [IO.File]::AppendAllLines(
        $instrumentationLog,
        [string[]](@("=== window matrix: $windowFilter ===") + $window.Lines),
        $utf8NoBom
    )
    Assert-Matrix (($window.Lines -join "`n") -match 'OK \(') 'window-matrix instrumentation did not report OK'

    if ($EndpointKind -eq '16kb') {
        Invoke-SerialAdb -Arguments @('shell', 'pm', 'clear', $packageName) | Out-Null
        Invoke-SerialAdb -Arguments @('push', $fixture, $remoteFixture) | Out-Null
        Invoke-SerialAdb -Arguments @('shell', 'touch', $remoteFixture) | Out-Null
        Invoke-SerialAdb -Arguments @(
            'shell', 'am', 'broadcast',
            '-a', 'android.intent.action.MEDIA_SCANNER_SCAN_FILE',
            '-d', "file://$remoteFixture"
        ) | Out-Null
        Start-Sleep -Seconds 3
        Invoke-SerialAdb -Arguments @('shell', 'am', 'force-stop', 'com.google.android.documentsui') -AllowFailure | Out-Null
        Invoke-SerialAdb -Arguments @('shell', 'am', 'force-stop', 'com.android.documentsui') -AllowFailure | Out-Null
        $selection = Invoke-SerialAdb -Arguments @('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', $selectionFilter, $instrumentationRunner)
        [IO.File]::AppendAllLines(
            $instrumentationLog,
            [string[]](@("=== API 37 16 KB selection debt: $selectionFilter ===") + $selection.Lines),
            $utf8NoBom
        )
        Assert-Matrix (($selection.Lines -join "`n") -match 'OK \(2 tests\)') 'selection-debt instrumentation did not report both tests OK'
    }

    $prepare = Invoke-SerialAdb -Arguments @('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', $processPrepareFilter, $instrumentationRunner)
    [IO.File]::AppendAllLines(
        $instrumentationLog,
        [string[]](@("=== process recovery prepare: $processPrepareFilter ===") + $prepare.Lines),
        $utf8NoBom
    )
    Assert-Matrix (($prepare.Lines -join "`n") -match 'OK \(') 'process-recovery prepare instrumentation did not report OK'

    Invoke-SerialAdb -Arguments @('shell', 'am', 'force-stop', $packageName) | Out-Null
    [IO.File]::AppendAllLines($instrumentationLog, [string[]]@('=== external am force-stop ==='), $utf8NoBom)
    $verify = Invoke-SerialAdb -Arguments @('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', $processVerifyFilter, $instrumentationRunner)
    [IO.File]::AppendAllLines(
        $instrumentationLog,
        [string[]](@("=== process recovery verify: $processVerifyFilter ===") + $verify.Lines),
        $utf8NoBom
    )
    Assert-Matrix (($verify.Lines -join "`n") -match 'OK \(') 'process-recovery verification instrumentation did not report OK'

    Invoke-SerialAdb -Arguments @('shell', 'am', 'force-stop', $packageName) | Out-Null
    Invoke-SerialAdb -Arguments @('shell', 'monkey', '-p', $packageName, '1') | Out-Null
    Start-Sleep -Milliseconds 1500
    $logcat = Invoke-SerialAdb -Arguments @('logcat', '-d', '-v', 'threadtime')
    [IO.File]::WriteAllLines($logcatPath, $logcat.Lines, $utf8NoBom)
    $fatal = @($logcat.Lines | Select-String -Pattern 'FATAL EXCEPTION|ANR in com\.xinyue\.reader|Process: com\.xinyue\.reader' | ForEach-Object Line)
    if ($fatal.Count -gt 0) {
        [IO.File]::WriteAllLines($fatalScanPath, $fatal, $utf8NoBom)
        throw "fatal/ANR evidence found: $fatalScanPath"
    }
    [IO.File]::WriteAllText($fatalScanPath, "No target-app fatal exception or ANR matched.`n", $utf8NoBom)
    $status = 'PASS'
} catch {
    $failure = $_.Exception.Message
    throw
} finally {
    if ($null -ne $originalFontScale) { Invoke-SerialAdb -Arguments @('shell', 'settings', 'put', 'system', 'font_scale', $originalFontScale) -AllowFailure | Out-Null }
    if ($null -ne $originalAutoRotation) { Invoke-SerialAdb -Arguments @('shell', 'settings', 'put', 'system', 'accelerometer_rotation', $originalAutoRotation) -AllowFailure | Out-Null }
    if ($null -ne $originalUserRotation) { Invoke-SerialAdb -Arguments @('shell', 'settings', 'put', 'system', 'user_rotation', $originalUserRotation) -AllowFailure | Out-Null }
    if (-not [string]::IsNullOrWhiteSpace($originalNavigationOverlay)) { Invoke-SerialAdb -Arguments @('shell', 'cmd', 'overlay', 'enable-exclusive', '--user', '0', '--category', $originalNavigationOverlay) -AllowFailure | Out-Null }
    if ($null -ne $originalFreeform) {
        if ($originalFreeform -eq 'null') { Invoke-SerialAdb -Arguments @('shell', 'settings', 'delete', 'global', 'enable_freeform_support') -AllowFailure | Out-Null }
        else { Invoke-SerialAdb -Arguments @('shell', 'settings', 'put', 'global', 'enable_freeform_support', $originalFreeform) -AllowFailure | Out-Null }
    }
    if ($originalWmSize -match 'Override size:\s*([0-9]+x[0-9]+)') { Invoke-SerialAdb -Arguments @('shell', 'wm', 'size', $Matches[1]) -AllowFailure | Out-Null }
    else { Invoke-SerialAdb -Arguments @('shell', 'wm', 'size', 'reset') -AllowFailure | Out-Null }
    if ($originalWmDensity -match 'Override density:\s*([0-9]+)') { Invoke-SerialAdb -Arguments @('shell', 'wm', 'density', $Matches[1]) -AllowFailure | Out-Null }
    else { Invoke-SerialAdb -Arguments @('shell', 'wm', 'density', 'reset') -AllowFailure | Out-Null }
    Invoke-SerialAdb -Arguments @('shell', 'rm', '-f', $remoteFixture) -AllowFailure | Out-Null

    $finalFontScale = Get-FirstLine (Invoke-SerialAdb -Arguments @('shell', 'settings', 'get', 'system', 'font_scale') -AllowFailure)
    $finalAutoRotation = Get-FirstLine (Invoke-SerialAdb -Arguments @('shell', 'settings', 'get', 'system', 'accelerometer_rotation') -AllowFailure)
    $finalUserRotation = Get-FirstLine (Invoke-SerialAdb -Arguments @('shell', 'settings', 'get', 'system', 'user_rotation') -AllowFailure)
    $finalNavigationOverlay = Get-ActiveNavigationOverlay
    $finalFreeform = Get-FirstLine (Invoke-SerialAdb -Arguments @('shell', 'settings', 'get', 'global', 'enable_freeform_support') -AllowFailure)
    $finalWmSize = (Invoke-SerialAdb -Arguments @('shell', 'wm', 'size') -AllowFailure).Lines -join '; '
    $finalWmDensity = (Invoke-SerialAdb -Arguments @('shell', 'wm', 'density') -AllowFailure).Lines -join '; '
    $fixtureAbsent = (Invoke-SerialAdb -Arguments @('shell', 'test', '!', '-e', $remoteFixture) -AllowFailure).ExitCode -eq 0
    $restorationChecks = [ordered]@{
        fontScale = ($finalFontScale -eq $originalFontScale)
        autoRotation = ($finalAutoRotation -eq $originalAutoRotation)
        userRotation = ($finalUserRotation -eq $originalUserRotation)
        navigationOverlay = ($finalNavigationOverlay -eq $originalNavigationOverlay)
        freeformSupport = ($finalFreeform -eq $originalFreeform)
        wmSize = ($finalWmSize -eq $originalWmSize)
        wmDensity = ($finalWmDensity -eq $originalWmDensity)
        fixtureRemoved = $fixtureAbsent
    }
    $restorationMismatches = @($restorationChecks.Keys | Where-Object { -not $restorationChecks[$_] })
    $restoration = [ordered]@{
        status = $(if ($restorationMismatches.Count -eq 0) { 'PASS' } else { 'FAIL' })
        mismatches = $restorationMismatches
        before = [ordered]@{
            fontScale = $originalFontScale
            autoRotation = $originalAutoRotation
            userRotation = $originalUserRotation
            navigationOverlay = $originalNavigationOverlay
            freeformSupport = $originalFreeform
            wmSize = $originalWmSize
            wmDensity = $originalWmDensity
        }
        after = [ordered]@{
            fontScale = $finalFontScale
            autoRotation = $finalAutoRotation
            userRotation = $finalUserRotation
            navigationOverlay = $finalNavigationOverlay
            freeformSupport = $finalFreeform
            wmSize = $finalWmSize
            wmDensity = $finalWmDensity
            fixtureRemoved = $fixtureAbsent
        }
    }
    if ($restorationMismatches.Count -gt 0) {
        $status = 'FAIL'
        $restorationFailure = "device restoration mismatch: $($restorationMismatches -join ', ')"
        if ([string]::IsNullOrWhiteSpace($failure)) { $failure = $restorationFailure }
    }

    $metadata = [ordered]@{
        status = $status
        failure = $failure
        serial = $Serial
        expectedApi = $ExpectedApi
        actualApi = $device.Api
        endpointKind = $EndpointKind
        pageSize = $device.PageSize
        navigationMode = $NavigationMode
        windowMode = $WindowMode
        candidateId = $candidate.CandidateId
        apkSha256 = $candidate.ApkHash
        apk = $candidate.ApkPath
        testApkSha256 = $candidate.TestApkHash
        testApk = $candidate.TestApkPath
        instrumentationFilter = $InstrumentationFilter
        windowInstrumentationFilter = $windowFilter
        startedAtUtc = $startedAt
        finishedAtUtc = [DateTimeOffset]::UtcNow.ToString('o')
        buildFingerprint = Get-FirstLine (Invoke-SerialAdb -Arguments @('shell', 'getprop', 'ro.build.fingerprint') -AllowFailure)
        abi = Get-FirstLine (Invoke-SerialAdb -Arguments @('shell', 'getprop', 'ro.product.cpu.abi') -AllowFailure)
        model = Get-FirstLine (Invoke-SerialAdb -Arguments @('shell', 'getprop', 'ro.product.model') -AllowFailure)
        restoration = $restoration
    }
    [IO.File]::WriteAllText($metadataPath, (($metadata | ConvertTo-Json -Depth 6) + "`n"), $utf8NoBom)
}

if ($null -ne $restorationFailure) { throw $restorationFailure }

[ordered]@{
    status = $status
    endpoint = $endpointRoot
    metadata = $metadataPath
    candidateId = $candidate.CandidateId
    apkSha256 = $candidate.ApkHash
    testApkSha256 = $candidate.TestApkHash
} | ConvertTo-Json -Depth 4
