param(
    [ValidateSet('before','after')][string]$Stage = 'before',
    [int]$AndroidUser = 13
)

$script:UiAdb = Join-Path $env:USERPROFILE '.cache/newmybook-toolchain/android-sdk/platform-tools/adb.exe'
$script:UiRoot = Join-Path $PSScriptRoot "../current/1.0.2/$Stage"
$script:UiUser = $AndroidUser

function Ui-Adb { & $script:UiAdb -s emulator-5554 @args }
function Ui-Capture([string]$Name) {
    if ($Name -notmatch '^[a-z0-9-]+$') { throw 'Use a safe screenshot name.' }
    if ((Ui-Adb shell am get-current-user).Trim() -ne "$script:UiUser") {
        throw 'Capture is limited to the isolated public-fixture test user.'
    }
    Ui-Adb shell input keyevent 224
    Ui-Adb shell wm dismiss-keyguard
    Start-Sleep -Milliseconds 900
    $remote = "/data/local/tmp/ui102-$Name"
    for ($attempt = 0; $attempt -lt 3; $attempt++) {
        $dump = Ui-Adb shell uiautomator dump "$remote.xml" 2>&1
        if ("$dump" -match 'dumped to') { break }
        Start-Sleep -Milliseconds 600
    }
    if ("$dump" -notmatch 'dumped to') { throw "Fresh UI dump failed: $dump" }
    Ui-Adb pull "$remote.xml" (Join-Path $script:UiRoot "$Name.xml") *> $null
    Ui-Adb shell screencap -p "$remote.png"
    Ui-Adb pull "$remote.png" (Join-Path $script:UiRoot "$Name.png") *> $null
    return [xml](Get-Content -LiteralPath (Join-Path $script:UiRoot "$Name.xml"))
}
function Ui-Read([string]$Name) { [xml](Get-Content -LiteralPath (Join-Path $script:UiRoot "$Name.xml")) }
function Ui-List($Xml) {
    $Xml.SelectNodes('//node') | Where-Object { $_.'content-desc' -or ($_.text -and $_.text.Length -lt 95) } |
        Select-Object text,content-desc,bounds
}
function Ui-Tap($Xml,[string]$Label) {
    $node = @($Xml.SelectNodes('//node') | Where-Object { $_.text -eq $Label -or $_.'content-desc' -eq $Label }) | Select-Object -Last 1
    if (-not $node) { throw "Action not found: $Label" }
    $bounds = [regex]::Matches($node.bounds,'\d+') | ForEach-Object { [int]$_.Value }
    Ui-Adb shell input tap (($bounds[0]+$bounds[2])/2) (($bounds[1]+$bounds[3])/2)
    Start-Sleep -Milliseconds 450
}
function Ui-Back { Ui-Adb shell input keyevent 4; Start-Sleep -Milliseconds 450 }
function Ui-Scroll { Ui-Adb shell input swipe 850 1890 850 650 380; Start-Sleep -Milliseconds 450 }
