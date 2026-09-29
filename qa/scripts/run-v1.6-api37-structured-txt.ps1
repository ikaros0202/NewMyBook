[CmdletBinding()]
param(
    [Alias('RegistryPath')]
    [string]$PrivateRegistryPath,

    [string]$PublicFixturePath,

    [string]$AcceptanceApk,

    [string]$AcceptanceTestApk,

    [Alias('ProbePath')]
    [string]$DeviceProbePath,

    [string]$EvidenceRoot,

    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$script:EndpointName = 'XinYue_API37'
$script:ExpectedSdk = 37
$script:ExpectedAbi = 'x86_64'
$script:ExpectedPageSize = 16384
$script:AppPackage = 'com.xinyue.reader'
$script:TestPackage = 'com.xinyue.reader.test'
$script:InstrumentationRunner = 'com.xinyue.reader.test/androidx.test.runner.AndroidJUnitRunner'
$script:InstrumentationClass = 'com.xinyue.reader.V16StructuredTxtInstrumentedTest'
$script:RemoteInput = '/sdcard/Download/v16-structured-txt-input.txt'
$script:RemoteScopeA = '/sdcard/Download/v16-scope-a.txt'
$script:RemoteScopeB = '/sdcard/Download/v16-scope-b.txt'
$script:RemoteTargets = @($script:RemoteInput, $script:RemoteScopeA, $script:RemoteScopeB)
$script:InstrumentationMethods = @(
    'structuredTxtImportsThroughDocumentsUiAndOpensRealReader',
    'structuredTxtRendersTitleSpansAndRunningHeaderContract',
    'structuredTxtPagesAreContinuousWithoutBlankOrChapterOverlap',
    'structuredTxtCopiesRawSelectionWithoutLayoutPlaceholder',
    'structuredTxtQuickAndFullSettingsHonorSaveCancelAndScope',
    'structuredTxtCompactShelfControlsRemainReachableAtTwoHundredPercentAndLandscape',
    'structuredTxtSearchAndNoteKeepRawOffsetsAtChapterBoundary',
    'structuredTxtTtsUsesRawPageTextWhenEngineAvailable'
)
$script:AdbPath = $null
$script:CurrentCaseNumber = 0
$script:CurrentStep = '00'
$script:DeviceSerial = $null
$script:OriginalDeviceState = $null

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Resolve-Path -LiteralPath (Join-Path $scriptRoot '..\..')).Path
if ([string]::IsNullOrWhiteSpace($PublicFixturePath)) {
    $PublicFixturePath = Join-Path $repoRoot 'qa\v1.6\structured-txt-sample.txt'
}
if ([string]::IsNullOrWhiteSpace($AcceptanceApk)) {
    $AcceptanceApk = Join-Path $repoRoot 'app\build\outputs\apk\acceptance\app-acceptance.apk'
}
if ([string]::IsNullOrWhiteSpace($AcceptanceTestApk)) {
    $AcceptanceTestApk = Join-Path $repoRoot 'app\build\outputs\apk\androidTest\acceptance\app-acceptance-androidTest.apk'
}

function Assert-Runner {
    param([bool]$Condition)
    if (-not $Condition) {
        throw 'runner failure'
    }
}

function Get-PropertyNames {
    param([object]$Value)
    return @($Value.PSObject.Properties | ForEach-Object { $_.Name })
}

function Test-ExactPropertyNames {
    param(
        [object]$Value,
        [string[]]$Expected
    )
    $actual = @(Get-PropertyNames -Value $Value | Sort-Object)
    $expectedSorted = @($Expected | Sort-Object)
    if ($actual.Count -ne $expectedSorted.Count) {
        return $false
    }
    for ($index = 0; $index -lt $actual.Count; $index++) {
        if ($actual[$index] -cne $expectedSorted[$index]) {
            return $false
        }
    }
    return $true
}

function Resolve-PrivateRegistryPath {
    if (-not [string]::IsNullOrWhiteSpace($PrivateRegistryPath)) {
        return $PrivateRegistryPath
    }
    if ($DryRun.IsPresent) {
        return $null
    }
    $privateRoot = [Environment]::GetEnvironmentVariable('XINYUE_PRIVATE_QA_ROOT')
    if ([string]::IsNullOrWhiteSpace($privateRoot)) {
        return $null
    }
    return (Join-Path $privateRoot 'regression-inputs.json')
}

