[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][AllowEmptyString()][string]$Serial,
    [Parameter(Mandatory = $true)][int]$ExpectedApi,
    [Parameter(Mandatory = $true)][ValidateSet('standard', '16kb')][string]$EndpointKind,
    [Parameter(Mandatory = $true)][string]$Apk,
    [string]$CandidateMetadata = 'qa\staging\v0.1.2\candidate-metadata.json',
    [string]$EvidenceRoot = 'qa\v1.2\accessibility-matrix',
    [string]$DeviceProbeJson,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$deviceRunner = Join-Path $PSScriptRoot 'run-v1.2-device-matrix.ps1'
$fixture = Join-Path $repoRoot 'qa\sample-novel.txt'
$remoteFixture = '/sdcard/Download/sample-novel.txt'
$testFilter = 'com.xinyue.reader.ReaderV12AccessibilityTest'
$visualTestFilter = 'com.xinyue.reader.ReaderV12AccessibilityTest#captureRequestedReaderVisualState'
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

function Assert-Accessibility {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw "Accessibility matrix contract failed: $Message" }
}

function Resolve-RepoPath {
    param([string]$Path)
    if ([IO.Path]::IsPathRooted($Path)) { return [IO.Path]::GetFullPath($Path) }
    return [IO.Path]::GetFullPath((Join-Path $repoRoot $Path))
}

function Invoke-Native {
    param([string]$Executable, [string[]]$Arguments, [switch]$AllowFailure)
    $old = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $lines = @(& $Executable @Arguments 2>&1 | ForEach-Object { $_.ToString() })
        $exit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $old }
    if (-not $AllowFailure -and $exit -ne 0) { throw "$Executable $($Arguments -join ' ') failed ($exit): $($lines -join ' ')" }
    [pscustomobject]@{ ExitCode = $exit; Lines = $lines }
}

function Invoke-Adb {
    param([string[]]$Arguments, [switch]$AllowFailure)
    Invoke-Native -Executable $script:Adb -Arguments (@('-s', $Serial) + $Arguments) -AllowFailure:$AllowFailure
}

function First-Line {
    param([object]$Result)
    if ($Result.Lines.Count -eq 0) { return '' }
    return $Result.Lines[0].Trim()
}

function Read-Setting {
    param([string]$Namespace, [string]$Name)
    First-Line (Invoke-Adb -Arguments @('shell', 'settings', 'get', $Namespace, $Name) -AllowFailure)
}

function Restore-Setting {
    param([string]$Namespace, [string]$Name, [string]$Value)
    if ($Value -eq 'null' -or $Value -eq '') {
        Invoke-Adb -Arguments @('shell', 'settings', 'delete', $Namespace, $Name) -AllowFailure | Out-Null
    } else {
        Invoke-Adb -Arguments @('shell', 'settings', 'put', $Namespace, $Name, $Value) -AllowFailure | Out-Null
    }
}

function Read-UiModeNight {
    First-Line (Invoke-Adb -Arguments @('shell', 'cmd', 'uimode', 'night') -AllowFailure)
}

function Set-UiModeNight {
    param([ValidateSet('yes', 'no', 'auto', 'custom')][string]$Mode)
    Invoke-Adb -Arguments @('shell', 'cmd', 'uimode', 'night', $Mode) | Out-Null
}

function Restore-UiModeNight {
    param([string]$Captured)
    if ($Captured -match 'Night mode:\s*(yes|no|auto|custom)') {
        Set-UiModeNight -Mode $Matches[1]
    } else {
        throw "Accessibility matrix contract failed: captured ui mode is not restorable: $Captured"
    }
}

Assert-Accessibility (-not [string]::IsNullOrWhiteSpace($Serial)) 'serial is empty'
Assert-Accessibility (Test-Path -LiteralPath $deviceRunner -PathType Leaf) 'E2 device validator is missing'
Assert-Accessibility (Test-Path -LiteralPath $fixture -PathType Leaf) 'public fixture is missing'
$Apk = Resolve-RepoPath $Apk
$CandidateMetadata = Resolve-RepoPath $CandidateMetadata
$EvidenceRoot = Resolve-RepoPath $EvidenceRoot

