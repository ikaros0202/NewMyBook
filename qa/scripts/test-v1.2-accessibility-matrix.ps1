$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$runner = Join-Path $PSScriptRoot 'run-v1.2-accessibility-matrix.ps1'
$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$metadata = Join-Path $repoRoot 'qa\staging\v0.1.2\candidate-metadata.json'
$apk = Join-Path $repoRoot 'qa\staging\v0.1.2\xinyue-v0.1.2-acceptance.apk'
$testRoot = Join-Path $repoRoot 'qa\staging\accessibility-contract'
$probe = Join-Path $testRoot 'probe.json'
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}

Assert-True (Test-Path -LiteralPath $runner -PathType Leaf) 'accessibility runner is missing'
Assert-True (Test-Path -LiteralPath $metadata -PathType Leaf) 'candidate metadata is missing'
Assert-True (Test-Path -LiteralPath $apk -PathType Leaf) 'candidate APK is missing'

if (Test-Path -LiteralPath $testRoot) { Remove-Item -LiteralPath $testRoot -Recurse -Force }
New-Item -ItemType Directory -Path $testRoot -Force | Out-Null
try {
    [IO.File]::WriteAllText(
        $probe,
        (([ordered]@{ devices = @([ordered]@{ serial = 'emulator-5554'; state = 'device'; api = 37; pageSize = 16384 }) } | ConvertTo-Json -Depth 5) + "`n"),
        $utf8NoBom
    )
    $output = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $runner `
        -Serial emulator-5554 -ExpectedApi 37 -EndpointKind 16kb `
        -Apk $apk -CandidateMetadata $metadata -EvidenceRoot (Join-Path $testRoot 'evidence') `
        -DeviceProbeJson $probe -DryRun 2>&1
    Assert-True ($LASTEXITCODE -eq 0) "valid accessibility dry run failed: $($output -join ' ')"
    $plan = ($output -join "`n") | ConvertFrom-Json
    $candidate = Get-Content -LiteralPath $metadata -Raw -Encoding UTF8 | ConvertFrom-Json

    Assert-True ($plan.candidateId -eq $candidate.candidateId) 'candidateId missing or altered'
    Assert-True ($plan.apkSha256 -eq $candidate.artifacts.acceptanceApk.sha256) 'APK hash missing or altered'
    Assert-True ($plan.testApkSha256 -eq $candidate.artifacts.acceptanceTestApk.sha256) 'test APK hash missing or altered'
    Assert-True ($plan.commands -match 'font_scale 1\.0' -and $plan.commands -match 'font_scale 2\.0') '100%/200% font rows missing'
    Assert-True ($plan.commands -match 'ReaderV12AccessibilityTest') 'reader accessibility instrumentation missing'
    foreach ($scenario in @(
        'preset-paper','preset-sepia','preset-green','preset-dark','preset-oled',
        'custom-high-contrast','custom-low-contrast','focus-band',
        'system-light','system-dark','scheduled-day','scheduled-night',
        'library-covers-long-title','statistics','backup-conflict-preview'
    )) {
        Assert-True ($plan.commands -match [regex]::Escape($scenario)) "visual scenario missing: $scenario"
    }
    Assert-True ($plan.commands -match 'captureRequestedReaderVisualState') 'in-app screenshot capture test missing'
    Assert-True ($plan.commands -match 'instrumentation status PNG chunks') 'instrumentation screenshot extraction path missing'
    Assert-True ($plan.commands -match 'portrait' -and $plan.commands -match 'landscape' -and $plan.commands -match 'tablet') 'portrait/landscape/tablet visual rows missing'
    Assert-True ($plan.commands -match 'cmd uimode night no' -and $plan.commands -match 'cmd uimode night yes') 'live system light/dark switching missing'
    Assert-True ($plan.commands -match 'restore original wm size before non-tablet visual rows') 'phone window restoration after tablet row missing'
    Assert-True ($plan.captureCommands -match 'font_scale') 'font scale capture missing'
    Assert-True ($plan.captureCommands -match 'high_text_contrast_enabled') 'high-text-contrast capture missing'
    Assert-True ($plan.captureCommands -match 'ui_night_mode|uimode') 'dark-mode capture missing'
    Assert-True ($plan.captureCommands -match 'accelerometer_rotation|user_rotation') 'rotation capture missing'
    Assert-True ($plan.captureCommands -match 'window_animation_scale' -and $plan.captureCommands -match 'transition_animation_scale' -and $plan.captureCommands -match 'animator_duration_scale') 'animation-scale capture missing'
    Assert-True ($plan.captureCommands -match 'enabled_accessibility_services') 'accessibility-service observation missing'
    Assert-True ($plan.restoreCommands -match 'font_scale') 'font scale restoration missing'
    Assert-True ($plan.restoreCommands -match 'high_text_contrast_enabled') 'high-text-contrast restoration missing'
    Assert-True ($plan.restoreCommands -match 'ui_night_mode|uimode') 'dark-mode restoration missing'
    Assert-True ($plan.restoreCommands -match 'accelerometer_rotation|user_rotation') 'rotation restoration missing'
    Assert-True ($plan.restoreCommands -match 'window_animation_scale' -and $plan.restoreCommands -match 'transition_animation_scale' -and $plan.restoreCommands -match 'animator_duration_scale') 'animation-scale restoration missing'
    Assert-True ($plan.restoreCommands -match 'verify restored accessibility matrix settings') 'post-restoration verification missing'
    Assert-True ($plan.commands -notmatch 'settings put secure enabled_accessibility_services|settings put secure accessibility_enabled') 'runner must never enable or disable accessibility services'
    Assert-True ($plan.talkBackPolicy -match 'observe-only') 'TalkBack observe-only policy missing'

    $badProbe = Join-Path $testRoot 'bad-probe.json'
    [IO.File]::WriteAllText(
        $badProbe,
        (([ordered]@{ devices = @([ordered]@{ serial = 'emulator-5554'; state = 'device'; api = 37; pageSize = 4096 }) } | ConvertTo-Json -Depth 5) + "`n"),
        $utf8NoBom
    )
    $oldPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $runner `
            -Serial emulator-5554 -ExpectedApi 37 -EndpointKind 16kb `
            -Apk $apk -CandidateMetadata $metadata -EvidenceRoot (Join-Path $testRoot 'bad') `
            -DeviceProbeJson $badProbe -DryRun *> $null
        $badExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $oldPreference }
    Assert-True ($badExit -ne 0) '16 KiB row accepted a 4 KiB endpoint'
} finally {
    if (Test-Path -LiteralPath $testRoot) { Remove-Item -LiteralPath $testRoot -Recurse -Force }
}

Write-Output 'PASS: V1.2 accessibility-matrix dry-run contract'