function Read-PrivateCases {
    $registryPath = Resolve-PrivateRegistryPath
    Assert-Runner (-not [string]::IsNullOrWhiteSpace($registryPath))

    Assert-Runner (Test-Path -LiteralPath $registryPath -PathType Leaf)
    $registry = Get-Content -LiteralPath $registryPath -Raw -Encoding UTF8 | ConvertFrom-Json
    Assert-Runner (Test-ExactPropertyNames -Value $registry -Expected @('version', 'inputs'))
    Assert-Runner ($null -ne $registry.version)
    Assert-Runner ($null -ne $registry.inputs)
    Assert-Runner ($registry.inputs -is [Array])

    $cases = [System.Collections.Generic.List[object]]::new()
    $ordinal = 1
    foreach ($entry in @($registry.inputs)) {
        Assert-Runner (Test-ExactPropertyNames -Value $entry -Expected @('id', 'path', 'requiredFor'))
        Assert-Runner ($entry.id -is [string] -and -not [string]::IsNullOrWhiteSpace($entry.id))
        Assert-Runner ($entry.path -is [string] -and -not [string]::IsNullOrWhiteSpace($entry.path))
        Assert-Runner ($entry.requiredFor -is [Array])
        $requiredForCount = @($entry.requiredFor).Count
        Assert-Runner ($requiredForCount -eq 4)

        [void]$cases.Add([pscustomobject]@{
            CaseNumber = $ordinal
            Alias = ('private-case-{0:D2}' -f $ordinal)
            SourcePath = [string]$entry.path
            OracleMode = 'structural'
        })
        $ordinal++
    }
    Assert-Runner ($cases.Count -ge 1)
    return [object[]]$cases.ToArray()
}

function Get-AdbCommandPath {
    $command = Get-Command adb -ErrorAction SilentlyContinue
    Assert-Runner ($null -ne $command)
    return $command.Source
}

function Invoke-SuppressedAdb {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments,

        [switch]$AllowFailure
    )

    $exitCode = 1
    $lines = @()
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $rawOutput = @(& $script:AdbPath @Arguments 2>&1)
        $exitCode = $LASTEXITCODE
        $lines = @($rawOutput | ForEach-Object { $_.ToString() })
    } catch {
        $exitCode = 1
        $lines = @()
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    if (-not $AllowFailure.IsPresent -and $exitCode -ne 0) {
        throw 'runner failure'
    }
    return [pscustomobject]@{
        ExitCode = $exitCode
        Lines = $lines
    }
}

function Invoke-SerialAdb {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Serial,

        [Parameter(Mandatory = $true)]
        [string[]]$Arguments,

        [switch]$AllowFailure
    )
    return Invoke-SuppressedAdb -Arguments (@('-s', $Serial) + $Arguments) -AllowFailure:$AllowFailure.IsPresent
}

function Get-FirstLine {
    param([object]$Result)
    if ($null -eq $Result -or $null -eq $Result.Lines -or $Result.Lines.Count -eq 0) {
        return ''
    }
    return $Result.Lines[0].Trim()
}

function Get-PageSizeFromAdb {
    param([string]$Serial)

    $pageResult = Invoke-SerialAdb -Serial $Serial -Arguments @('shell', 'getconf', 'PAGE_SIZE') -AllowFailure
    $first = Get-FirstLine -Result $pageResult
    if ($pageResult.ExitCode -eq 0 -and $first -match '^\d+$') {
        return [int]$first
    }

    $fallback = Invoke-SerialAdb -Serial $Serial -Arguments @('shell', 'grep', '-m', '1', 'KernelPageSize', '/proc/self/smaps') -AllowFailure
    $fallbackText = ($fallback.Lines -join ' ')
    if ($fallback.ExitCode -eq 0 -and $fallbackText -match 'KernelPageSize:\s*(\d+)\s*kB') {
        return ([int]$Matches[1] * 1024)
    }
    return 0
}

