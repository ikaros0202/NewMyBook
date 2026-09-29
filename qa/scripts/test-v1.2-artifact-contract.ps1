[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Apk,

    [Parameter(Mandatory = $true)]
    [string]$Aab,

    [Parameter(Mandatory = $true)]
    [string]$Aapt2,

    [string]$ExpectedVersionCode = "3",

    [string]$ExpectedVersionName = "0.1.2"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$androidNamespace = "http://schemas.android.com/apk/res/android"
$expected = @{
    ApplicationId = "com.xinyue.reader"
    VersionCode = $ExpectedVersionCode
    VersionName = $ExpectedVersionName
    MinSdk = "26"
    TargetSdk = "37"
}
$forbiddenPermissions = @(
    "android.permission.INTERNET",
    "android.permission.READ_EXTERNAL_STORAGE",
    "android.permission.WRITE_EXTERNAL_STORAGE",
    "android.permission.MANAGE_EXTERNAL_STORAGE",
    "android.permission.QUERY_ALL_PACKAGES"
)

function Assert-Contract {
    param(
        [Parameter(Mandatory = $true)]
        [bool]$Condition,

        [Parameter(Mandatory = $true)]
        [string]$Message
    )

    if (-not $Condition) {
        throw "Artifact contract failed: $Message"
    }
}

function Resolve-RequiredPath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [string]$Description
    )

    Assert-Contract -Condition (Test-Path -LiteralPath $Path -PathType Leaf) -Message "$Description does not exist: $Path"
    return (Resolve-Path -LiteralPath $Path).Path
}

function Invoke-NativeText {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Executable,

        [Parameter(Mandatory = $true)]
        [string[]]$Arguments,

        [Parameter(Mandatory = $true)]
        [string]$Description
    )

    $output = @(& $Executable @Arguments 2>&1)
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        $details = $output -join [Environment]::NewLine
        throw "$Description failed with exit code ${exitCode}:$([Environment]::NewLine)$details"
    }
    return ($output -join [Environment]::NewLine)
}

function Find-ApkAnalyzer {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ResolvedAapt2
    )

    $buildToolsDirectory = Split-Path -Parent $ResolvedAapt2
    $sdkRoot = Split-Path -Parent (Split-Path -Parent $buildToolsDirectory)
    $preferred = Join-Path $sdkRoot "cmdline-tools\latest\bin\apkanalyzer.bat"
    if (Test-Path -LiteralPath $preferred -PathType Leaf) {
        return (Resolve-Path -LiteralPath $preferred).Path
    }

    $fallback = Get-ChildItem -LiteralPath (Join-Path $sdkRoot "cmdline-tools") -Recurse -File -Filter "apkanalyzer.bat" -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending |
        Select-Object -First 1
    Assert-Contract -Condition ($null -ne $fallback) -Message "apkanalyzer.bat was not found under SDK root $sdkRoot"
    return $fallback.FullName
}

function Find-CachedModuleJar {
    param(
        [Parameter(Mandatory = $true)]
        [string]$GradleModules,

        [Parameter(Mandatory = $true)]
        [string]$Group,

        [Parameter(Mandatory = $true)]
        [string]$Artifact,

        [string]$PreferredVersion
    )

    $artifactRoot = Join-Path $GradleModules (Join-Path $Group $Artifact)
    Assert-Contract -Condition (Test-Path -LiteralPath $artifactRoot -PathType Container) -Message "Gradle cache is missing $Group/$Artifact"

    $candidates = @(Get-ChildItem -LiteralPath $artifactRoot -Recurse -File -Filter "$Artifact-*.jar" -ErrorAction SilentlyContinue)
    if ($PreferredVersion) {
        $preferred = @($candidates | Where-Object { $_.FullName -like "*\$PreferredVersion\*" } | Sort-Object FullName | Select-Object -First 1)
        if ($preferred.Count -gt 0) {
            return $preferred[0].FullName
        }
    }

    $fallback = @($candidates | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1)
    Assert-Contract -Condition ($fallback.Count -gt 0) -Message "Gradle cache contains no JAR for $Group/$Artifact"
    return $fallback[0].FullName
}