$validationArgs = @(
    '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $deviceRunner,
    '-Serial', $Serial, '-ExpectedApi', "$ExpectedApi", '-EndpointKind', $EndpointKind,
    '-Apk', $Apk, '-CandidateMetadata', $CandidateMetadata,
    '-EvidenceRoot', $EvidenceRoot, '-NavigationMode', 'gesture', '-WindowMode', 'phone-portrait', '-DryRun'
)
if (-not [string]::IsNullOrWhiteSpace($DeviceProbeJson)) { $validationArgs += @('-DeviceProbeJson', (Resolve-RepoPath $DeviceProbeJson)) }
$validation = Invoke-Native -Executable 'powershell.exe' -Arguments $validationArgs
$validated = ($validation.Lines -join "`n") | ConvertFrom-Json
$candidate = Get-Content -LiteralPath $CandidateMetadata -Raw -Encoding UTF8 | ConvertFrom-Json
$testApk = Resolve-RepoPath $candidate.artifacts.acceptanceTestApk.path
Assert-Accessibility (Test-Path -LiteralPath $testApk -PathType Leaf) 'immutable acceptance test APK is missing'
$testHash = (Get-FileHash -LiteralPath $testApk -Algorithm SHA256).Hash.ToUpperInvariant()
Assert-Accessibility ($testHash -eq $candidate.artifacts.acceptanceTestApk.sha256) 'acceptance test APK hash mismatch'

$captureCommands = @(
    'settings get system font_scale',
    'settings get secure high_text_contrast_enabled',
    'settings get secure ui_night_mode; cmd uimode night',
    'settings get system accelerometer_rotation; settings get system user_rotation',
    'settings get global window_animation_scale',
    'settings get global transition_animation_scale',
    'settings get global animator_duration_scale',
    'settings get secure enabled_accessibility_services; settings get secure accessibility_enabled',
    'cmd overlay list --user 0 for navigation mode',
    'wm size; wm density'
)
$commands = @(
    "verify candidateId=$($candidate.candidateId) apkSha256=$($candidate.artifacts.acceptanceApk.sha256) testApkSha256=$testHash",
    "adb -s $Serial install -r $Apk",
    "adb -s $Serial install -r $testApk",
    "adb -s $Serial push $fixture $remoteFixture and media scan",
    "row font_scale 1.0 light normal: am instrument $testFilter",
    "row font_scale 2.0 dark high-contrast: am instrument $testFilter",
    "visual portrait preset-paper preset-sepia preset-green: am instrument $visualTestFilter then decode instrumentation status PNG chunks",
    "visual landscape preset-dark focus-band: am instrument $visualTestFilter then decode instrumentation status PNG chunks",
    "visual portrait custom-high-contrast custom-low-contrast: am instrument $visualTestFilter then decode instrumentation status PNG chunks",
    "visual tablet preset-oled: am instrument $visualTestFilter then decode instrumentation status PNG chunks",
    "visual system-light system-dark scheduled-day scheduled-night: am instrument $visualTestFilter then decode instrumentation status PNG chunks",
    "visual library-covers-long-title statistics backup-conflict-preview: am instrument $visualTestFilter then decode instrumentation status PNG chunks",
    'switch live system appearance with cmd uimode night no and cmd uimode night yes',
    'restore original wm size before non-tablet visual rows',
    'observe enabled_accessibility_services only; prompt manual TalkBack journey when disabled',
    'capture public-fixture screenshots and scan target-app Fatal/ANR'
)
$restoreCommands = @(
    'restore font_scale',
    'restore high_text_contrast_enabled',
    'restore ui_night_mode with settings/cmd uimode',
    'restore accelerometer_rotation and user_rotation',
    'restore window_animation_scale',
    'restore transition_animation_scale',
    'restore animator_duration_scale',
    'restore wm size and wm density',
    'verify navigation mode unchanged',
    'verify restored accessibility matrix settings'
)

if ($DryRun) {
    [ordered]@{
        serial = $Serial
        expectedApi = $ExpectedApi
        endpointKind = $EndpointKind
        candidateId = $candidate.candidateId
        apkSha256 = $candidate.artifacts.acceptanceApk.sha256
        testApkSha256 = $testHash
        commands = $commands -join "`n"
        captureCommands = $captureCommands -join "`n"
        restoreCommands = $restoreCommands -join "`n"
        talkBackPolicy = 'observe-only; never change enabled_accessibility_services or accessibility_enabled'
    } | ConvertTo-Json -Depth 5
    return
}