function Get-LiveEndpointProbe {
    $devicesResult = Invoke-SuppressedAdb -Arguments @('devices')
    $devices = [System.Collections.Generic.List[object]]::new()
    foreach ($line in @($devicesResult.Lines)) {
        if ($line -match '^\s*([^\s]+)\s+([^\s]+)') {
            $serial = $Matches[1]
            $state = $Matches[2]
            if ($serial -ne 'List') {
                [void]$devices.Add([pscustomobject]@{ Serial = $serial; State = $state })
            }
        }
    }

    $active = @($devices | Where-Object { $_.State -ne 'offline' })
    Assert-Runner ($active.Count -eq 1)
    $selected = $active[0]
    $serial = [string]$selected.Serial
    $avdName = Get-FirstLine -Result (Invoke-SerialAdb -Serial $serial -Arguments @('shell', 'getprop', 'ro.boot.qemu.avd_name'))
    $bootCompleted = Get-FirstLine -Result (Invoke-SerialAdb -Serial $serial -Arguments @('shell', 'getprop', 'sys.boot_completed'))
    Assert-Runner ($bootCompleted -ceq '1')
    $sdk = Get-FirstLine -Result (Invoke-SerialAdb -Serial $serial -Arguments @('shell', 'getprop', 'ro.build.version.sdk'))
    $abi = Get-FirstLine -Result (Invoke-SerialAdb -Serial $serial -Arguments @('shell', 'getprop', 'ro.product.cpu.abi'))
    return [pscustomobject]@{
        Name = $avdName
        Serial = $serial
        State = [string]$selected.State
        Sdk = $sdk
        Abi = $abi
        PageSize = Get-PageSizeFromAdb -Serial $serial
    }
}

function Read-DeviceProbe {
    if ($DryRun.IsPresent) {
        Assert-Runner (-not [string]::IsNullOrWhiteSpace($DeviceProbePath))
        Assert-Runner (Test-Path -LiteralPath $DeviceProbePath -PathType Leaf)
        $probe = Get-Content -LiteralPath $DeviceProbePath -Raw -Encoding UTF8 | ConvertFrom-Json
        Assert-Runner ($null -ne $probe.endpoints -and $probe.endpoints -is [Array])
        return @($probe.endpoints)
    }
    return @(Get-LiveEndpointProbe)
}

function Assert-ApprovedEndpoint {
    param([object[]]$Endpoints)

    $active = @($Endpoints | Where-Object { [string]$_.state -ne 'offline' })
    Assert-Runner ($active.Count -eq 1)
    $endpoint = $active[0]
    Assert-Runner ([string]$endpoint.name -ceq $script:EndpointName)
    Assert-Runner ([string]$endpoint.state -ceq 'device')
    Assert-Runner ([int]$endpoint.sdk -eq $script:ExpectedSdk)
    Assert-Runner ([string]$endpoint.abi -ceq $script:ExpectedAbi)
    Assert-Runner ([int]$endpoint.pageSize -eq $script:ExpectedPageSize)
    Assert-Runner (-not [string]::IsNullOrWhiteSpace([string]$endpoint.serial))
    return [pscustomobject]@{
        Name = [string]$endpoint.name
        Serial = [string]$endpoint.serial
        State = [string]$endpoint.state
        Sdk = [int]$endpoint.sdk
        Abi = [string]$endpoint.abi
        PageSize = [int]$endpoint.pageSize
    }
}

function Get-ActiveNavigationOverlay {
    param([string]$Serial)
    $result = Invoke-SerialAdb -Serial $Serial -Arguments @('shell', 'cmd', 'overlay', 'list', '--user', '0') -AllowFailure
    $line = @($result.Lines | Where-Object { $_ -match '^\s*\[x\].*navigationbar' } | Select-Object -First 1)
    if ($line.Count -eq 0) {
        return ''
    }
    if ($line[0] -match '([A-Za-z0-9._]+)$') {
        return $Matches[1]
    }
    return ''
}

