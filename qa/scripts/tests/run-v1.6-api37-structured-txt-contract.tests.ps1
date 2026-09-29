Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$runner = Join-Path $PSScriptRoot '..\run-v1.6-api37-structured-txt.ps1'
$runner = [IO.Path]::GetFullPath($runner)
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}

function Get-FullPathWithoutTrailingSeparator {
    param([string]$Path)
    return ([IO.Path]::GetFullPath($Path)).TrimEnd([char[]]@('\', '/'))
}

function Remove-SafeContractTempDirectory {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Container)) {
        return
    }

    $resolvedPath = (Resolve-Path -LiteralPath $Path -ErrorAction Stop).Path
    $resolvedFull = Get-FullPathWithoutTrailingSeparator -Path $resolvedPath
    $tempRootFull = Get-FullPathWithoutTrailingSeparator -Path ([IO.Path]::GetTempPath())
    $tempRootPrefix = $tempRootFull + [IO.Path]::DirectorySeparatorChar
    Assert-True ($resolvedFull.StartsWith($tempRootPrefix, [StringComparison]::OrdinalIgnoreCase)) 'temporary directory escaped the system temp root'
    Assert-True ($resolvedFull -ne $tempRootFull) 'refusing to recursively remove the system temp root'
    Remove-Item -LiteralPath $resolvedFull -Recurse -Force -ErrorAction Stop
}

$tempRoot = Get-FullPathWithoutTrailingSeparator -Path ([IO.Path]::GetTempPath())
$testRoot = Join-Path $tempRoot ('newmybook-v16-structured-txt-contract-' + [Guid]::NewGuid().ToString('N'))

function Write-JsonFixture {
    param([string]$Path, [object]$Value)
    [IO.File]::WriteAllText($Path, (($Value | ConvertTo-Json -Depth 8) + "`n"), $utf8NoBom)
}

