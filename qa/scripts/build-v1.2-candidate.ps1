[CmdletBinding()]
param(
    [string]$Aapt2 = $env:ANDROID_AAPT2
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..\..")).Path
$stagingRoot = Join-Path $repoRoot "qa\staging"
$candidateRoot = Join-Path $stagingRoot "v0.1.2"
$inventoryPath = Join-Path $candidateRoot "candidate-inputs.sha256"
$metadataPath = Join-Path $candidateRoot "candidate-metadata.json"
$evidencePath = Join-Path $repoRoot "qa\v1.2\release-candidate.md"
$artifactContract = Join-Path $PSScriptRoot "test-v1.2-artifact-contract.ps1"
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

function Assert-Candidate {
    param(
        [Parameter(Mandatory = $true)]
        [bool]$Condition,

        [Parameter(Mandatory = $true)]
        [string]$Message
    )

    if (-not $Condition) {
        throw "Candidate build failed: $Message"
    }
}

function Write-Utf8NoBom {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [string]$Content
    )

    [IO.File]::WriteAllText($Path, $Content, $utf8NoBom)
}

function Remove-CandidateRootSafely {
    $expected = [IO.Path]::GetFullPath((Join-Path ([IO.Path]::GetFullPath($stagingRoot)) "v0.1.2"))
    $actual = [IO.Path]::GetFullPath($candidateRoot)
    Assert-Candidate -Condition ($actual -eq $expected) -Message "refusing to remove unexpected staging path $actual"
    Assert-Candidate -Condition ($actual.StartsWith(([IO.Path]::GetFullPath($repoRoot) + [IO.Path]::DirectorySeparatorChar), [StringComparison]::OrdinalIgnoreCase)) -Message "staging path escapes the repository"
    if (Test-Path -LiteralPath $actual -PathType Container) {
        Remove-Item -LiteralPath $actual -Recurse -Force
    }
}

function Get-RelativeRepositoryPath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$FullName
    )

    $rootPrefix = $repoRoot.TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
    $resolved = [IO.Path]::GetFullPath($FullName)
    Assert-Candidate -Condition ($resolved.StartsWith($rootPrefix, [StringComparison]::OrdinalIgnoreCase)) -Message "input escapes the repository: $resolved"
    return $resolved.Substring($rootPrefix.Length).Replace('\', '/')
}

function Test-CandidateInputPath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RelativePath
    )

    $fileName = [IO.Path]::GetFileName($RelativePath)
    return (
        $RelativePath.StartsWith("build-logic/", [StringComparison]::Ordinal) -or
        $RelativePath.StartsWith("gradle/", [StringComparison]::Ordinal) -or
        $RelativePath.Contains("/src/") -or
        $RelativePath.Contains("/schemas/") -or
        $fileName -in @(
            "build.gradle.kts",
            "settings.gradle.kts",
            "gradle.properties",
            "gradlew",
            "gradlew.bat",
            "proguard-rules.pro",
            "consumer-rules.pro"
        ) -or
        $fileName.EndsWith(".pro", [StringComparison]::OrdinalIgnoreCase)
    )
}

function Get-CandidateInputFiles {
    $excludedAnywhere = @(".git", ".gradle", ".idea", ".kotlin", "build", "out")
    $excludedAtRoot = @("config", "dist", "docs", "qa", "tasks")
    $pending = New-Object 'System.Collections.Generic.Stack[string]'
    $pending.Push($repoRoot)
    $files = New-Object 'System.Collections.Generic.List[System.IO.FileInfo]'

    while ($pending.Count -gt 0) {
        $directory = $pending.Pop()
        foreach ($item in Get-ChildItem -LiteralPath $directory -Force) {
            if ($item.PSIsContainer) {
                if ($excludedAnywhere -contains $item.Name) {
                    continue
                }
                if ($directory -eq $repoRoot -and $excludedAtRoot -contains $item.Name) {
                    continue
                }
                $pending.Push($item.FullName)
                continue
            }

            $relativePath = Get-RelativeRepositoryPath -FullName $item.FullName
            if (Test-CandidateInputPath -RelativePath $relativePath) {
                $files.Add($item)
            }
        }
    }

    $publicFixture = Join-Path $repoRoot "qa\sample-novel.txt"
    Assert-Candidate -Condition (Test-Path -LiteralPath $publicFixture -PathType Leaf) -Message "public fixture is missing: qa/sample-novel.txt"
    $files.Add((Get-Item -LiteralPath $publicFixture))

    return @($files | Sort-Object { Get-RelativeRepositoryPath -FullName $_.FullName })
}