function Read-DeviceState {
    param(
        [string]$Serial,
        [switch]$AllowFailure
    )
    try {
        # Preserve exact wm size and wm density snapshots for restoration.
        return [pscustomobject]@{
            FontScale = Get-FirstLine -Result (Invoke-SerialAdb -Serial $Serial -Arguments @('shell', 'settings', 'get', 'system', 'font_scale'))
            AutoRotation = Get-FirstLine -Result (Invoke-SerialAdb -Serial $Serial -Arguments @('shell', 'settings', 'get', 'system', 'accelerometer_rotation'))
            UserRotation = Get-FirstLine -Result (Invoke-SerialAdb -Serial $Serial -Arguments @('shell', 'settings', 'get', 'system', 'user_rotation'))
            FixedToUserRotation = Get-FirstLine -Result (Invoke-SerialAdb -Serial $Serial -Arguments @('shell', 'wm', 'fixed-to-user-rotation'))
            NavigationOverlay = Get-ActiveNavigationOverlay -Serial $Serial
            Freeform = Get-FirstLine -Result (Invoke-SerialAdb -Serial $Serial -Arguments @('shell', 'settings', 'get', 'global', 'enable_freeform_support'))
            WmSize = ((Invoke-SerialAdb -Serial $Serial -Arguments @('shell', 'wm', 'size')).Lines -join '; ')
            WmDensity = ((Invoke-SerialAdb -Serial $Serial -Arguments @('shell', 'wm', 'density')).Lines -join '; ')
        }
    } catch {
        if ($AllowFailure.IsPresent) {
            return $null
        }
        throw 'runner failure'
    }
}

function Invoke-RestoreCommand {
    param([string]$Serial, [string[]]$Arguments)
    $result = Invoke-SerialAdb -Serial $Serial -Arguments $Arguments -AllowFailure
    return ($result.ExitCode -eq 0)
}

function Restore-SettingValue {
    param(
        [string]$Serial,
        [string]$Namespace,
        [string]$Name,
        [object]$Value
    )

    $text = if ($null -eq $Value) { '' } else { ([string]$Value).Trim() }
    if ([string]::IsNullOrWhiteSpace($text) -or $text -ieq 'null') {
        return Invoke-RestoreCommand -Serial $Serial -Arguments @('shell', 'settings', 'delete', $Namespace, $Name)
    }
    return Invoke-RestoreCommand -Serial $Serial -Arguments @('shell', 'settings', 'put', $Namespace, $Name, $text)
}

function Restore-DeviceState {
    param([string]$Serial, [object]$State)
    $ok = $true

    $commandOk = Restore-SettingValue -Serial $Serial -Namespace 'system' -Name 'font_scale' -Value $State.FontScale
    $ok = $commandOk -and $ok
    $commandOk = Restore-SettingValue -Serial $Serial -Namespace 'system' -Name 'accelerometer_rotation' -Value $State.AutoRotation
    $ok = $commandOk -and $ok
    $commandOk = Restore-SettingValue -Serial $Serial -Namespace 'system' -Name 'user_rotation' -Value $State.UserRotation
    $ok = $commandOk -and $ok

    $fixedRotationMode = ([string]$State.FixedToUserRotation).Trim()
    if ($fixedRotationMode -in @('default', 'enabled', 'disabled', 'enabled_if_no_auto_rotation')) {
        $commandOk = Invoke-RestoreCommand -Serial $Serial -Arguments @('shell', 'wm', 'fixed-to-user-rotation', $fixedRotationMode)
        $ok = $commandOk -and $ok
    } else {
        $ok = $false
    }

    if ([string]$State.AutoRotation -ceq '1') {
        $commandOk = Invoke-RestoreCommand -Serial $Serial -Arguments @('shell', 'wm', 'user-rotation', 'free')
    } elseif ([string]$State.UserRotation -match '^[0-3]$') {
        $commandOk = Invoke-RestoreCommand -Serial $Serial -Arguments @('shell', 'wm', 'user-rotation', 'lock', [string]$State.UserRotation)
    } else {
        $commandOk = $false
    }
    $ok = $commandOk -and $ok

    # Save and restore the original cmd overlay navigation state.
    if (-not [string]::IsNullOrWhiteSpace([string]$State.NavigationOverlay)) {
        $commandOk = Invoke-RestoreCommand -Serial $Serial -Arguments @('shell', 'cmd', 'overlay', 'enable-exclusive', '--user', '0', '--category', [string]$State.NavigationOverlay)
        $ok = $commandOk -and $ok
    }

    $commandOk = Restore-SettingValue -Serial $Serial -Namespace 'global' -Name 'enable_freeform_support' -Value $State.Freeform
    $ok = $commandOk -and $ok

    if ([string]$State.WmSize -match 'Override size:\s*([0-9]+x[0-9]+)') {
        $commandOk = Invoke-RestoreCommand -Serial $Serial -Arguments @('shell', 'wm', 'size', $Matches[1])
    } else {
        $commandOk = Invoke-RestoreCommand -Serial $Serial -Arguments @('shell', 'wm', 'size', 'reset')
    }
    $ok = $commandOk -and $ok

    if ([string]$State.WmDensity -match 'Override density:\s*([0-9]+)') {
        $commandOk = Invoke-RestoreCommand -Serial $Serial -Arguments @('shell', 'wm', 'density', $Matches[1])
    } else {
        $commandOk = Invoke-RestoreCommand -Serial $Serial -Arguments @('shell', 'wm', 'density', 'reset')
    }
    $ok = $commandOk -and $ok
    return $ok
}

