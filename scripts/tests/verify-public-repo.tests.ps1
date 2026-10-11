param(
    [string]$ScriptUnderTest = (Join-Path $PSScriptRoot "..\verify-public-repo.ps1")
)

$ErrorActionPreference = "Stop"
$systemTempRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd("\") + "\"
$testRoot = [System.IO.Path]::GetFullPath(
    (Join-Path $systemTempRoot ("xinyue-privacy-audit-" + [guid]::NewGuid()))
)
if (-not $testRoot.StartsWith($systemTempRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Test directory resolved outside the system temporary directory"
}
New-Item -ItemType Directory -Path $testRoot | Out-Null

function Invoke-Audit([string]$FixtureRoot, [string]$DenylistPath = "") {
    $arguments = @(
        "-NoProfile",
        "-ExecutionPolicy", "Bypass",
        "-File", $ScriptUnderTest,
        "-RepositoryRoot", $FixtureRoot
    )
    if ($DenylistPath) {
        $arguments += @("-PrivateDenylistPath", $DenylistPath)
    }
    & powershell.exe @arguments *> $null
    return $LASTEXITCODE
}

try {
    $cleanRoot = Join-Path $testRoot "clean"
    New-Item -ItemType Directory -Path $cleanRoot | Out-Null
    Set-Content -Encoding UTF8 -LiteralPath (Join-Path $cleanRoot "README.md") -Value 'Use %USERPROFILE% for local tools.'
    if ((Invoke-Audit $cleanRoot) -ne 0) {
        throw "A clean public fixture must pass"
    }

    $homeLeakRoot = Join-Path $testRoot "home-leak"
    New-Item -ItemType Directory -Path $homeLeakRoot | Out-Null
    $concreteHomePath = "C:" + [char]92 + "Users" + [char]92 + "ExamplePerson" + [char]92 + "private.txt"
    Set-Content -Encoding UTF8 -LiteralPath (Join-Path $homeLeakRoot "notes.md") -Value "Local path: $concreteHomePath"
    if ((Invoke-Audit $homeLeakRoot) -eq 0) {
        throw "A concrete Windows user-home path must fail"
    }

    $secretRoot = Join-Path $testRoot "secret"
    New-Item -ItemType Directory -Path $secretRoot | Out-Null
    Set-Content -Encoding UTF8 -LiteralPath (Join-Path $secretRoot "config.txt") -Value ('token=' + 'ghp_' + ('A' * 36))
    if ((Invoke-Audit $secretRoot) -eq 0) {
        throw "A credential-shaped token must fail"
    }

    $denylistRoot = Join-Path $testRoot "denylist"
    New-Item -ItemType Directory -Path $denylistRoot | Out-Null
    Set-Content -Encoding UTF8 -LiteralPath (Join-Path $denylistRoot "evidence.md") -Value 'PRIVATE_FIXTURE_MARKER'
    $denylist = Join-Path $testRoot "private-denylist.txt"
    Set-Content -Encoding UTF8 -LiteralPath $denylist -Value 'PRIVATE_FIXTURE_MARKER'
    if ((Invoke-Audit $denylistRoot $denylist) -eq 0) {
        throw "A literal from the external private denylist must fail"
    }

    $unicodeDenylistRoot = Join-Path $testRoot "denylist-utf16"
    New-Item -ItemType Directory -Path $unicodeDenylistRoot | Out-Null
    [System.IO.File]::WriteAllBytes(
        (Join-Path $unicodeDenylistRoot "evidence.txt"),
        [System.Text.Encoding]::Unicode.GetBytes('PRIVATE_FIXTURE_MARKER')
    )
    if ((Invoke-Audit $unicodeDenylistRoot $denylist) -eq 0) {
        throw "A BOM-less UTF-16 text file containing an external private denylist literal must fail"
    }

    $denylistFilenameRoot = Join-Path $testRoot "denylist-filename"
    New-Item -ItemType Directory -Path $denylistFilenameRoot | Out-Null
    Set-Content -Encoding UTF8 -LiteralPath (Join-Path $denylistFilenameRoot "PRIVATE_FIXTURE_MARKER.md") -Value 'generic content'
    if ((Invoke-Audit $denylistFilenameRoot $denylist) -eq 0) {
        throw "A filename containing an external private denylist literal must fail"
    }
    foreach ($extension in @('.py', '.pyw', '.pyi')) {
        $pythonRoot = Join-Path $testRoot ('python-' + $extension.Substring(1))
        New-Item -ItemType Directory -Path $pythonRoot | Out-Null
        $pythonFile = Join-Path $pythonRoot ('helper' + $extension)
        foreach ($unsafeText in @($concreteHomePath, ('ghp_' + ('B' * 36)), 'PRIVATE_FIXTURE_MARKER')) {
            Set-Content -Encoding UTF8 -LiteralPath $pythonFile -Value $unsafeText
            if ((Invoke-Audit $pythonRoot $denylist) -eq 0) { throw "Python source privacy leak was missed: $extension" }
        }
        Set-Content -Encoding UTF8 -LiteralPath $pythonFile -Value 'print("public fixture")'
        if ((Invoke-Audit $pythonRoot $denylist) -ne 0) { throw "Clean Python source must pass: $extension" }
    }
    $localRoot = Join-Path $testRoot 'ignored-local-denylist'
    New-Item -ItemType Directory -Path (Join-Path $localRoot 'private-local/qa') -Force | Out-Null
    & git -C $localRoot init -q
    if ($LASTEXITCODE -ne 0) { throw 'Could not initialize privacy fixture repository' }
    Set-Content -LiteralPath (Join-Path $localRoot '.gitignore') -Value 'private-local/'
    Set-Content -LiteralPath (Join-Path $localRoot 'README.md') -Value 'Public fixture'
    $localDenylist = Join-Path $localRoot 'private-local/qa/denylist.txt'
    Set-Content -LiteralPath $localDenylist -Value 'PRIVATE_LOCAL_MARKER'
    if ((Invoke-Audit $localRoot $localDenylist) -ne 0) {
        throw 'An ignored project-local QA denylist must be supported'
    }
    Set-Content -LiteralPath (Join-Path $localRoot 'README.md') -Value 'PRIVATE_LOCAL_MARKER'
    if ((Invoke-Audit $localRoot $localDenylist) -eq 0) {
        throw 'An ignored project-local QA denylist must still detect private content'
    }
    # Git initializes hidden metadata; clear that attribute for ordinary cleanup.
    (Get-Item -LiteralPath (Join-Path $localRoot '.git') -Force).Attributes = [IO.FileAttributes]::Directory
} finally {
    if ($testRoot.StartsWith($systemTempRoot, [System.StringComparison]::OrdinalIgnoreCase) -and
        (Test-Path -LiteralPath $testRoot)) {
        Remove-Item -LiteralPath $testRoot -Recurse
    }
}

Write-Output "PASS: public repository privacy audit rejects home paths, secrets, and private denylist literals."