function Get-CandidateInventoryText {
    $lines = foreach ($file in Get-CandidateInputFiles) {
        $relativePath = Get-RelativeRepositoryPath -FullName $file.FullName
        $hash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToUpperInvariant()
        "$hash  $relativePath"
    }
    return (($lines -join "`n") + "`n")
}

function Get-Utf8Sha256 {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Text
    )

    $algorithm = [Security.Cryptography.SHA256]::Create()
    try {
        $bytes = $utf8NoBom.GetBytes($Text)
        return ([BitConverter]::ToString($algorithm.ComputeHash($bytes))).Replace("-", "")
    } finally {
        $algorithm.Dispose()
    }
}

function Resolve-Aapt2 {
    param(
        [string]$RequestedPath
    )

    if ($RequestedPath -and (Test-Path -LiteralPath $RequestedPath -PathType Leaf)) {
        return (Resolve-Path -LiteralPath $RequestedPath).Path
    }

    $sdkRoot = $env:ANDROID_SDK_ROOT
    if (-not $sdkRoot) {
        $sdkRoot = $env:ANDROID_HOME
    }
    if (-not $sdkRoot) {
        $localProperties = Join-Path $repoRoot "local.properties"
        Assert-Candidate -Condition (Test-Path -LiteralPath $localProperties -PathType Leaf) -Message "ANDROID_AAPT2, ANDROID_SDK_ROOT, ANDROID_HOME, and local.properties are unavailable"
        $sdkLine = Get-Content -LiteralPath $localProperties | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
        Assert-Candidate -Condition ($null -ne $sdkLine) -Message "local.properties does not define sdk.dir"
        $sdkRoot = $sdkLine.Substring("sdk.dir=".Length).Replace('\:', ':').Replace('/', [IO.Path]::DirectorySeparatorChar)
    }

    $buildToolsRoot = Join-Path $sdkRoot "build-tools"
    Assert-Candidate -Condition (Test-Path -LiteralPath $buildToolsRoot -PathType Container) -Message "Android build-tools directory is missing: $buildToolsRoot"
    $candidate = Get-ChildItem -LiteralPath $buildToolsRoot -Recurse -File -Filter "aapt2.exe" |
        Sort-Object { [version]$_.Directory.Name } -Descending |
        Select-Object -First 1
    Assert-Candidate -Condition ($null -ne $candidate) -Message "aapt2.exe was not found under $buildToolsRoot"
    return $candidate.FullName
}

function Invoke-Gradle {
    $arguments = @(
        ":app:assembleAcceptance",
        ":app:assembleAcceptanceAndroidTest",
        ":app:bundleRelease",
        "--no-configuration-cache"
    )
    Push-Location $repoRoot
    try {
        & (Join-Path $repoRoot "gradlew.bat") @arguments
        Assert-Candidate -Condition ($LASTEXITCODE -eq 0) -Message "Gradle candidate build exited with code $LASTEXITCODE"
    } finally {
        Pop-Location
    }
}

function Invoke-ArtifactContract {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Apk,

        [Parameter(Mandatory = $true)]
        [string]$Aab,

        [Parameter(Mandatory = $true)]
        [string]$ResolvedAapt2
    )

    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $artifactContract -Apk $Apk -Aab $Aab -Aapt2 $ResolvedAapt2
    Assert-Candidate -Condition ($LASTEXITCODE -eq 0) -Message "artifact contract exited with code $LASTEXITCODE"
}

function Get-ToolVersionLines {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ResolvedAapt2
    )

    Push-Location $repoRoot
    try {
        $gradleOutput = @(& (Join-Path $repoRoot "gradlew.bat") --version)
        Assert-Candidate -Condition ($LASTEXITCODE -eq 0) -Message "Gradle version query failed"
    } finally {
        Pop-Location
    }
    $gradleVersion = (($gradleOutput | Where-Object { $_ -match '^Gradle\s+' } | Select-Object -First 1) -replace '^Gradle\s+', '').Trim()
    $launcherJvm = (($gradleOutput | Where-Object { $_ -match '^Launcher JVM:' } | Select-Object -First 1) -replace '^Launcher JVM:\s*', '').Trim()
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $aapt2Output = @(& $ResolvedAapt2 version 2>&1)
        $aapt2ExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    Assert-Candidate -Condition ($aapt2ExitCode -eq 0) -Message "aapt2 version query failed"
    $aapt2Version = (($aapt2Output | ForEach-Object { $_.ToString() }) -join " ").Trim()

    return [pscustomobject]@{
        Gradle = $gradleVersion
        Jdk = $launcherJvm
        AndroidBuildTools = (Split-Path -Leaf (Split-Path -Parent $ResolvedAapt2))
        Aapt2 = $aapt2Version
    }
}