function Test-DeviceStateEqual {
    param([object]$Expected, [object]$Actual)
    if ($null -eq $Expected -or $null -eq $Actual) {
        return $false
    }
    return (
        [string]$Expected.FontScale -ceq [string]$Actual.FontScale -and
        [string]$Expected.AutoRotation -ceq [string]$Actual.AutoRotation -and
        [string]$Expected.UserRotation -ceq [string]$Actual.UserRotation -and
        [string]$Expected.FixedToUserRotation -ceq [string]$Actual.FixedToUserRotation -and
        [string]$Expected.NavigationOverlay -ceq [string]$Actual.NavigationOverlay -and
        [string]$Expected.Freeform -ceq [string]$Actual.Freeform -and
        [string]$Expected.WmSize -ceq [string]$Actual.WmSize -and
        [string]$Expected.WmDensity -ceq [string]$Actual.WmDensity
    )
}

function Invoke-PrivateSourcePush {
    param(
        [string]$Serial,
        [string]$SourcePath,
        [string]$RemotePath
    )
    # SourcePath is passed only to this suppressed adb child process argument.
    [void](Invoke-SerialAdb -Serial $Serial -Arguments @('push', $SourcePath, $RemotePath))
    [void](Invoke-SerialAdb -Serial $Serial -Arguments @('shell', 'touch', $RemotePath))
    [void](Invoke-SerialAdb -Serial $Serial -Arguments @(
        'shell', 'am', 'broadcast',
        '-a', 'android.intent.action.MEDIA_SCANNER_SCAN_FILE',
        '-d', "file://$RemotePath"
    ))
}

function Invoke-ScopeSourcePush {
    param(
        [string]$Serial,
        [string]$SourcePath,
        [string]$RemotePath,
        [string]$ScopeMarker
    )
    [void](Invoke-SerialAdb -Serial $Serial -Arguments @('push', $SourcePath, $RemotePath))
    $appendCommand = "printf '\n\n$ScopeMarker\n' >> '$RemotePath'"
    # Pass the complete command as adb shell's single remote command. Splitting it
    # into `sh -c` arguments makes adb flatten the format string, so Android's
    # printf receives no format argument and the scope fixture is never created.
    [void](Invoke-SerialAdb -Serial $Serial -Arguments @('shell', $appendCommand))
    [void](Invoke-SerialAdb -Serial $Serial -Arguments @('shell', 'touch', $RemotePath))
    [void](Invoke-SerialAdb -Serial $Serial -Arguments @(
        'shell', 'am', 'broadcast',
        '-a', 'android.intent.action.MEDIA_SCANNER_SCAN_FILE',
        '-d', "file://$RemotePath"
    ))
}