function Get-BundletoolClasspath {
    $gradleHome = if ($env:GRADLE_USER_HOME) {
        $env:GRADLE_USER_HOME
    } elseif ($env:USERPROFILE) {
        Join-Path $env:USERPROFILE ".gradle"
    } else {
        throw "Neither GRADLE_USER_HOME nor USERPROFILE is set; cannot locate the offline Bundletool dependencies."
    }
    $modules = Join-Path $gradleHome "caches\modules-2\files-2.1"

    $dependencies = @(
        @("com.android.tools.build", "bundletool", "1.18.3"),
        @("com.android.tools.build", "aapt2-proto", "9.2.0-15009934"),
        @("com.google.auto.value", "auto-value-annotations", "1.6.2"),
        @("com.google.errorprone", "error_prone_annotations", "2.41.0"),
        @("com.google.guava", "guava", "32.0.1-jre"),
        @("com.google.guava", "failureaccess", "1.0.3"),
        @("com.google.guava", "listenablefuture", "9999.0-empty-to-avoid-conflict-with-guava"),
        @("com.google.code.findbugs", "jsr305", "3.0.2"),
        @("org.checkerframework", "checker-qual", "3.43.0"),
        @("com.google.j2objc", "j2objc-annotations", "3.0.0"),
        @("com.google.protobuf", "protobuf-java", "4.28.3"),
        @("com.google.protobuf", "protobuf-java-util", "4.28.3"),
        @("com.google.code.gson", "gson", "2.11.0"),
        @("com.google.dagger", "dagger", "2.28.3"),
        @("javax.inject", "javax.inject", "1"),
        @("org.bitbucket.b_c", "jose4j", "0.9.5"),
        @("org.slf4j", "slf4j-api", "1.7.30")
    )

    $jars = foreach ($dependency in $dependencies) {
        Find-CachedModuleJar -GradleModules $modules -Group $dependency[0] -Artifact $dependency[1] -PreferredVersion $dependency[2]
    }
    return ($jars -join [IO.Path]::PathSeparator)
}

function ConvertTo-ManifestXml {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Text,

        [Parameter(Mandatory = $true)]
        [string]$Description
    )

    try {
        return [xml]$Text
    } catch {
        throw "$Description did not produce valid manifest XML: $($_.Exception.Message)"
    }
}

