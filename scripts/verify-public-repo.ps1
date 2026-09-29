param(
    [string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [string]$PrivateDenylistPath = $env:XINYUE_PRIVATE_DENYLIST
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path -LiteralPath $RepositoryRoot).Path
$rootPrefix = $root.TrimEnd("\") + "\"
$failures = [System.Collections.Generic.List[string]]::new()

function Add-Failure([string]$Path, [string]$Rule) {
    $failures.Add("${Path}: $Rule")
}

function Get-TextCandidates([string]$Path) {
    $bytes = [System.IO.File]::ReadAllBytes($Path)
    if ($bytes.Length -eq 0) {
        return ,@("")
    }
    if ($bytes.Length -ge 2 -and $bytes[0] -eq 0xFF -and $bytes[1] -eq 0xFE) {
        return ,@([System.Text.Encoding]::Unicode.GetString($bytes, 2, $bytes.Length - 2))
    }
    if ($bytes.Length -ge 2 -and $bytes[0] -eq 0xFE -and $bytes[1] -eq 0xFF) {
        return ,@([System.Text.Encoding]::BigEndianUnicode.GetString($bytes, 2, $bytes.Length - 2))
    }
    if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
        return ,@([System.Text.Encoding]::UTF8.GetString($bytes, 3, $bytes.Length - 3))
    }

    return ,@(
        [System.Text.Encoding]::UTF8.GetString($bytes),
        [System.Text.Encoding]::Unicode.GetString($bytes),
        [System.Text.Encoding]::BigEndianUnicode.GetString($bytes)
    )
}

function Test-AnyRegexMatch([string[]]$Contents, [string]$Pattern, [switch]$CaseSensitive) {
    foreach ($candidate in $Contents) {
        if ($CaseSensitive) {
            if ($candidate -cmatch $Pattern) { return $true }
        } elseif ($candidate -match $Pattern) {
            return $true
        }
    }
    return $false
}

function Test-AnyLiteral([string[]]$Contents, [string]$Literal) {
    foreach ($candidate in $Contents) {
        if ($candidate.IndexOf($Literal, [System.StringComparison]::Ordinal) -ge 0) {
            return $true
        }
    }
    return $false
}

function Get-PublicFiles {
    $gitDirectory = Join-Path $root ".git"
    if (Test-Path -LiteralPath $gitDirectory) {
        Push-Location $root
        try {
            $relativePaths = @(& git ls-files --cached --others --exclude-standard)
            if ($LASTEXITCODE -ne 0) {
                throw "git ls-files failed with exit code $LASTEXITCODE"
            }
            foreach ($relativePath in $relativePaths) {
                $candidate = Join-Path $root $relativePath
                if (Test-Path -LiteralPath $candidate -PathType Leaf) {
                    Get-Item -LiteralPath $candidate
                }
            }
        } finally {
            Pop-Location
        }
        return
    }

    Get-ChildItem -LiteralPath $root -File -Recurse | Where-Object {
        $relativePath = $_.FullName.Substring($rootPrefix.Length).Replace("\", "/")
        $relativePath -notmatch '(^|/)(\.git|\.gradle|\.kotlin|\.superpowers|\.idea|build)(/|$)'
    }
}

$denylist = @()
if ($PrivateDenylistPath) {
    if (-not (Test-Path -LiteralPath $PrivateDenylistPath -PathType Leaf)) {
        throw "Private denylist does not exist: $PrivateDenylistPath"
    }
    $denylistFullPath = (Resolve-Path -LiteralPath $PrivateDenylistPath).Path
    if ($denylistFullPath.StartsWith($rootPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Private denylist must be stored outside the repository"
    }
    $denylist = @(Get-Content -Encoding UTF8 -LiteralPath $denylistFullPath | Where-Object {
        $_.Trim() -and -not $_.TrimStart().StartsWith("#")
    })
}

$textExtensions = @(
    ".bat", ".conf", ".css", ".csv", ".gradle", ".html", ".java", ".js",
    ".json", ".kt", ".kts", ".log", ".md", ".properties", ".ps1", ".sh",
    ".toml", ".txt", ".xml", ".yaml", ".yml"
)
$archiveExtensions = @(".aab", ".apk", ".zip")
$imageExtensions = @(".gif", ".jpeg", ".jpg", ".png", ".webp")
$files = @(Get-PublicFiles)
$textFileCount = 0
$archiveCount = 0
$imageCount = 0

foreach ($file in $files) {
    $relativePath = $file.FullName.Substring($rootPrefix.Length).Replace("\", "/")
    $extension = $file.Extension.ToLowerInvariant()

    if (($file.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
        Add-Failure $relativePath "symbolic links or reparse points are not allowed in the public file set"
    }
    if ($file.Length -ge 100MB) {
        Add-Failure $relativePath "file is at least 100 MiB"
    }
    if ($relativePath -match '(?i)(^|/)(local\.properties|\.superpowers(/|$)|\.kotlin(/|$)|\.gradle(/|$)|private-local(/|$)|private-qa(/|$))') {
        Add-Failure $relativePath "machine-local or private-only path is included"
    }
    if ($relativePath -match '(?i)\.(jks|keystore|p12|pem|key)$') {
        Add-Failure $relativePath "credential container or private key file is included"
    }
    foreach ($privateLiteral in $denylist) {
        if ($relativePath.IndexOf($privateLiteral, [System.StringComparison]::Ordinal) -ge 0) {
            Add-Failure $relativePath "filename contains a private denylist literal"
            break
        }
    }

    if ($extension -in $imageExtensions) {
        $imageCount++
    }

    if ($extension -in $textExtensions) {
        $textFileCount++
        $contentCandidates = @(Get-TextCandidates $file.FullName)

        if (Test-AnyRegexMatch $contentCandidates '(?i)([A-Z]:\\Users\\[^\\\r\n]+\\|(?<![A-Za-z0-9_.-])/(?:Users|home)/[^/\s]+/)') {
            Add-Failure $relativePath "concrete user-home path found"
        }
        if (Test-AnyRegexMatch $contentCandidates '(?<![A-Za-z0-9_])gh[opusr]_[A-Za-z0-9]{20,}(?![A-Za-z0-9])|github_pat_[A-Za-z0-9_]{20,}') {
            Add-Failure $relativePath "GitHub credential-shaped token found"
        }
        if (Test-AnyRegexMatch $contentCandidates '(?<![0-9A-Za-z_-])AIza[0-9A-Za-z_-]{35}(?![0-9A-Za-z_-])') {
            Add-Failure $relativePath "Google API key-shaped token found"
        }
        if (Test-AnyRegexMatch $contentCandidates '(?<![0-9A-Z])AKIA[0-9A-Z]{16}(?![0-9A-Z])' -CaseSensitive) {
            Add-Failure $relativePath "AWS access key-shaped token found"
        }
        if (Test-AnyRegexMatch $contentCandidates '-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----') {
            Add-Failure $relativePath "private key block found"
        }
        foreach ($privateLiteral in $denylist) {
            if (Test-AnyLiteral $contentCandidates $privateLiteral) {
                Add-Failure $relativePath "literal from external private denylist found"
                break
            }
        }
    }

    if ($extension -in $archiveExtensions) {
        $archiveCount++
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $archive = [System.IO.Compression.ZipFile]::OpenRead($file.FullName)
        try {
            foreach ($entry in $archive.Entries) {
                $entryName = $entry.FullName
                $hasWindowsHome = $entryName -match '(?i)[A-Z]:\\Users\\'
                $hasUnixHome = $entryName.StartsWith("/Users/", [System.StringComparison]::OrdinalIgnoreCase) -or
                    $entryName.StartsWith("/home/", [System.StringComparison]::OrdinalIgnoreCase)
                $hasPrivateEntry = $entryName -match '(?i)(\.jks$|\.keystore$|private-local|private-qa)'
                if ($hasWindowsHome -or $hasUnixHome -or $hasPrivateEntry) {
                    Add-Failure $relativePath "archive entry name contains a local/private marker"
                    break
                }
                foreach ($privateLiteral in $denylist) {
                    if ($entry.FullName.IndexOf($privateLiteral, [System.StringComparison]::Ordinal) -ge 0) {
                        Add-Failure $relativePath "archive entry name contains a private denylist literal"
                        break
                    }
                }
            }
        } finally {
            $archive.Dispose()
        }
    }
}

if ($failures.Count -gt 0) {
    Write-Output "AUDIT_FAIL files=$($files.Count) failures=$($failures.Count)"
    $failures | Sort-Object -Unique | ForEach-Object { Write-Output "FAIL $_" }
    exit 1
}

Write-Output "AUDIT_PASS files=$($files.Count) text=$textFileCount archives=$archiveCount images=$imageCount"
if ($imageCount -gt 0) {
    Write-Output "MANUAL_REVIEW_REQUIRED images=$imageCount"
}