function Install-AcceptancePackages {
    param([string]$Serial)
    Assert-Runner (Test-Path -LiteralPath $AcceptanceApk -PathType Leaf)
    Assert-Runner (Test-Path -LiteralPath $AcceptanceTestApk -PathType Leaf)
    [void](Invoke-SerialAdb -Serial $Serial -Arguments @('uninstall', $script:TestPackage) -AllowFailure)
    [void](Invoke-SerialAdb -Serial $Serial -Arguments @('uninstall', $script:AppPackage) -AllowFailure)
    [void](Invoke-SerialAdb -Serial $Serial -Arguments @('install', '-r', $AcceptanceApk))
    [void](Invoke-SerialAdb -Serial $Serial -Arguments @('install', '-r', $AcceptanceTestApk))
}

function Set-ReaderAcceptanceState {
    param([string]$Serial)
    [void](Invoke-SerialAdb -Serial $Serial -Arguments @('shell', 'settings', 'put', 'system', 'font_scale', '2.0'))
    [void](Invoke-SerialAdb -Serial $Serial -Arguments @('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0'))
    [void](Invoke-SerialAdb -Serial $Serial -Arguments @('shell', 'settings', 'put', 'system', 'user_rotation', '1'))
    [void](Invoke-SerialAdb -Serial $Serial -Arguments @('shell', 'wm', 'fixed-to-user-rotation', 'enabled'))
    [void](Invoke-SerialAdb -Serial $Serial -Arguments @('shell', 'wm', 'user-rotation', 'lock', '1'))
    [void](Invoke-SerialAdb -Serial $Serial -Arguments @('shell', 'cmd', 'activity', 'wait-for-broadcast-idle'))
}

function Invoke-V16Method {
    param(
        [string]$Serial,
        [object]$Case,
        [string]$Method,
        [string]$StepId
    )
    $script:CurrentStep = $StepId
    [void](Invoke-SerialAdb -Serial $Serial -Arguments @('shell', 'am', 'force-stop', $script:AppPackage) -AllowFailure)
    [void](Invoke-SerialAdb -Serial $Serial -Arguments @('shell', 'pm', 'clear', $script:AppPackage))
    $classFilter = "$($script:InstrumentationClass)#$Method"
    $arguments = @(
        'shell', 'am', 'instrument', '-w', '-r',
        '-e', 'class', $classFilter,
        '-e', 'v16_case_alias', [string]$Case.Alias,
        '-e', 'v16_oracle_mode', [string]$Case.OracleMode,
        $script:InstrumentationRunner
    )
    $result = Invoke-SerialAdb -Serial $Serial -Arguments $arguments -AllowFailure
    $joined = $result.Lines -join "`n"
    if ($result.ExitCode -eq 0 -and $joined -match 'V16_STATUS=SKIP\|step=[A-Za-z0-9-]+') {
        return [pscustomobject]@{ ExitCode = 0; Status = 'SKIP' }
    }
    if ($result.ExitCode -eq 0 -and $joined -match 'OK \(') {
        return [pscustomobject]@{ ExitCode = 0; Status = 'PASS' }
    }
    return [pscustomobject]@{ ExitCode = 1; Status = 'FAIL' }
}

function sanitizeFailure {
    param(
        [int]$CaseNumber,
        [string]$StepId,
        [int]$ExitCode,
        [ValidateSet('PASS', 'FAIL', 'SKIP')]
        [string]$Status
    )
    return [pscustomobject]@{
        CaseNumber = $CaseNumber
        StepId = $StepId
        ExitCode = $ExitCode
        Status = $Status
    }
}

function Write-SafeRecord {
    param([object]$Record)
    Write-Output ("case={0} step={1} exit={2} {3}" -f [int]$Record.CaseNumber, $Record.StepId, $Record.ExitCode, $Record.Status)
}

function Write-SafeEvidence {
    param([object]$Record)
    if ([string]::IsNullOrWhiteSpace($EvidenceRoot)) {
        return
    }
    try {
        New-Item -ItemType Directory -Path $EvidenceRoot -Force | Out-Null
        $fileName = ('case-{0:D2}.log' -f [int]$Record.CaseNumber)
        $path = Join-Path $EvidenceRoot $fileName
        $line = ("case={0} step={1} exit={2} {3}" -f [int]$Record.CaseNumber, $Record.StepId, $Record.ExitCode, $Record.Status) + [Environment]::NewLine
        [IO.File]::AppendAllText($path, $line, (New-Object System.Text.UTF8Encoding($false)))
    } catch {
        # Evidence failures are handled by the caller without exposing the path.
        $script:EvidenceWriteFailed = $true
    }
}