$sdkLine = Get-Content -LiteralPath (Join-Path $repoRoot 'local.properties') -Encoding UTF8 |
    Where-Object { $_ -like 'sdk.dir=*' } | Select-Object -First 1
Assert-Accessibility ($null -ne $sdkLine) 'sdk.dir is unavailable'
$sdkRoot = $sdkLine.Substring('sdk.dir='.Length).Replace('\:', ':').Replace('/', '\')
$script:Adb = Join-Path $sdkRoot 'platform-tools\adb.exe'
Assert-Accessibility (Test-Path -LiteralPath $script:Adb -PathType Leaf) 'adb.exe is missing'

$safeEndpoint = if ($EndpointKind -eq '16kb') { 'api37-16kb' } else { "api$ExpectedApi-standard" }
$evidenceDir = Join-Path $EvidenceRoot "$safeEndpoint-candidate-$($candidate.candidateId.Substring(0,16))"
New-Item -ItemType Directory -Path $evidenceDir -Force | Out-Null
$logPath = Join-Path $evidenceDir 'instrumentation.log'
$metadataPath = Join-Path $evidenceDir 'accessibility-metadata.json'
$fatalPath = Join-Path $evidenceDir 'fatal-anr-scan.txt'

$before = [ordered]@{
    fontScale = Read-Setting system font_scale
    highTextContrast = Read-Setting secure high_text_contrast_enabled
    uiNightMode = Read-Setting secure ui_night_mode
    uiModeNight = Read-UiModeNight
    autoRotation = Read-Setting system accelerometer_rotation
    userRotation = Read-Setting system user_rotation
    windowAnimation = Read-Setting global window_animation_scale
    transitionAnimation = Read-Setting global transition_animation_scale
    animatorDuration = Read-Setting global animator_duration_scale
    accessibilityServices = Read-Setting secure enabled_accessibility_services
    accessibilityEnabled = Read-Setting secure accessibility_enabled
    navigationOverlay = (Invoke-Adb -Arguments @('shell', 'cmd', 'overlay', 'list', '--user', '0') -AllowFailure).Lines -join '; '
    wmSize = (Invoke-Adb -Arguments @('shell', 'wm', 'size') -AllowFailure).Lines -join '; '
    wmDensity = (Invoke-Adb -Arguments @('shell', 'wm', 'density') -AllowFailure).Lines -join '; '
}
$status = 'FAIL'
$failure = $null
$started = [DateTimeOffset]::UtcNow.ToString('o')
$rows = @()
$restoration = $null
try {
    Invoke-Adb -Arguments @('uninstall', 'com.xinyue.reader') -AllowFailure | Out-Null
    Invoke-Adb -Arguments @('uninstall', 'com.xinyue.reader.test') -AllowFailure | Out-Null
    Invoke-Adb -Arguments @('install', '-r', $Apk) | Out-Null
    Invoke-Adb -Arguments @('install', '-r', $testApk) | Out-Null
    Invoke-Adb -Arguments @('push', $fixture, $remoteFixture) | Out-Null
    Invoke-Adb -Arguments @('shell', 'touch', $remoteFixture) | Out-Null
    Invoke-Adb -Arguments @('shell', 'am', 'broadcast', '-a', 'android.intent.action.MEDIA_SCANNER_SCAN_FILE', '-d', "file://$remoteFixture") | Out-Null
    Start-Sleep -Seconds 2
    Invoke-Adb -Arguments @('logcat', '-c') -AllowFailure | Out-Null

    $rowSpecs = @(
        [ordered]@{ name = 'font100-light-normal'; font = '1.0'; night = 'no'; contrast = '0' },
        [ordered]@{ name = 'font200-dark-high-contrast'; font = '2.0'; night = 'yes'; contrast = '1' }
    )
    foreach ($row in $rowSpecs) {
        Invoke-Adb -Arguments @('shell', 'settings', 'put', 'system', 'font_scale', $row.font) | Out-Null
        Set-UiModeNight -Mode $row.night
        Invoke-Adb -Arguments @('shell', 'settings', 'put', 'secure', 'high_text_contrast_enabled', $row.contrast) | Out-Null
        Invoke-Adb -Arguments @('shell', 'am', 'force-stop', 'com.xinyue.reader') | Out-Null
        $result = Invoke-Adb -Arguments @('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', $testFilter, 'com.xinyue.reader.test/androidx.test.runner.AndroidJUnitRunner')
        [IO.File]::AppendAllLines($logPath, [string[]](@("=== $($row.name) ===") + $result.Lines), $utf8NoBom)
        Assert-Accessibility (($result.Lines -join "`n") -match 'OK \(') "$($row.name) instrumentation did not report OK"
        $rows += [ordered]@{ name = $row.name; fontScale = $row.font; uiNightMode = $row.night; highTextContrast = $row.contrast; status = 'PASS'; screenshot = $null }
    }

    $visualSpecs = @(
        [ordered]@{ name = 'preset-paper'; font = '1.0'; night = 'no'; contrast = '0'; rotation = '0'; window = 'phone-portrait' },
        [ordered]@{ name = 'preset-sepia'; font = '1.0'; night = 'no'; contrast = '0'; rotation = '0'; window = 'phone-portrait' },
        [ordered]@{ name = 'preset-green'; font = '2.0'; night = 'no'; contrast = '0'; rotation = '0'; window = 'phone-portrait' },
        [ordered]@{ name = 'custom-high-contrast'; font = '1.0'; night = 'no'; contrast = '0'; rotation = '0'; window = 'phone-portrait' },
        [ordered]@{ name = 'custom-low-contrast'; font = '2.0'; night = 'yes'; contrast = '1'; rotation = '0'; window = 'phone-portrait' },
        [ordered]@{ name = 'preset-dark'; font = '1.0'; night = 'yes'; contrast = '0'; rotation = '1'; window = 'phone-landscape' },
        [ordered]@{ name = 'focus-band'; font = '2.0'; night = 'no'; contrast = '0'; rotation = '1'; window = 'phone-landscape' },
        [ordered]@{ name = 'preset-oled'; font = '2.0'; night = 'yes'; contrast = '1'; rotation = '0'; window = 'tablet-portrait' },
        [ordered]@{ name = 'system-light'; font = '1.0'; night = 'no'; contrast = '0'; rotation = '0'; window = 'phone-portrait' },
        [ordered]@{ name = 'system-dark'; font = '1.0'; night = 'yes'; contrast = '0'; rotation = '0'; window = 'phone-portrait' },
        [ordered]@{ name = 'scheduled-day'; font = '1.0'; night = 'no'; contrast = '0'; rotation = '0'; window = 'phone-portrait' },
        [ordered]@{ name = 'scheduled-night'; font = '1.0'; night = 'yes'; contrast = '0'; rotation = '0'; window = 'phone-portrait' },
        [ordered]@{ name = 'library-covers-long-title'; font = '2.0'; night = 'no'; contrast = '0'; rotation = '0'; window = 'tablet-portrait' },
        [ordered]@{ name = 'statistics'; font = '2.0'; night = 'no'; contrast = '0'; rotation = '0'; window = 'phone-portrait' },
        [ordered]@{ name = 'backup-conflict-preview'; font = '2.0'; night = 'yes'; contrast = '1'; rotation = '0'; window = 'phone-portrait' }
    )
    foreach ($row in $visualSpecs) {
        Invoke-Adb -Arguments @('shell', 'settings', 'put', 'system', 'font_scale', $row.font) | Out-Null
        Set-UiModeNight -Mode $row.night
        Invoke-Adb -Arguments @('shell', 'settings', 'put', 'secure', 'high_text_contrast_enabled', $row.contrast) | Out-Null
        Invoke-Adb -Arguments @('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0') | Out-Null
        Invoke-Adb -Arguments @('shell', 'settings', 'put', 'system', 'user_rotation', $row.rotation) | Out-Null
        if ($row.window -eq 'tablet-portrait') {
            Invoke-Adb -Arguments @('shell', 'wm', 'size', '1080x1920') | Out-Null
            Invoke-Adb -Arguments @('shell', 'wm', 'density', '240') | Out-Null
        } elseif ($before.wmSize -match 'Override size:\s*([0-9]+x[0-9]+)') {
            Invoke-Adb -Arguments @('shell', 'wm', 'size', $Matches[1]) | Out-Null
        } else {
            Invoke-Adb -Arguments @('shell', 'wm', 'size', 'reset') | Out-Null
        }
        if ($row.window -ne 'tablet-portrait') {
            if ($before.wmDensity -match 'Override density:\s*([0-9]+)') {
                Invoke-Adb -Arguments @('shell', 'wm', 'density', $Matches[1]) | Out-Null
            } else {
                Invoke-Adb -Arguments @('shell', 'wm', 'density', 'reset') | Out-Null
            }
        }
        Start-Sleep -Milliseconds 800
        $localShot = Join-Path $evidenceDir "$($row.name).png"
        $result = Invoke-Adb -Arguments @(
            'shell', 'am', 'instrument', '-w', '-r',
            '-e', 'class', $visualTestFilter,
            '-e', 'visualScenario', $row.name,
            'com.xinyue.reader.test/androidx.test.runner.AndroidJUnitRunner'
        )
        [IO.File]::AppendAllLines($logPath, [string[]](@("=== visual $($row.name) ===") + $result.Lines), $utf8NoBom)
        Assert-Accessibility (($result.Lines -join "`n") -match 'OK \(') "$($row.name) visual instrumentation did not report OK"
        $chunkPrefix = "XINYUE_SCREENSHOT_CHUNK=$($row.name):"
        $chunkLines = @($result.Lines | Where-Object { $_ -like "*$chunkPrefix*" })
        Assert-Accessibility ($chunkLines.Count -gt 0) "$($row.name) instrumentation screenshot chunks are missing"
        $chunks = @{}
        $expectedChunkCount = $null
        foreach ($line in $chunkLines) {
            $payload = $line.Substring($line.IndexOf($chunkPrefix) + $chunkPrefix.Length)
            $parts = $payload.Split(':', 3)
            Assert-Accessibility ($parts.Count -eq 3) "$($row.name) screenshot chunk is malformed"
            $index = [int]$parts[0]
            $count = [int]$parts[1]
            if ($null -eq $expectedChunkCount) { $expectedChunkCount = $count }
            Assert-Accessibility ($count -eq $expectedChunkCount) "$($row.name) screenshot chunk count changed"
            $chunks[$index] = $parts[2].Trim()
        }
        Assert-Accessibility ($chunks.Count -eq $expectedChunkCount) "$($row.name) screenshot chunks are incomplete"
        $builder = New-Object Text.StringBuilder
        for ($index = 0; $index -lt $expectedChunkCount; $index++) {
            Assert-Accessibility $chunks.ContainsKey($index) "$($row.name) screenshot chunk $index is missing"
            [void]$builder.Append($chunks[$index])
        }
        $base64 = $builder.ToString()
        [IO.File]::WriteAllBytes($localShot, [Convert]::FromBase64String($base64))
        Assert-Accessibility ((Test-Path -LiteralPath $localShot -PathType Leaf) -and ((Get-Item -LiteralPath $localShot).Length -gt 0)) "$($row.name) screenshot pull failed"
        $rows += [ordered]@{
            name = $row.name; fontScale = $row.font; uiNightMode = $row.night
            highTextContrast = $row.contrast; rotation = $row.rotation; window = $row.window
            status = 'PASS'; screenshot = [IO.Path]::GetFileName($localShot)
        }
    }
    $logcat = Invoke-Adb -Arguments @('logcat', '-d', '-v', 'threadtime')
    $fatal = @($logcat.Lines | Select-String -Pattern 'FATAL EXCEPTION|ANR in com\.xinyue\.reader|Process: com\.xinyue\.reader' | ForEach-Object Line)
    Assert-Accessibility ($fatal.Count -eq 0) 'target-app Fatal/ANR detected'
    [IO.File]::WriteAllText($fatalPath, "No target-app fatal exception or ANR matched.`n", $utf8NoBom)
    $status = 'PASS'
} catch {
    $failure = $_.Exception.Message
    throw
} finally {
    Restore-Setting system font_scale $before.fontScale
    Restore-Setting secure high_text_contrast_enabled $before.highTextContrast
    Restore-UiModeNight $before.uiModeNight
    Restore-Setting secure ui_night_mode $before.uiNightMode
    Restore-Setting system accelerometer_rotation $before.autoRotation
    Restore-Setting system user_rotation $before.userRotation
    Restore-Setting global window_animation_scale $before.windowAnimation
    Restore-Setting global transition_animation_scale $before.transitionAnimation
    Restore-Setting global animator_duration_scale $before.animatorDuration
    if ($before.wmSize -match 'Override size:\s*([0-9]+x[0-9]+)') {
        Invoke-Adb -Arguments @('shell', 'wm', 'size', $Matches[1]) -AllowFailure | Out-Null
    } else {
        Invoke-Adb -Arguments @('shell', 'wm', 'size', 'reset') -AllowFailure | Out-Null
    }
    if ($before.wmDensity -match 'Override density:\s*([0-9]+)') {
        Invoke-Adb -Arguments @('shell', 'wm', 'density', $Matches[1]) -AllowFailure | Out-Null
    } else {
        Invoke-Adb -Arguments @('shell', 'wm', 'density', 'reset') -AllowFailure | Out-Null
    }
    Invoke-Adb -Arguments @('shell', 'rm', '-f', $remoteFixture) -AllowFailure | Out-Null
    $after = [ordered]@{
        fontScale = Read-Setting system font_scale
        highTextContrast = Read-Setting secure high_text_contrast_enabled
        uiNightMode = Read-Setting secure ui_night_mode
        uiModeNight = Read-UiModeNight
        autoRotation = Read-Setting system accelerometer_rotation
        userRotation = Read-Setting system user_rotation
        windowAnimation = Read-Setting global window_animation_scale
        transitionAnimation = Read-Setting global transition_animation_scale
        animatorDuration = Read-Setting global animator_duration_scale
        accessibilityServices = Read-Setting secure enabled_accessibility_services
        accessibilityEnabled = Read-Setting secure accessibility_enabled
        navigationOverlay = (Invoke-Adb -Arguments @('shell', 'cmd', 'overlay', 'list', '--user', '0') -AllowFailure).Lines -join '; '
        wmSize = (Invoke-Adb -Arguments @('shell', 'wm', 'size') -AllowFailure).Lines -join '; '
        wmDensity = (Invoke-Adb -Arguments @('shell', 'wm', 'density') -AllowFailure).Lines -join '; '
    }
    $keys = @('fontScale','highTextContrast','uiNightMode','uiModeNight','autoRotation','userRotation','windowAnimation','transitionAnimation','animatorDuration','accessibilityServices','accessibilityEnabled','navigationOverlay','wmSize','wmDensity')
    $effectiveEquivalences = @()
    $mismatches = @($keys | Where-Object {
        if ($_ -eq 'fontScale' -and $before[$_] -eq 'null' -and $after[$_] -eq '1.0') {
            $effectiveEquivalences += 'fontScale:null-default=1.0'
            return $false
        }
        return $before[$_] -ne $after[$_]
    })
    $restoration = [ordered]@{
        status = $(if ($mismatches.Count -eq 0) { 'PASS' } else { 'FAIL' })
        mismatches = $mismatches
        effectiveEquivalences = $effectiveEquivalences
        before = $before
        after = $after
    }
    if ($mismatches.Count -gt 0) { $status = 'FAIL'; if ($null -eq $failure) { $failure = "restoration mismatch: $($mismatches -join ', ')" } }
    $metadata = [ordered]@{
        status = $status; failure = $failure; candidateId = $candidate.candidateId
        apkSha256 = $candidate.artifacts.acceptanceApk.sha256; testApkSha256 = $testHash
        expectedApi = $ExpectedApi; endpointKind = $EndpointKind; pageSize = $validated.pageSize
        startedAtUtc = $started; finishedAtUtc = [DateTimeOffset]::UtcNow.ToString('o')
        talkBack = [ordered]@{ policy = 'observe-only'; enabledServices = $before.accessibilityServices; accessibilityEnabled = $before.accessibilityEnabled }
        rows = $rows; restoration = $restoration
    }
    [IO.File]::WriteAllText($metadataPath, (($metadata | ConvertTo-Json -Depth 8) + "`n"), $utf8NoBom)
}

Assert-Accessibility ($status -eq 'PASS') $failure
[ordered]@{ status = $status; evidence = $evidenceDir; metadata = $metadataPath; candidateId = $candidate.candidateId } | ConvertTo-Json -Depth 4
