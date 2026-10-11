# Legacy acceptance APKs share the production application ID. Never reuse a device
# carrying that package or retained data in ANY Android user, even a stopped user.
function Assert-EmptyAcceptanceDevice {
    param([Parameter(Mandatory = $true)][scriptblock]$InvokeAdb)
    $usersResult = & $InvokeAdb @('shell', 'pm', 'list', 'users')
    if ($usersResult.ExitCode -ne 0) { throw 'Cannot verify Android users; acceptance install refused.' }
    $userIds = @($usersResult.Lines | ForEach-Object {
        if ([string]$_ -match 'UserInfo\{(\d+):') { $Matches[1] }
    })
    if ($userIds.Count -eq 0) { throw 'Cannot verify Android users; acceptance install refused.' }
    foreach ($userId in $userIds) {
        $packages = & $InvokeAdb @('shell', 'pm', 'list', 'packages', '-u', '--user', $userId, 'com.xinyue.reader')
        if ($packages.ExitCode -ne 0) { throw 'Cannot verify retained application data; acceptance install refused.' }
        if (@($packages.Lines | Where-Object { ([string]$_).Trim() -match '^package:com\.xinyue\.reader(?:\.test)?$' }).Count -gt 0) {
            throw 'Existing XinYue installation or retained data found. Use the reusable public-reader-session workflow; these legacy acceptance APKs require an empty disposable device.'
        }
    }
}