function Invoke-OneCase {
    param([string]$Serial, [object]$Case)
    $script:CurrentCaseNumber = [int]$Case.CaseNumber
    $script:CurrentStep = '04'
    [void](Invoke-SerialAdb -Serial $Serial -Arguments @('shell', 'am', 'force-stop', $script:AppPackage) -AllowFailure)
    [void](Invoke-SerialAdb -Serial $Serial -Arguments @('shell', 'pm', 'clear', $script:AppPackage))

    Invoke-PrivateSourcePush -Serial $Serial -SourcePath ([string]$Case.SourcePath) -RemotePath $script:RemoteInput
    Invoke-ScopeSourcePush -Serial $Serial -SourcePath ([string]$Case.SourcePath) -RemotePath $script:RemoteScopeA -ScopeMarker 'v16-scope-a'
    Invoke-ScopeSourcePush -Serial $Serial -SourcePath ([string]$Case.SourcePath) -RemotePath $script:RemoteScopeB -ScopeMarker 'v16-scope-b'

    $stepNumber = 5
    foreach ($method in $script:InstrumentationMethods) {
        $stepId = '{0:D2}' -f $stepNumber
        $methodResult = Invoke-V16Method -Serial $Serial -Case $Case -Method $method -StepId $stepId
        $record = sanitizeFailure -CaseNumber ([int]$Case.CaseNumber) -StepId $stepId -ExitCode ([int]$methodResult.ExitCode) -Status ([string]$methodResult.Status)
        Write-SafeRecord -Record $record
        Write-SafeEvidence -Record $record
        if ($methodResult.Status -eq 'FAIL') {
            throw 'runner failure'
        }
        $stepNumber++
    }
}

function Write-DryRunPlan {
    param([object[]]$Cases)
    Write-Output 'PLAN endpoint=XinYue_API37'
    Write-Output 'PLAN step=01 preflight'
    Write-Output 'PLAN step=02 capture-device-state'
    Write-Output 'PLAN step=03 install-acceptance'
    foreach ($case in $Cases) {
        Write-Output ("PLAN case={0} step=04" -f [int]$case.CaseNumber)
        $stepNumber = 5
        foreach ($method in $script:InstrumentationMethods) {
            Write-Output ("PLAN case={0} step={1:D2}" -f [int]$case.CaseNumber, $stepNumber)
            $stepNumber++
        }
    }
    Write-Output 'PLAN step=90 cleanup-neutral-targets'
    Write-Output 'PLAN step=91 restore-device-state'
    Write-Output 'PLAN step=92 verify-restoration'
}

function Test-NeutralTargetsAbsent {
    param([string]$Serial)
    $ok = $true
    foreach ($remoteTarget in $script:RemoteTargets) {
        $result = Invoke-SerialAdb -Serial $Serial -Arguments @('shell', 'test', '!', '-e', $remoteTarget) -AllowFailure
        $ok = ($result.ExitCode -eq 0) -and $ok
    }
    return $ok
}

function Test-PackageAbsent {
    param([string]$Serial, [string]$PackageName)
    $result = Invoke-SerialAdb -Serial $Serial -Arguments @('shell', 'pm', 'path', $PackageName) -AllowFailure
    $packageResults = @($result.Lines | Where-Object {
        ([string]$_).Trim() -match '^package:\s*\S+'
    })
    return ($packageResults.Count -eq 0)
}

