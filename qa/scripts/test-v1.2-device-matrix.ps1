$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$runner = Join-Path $PSScriptRoot 'run-v1.2-device-matrix.ps1'
$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$apk = Join-Path $repoRoot 'qa\staging\v0.1.2\xinyue-v0.1.2-acceptance.apk'
$testApk = Join-Path $repoRoot 'qa\staging\v0.1.2\xinyue-v0.1.2-acceptance-androidTest.apk'
$metadata = Join-Path $repoRoot 'qa\staging\v0.1.2\candidate-metadata.json'
$inventory = Join-Path $repoRoot 'qa\staging\v0.1.2\candidate-inputs.sha256'
$debugApk = Join-Path $repoRoot 'app\build\outputs\apk\debug\app-debug.apk'
$testRoot = Join-Path $repoRoot 'qa\staging\device-matrix-contract'
$evidenceRoot = Join-Path $testRoot 'evidence'
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}

function Write-JsonFixture {
    param([string]$Path, [object]$Value)
    [IO.File]::WriteAllText($Path, (($Value | ConvertTo-Json -Depth 8) + "`n"), $utf8NoBom)
}

function Invoke-Runner {
    param(
        [string]$Serial = 'emulator-5554',
        [int]$ExpectedApi = 37,
        [string]$EndpointKind = '16kb',
        [string]$Apk = $apk,
        [string]$CandidateMetadata = $metadata,
        [string]$Probe = (Join-Path $testRoot 'probe-valid.json')
    )

    $arguments = @(
        '-NoProfile',
        '-ExecutionPolicy', 'Bypass',
        '-File', $runner,
        '-Serial', $Serial,
        '-ExpectedApi', "$ExpectedApi",
        '-EndpointKind', $EndpointKind,
        '-Apk', $Apk,
        '-CandidateMetadata', $CandidateMetadata,
        '-EvidenceRoot', $evidenceRoot,
        '-NavigationMode', 'gesture',
        '-WindowMode', 'phone-portrait',
        '-InstrumentationFilter', 'com.xinyue.reader.ReleaseCriticalJourneyTest',
        '-DeviceProbeJson', $Probe,
        '-DryRun'
    )
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = @(& powershell.exe @arguments 2>&1)
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    return [pscustomobject]@{
        ExitCode = $exitCode
        Text = (($output | ForEach-Object { $_.ToString() }) -join "`n")
    }
}

function Assert-RunnerFailure {
    param([object]$Result, [string]$Pattern, [string]$Case)
    Assert-True ($Result.ExitCode -ne 0) "$Case unexpectedly passed"
    Assert-True ($Result.Text -match $Pattern) "$Case failed for the wrong reason: $($Result.Text)"
}

Assert-True (Test-Path -LiteralPath $runner -PathType Leaf) 'device-matrix runner is missing'
Assert-True (Test-Path -LiteralPath $apk -PathType Leaf) 'staged acceptance APK is missing; complete E1 first'
Assert-True (Test-Path -LiteralPath $testApk -PathType Leaf) 'staged acceptance test APK is missing; rebuild the immutable candidate first'
Assert-True (Test-Path -LiteralPath $metadata -PathType Leaf) 'candidate metadata is missing; complete E1 first'
Assert-True (Test-Path -LiteralPath $inventory -PathType Leaf) 'candidate inventory is missing; complete E1 first'

$expectedTestRoot = [IO.Path]::GetFullPath((Join-Path $repoRoot 'qa\staging\device-matrix-contract'))
$actualTestRoot = [IO.Path]::GetFullPath($testRoot)
Assert-True ($expectedTestRoot -eq $actualTestRoot) 'refusing to use an unexpected contract-test directory'
if (Test-Path -LiteralPath $actualTestRoot -PathType Container) {
    Remove-Item -LiteralPath $actualTestRoot -Recurse -Force
}
New-Item -ItemType Directory -Path $actualTestRoot -Force | Out-Null

