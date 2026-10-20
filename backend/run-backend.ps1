# Campus Marketplace - Spring Boot Runner
param (
    [string]$Profile = ""
)

$candidates = @(
    "$env:USERPROFILE\.antigravity-ide\extensions\redhat.java-1.56.0-win32-x64\jre\21.0.12.1-win32-x86_64\bin\java.exe"
)
$javaCandidate = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $javaCandidate) {
    $javaCandidate = Get-ChildItem -Path "$env:USERPROFILE\.p2\pool" -Filter "java.exe" -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName
}
if ($javaCandidate -and (Test-Path $javaCandidate)) {
    $javaExe = $javaCandidate
} else {
    $cmd = Get-Command java -ErrorAction SilentlyContinue
    if ($cmd) { $javaExe = $cmd.Source } else { $javaExe = "java" }
}

$m2Repo = "$env:USERPROFILE\.m2\repository"
$allJars = Get-ChildItem -Path $m2Repo -Filter "*.jar" -Recurse | Where-Object { $_.Name -notmatch "-sources\.jar$" -and $_.Name -notmatch "-javadoc\.jar$" } | Select-Object -ExpandProperty FullName
# Exclude legacy SLF4J 1.x jars that conflict with Spring Boot 3.2.5 (SLF4J 2.x)
$compatibleJars = $allJars | Where-Object { $_ -notmatch "slf4j-api-1\." -and $_ -notmatch "log4j-to-slf4j-2\.1" }
$jars = $compatibleJars -join ";"
$lombokJar = Get-ChildItem -Path $m2Repo -Filter "lombok-*.jar" -Recurse -ErrorAction SilentlyContinue | Where-Object { $_.Name -notmatch "sources" } | Select-Object -First 1 -ExpandProperty FullName
if (-not $lombokJar) { $lombokJar = "lombok.jar" }
$classpath = "target\classes;src\main\resources;$jars;$lombokJar;..\lombok.jar;lombok.jar"

Copy-Item -Path "src\main\resources\*" -Destination "target\classes\" -Recurse -Force -ErrorAction SilentlyContinue

$argFile = "$PSScriptRoot\target\java-args.txt"
$argLines = [System.Collections.Generic.List[string]]::new()
$argLines.Add("-cp")
$argLines.Add("target\classes;src\main\resources;$jars;$lombokJar")

if ($Profile -ne "") {
    $argLines.Add("-Dspring.profiles.active=$Profile")
}

$argLines.Add("com.campus.marketplace.CampusMarketplaceApplication")

$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
[System.IO.File]::WriteAllLines($argFile, $argLines, $utf8NoBom)

Write-Host "Starting Campus Marketplace Spring Boot Backend on http://localhost:8080..."
& $javaExe "@$argFile"