function Invoke-CleanupAndRestore {
    param([string]$Serial, [object]$OriginalState)
    $ok = $true

    $result = Invoke-SerialAdb -Serial $Serial -Arguments @('shell', 'am', 'force-stop', $script:AppPackage) -AllowFailure
    $ok = ($result.ExitCode -eq 0) -and $ok
    foreach ($remoteTarget in $script:RemoteTargets) {
        $result = Invoke-SerialAdb -Serial $Serial -Arguments @('shell', 'rm', '-f', $remoteTarget) -AllowFailure
        $ok = ($result.ExitCode -eq 0) -and $ok
    }
    $result = Invoke-SerialAdb -Serial $Serial -Arguments @('shell', 'pm', 'clear', $script:AppPackage) -AllowFailure
    $ok = ($result.ExitCode -eq 0) -and $ok
    $result = Invoke-SerialAdb -Serial $Serial -Arguments @('shell', 'pm', 'clear', $script:TestPackage) -AllowFailure
    $ok = ($result.ExitCode -eq 0) -and $ok
    $result = Invoke-SerialAdb -Serial $Serial -Arguments @('uninstall', $script:TestPackage) -AllowFailure
    $ok = ($result.ExitCode -eq 0) -and $ok
    $result = Invoke-SerialAdb -Serial $Serial -Arguments @('uninstall', $script:AppPackage) -AllowFailure
    $ok = ($result.ExitCode -eq 0) -and $ok

    if ($null -eq $OriginalState) {
        $ok = $false
    } else {
        $restored = Restore-DeviceState -Serial $Serial -State $OriginalState
        $ok = $restored -and $ok
    }

    $finalState = Read-DeviceState -Serial $Serial -AllowFailure
    $ok = (Test-DeviceStateEqual -Expected $OriginalState -Actual $finalState) -and $ok
    $ok = (Test-NeutralTargetsAbsent -Serial $Serial) -and $ok
    $ok = (Test-PackageAbsent -Serial $Serial -PackageName $script:AppPackage) -and $ok
    $ok = (Test-PackageAbsent -Serial $Serial -PackageName $script:TestPackage) -and $ok
    return $ok
}

$script:EvidenceWriteFailed = $false
$runSucceeded = $false
$cleanupSucceeded = $true

try {
    Assert-Runner ($DryRun.IsPresent -or [string]::IsNullOrWhiteSpace($DeviceProbePath))

    if (-not $DryRun.IsPresent) {
        $script:AdbPath = Get-AdbCommandPath
    }

    $cases = @([pscustomobject]@{
        CaseNumber = 0
        Alias = 'public-v16'
        SourcePath = $PublicFixturePath
        OracleMode = 'public'
    }) + @(Read-PrivateCases)
    $endpoints = Read-DeviceProbe
    $device = Assert-ApprovedEndpoint -Endpoints $endpoints

    if ($DryRun.IsPresent) {
        Write-DryRunPlan -Cases $cases
        $runSucceeded = $true
    } else {
        # Re-resolve the live endpoint through ADB before every device action.
        $device = Assert-ApprovedEndpoint -Endpoints @(Get-LiveEndpointProbe)
        $script:DeviceSerial = [string]$device.Serial
        Assert-Runner (Test-Path -LiteralPath $PublicFixturePath -PathType Leaf)
        Assert-Runner ($cases.Count -ge 2)

        $script:CurrentStep = '02'
        $script:OriginalDeviceState = Read-DeviceState -Serial $script:DeviceSerial
        $script:CurrentStep = '03'
        Install-AcceptancePackages -Serial $script:DeviceSerial
        Set-ReaderAcceptanceState -Serial $script:DeviceSerial

        foreach ($case in $cases) {
            Invoke-OneCase -Serial $script:DeviceSerial -Case $case
        }
        $runSucceeded = $true
    }
} catch {
    $runSucceeded = $false
} finally {
    if (-not $DryRun.IsPresent -and -not [string]::IsNullOrWhiteSpace($script:DeviceSerial)) {
        try {
            $cleanupSucceeded = Invoke-CleanupAndRestore -Serial $script:DeviceSerial -OriginalState $script:OriginalDeviceState
        } catch {
            $cleanupSucceeded = $false
        }
    }
    if ($script:EvidenceWriteFailed) {
        $runSucceeded = $false
    }
    if (-not $cleanupSucceeded) {
        $runSucceeded = $false
    }
}

if ($runSucceeded) {
    if ($DryRun.IsPresent) {
        Write-Output 'PLAN exit=0 PASS'
    } else {
        Write-Output 'exit=0 PASS'
    }
    exit 0
}

$failureRecord = sanitizeFailure -CaseNumber $script:CurrentCaseNumber -StepId $script:CurrentStep -ExitCode 1 -Status 'FAIL'
Write-SafeRecord -Record $failureRecord
Write-SafeEvidence -Record $failureRecord
exit 1
