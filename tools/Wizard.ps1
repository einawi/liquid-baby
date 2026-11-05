# Liquid Baby - build and release wizard (Windows PowerShell, ASCII only)
$ErrorActionPreference = 'Continue'
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

$DefaultRepoName = 'liquid-baby'
$KeysDir = Join-Path ([Environment]::GetFolderPath('MyDocuments')) 'LiquidBaby-KEYS'
$ApkDir = Join-Path $Root 'APK'
$ErrFile = Join-Path $Root 'build-error.txt'
$Workflow = 'release.yml'

function Say($m) { Write-Host $m -ForegroundColor Cyan }
function Good($m) { Write-Host $m -ForegroundColor Green }
function Bad($m) { Write-Host $m -ForegroundColor Red }

function Refresh-Path {
    $m = [Environment]::GetEnvironmentVariable('Path', 'Machine')
    $u = [Environment]::GetEnvironmentVariable('Path', 'User')
    $env:Path = $m + ';' + $u
}

function Have($name) {
    return [bool](Get-Command $name -ErrorAction SilentlyContinue)
}

function Ensure-Tool($cmd, $wingetId, $label) {
    if (Have $cmd) { return $true }
    Say "Installing $label ..."
    if (-not (Have 'winget')) {
        Bad 'winget is missing. Install "App Installer" from the Microsoft Store, then run this again.'
        return $false
    }
    winget install -e --id $wingetId --accept-package-agreements --accept-source-agreements
    Refresh-Path
    if (Have $cmd) { return $true }
    Bad "$label was installed but is not visible yet. Close this window and double-click Rebuild-APK.bat again."
    return $false
}

function Find-Keytool {
    if (Have 'keytool') { return (Get-Command keytool).Source }
    $roots = @('C:\Program Files\Eclipse Adoptium', 'C:\Program Files\Java')
    foreach ($r in $roots) {
        if (Test-Path $r) {
            $f = Get-ChildItem -Path $r -Recurse -Filter keytool.exe -ErrorAction SilentlyContinue | Select-Object -First 1
            if ($f) { return $f.FullName }
        }
    }
    return $null
}

function Get-Version {
    $g = Get-Content (Join-Path $Root 'app\build.gradle.kts') -Raw
    $vn = [regex]::Match($g, 'val appVersionName = "([^"]+)"').Groups[1].Value
    $vc = [regex]::Match($g, 'val appVersionCode = (\d+)').Groups[1].Value
    return @{ Name = $vn; Code = $vc; Tag = "v$vn-build$vc" }
}

function Get-RepoFromGit {
    if (-not (Test-Path (Join-Path $Root '.git'))) { return $null }
    $u = (git remote get-url origin 2>$null)
    if (-not $u) { return $null }
    $m = [regex]::Match("$u", 'github\.com[:/](.+?)(\.git)?$')
    if ($m.Success) { return $m.Groups[1].Value }
    return $null
}

function Ensure-Login {
    gh auth status *> $null
    if ($LASTEXITCODE -ne 0) {
        Say 'Signing in to GitHub. A browser window will open. Follow the steps there.'
        gh auth login --web --git-protocol https --scopes workflow
        gh auth status *> $null
        if ($LASTEXITCODE -ne 0) { Bad 'GitHub sign-in did not finish.'; return $false }
    }
    $st = "" + (gh auth status 2>&1 | Out-String)
    if ($st -notmatch 'workflow') {
        Say 'GitHub needs one more permission (workflow) so it can run builds. A browser window will open.'
        gh auth refresh -h github.com -s workflow
    }
    gh auth setup-git
    return $true
}

function Ensure-Repo {
    $repo = Get-RepoFromGit
    if ($repo) { return $repo }
    $login = ("" + (gh api user --jq .login)).Trim()
    if (-not $login) { Bad 'Could not read your GitHub user name.'; return $null }
    $name = Read-Host "Repository name on GitHub (press Enter for $DefaultRepoName)"
    if ([string]::IsNullOrWhiteSpace($name)) { $name = $DefaultRepoName }
    $repo = "$login/$name"
    gh repo view $repo *> $null
    $exists = ($LASTEXITCODE -eq 0)
    if (-not (Test-Path (Join-Path $Root '.git'))) { git init -b main | Out-Null }
    if (-not $exists) {
        Say "Creating public repository $repo ..."
        gh repo create $repo --public
        if ($LASTEXITCODE -ne 0) { Bad 'Could not create the repository.'; return $null }
    }
    git remote remove origin 2>$null
    git remote add origin "https://github.com/$repo.git"
    if ($exists) {
        $heads = ("" + (git ls-remote --heads origin main)).Trim()
        if ($heads) {
            Say 'Repository already has history. Attaching this folder to it (no force push).'
            git fetch origin main
            if ($LASTEXITCODE -ne 0) { Bad 'Could not fetch the existing history.'; return $null }
            git update-ref refs/heads/main refs/remotes/origin/main
            git reset -q
        }
    }
    git config user.name $login
    git config user.email "$login@users.noreply.github.com"
    return $repo
}