function Test-ManifestContract {
    param(
        [Parameter(Mandatory = $true)]
        [xml]$Manifest,

        [Parameter(Mandatory = $true)]
        [string]$ArtifactName
    )

    $manager = New-Object System.Xml.XmlNamespaceManager($Manifest.NameTable)
    $manager.AddNamespace("android", $androidNamespace)
    $root = $Manifest.DocumentElement
    Assert-Contract -Condition ($root.LocalName -eq "manifest") -Message "$ArtifactName root element is not manifest"
    Assert-Contract -Condition ($root.GetAttribute("package") -eq $expected.ApplicationId) -Message "$ArtifactName applicationId is '$($root.GetAttribute("package"))'"
    Assert-Contract -Condition ($root.GetAttribute("versionCode", $androidNamespace) -eq $expected.VersionCode) -Message "$ArtifactName versionCode is '$($root.GetAttribute("versionCode", $androidNamespace))'"
    Assert-Contract -Condition ($root.GetAttribute("versionName", $androidNamespace) -eq $expected.VersionName) -Message "$ArtifactName versionName is '$($root.GetAttribute("versionName", $androidNamespace))'"

    $usesSdk = $root.SelectSingleNode("uses-sdk")
    Assert-Contract -Condition ($null -ne $usesSdk) -Message "$ArtifactName has no uses-sdk element"
    Assert-Contract -Condition ($usesSdk.GetAttribute("minSdkVersion", $androidNamespace) -eq $expected.MinSdk) -Message "$ArtifactName minSdk is '$($usesSdk.GetAttribute("minSdkVersion", $androidNamespace))'"
    Assert-Contract -Condition ($usesSdk.GetAttribute("targetSdkVersion", $androidNamespace) -eq $expected.TargetSdk) -Message "$ArtifactName targetSdk is '$($usesSdk.GetAttribute("targetSdkVersion", $androidNamespace))'"

    $permissions = @($root.SelectNodes("uses-permission", $manager) | ForEach-Object { $_.GetAttribute("name", $androidNamespace) })
    foreach ($permission in $forbiddenPermissions) {
        Assert-Contract -Condition ($permissions -notcontains $permission) -Message "$ArtifactName declares forbidden permission $permission"
    }

    $application = $root.SelectSingleNode("application")
    Assert-Contract -Condition ($null -ne $application) -Message "$ArtifactName has no application element"
    Assert-Contract -Condition ($application.GetAttribute("debuggable", $androidNamespace) -ne "true") -Message "$ArtifactName is debuggable"
    Assert-Contract -Condition ($application.GetAttribute("usesCleartextTraffic", $androidNamespace) -ne "true") -Message "$ArtifactName allows cleartext traffic"
    Assert-Contract -Condition (-not $application.HasAttribute("networkSecurityConfig", $androidNamespace)) -Message "$ArtifactName defines a network security exception"

    foreach ($profileable in @($application.SelectNodes("profileable"))) {
        Assert-Contract -Condition ($profileable.GetAttribute("shell", $androidNamespace) -ne "true") -Message "$ArtifactName is profileable by shell"
    }

    $componentNames = @("activity", "activity-alias", "service", "receiver", "provider")
    foreach ($componentName in $componentNames) {
        foreach ($component in @($application.SelectNodes($componentName))) {
            if ($component.GetAttribute("exported", $androidNamespace) -ne "true") {
                continue
            }

            $isLauncher = $false
            foreach ($intentFilter in @($component.SelectNodes("intent-filter"))) {
                $hasMain = @($intentFilter.SelectNodes("action") | Where-Object { $_.GetAttribute("name", $androidNamespace) -eq "android.intent.action.MAIN" }).Count -gt 0
                $hasLauncher = @($intentFilter.SelectNodes("category") | Where-Object { $_.GetAttribute("name", $androidNamespace) -eq "android.intent.category.LAUNCHER" }).Count -gt 0
                if ($hasMain -and $hasLauncher) {
                    $isLauncher = $true
                    break
                }
            }

            $name = $component.GetAttribute("name", $androidNamespace)
            $isRequiredSystemJobService =
                $componentName -eq "service" -and
                $name -eq "androidx.work.impl.background.systemjob.SystemJobService" -and
                $component.GetAttribute("permission", $androidNamespace) -eq "android.permission.BIND_JOB_SERVICE"
            Assert-Contract -Condition (
                (($componentName -eq "activity" -or $componentName -eq "activity-alias") -and $isLauncher) -or
                $isRequiredSystemJobService
            ) -Message "$ArtifactName exports unnecessary non-launcher component $componentName '$name'"
        }
    }
}

$resolvedApk = Resolve-RequiredPath -Path $Apk -Description "Acceptance APK"
$resolvedAab = Resolve-RequiredPath -Path $Aab -Description "Release AAB"
$resolvedAapt2 = Resolve-RequiredPath -Path $Aapt2 -Description "aapt2"
$apkAnalyzer = Find-ApkAnalyzer -ResolvedAapt2 $resolvedAapt2

$badging = Invoke-NativeText -Executable $resolvedAapt2 -Arguments @("dump", "badging", $resolvedApk) -Description "aapt2 APK inspection"
Assert-Contract -Condition ($badging -notmatch "(?m)^application-debuggable$") -Message "Acceptance APK is debuggable according to aapt2"

$apkManifestText = Invoke-NativeText -Executable $apkAnalyzer -Arguments @("manifest", "print", $resolvedApk) -Description "APK manifest inspection"
$bundletoolClasspath = Get-BundletoolClasspath
$aabManifestText = Invoke-NativeText -Executable "java" -Arguments @(
    "-cp",
    $bundletoolClasspath,
    "com.android.tools.build.bundletool.BundleToolMain",
    "dump",
    "manifest",
    "--bundle=$resolvedAab",
    "--module=base"
) -Description "AAB manifest inspection"

Test-ManifestContract -Manifest (ConvertTo-ManifestXml -Text $apkManifestText -Description "APK inspection") -ArtifactName "acceptance APK"
Test-ManifestContract -Manifest (ConvertTo-ManifestXml -Text $aabManifestText -Description "AAB inspection") -ArtifactName "release AAB"

Write-Host "Artifact contract passed for acceptance APK and release AAB."
