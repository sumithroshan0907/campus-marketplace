# Campus Marketplace - Java Backend Compiler
$candidates = @(
    "$env:USERPROFILE\.antigravity-ide\extensions\redhat.java-1.56.0-win32-x64\jre\21.0.12.1-win32-x86_64\bin\javac.exe"
)
$javaCandidate = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $javaCandidate) {
    $javaCandidate = Get-ChildItem -Path "$env:USERPROFILE\.p2\pool" -Filter "javac.exe" -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName
}
if ($javaCandidate -and (Test-Path $javaCandidate)) {
    $javacExe = $javaCandidate
} else {
    $cmd = Get-Command javac -ErrorAction SilentlyContinue
    if ($cmd) { $javacExe = $cmd.Source } else { $javacExe = "javac" }
}

$m2Repo = "$env:USERPROFILE\.m2\repository"
$allJars = Get-ChildItem -Path $m2Repo -Filter "*.jar" -Recurse | Where-Object { $_.Name -notmatch "-sources\.jar$" -and $_.Name -notmatch "-javadoc\.jar$" } | Select-Object -ExpandProperty FullName
$compatibleJars = $allJars | Where-Object { $_ -notmatch "slf4j-api-1\." -and $_ -notmatch "log4j-to-slf4j-2\.1" }
$jars = $compatibleJars -join ";"
$sources = Get-ChildItem -Path "src\main\java" -Filter "*.java" -Recurse | Select-Object -ExpandProperty FullName

$lombokJar = Get-ChildItem -Path $m2Repo -Filter "lombok-*.jar" -Recurse -ErrorAction SilentlyContinue | Where-Object { $_.Name -notmatch "sources" } | Select-Object -First 1 -ExpandProperty FullName
if (-not $lombokJar) { $lombokJar = "lombok.jar" }

if (-not (Test-Path "target\classes")) {
    New-Item -ItemType Directory -Path "target\classes" -Force | Out-Null
}

$argFile = "$PSScriptRoot\target\javac-args.txt"
$argLines = @(
    "-parameters",
    "-processorpath",
    "$lombokJar",
    "-cp",
    "$lombokJar;..\lombok.jar;lombok.jar;target\classes;src\main\resources;$jars",
    "-d",
    "target\classes"
)
$sources | ForEach-Object { $argLines += $_ }
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
[System.IO.File]::WriteAllLines($argFile, $argLines, $utf8NoBom)

Write-Host "Compiling Java backend using $javacExe with argfile..."
& $javacExe "@$argFile"

if ($LASTEXITCODE -eq 0) {
    Copy-Item -Path "src\main\resources\*" -Destination "target\classes\" -Recurse -Force
    Write-Host "Compilation successful!"
} else {
    Write-Host "Compilation failed with code $LASTEXITCODE"
    exit $LASTEXITCODE
}