function Ensure-Signing($repo) {
    $secrets = "" + (gh secret list --repo $repo 2>$null | Out-String)
    if (($secrets -match 'KEYSTORE_BASE64') -and ($secrets -match 'KEYSTORE_PASSWORD') -and ($secrets -match 'KEY_ALIAS') -and ($secrets -match 'KEY_PASSWORD')) {
        return $true
    }
    if (-not (Test-Path $KeysDir)) { New-Item -ItemType Directory -Path $KeysDir -Force | Out-Null }
    $jks = Join-Path $KeysDir 'liquidbaby.jks'
    $info = Join-Path $KeysDir 'keys-info.txt'
    $alias = 'liquidbaby'
    if (-not (Test-Path $jks)) {
        $kt = Find-Keytool
        if (-not $kt) { Bad 'keytool was not found. Install Java (Temurin 21) and run again.'; return $false }
        $pw = -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 24 | ForEach-Object { [char]$_ })
        Say 'Creating a new signing key for Liquid Baby ...'
        & $kt -genkeypair -keystore $jks -alias $alias -keyalg RSA -keysize 2048 -validity 10000 -storepass $pw -keypass $pw -dname 'CN=Liquid Baby, O=Liquid, C=SY'
        if ($LASTEXITCODE -ne 0) { Bad 'Could not create the signing key.'; return $false }
        Set-Content -Path $info -Value @("STORE_PASSWORD=$pw", "KEY_ALIAS=$alias") -Encoding ASCII
        Write-Host ''
        Write-Host '=====================================================' -ForegroundColor Yellow
        Write-Host ' IMPORTANT - BACK UP THIS FOLDER NOW:' -ForegroundColor Yellow
        Write-Host " $KeysDir" -ForegroundColor Yellow
        Write-Host ' If you lose the key, nobody can update the installed' -ForegroundColor Yellow
        Write-Host ' app any more. Copy the folder to a USB stick or cloud.' -ForegroundColor Yellow
        Write-Host '=====================================================' -ForegroundColor Yellow
        Write-Host ''
        Read-Host 'Press Enter after you have read this'
    }
    if (-not (Test-Path $info)) { Bad "keys-info.txt is missing in $KeysDir"; return $false }
    $pwLine = (Get-Content $info | Where-Object { $_ -like 'STORE_PASSWORD=*' } | Select-Object -First 1)
    $pw2 = $pwLine.Substring('STORE_PASSWORD='.Length)
    $b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($jks))
    gh secret set KEYSTORE_BASE64 --repo $repo --body $b64
    gh secret set KEYSTORE_PASSWORD --repo $repo --body $pw2
    gh secret set KEY_ALIAS --repo $repo --body $alias
    gh secret set KEY_PASSWORD --repo $repo --body $pw2
    if ($LASTEXITCODE -ne 0) { Bad 'Could not store the signing key on GitHub.'; return $false }
    return $true
}

function Write-BuildError($repo, $runId) {
    $log = "" + (gh run view $runId --repo $repo --log-failed 2>&1 | Out-String)
    $lines = $log -split "`r?`n"
    $keep = New-Object System.Collections.Generic.List[string]
    foreach ($l in $lines) {
        $t = ($l -replace '\x1b\[[0-9;]*m', '')
        $t = ($t -replace '^.*?\d{4}-\d\d-\d\dT[\d:.]+Z\s*', '').Trim()
        if ($t -match '^e: |error:|FAILURE|What went wrong|Execution failed|Unresolved reference|FAILED|Caused by|AAPT|Exception|already exists|secret is missing') {
            if (-not $keep.Contains($t)) { $keep.Add($t) }
        }
    }
    if ($keep.Count -eq 0) {
        $keep.AddRange([string[]]($lines | Select-Object -Last 60))
    }
    $out = $keep | Select-Object -First 120
    Set-Content -Path $ErrFile -Value $out -Encoding UTF8
    Bad "The build failed. A short error list was saved to: $ErrFile"
    Bad 'Open build-error.txt, copy everything, and paste it to Claude.'
}