try {
    if (-not (Test-Path -LiteralPath $debugApk -PathType Leaf)) {
        Push-Location $repoRoot
        try {
            & (Join-Path $repoRoot 'gradlew.bat') :app:assembleDebug --no-configuration-cache --console=plain | Out-Null
            Assert-True ($LASTEXITCODE -eq 0) 'assembleDebug failed while preparing the non-production-package fixture'
        } finally {
            Pop-Location
        }
    }

    $validProbe = [ordered]@{
        devices = @(
            [ordered]@{
                serial = 'emulator-5554'
                state = 'device'
                api = 37
                pageSize = 16384
            }
        )
    }
    Write-JsonFixture -Path (Join-Path $testRoot 'probe-valid.json') -Value $validProbe

    $valid = Invoke-Runner
    Assert-True ($valid.ExitCode -eq 0) "valid dry run failed: $($valid.Text)"
    $plan = $valid.Text | ConvertFrom-Json
    $candidate = Get-Content -LiteralPath $metadata -Raw -Encoding UTF8 | ConvertFrom-Json
    Assert-True ($plan.serial -eq 'emulator-5554') 'serial not preserved'
    Assert-True ($plan.expectedApi -eq 37) 'expected API not preserved'
    Assert-True ($plan.endpointKind -eq '16kb') 'endpoint kind not preserved'
    Assert-True ($plan.navigationMode -eq 'gesture') 'navigation mode not preserved'
    Assert-True ($plan.windowMode -eq 'phone-portrait') 'window mode not preserved'
    Assert-True ($plan.instrumentationFilter -eq 'com.xinyue.reader.ReleaseCriticalJourneyTest') 'instrumentation filter not preserved'
    Assert-True ($plan.candidateId -eq $candidate.candidateId) 'candidateId missing or altered'
    Assert-True ($plan.apkSha256 -eq $candidate.artifacts.acceptanceApk.sha256) 'APK hash missing or altered'
    Assert-True ($plan.testApkSha256 -eq $candidate.artifacts.acceptanceTestApk.sha256) 'test APK hash missing or altered'
    Assert-True ([IO.Path]::GetFullPath($plan.evidenceRoot).Length -lt 240) 'evidence path exceeds the Windows-safe length budget'
    Assert-True ($plan.commands -match [regex]::Escape($candidate.artifacts.acceptanceApk.sha256)) 'APK hash absent from command plan'
    Assert-True ($plan.commands -match [regex]::Escape($candidate.artifacts.acceptanceTestApk.sha256)) 'test APK hash absent from command plan'
    Assert-True ($plan.commands -match 'adb -s emulator-5554 install -r') 'exact APK install command missing'
    Assert-True ($plan.commands -match 'android\.intent\.action\.MEDIA_SCANNER_SCAN_FILE') 'pushed fixture media scan missing'
    Assert-True ($plan.commands -match '/proc/self/smaps') 'API 26 page-size fallback probe missing'
    Assert-True ($plan.commands -notmatch 'assembleAcceptanceAndroidTest') 'matrix must not rebuild the immutable test APK'
    Assert-True ($plan.commands -match 'xinyue-v0\.1\.2-acceptance-androidTest\.apk') 'staged test APK install command missing'
    Assert-True ($plan.commands -match 'am instrument') 'direct instrumentation command missing'
    Assert-True ($plan.commands -match 'com\.xinyue\.reader\.ReaderWindowMatrixTest') 'window-matrix instrumentation missing'
    Assert-True ($plan.commands -match 'com\.xinyue\.reader\.ReaderSelectionInstrumentedTest') 'API 37 selection-debt instrumentation missing'
    Assert-True ($plan.commands -match 'pm clear com\.xinyue\.reader then am instrument') 'selection-debt clean-state setup missing'
    Assert-True ($plan.commands -match 'ReaderProcessRecoveryTest#prepareStableProgressForProcessDeath') 'process-recovery prepare instrumentation missing'
    Assert-True ($plan.commands -match 'am force-stop com\.xinyue\.reader') 'force-stop step missing'
    Assert-True ($plan.commands -match 'ReaderProcessRecoveryTest#verifyStableProgressAfterProcessDeath') 'process-recovery verification instrumentation missing'
    Assert-True ($plan.commands -match 'monkey -p com\.xinyue\.reader 1') 'relaunch step missing'
    Assert-True ($plan.finallyCommands -match 'font_scale') 'font-scale restoration missing'
    Assert-True ($plan.finallyCommands -match 'accelerometer_rotation') 'rotation restoration missing'
    Assert-True ($plan.finallyCommands -match 'user_rotation') 'orientation restoration missing'
    Assert-True ($plan.finallyCommands -match 'cmd overlay') 'navigation-overlay restoration missing'
    Assert-True ($plan.finallyCommands -match 'enable_freeform_support') 'multi-window restoration missing'
    Assert-True ($plan.finallyCommands -match 'wm size reset|wm size <original') 'window-size restoration missing'
    Assert-True ($plan.finallyCommands -match 'wm density reset|wm density <original') 'window-density restoration missing'
    Assert-True ($plan.finallyCommands -match 'verify restored font_scale, rotation, freeform, wm size, and wm density') 'post-restoration verification missing'

    $api26Probe = [ordered]@{ devices = @([ordered]@{ serial = 'emulator-5556'; state = 'device'; api = 26; pageSize = 4096 }) }
    $api26ProbePath = Join-Path $testRoot 'probe-api26.json'
    Write-JsonFixture -Path $api26ProbePath -Value $api26Probe
    $api26 = Invoke-Runner -Serial 'emulator-5556' -ExpectedApi 26 -EndpointKind 'standard' -Probe $api26ProbePath
    Assert-True ($api26.ExitCode -eq 0) "approved API 26 dry run failed: $($api26.Text)"
    $api26Plan = $api26.Text | ConvertFrom-Json
    Assert-True ($api26Plan.expectedApi -eq 26 -and $api26Plan.endpointKind -eq 'standard') 'API 26 endpoint contract was altered'

    Assert-RunnerFailure -Result (Invoke-Runner -Serial ' ') -Pattern 'serial.*empty|empty.*serial' -Case 'empty serial'

    $apiMismatchProbe = [ordered]@{ devices = @([ordered]@{ serial = 'emulator-5554'; state = 'device'; api = 35; pageSize = 16384 }) }
    Write-JsonFixture -Path (Join-Path $testRoot 'probe-api-mismatch.json') -Value $apiMismatchProbe
    Assert-RunnerFailure -Result (Invoke-Runner -Probe (Join-Path $testRoot 'probe-api-mismatch.json')) -Pattern 'API mismatch' -Case 'API mismatch'

    $pageSizeProbe = [ordered]@{ devices = @([ordered]@{ serial = 'emulator-5554'; state = 'device'; api = 37; pageSize = 4096 }) }
    Write-JsonFixture -Path (Join-Path $testRoot 'probe-page-size.json') -Value $pageSizeProbe
    Assert-RunnerFailure -Result (Invoke-Runner -Probe (Join-Path $testRoot 'probe-page-size.json')) -Pattern '16.?KB|16384|page size' -Case '16 KB page-size mismatch'

    $ambiguousProbe = [ordered]@{ devices = @(
        [ordered]@{ serial = 'emulator-5554'; state = 'device'; api = 37; pageSize = 16384 },
        [ordered]@{ serial = 'emulator-5554'; state = 'device'; api = 37; pageSize = 16384 }
    ) }
    Write-JsonFixture -Path (Join-Path $testRoot 'probe-ambiguous.json') -Value $ambiguousProbe
    Assert-RunnerFailure -Result (Invoke-Runner -Probe (Join-Path $testRoot 'probe-ambiguous.json')) -Pattern 'ambiguous|exactly one' -Case 'ambiguous device'

    $alteredCandidate = Get-Content -LiteralPath $metadata -Raw -Encoding UTF8 | ConvertFrom-Json
    $alteredCandidate.candidateId = '0' * 64
    $alteredCandidatePath = Join-Path $testRoot 'candidate-altered.json'
    Write-JsonFixture -Path $alteredCandidatePath -Value $alteredCandidate
    Assert-RunnerFailure -Result (Invoke-Runner -CandidateMetadata $alteredCandidatePath) -Pattern 'candidateId|inventory' -Case 'altered candidateId'

    $alteredTestCandidate = Get-Content -LiteralPath $metadata -Raw -Encoding UTF8 | ConvertFrom-Json
    $alteredTestCandidate.artifacts.acceptanceTestApk.sha256 = '0' * 64
    $alteredTestCandidatePath = Join-Path $testRoot 'candidate-test-apk-altered.json'
    Write-JsonFixture -Path $alteredTestCandidatePath -Value $alteredTestCandidate
    Assert-RunnerFailure -Result (Invoke-Runner -CandidateMetadata $alteredTestCandidatePath) -Pattern 'test APK hash' -Case 'altered test APK hash'

    $debugMetadata = Get-Content -LiteralPath $metadata -Raw -Encoding UTF8 | ConvertFrom-Json
    $debugMetadata.artifacts.acceptanceApk.sha256 = (Get-FileHash -LiteralPath $debugApk -Algorithm SHA256).Hash
    $debugMetadataPath = Join-Path $testRoot 'candidate-debug-package.json'
    Write-JsonFixture -Path $debugMetadataPath -Value $debugMetadata
    Assert-RunnerFailure -Result (Invoke-Runner -Apk $debugApk -CandidateMetadata $debugMetadataPath) -Pattern 'applicationId|package.*com\.xinyue\.reader' -Case 'non-production package'

    'PASS: V1.2 device-matrix dry-run contract'
} finally {
    if (Test-Path -LiteralPath $actualTestRoot -PathType Container) {
        Remove-Item -LiteralPath $actualTestRoot -Recurse -Force
    }
}