function Invoke-ContractRunner {
    param(
        [string]$RegistryPath,
        [string]$ProbePath,
        [string]$PublicFixturePath = 'C:\contract-public\structured-txt-sample.txt',
        [string]$AcceptanceApk = 'C:\contract-artifacts\acceptance.apk',
        [string]$AcceptanceTestApk = 'C:\contract-artifacts\acceptance-test.apk'
    )

    $arguments = @(
        '-NoProfile',
        '-ExecutionPolicy', 'Bypass',
        '-File', $runner,
        '-PrivateRegistryPath', $RegistryPath,
        '-PublicFixturePath', $PublicFixturePath,
        '-AcceptanceApk', $AcceptanceApk,
        '-AcceptanceTestApk', $AcceptanceTestApk,
        '-DeviceProbePath', $ProbePath,
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
    [pscustomobject]@{
        ExitCode = $exitCode
        Text = (($output | ForEach-Object { $_.ToString() }) -join "`n")
    }
}

function Assert-FailedWithoutSensitiveText {
    param([object]$Result, [string]$CaseName, [string[]]$SensitiveValues)
    Assert-True ($Result.ExitCode -ne 0) "$CaseName unexpectedly passed"
    foreach ($value in $SensitiveValues) {
        Assert-True ($Result.Text -notmatch [regex]::Escape($value)) "$CaseName leaked sensitive fixture text"
    }
}

Assert-True (Test-Path -LiteralPath $runner -PathType Leaf) 'runner is missing'

$content = Get-Content -LiteralPath $runner -Raw -Encoding UTF8
$contractContent = Get-Content -LiteralPath $PSCommandPath -Raw -Encoding UTF8
$testPath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..\app\src\androidTest\kotlin\com\xinyue\reader\V16StructuredTxtInstrumentedTest.kt'))
Assert-True (Test-Path -LiteralPath $testPath -PathType Leaf) 'V16 acceptance test is missing'
$testContent = Get-Content -LiteralPath $testPath -Raw -Encoding UTF8
Assert-True ($content -notmatch 'sanitizeFailure\s+-CaseAlias') 'failure records must use numeric case numbers'
Assert-True ($content -notmatch '\$script:CurrentAlias') 'unused current alias state must not remain'
Assert-True ($content -match 'CaseNumber\s*=\s*0\s*\r?\n\s*Alias\s*=\s*''public-v16''') 'public case must declare CaseNumber zero'
Assert-True ($content -match '\[CmdletBinding\(\)\]') 'runner must use CmdletBinding'
Assert-True ($content -match '\[switch\]\$DryRun') 'runner must expose DryRun'
Assert-True ($content -match 'IsNullOrWhiteSpace\(\$AcceptanceApk\)[\s\S]*?app\\build\\outputs\\apk\\acceptance\\app-acceptance\.apk') 'runner must default to the acceptance APK'
Assert-True ($content -match 'IsNullOrWhiteSpace\(\$AcceptanceTestApk\)[\s\S]*?app\\build\\outputs\\apk\\androidTest\\acceptance\\app-acceptance-androidTest\.apk') 'runner must default to the acceptance test APK'
Assert-True ($content -match 'wait-for-broadcast-idle') 'runner must wait for API37 configuration broadcasts before UI tests'
Assert-True ($content -match 'XinYue_API37') 'runner must pin the endpoint name'
Assert-True ($content -match 'sys\.boot_completed') 'runner must require a completed API37 boot'
Assert-True ($content -match 'x86_64') 'runner must verify x86_64'
Assert-True ($content -match '16384') 'runner must verify 16 KB pages'
Assert-True ($content -match 'V16StructuredTxtInstrumentedTest') 'runner must use the V16 test class'
Assert-True ($content -match 'structuredTxtImportsThroughDocumentsUiAndOpensRealReader') 'import method missing'
Assert-True ($content -match 'structuredTxtRendersTitleSpansAndRunningHeaderContract') 'title method missing'
Assert-True ($content -match 'structuredTxtPagesAreContinuousWithoutBlankOrChapterOverlap') 'pagination method missing'
Assert-True ($content -match 'structuredTxtCopiesRawSelectionWithoutLayoutPlaceholder') 'copy method missing'
Assert-True ($content -match 'structuredTxtQuickAndFullSettingsHonorSaveCancelAndScope') 'settings method missing'
Assert-True ($content -match 'structuredTxtCompactShelfControlsRemainReachableAtTwoHundredPercentAndLandscape') 'shelf method missing'
Assert-True ($content -match 'structuredTxtSearchAndNoteKeepRawOffsetsAtChapterBoundary') 'search method missing'
Assert-True ($content -match 'structuredTxtTtsUsesRawPageTextWhenEngineAvailable') 'TTS method missing'
Assert-True ($content -match 'try\s*\{[\s\S]*finally\s*\{') 'runner must have a try/finally boundary'
Assert-True ($content -match 'Invoke-SuppressedAdb') 'ADB output must be suppressed'
Assert-True ($content -notmatch '(?i)rm[^\r\n]*\*') 'cleanup must not use wildcard deletion'
foreach ($remoteTarget in @(
    '/sdcard/Download/v16-structured-txt-input.txt',
    '/sdcard/Download/v16-scope-a.txt',
    '/sdcard/Download/v16-scope-b.txt'
)) {
    Assert-True ($content -match [regex]::Escape($remoteTarget)) 'a required neutral target is missing'
}
foreach ($deviceStateKey in @(
    'font_scale',
    'accelerometer_rotation',
    'user_rotation',
    'fixed-to-user-rotation',
    'enable_freeform_support',
    'wm size',
    'wm density',
    'cmd overlay'
)) {
    Assert-True ($content -match [regex]::Escape($deviceStateKey)) 'a required device-state operation is missing'
}
Assert-True ($content -match 'function\s+Restore-SettingValue') 'settings restoration must use a shared safe helper'
Assert-True ($content -match 'IsNullOrWhiteSpace\(\$text\)[\s\S]*?settings'', ''delete') 'empty settings must be deleted'
Assert-True ($content -match '\$text\s*-ieq\s*''null''') 'literal null settings must be deleted'
Assert-True ($content -match [regex]::Escape("'settings', 'put")) 'non-empty settings must be restored with put'
Assert-True ($content -match "Restore-SettingValue[^\r\n]*-Name 'font_scale'") 'font_scale must use the safe restoration helper'
Assert-True ($content -match "Restore-SettingValue[^\r\n]*-Name 'accelerometer_rotation'") 'auto-rotation must use the safe restoration helper'
Assert-True ($content -match "Restore-SettingValue[^\r\n]*-Name 'user_rotation'") 'user rotation must use the safe restoration helper'
Assert-True ($content -match "'wm', 'fixed-to-user-rotation', 'enabled'") 'acceptance mode must force user rotation on API37'
Assert-True ($content -match "'wm', 'user-rotation', 'lock', '1'") 'acceptance mode must lock the display to landscape'
Assert-True ($content -match "FixedToUserRotation") 'fixed-to-user-rotation state must be captured and restored'
Assert-True ($content -match "Restore-SettingValue[^\r\n]*-Name 'enable_freeform_support'") 'freeform setting must use the safe restoration helper'
Assert-True ($content -match '\$requiredForCount\s*=\s*@\(\$entry\.requiredFor\)\.Count') 'requiredFor must be handled by Count only'
Assert-True ($content -match '\$requiredForCount\s*-eq\s*4') 'requiredFor must require exactly four entries'
Assert-True ($content -match 'Assert-Runner\s*\(\$cases\.Count\s*-ge\s*1\)') 'private registry must contain at least one case'
Assert-True ($content -notmatch 'foreach\s*\(\$requiredMethod\s+in\s+@\(\$entry\.requiredFor\)\)') 'requiredFor entries must not be read or emitted'
Assert-True ($content -match 'Assert-Runner\s*\(\$cases\.Count\s*-ge\s*2\)') 'live acceptance must require public and private cases'
Assert-True ($content -match [regex]::Escape('package:\s*\S+')) 'package absence must inspect package result lines'
Assert-True ($content -match '\$packageResults\s*=\s*@\(\$result\.Lines\s*\|\s*Where-Object') 'package absence must inspect pm output'
Assert-True ($content -match '\$packageResults\.Count\s*-eq\s*0') 'package absence must use result-line count'
Assert-True ($content -notmatch 'return\s*\(\$result\.ExitCode\s*-ne\s*0\)') 'package absence must not use exit code alone'
Assert-True ($contractContent -match 'function\s+Remove-SafeContractTempDirectory') 'contract cleanup must use a guarded helper'
Assert-True ($contractContent -match '\[Guid\]::NewGuid\(\)') 'contract temp directory must be unique'
Assert-True ($contractContent -match 'Resolve-Path') 'contract cleanup must resolve before recursive removal'

# Task 6 review contracts. These assertions intentionally fail before the fix.
Assert-True ($content -match 'function\s+Invoke-V16Method[\s\S]*?pm[\s,\x27]+clear') 'each instrumentation method must clear app data first'
Assert-True ($content -match 'function\s+Invoke-ScopeSourcePush') 'scope A/B source preparation is missing'
Assert-True ($content -match 'Invoke-ScopeSourcePush[\s\S]*?scope-a[\s\S]*?Invoke-ScopeSourcePush[\s\S]*?scope-b') 'scope A/B must receive distinct neutral content'
Assert-True ($content -match 'Invoke-SerialAdb\s+-Serial\s+\$Serial\s+-Arguments\s+@\(''shell'',\s+\$appendCommand\)') 'scope marker append must pass one complete adb shell command'
Assert-True ($content -notmatch '@\(''shell'',\s*''sh'',\s*''-c'',\s*\$appendCommand\)') 'scope marker append must not split adb shell sh -c arguments'
Assert-True ($content -match 'V16_STATUS=SKIP') 'runner must recognize explicit test skip status'
Assert-True ($content -match 'methodResult\.Status[\s\S]*?Write-SafeRecord') 'method result status must drive safe PASS/SKIP evidence'
Assert-True ($testContent -match 'candidate\.startOffset\s*(?:!=|-ne)\s*swipePreviousEndOffset') 'single-page swipe must require exact continuity'
Assert-True ($testContent -match 'requireCompleteBook\s*=\s*publicOracle') 'private long books must use a bounded page window rather than require full traversal'
Assert-True ($testContent -match '!it\.isWhitespace\(\)\s*&&\s*it\s*!=\s*\x27\\u2060\x27') 'effective start must skip whitespace and U+2060'
Assert-True ($testContent -match 'assertAllPublicTitleSpans') 'public title test must visit every title span'
Assert-True ($testContent -match 'private\s+fun\s+openCaseBook\([\s\S]*?onAllNodes\(bookTitleTagMatcher,\s*useUnmergedTree\s*=\s*true\)') 'private book fallback must use the unmerged tagged title tree'
Assert-True ($testContent -match 'private\s+fun\s+openReaderMenu\([\s\S]*?repeat\(3\)[\s\S]*?reader-menu-navigation') 'reader menu opening must tolerate a repagination tap race'
Assert-True ($testContent -match 'SCOPE_A_INPUT' -and $testContent -match 'SCOPE_B_INPUT' -and $testContent -match 'prepareScopeBooks' -and $testContent -match 'openBookByTag') 'settings scope test must import and switch between two books'
Assert-True (
    $testContent -match 'waitForStep\(\s*"search-result"[\s\S]*?SemanticsProperties\.StateDescription[\s\S]*?performScrollToIndex\(3\)'
) 'private search without a result must fail'
Assert-True ($testContent -match 'library-create-group[\s\S]*library-view-series[\s\S]*library-status-filters[\s\S]*performScrollTo') 'shelf matrix must cover group, view, filters and horizontal reachability'
Assert-True ($testContent -match 'reportSkip\("tts-engine"\)') 'TTS skip must be explicit and neutral'
Assert-True ($testContent -match 'reportSkip\("tts-raw-oracle"\)') 'TTS raw oracle gap must remain explicit'
Assert-True ($testContent -match 'sendStatus\([\s\S]*?V16_STATUS=SKIP') 'TTS skip status must use the instrumentation result channel'

Remove-SafeContractTempDirectory -Path $testRoot
New-Item -ItemType Directory -Path $testRoot -Force | Out-Null

try {
    $privateSourceSentinel = 'C:\outside-private\opaque-source-01.txt'
    $privateIdSentinel = 'opaque-registry-id-01'
    $privateRawSentinel = 'OPAQUE_PRIVATE_RAW_SENTINEL'

    $registry = [ordered]@{
        version = 1
        inputs = @(
            [ordered]@{
                id = $privateIdSentinel
                path = $privateSourceSentinel
                requiredFor = @('structural-boundary', 'structural-settings', 'raw-selection', 'settings-scope')
            }
        )
    }
    $registryPath = Join-Path $testRoot 'registry-valid.json'
    Write-JsonFixture -Path $registryPath -Value $registry

    $probe = [ordered]@{
        endpoints = @(
            [ordered]@{
                name = 'XinYue_API37'
                serial = 'contract-serial'
                state = 'device'
                sdk = 37
                abi = 'x86_64'
                pageSize = 16384
            }
        )
    }
    $probePath = Join-Path $testRoot 'probe-valid.json'
    Write-JsonFixture -Path $probePath -Value $probe

    $valid = Invoke-ContractRunner -RegistryPath $registryPath -ProbePath $probePath
    Assert-True ($valid.ExitCode -eq 0) 'valid DryRun failed'
    Assert-True ($valid.Text -match 'PLAN case=0 step=04') 'public case is absent from the plan'
    Assert-True ($valid.Text -match 'PLAN case=1 step=04') 'private case number is absent from the plan'
    Assert-True ($valid.Text -match 'PLAN exit=0 PASS') 'DryRun did not report PASS'
    Assert-True ($valid.Text -notmatch '(?i)public-v16|private-case') 'case aliases entered DryRun output'
    Assert-True ($valid.Text -notmatch [regex]::Escape($privateSourceSentinel)) 'DryRun leaked private source path'
    Assert-True ($valid.Text -notmatch [regex]::Escape($privateIdSentinel)) 'DryRun leaked private registry id'
    Assert-True ($valid.Text -notmatch [regex]::Escape($privateRawSentinel)) 'DryRun leaked private raw content'
    Assert-True ($valid.Text -notmatch '(?i)opaque-source-01|outside-private|\.txt|sha256|hash') 'DryRun leaked sensitive source metadata'
    foreach ($line in @($valid.Text -split "`r?`n" | Where-Object { $_ -and $_ -notmatch '^$' })) {
        Assert-True ($line -match '^(PLAN )?(endpoint=XinYue_API37|case=\d+ step=\d{2}|step=\d{2}|exit=\d+ (PASS|FAIL))') 'DryRun emitted an uncontracted line'
    }

    $extraTopLevel = [ordered]@{
        version = 1
        inputs = $registry.inputs
        extra = 'must-fail'
    }
    $extraTopLevelPath = Join-Path $testRoot 'registry-extra-top.json'
    Write-JsonFixture -Path $extraTopLevelPath -Value $extraTopLevel
    Assert-FailedWithoutSensitiveText -Result (Invoke-ContractRunner -RegistryPath $extraTopLevelPath -ProbePath $probePath) -CaseName 'extra top-level registry key' -SensitiveValues @($privateSourceSentinel, $privateIdSentinel)

    $extraItem = [ordered]@{
        version = 1
        inputs = @([ordered]@{
            id = $privateIdSentinel
            path = $privateSourceSentinel
            requiredFor = @('structural-boundary')
            extra = 'must-fail'
        })
    }
    $extraItemPath = Join-Path $testRoot 'registry-extra-item.json'
    Write-JsonFixture -Path $extraItemPath -Value $extraItem
    Assert-FailedWithoutSensitiveText -Result (Invoke-ContractRunner -RegistryPath $extraItemPath -ProbePath $probePath) -CaseName 'extra registry item key' -SensitiveValues @($privateSourceSentinel, $privateIdSentinel)

    $wrongRequiredFor = [ordered]@{
        version = 1
        inputs = @([ordered]@{
            id = $privateIdSentinel
            path = $privateSourceSentinel
            requiredFor = 'not-an-array'
        })
    }
    $wrongRequiredForPath = Join-Path $testRoot 'registry-required-for-string.json'
    Write-JsonFixture -Path $wrongRequiredForPath -Value $wrongRequiredFor
    Assert-FailedWithoutSensitiveText -Result (Invoke-ContractRunner -RegistryPath $wrongRequiredForPath -ProbePath $probePath) -CaseName 'requiredFor array shape' -SensitiveValues @($privateSourceSentinel, $privateIdSentinel)

    $wrongRequiredForCount = [ordered]@{
        version = 1
        inputs = @([ordered]@{
            id = $privateIdSentinel
            path = $privateSourceSentinel
            requiredFor = @('structural-boundary', 'structural-settings', 'raw-selection')
        })
    }
    $wrongRequiredForCountPath = Join-Path $testRoot 'registry-required-for-count.json'
    Write-JsonFixture -Path $wrongRequiredForCountPath -Value $wrongRequiredForCount
    Assert-FailedWithoutSensitiveText -Result (Invoke-ContractRunner -RegistryPath $wrongRequiredForCountPath -ProbePath $probePath) -CaseName 'requiredFor count' -SensitiveValues @($privateSourceSentinel, $privateIdSentinel)

    $wrongEndpoint = $probe | ConvertTo-Json -Depth 8 | ConvertFrom-Json
    $wrongEndpoint.endpoints[0].name = 'Other_API'
    $wrongEndpointPath = Join-Path $testRoot 'probe-wrong-endpoint.json'
    Write-JsonFixture -Path $wrongEndpointPath -Value $wrongEndpoint
    $wrongEndpointResult = Invoke-ContractRunner -RegistryPath $registryPath -ProbePath $wrongEndpointPath
    Assert-FailedWithoutSensitiveText -Result $wrongEndpointResult -CaseName 'endpoint identity' -SensitiveValues @($privateSourceSentinel, $privateIdSentinel)
    Assert-True ($wrongEndpointResult.Text -match 'case=0 step=00 exit=1 FAIL') 'safe failure record was not emitted'
    Assert-True ($wrongEndpointResult.Text -notmatch '(?i)public-v16|private-case') 'safe failure record leaked a case alias'

    $wrongApi = $probe | ConvertTo-Json -Depth 8 | ConvertFrom-Json
    $wrongApi.endpoints[0].sdk = 35
    $wrongApiPath = Join-Path $testRoot 'probe-wrong-api.json'
    Write-JsonFixture -Path $wrongApiPath -Value $wrongApi
    Assert-FailedWithoutSensitiveText -Result (Invoke-ContractRunner -RegistryPath $registryPath -ProbePath $wrongApiPath) -CaseName 'SDK identity' -SensitiveValues @($privateSourceSentinel, $privateIdSentinel)

    $wrongAbi = $probe | ConvertTo-Json -Depth 8 | ConvertFrom-Json
    $wrongAbi.endpoints[0].abi = 'x86'
    $wrongAbiPath = Join-Path $testRoot 'probe-wrong-abi.json'
    Write-JsonFixture -Path $wrongAbiPath -Value $wrongAbi
    Assert-FailedWithoutSensitiveText -Result (Invoke-ContractRunner -RegistryPath $registryPath -ProbePath $wrongAbiPath) -CaseName 'ABI identity' -SensitiveValues @($privateSourceSentinel, $privateIdSentinel)

    $wrongPageSize = $probe | ConvertTo-Json -Depth 8 | ConvertFrom-Json
    $wrongPageSize.endpoints[0].pageSize = 4096
    $wrongPageSizePath = Join-Path $testRoot 'probe-wrong-page-size.json'
    Write-JsonFixture -Path $wrongPageSizePath -Value $wrongPageSize
    Assert-FailedWithoutSensitiveText -Result (Invoke-ContractRunner -RegistryPath $registryPath -ProbePath $wrongPageSizePath) -CaseName 'page-size identity' -SensitiveValues @($privateSourceSentinel, $privateIdSentinel)

    $multipleEndpoints = [ordered]@{
        endpoints = @(
            $probe.endpoints[0],
            [ordered]@{
                name = 'XinYue_API37'
                serial = 'contract-serial-2'
                state = 'device'
                sdk = 37
                abi = 'x86_64'
                pageSize = 16384
            }
        )
    }
    $multipleEndpointsPath = Join-Path $testRoot 'probe-multiple.json'
    Write-JsonFixture -Path $multipleEndpointsPath -Value $multipleEndpoints
    Assert-FailedWithoutSensitiveText -Result (Invoke-ContractRunner -RegistryPath $registryPath -ProbePath $multipleEndpointsPath) -CaseName 'unique endpoint' -SensitiveValues @($privateSourceSentinel, $privateIdSentinel)

    Assert-True ($content -notmatch '\$sourcePath[^\r\n]*(Write-Output|Write-Host|Write-Safe|Write-Evidence|ConvertTo-Json)') 'private source path must not enter output or evidence'
    Assert-True ($content -notmatch '(?i)gradlew|connectedAcceptance|Start-Process[^\r\n]*gradle') 'contract runner must not build APKs'
    Assert-True ($content -notmatch '(?i)git\s+(ls-files|status|diff|log|checkout|reset)') 'runner must not invoke Git'

    'PASS: V1.6 API37 structured-TXT runner contract'
} finally {
    Remove-SafeContractTempDirectory -Path $testRoot
}