function Follow-Run($repo, $runId, $tag) {
    Say "Watching build $runId ... (this takes a few minutes)"
    gh run watch $runId --repo $repo --exit-status
    $conclusion = ("" + (gh run view $runId --repo $repo --json conclusion --jq .conclusion)).Trim()
    if ($conclusion -ne 'success') {
        Write-BuildError $repo $runId
        return
    }
    if (Test-Path $ErrFile) { Remove-Item $ErrFile -Force }
    if (-not (Test-Path $ApkDir)) { New-Item -ItemType Directory -Path $ApkDir -Force | Out-Null }
    gh release download $tag --repo $repo --dir $ApkDir --clobber --pattern '*.apk' --pattern '*.sha256'
    if ($LASTEXITCODE -ne 0) { Bad "Build worked but the download of release $tag failed."; return }
    Good 'Build finished. Your APK is in the APK folder:'
    Good "  $ApkDir"
    Write-Host 'Copy the APK to your phone and install it. Later versions can be installed from inside the app.'
    explorer.exe $ApkDir
}

function Build-And-Publish {
    if (-not (Ensure-Tool 'git' 'Git.Git' 'Git')) { return }
    if (-not (Ensure-Tool 'gh' 'GitHub.cli' 'GitHub CLI')) { return }
    if (-not (Find-Keytool)) {
        if (-not (Ensure-Tool 'keytool' 'EclipseAdoptium.Temurin.21.JDK' 'Java (Temurin 21)')) {
            if (-not (Find-Keytool)) { return }
        }
    }
    if (-not (Ensure-Login)) { return }
    $repo = Ensure-Repo
    if (-not $repo) { return }
    if (-not (Ensure-Signing $repo)) { return }

    $v = Get-Version
    Say "Version $($v.Name) build $($v.Code)  ->  release $($v.Tag)"
    git add -A
    git diff --cached --quiet
    if ($LASTEXITCODE -ne 0) {
        git commit -m "Liquid Baby $($v.Name) build $($v.Code)" | Out-Null
    }
    git branch -M main
    $pushOut = "" + (git push -u origin main 2>&1 | Out-String)
    Write-Host $pushOut
    if ($LASTEXITCODE -ne 0) {
        if ($pushOut -match 'workflow') {
            Bad 'GitHub refused the build file: the sign-in is missing the workflow permission. Run this once, then try again:'
            Bad '  gh auth refresh -h github.com -s workflow'
        }
        elseif ($pushOut -match 'non-fast-forward|fetch first|rejected') {
            Bad 'Push was refused. GitHub has changes that are not in this folder. Do not force anything - tell Claude.'
        }
        else {
            Bad 'Push failed. Copy the text above and send it to Claude.'
        }
        return
    }
    $sha = ("" + (git rev-parse HEAD)).Trim()
    $runId = $null
    Say 'Waiting for GitHub to start the build ...'
    for ($i = 0; $i -lt 40; $i++) {
        $out = ("" + (gh run list --repo $repo --workflow $Workflow --commit $sha --limit 1 --json databaseId --jq '.[0].databaseId' 2>$null)).Trim()
        if ($out -match '^\d+$') { $runId = $out; break }
        Start-Sleep -Seconds 3
    }
    if (-not $runId) { Bad 'No build was started. Check the Actions tab on GitHub.'; return }
    Follow-Run $repo $runId $v.Tag
}

function Follow-Running {
    if (-not (Have 'gh')) { Bad 'GitHub CLI is not installed yet. Use option 1 first.'; return }
    $repo = Get-RepoFromGit
    if (-not $repo) { Bad 'This folder is not connected to GitHub yet. Use option 1 first.'; return }
    $out = ("" + (gh run list --repo $repo --workflow $Workflow --limit 1 --json databaseId --jq '.[0].databaseId' 2>$null)).Trim()
    if ($out -notmatch '^\d+$') { Bad 'No build found.'; return }
    $v = Get-Version
    Follow-Run $repo $out $v.Tag
}

while ($true) {
    Write-Host ''
    Write-Host '=== Liquid Baby build wizard ===' -ForegroundColor Cyan
    Write-Host ' 1 = Build and publish now'
    Write-Host ' 2 = Follow a build that is already running'
    Write-Host ' 3 = Show where my signing key is'
    Write-Host ' 0 = Exit'
    $c = Read-Host 'Choose'
    if ($c -eq '1') { Build-And-Publish }
    elseif ($c -eq '2') { Follow-Running }
    elseif ($c -eq '3') { Write-Host "Signing key folder: $KeysDir"; Write-Host 'Back it up. If it is lost, the installed app can never be updated.' }
    elseif ($c -eq '0') { break }
}
