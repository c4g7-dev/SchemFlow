param(
  [Parameter(Mandatory=$true)][string]$Version,
  [string]$Branch = "main"
)

$ErrorActionPreference = "Stop"

function Update-FileContent {
  param([string]$Path, [scriptblock]$Updater)
  $text = Get-Content -Raw -Path $Path -Encoding UTF8
  $newText = & $Updater $text
  if ($newText -ne $text) {
    Set-Content -Path $Path -Value $newText -Encoding UTF8 -NoNewline
  }
}

# 1) Ensure clean tree
Write-Host "Checking git status..." -f Cyan
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

# 2) Bump pom.xml <revision>
$pom = Join-Path $root 'pom.xml'
Write-Host "Updating pom.xml revision to $Version" -f Cyan
Update-FileContent $pom { param($t) ($t -replace '<revision>.+?</revision>', "<revision>$Version</revision>") }

# 3) Update README jar names
$readme = Join-Path $root 'README.md'
Write-Host "Updating README jar references" -f Cyan
Update-FileContent $readme { param($t)
  $t = $t -replace 'SchemFlow-\d+\.\d+(?:\.\d+)?-all\.jar', "SchemFlow-$Version-all.jar"
  $t
}

# 4) Update CHANGELOG top entry to this version with date
$changelog = Join-Path $root 'CHANGELOG.md'
$today = (Get-Date).ToString('yyyy-MM-dd')
Write-Host "Stamping CHANGELOG $Version ($today)" -f Cyan
Update-FileContent $changelog { param($t)
  $t -replace '##\s+Unreleased', "## $Version - $today"
}

# 5) Build
Write-Host "Building with Maven..." -f Cyan
mvn -q -DskipTests=false package

# 6) Commit, tag, push
Write-Host "Commit and tag..." -f Cyan
& git add $pom $readme $changelog
& git commit -m "chore(release): bump version to $Version"
& git push origin $Branch
& git tag "v$Version" -m "SchemFlow v$Version"
& git push origin "v$Version"

Write-Host "Release $Version completed." -f Green