$buildCommand = ".\gradlew.bat :app:assembleAcceptance :app:assembleAcceptanceAndroidTest :app:bundleRelease --no-configuration-cache"
$resolvedAapt2 = Resolve-Aapt2 -RequestedPath $Aapt2

Remove-CandidateRootSafely
New-Item -ItemType Directory -Path $candidateRoot -Force | Out-Null

try {
    $inventoryText = Get-CandidateInventoryText
    Write-Utf8NoBom -Path $inventoryPath -Content $inventoryText
    $candidateId = (Get-FileHash -LiteralPath $inventoryPath -Algorithm SHA256).Hash.ToUpperInvariant()
    Assert-Candidate -Condition ($candidateId -eq (Get-Utf8Sha256 -Text $inventoryText)) -Message "inventory hash is not canonical UTF-8"

    Invoke-Gradle

    $postBuildInventoryText = Get-CandidateInventoryText
    $postBuildCandidateId = Get-Utf8Sha256 -Text $postBuildInventoryText
    Assert-Candidate -Condition ($postBuildCandidateId -eq $candidateId) -Message "candidate inputs changed during the build; staging was invalidated"

    $builtApk = Join-Path $repoRoot "app\build\outputs\apk\acceptance\app-acceptance.apk"
    $builtTestApk = Join-Path $repoRoot "app\build\outputs\apk\androidTest\acceptance\app-acceptance-androidTest.apk"
    $builtAab = Join-Path $repoRoot "app\build\outputs\bundle\release\app-release.aab"
    Assert-Candidate -Condition (Test-Path -LiteralPath $builtApk -PathType Leaf) -Message "acceptance APK output is missing"
    Assert-Candidate -Condition (Test-Path -LiteralPath $builtTestApk -PathType Leaf) -Message "acceptance test APK output is missing"
    Assert-Candidate -Condition (Test-Path -LiteralPath $builtAab -PathType Leaf) -Message "release AAB output is missing"
    Invoke-ArtifactContract -Apk $builtApk -Aab $builtAab -ResolvedAapt2 $resolvedAapt2

    $stagedApk = Join-Path $candidateRoot "xinyue-v0.1.2-acceptance.apk"
    $stagedTestApk = Join-Path $candidateRoot "xinyue-v0.1.2-acceptance-androidTest.apk"
    $stagedAab = Join-Path $candidateRoot "xinyue-v0.1.2-release.aab"
    Copy-Item -LiteralPath $builtApk -Destination $stagedApk
    Copy-Item -LiteralPath $builtTestApk -Destination $stagedTestApk
    Copy-Item -LiteralPath $builtAab -Destination $stagedAab

    $sourceApkHash = (Get-FileHash -LiteralPath $builtApk -Algorithm SHA256).Hash.ToUpperInvariant()
    $sourceTestApkHash = (Get-FileHash -LiteralPath $builtTestApk -Algorithm SHA256).Hash.ToUpperInvariant()
    $sourceAabHash = (Get-FileHash -LiteralPath $builtAab -Algorithm SHA256).Hash.ToUpperInvariant()
    $apkHash = (Get-FileHash -LiteralPath $stagedApk -Algorithm SHA256).Hash.ToUpperInvariant()
    $testApkHash = (Get-FileHash -LiteralPath $stagedTestApk -Algorithm SHA256).Hash.ToUpperInvariant()
    $aabHash = (Get-FileHash -LiteralPath $stagedAab -Algorithm SHA256).Hash.ToUpperInvariant()
    Assert-Candidate -Condition ($sourceApkHash -eq $apkHash) -Message "staged APK bytes differ from the verified build output"
    Assert-Candidate -Condition ($sourceTestApkHash -eq $testApkHash) -Message "staged test APK bytes differ from the verified build output"
    Assert-Candidate -Condition ($sourceAabHash -eq $aabHash) -Message "staged AAB bytes differ from the verified build output"

    Invoke-ArtifactContract -Apk $stagedApk -Aab $stagedAab -ResolvedAapt2 $resolvedAapt2
    $toolVersions = Get-ToolVersionLines -ResolvedAapt2 $resolvedAapt2
    $timestamp = [DateTimeOffset]::UtcNow.ToString("o")
    $inputCount = @($inventoryText.TrimEnd("`r", "`n").Split("`n")).Count

    $metadata = [ordered]@{
        schemaVersion = 1
        candidateId = $candidateId
        versionCode = 3
        versionName = "0.1.2"
        createdAtUtc = $timestamp
        inventory = "qa/staging/v0.1.2/candidate-inputs.sha256"
        inputCount = $inputCount
        build = [ordered]@{
            command = $buildCommand
            gradle = $toolVersions.Gradle
            jdk = $toolVersions.Jdk
            androidBuildTools = $toolVersions.AndroidBuildTools
            aapt2 = $toolVersions.Aapt2
        }
        artifacts = [ordered]@{
            acceptanceApk = [ordered]@{
                path = "qa/staging/v0.1.2/xinyue-v0.1.2-acceptance.apk"
                sha256 = $apkHash
                signing = "Local Android debug key for direct installation only"
            }
            acceptanceTestApk = [ordered]@{
                path = "qa/staging/v0.1.2/xinyue-v0.1.2-acceptance-androidTest.apk"
                sha256 = $testApkHash
                signing = "Generated instrumentation companion for this exact candidate"
            }
            releaseAab = [ordered]@{
                path = "qa/staging/v0.1.2/xinyue-v0.1.2-release.aab"
                sha256 = $aabHash
                signing = "No repository-stored production signing secret"
            }
        }
    }
    Write-Utf8NoBom -Path $metadataPath -Content (($metadata | ConvertTo-Json -Depth 8) + "`n")

    $evidenceDirectory = Split-Path -Parent $evidencePath
    New-Item -ItemType Directory -Path $evidenceDirectory -Force | Out-Null
    $evidence = @"
# V1.2 Release Candidate

- Status: E1 candidate identity and artifact contract passed. E2-E8 must reference this exact candidateId and the applicable artifact hash.
- Build timestamp (UTC): $timestamp
- candidateId: $candidateId
- Candidate input count: $inputCount
- Input inventory: qa/staging/v0.1.2/candidate-inputs.sha256
- Machine-readable metadata: qa/staging/v0.1.2/candidate-metadata.json
- Acceptance APK SHA-256: $apkHash
- Acceptance test APK SHA-256: $testApkHash
- Release AAB SHA-256: $aabHash
- Build command: $buildCommand
- Gradle: $($toolVersions.Gradle)
- JDK: $($toolVersions.Jdk)
- Android Build Tools: $($toolVersions.AndroidBuildTools)
- aapt2: $($toolVersions.Aapt2)

## Artifacts

- qa/staging/v0.1.2/xinyue-v0.1.2-acceptance.apk: non-debuggable, minified, and resource-shrunk. It uses the local Android debug key for direct-install acceptance only and is not production-signed.
- qa/staging/v0.1.2/xinyue-v0.1.2-acceptance-androidTest.apk: immutable instrumentation companion built from the same candidate inventory; device matrices verify its SHA-256 before installation.
- qa/staging/v0.1.2/xinyue-v0.1.2-release.aab: normal Release bundle. No production signing secret is stored in the repository; the publisher must apply the production signing process before store upload.

## Immutability rule

Every downstream device, performance, accessibility, boundary-content, and release-audit script must read candidate-metadata.json, then verify the current candidateId and the SHA-256 of the APK or AAB it consumes. Production source, resource, Gradle/build-logic input, Room schema, manifest, ProGuard rule, Baseline Profile, or public fixture changes invalidate staging and require a rebuilt candidate. Under ADR-005, only affected minimum gates are rerun; test-helper and evidence-document changes alone do not require a candidate rebuild. Different IDs or hashes must never be mixed without an explicit evidence note explaining which unchanged behavior is being reused.
"@
    Write-Utf8NoBom -Path $evidencePath -Content ($evidence.TrimStart() + "`n")

    Write-Host "V1.2 candidate staged successfully."
    Write-Host "candidateId=$candidateId"
    Write-Host "acceptanceApkSha256=$apkHash"
    Write-Host "acceptanceTestApkSha256=$testApkHash"
    Write-Host "releaseAabSha256=$aabHash"
} catch {
    Remove-CandidateRootSafely
    throw
}
