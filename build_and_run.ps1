$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

function Fail($msg) {
    Write-Host $msg -ForegroundColor Red
    Read-Host "Enter druecken zum Beenden"
    exit 1
}

if (-not (Get-Command javac -ErrorAction SilentlyContinue)) {
    Fail "FEHLER: 'javac' nicht gefunden. Bitte ein JDK ab Version 21 installieren (https://adoptium.net)."
}

New-Item -ItemType Directory -Force -Path "out", "dist" | Out-Null

Write-Host "[1/3] Sammle Java-Quelldateien..." -ForegroundColor Cyan
$sources = Get-ChildItem -Path "src/main/java" -Filter "*.java" -Recurse | ForEach-Object { $_.FullName }

Write-Host "[2/3] Kompiliere mit javac..." -ForegroundColor Cyan
& javac --release 21 -encoding UTF-8 -d out $sources
if ($LASTEXITCODE -ne 0) { Fail "Kompilierung fehlgeschlagen!" }

Write-Host "[3/3] Erstelle JAR und starte Spiel..." -ForegroundColor Cyan
& jar --create --file "dist/chronos-descent.jar" --main-class "de.chronos.Main" -C out .
if ($LASTEXITCODE -ne 0) { Fail "JAR konnte nicht erstellt werden!" }

& java -jar "dist/chronos-descent.jar"
if ($LASTEXITCODE -ne 0) {
    & java -version
    Fail "Das Spiel wurde mit einem Fehler beendet. Siehe Meldung oben."
}
