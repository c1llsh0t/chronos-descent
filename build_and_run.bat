@echo off
setlocal
cd /d "%~dp0"

where javac >nul 2>nul
if errorlevel 1 (
    echo FEHLER: "javac" wurde nicht gefunden.
    echo Bitte ein JDK ab Version 21 installieren, z.B. von https://adoptium.net
    echo und sicherstellen, dass es im PATH steht.
    pause
    exit /b 1
)

if not exist out mkdir out
if not exist dist mkdir dist

echo [1/3] Sammle Java-Quelldateien...
dir /s /b src\main\java\*.java > sources.txt

echo [2/3] Kompiliere mit javac...
javac --release 21 -encoding UTF-8 -d out @sources.txt
if errorlevel 1 (
    del sources.txt
    echo Kompilierung fehlgeschlagen!
    pause
    exit /b 1
)
del sources.txt

echo [3/3] Erstelle ausfuehrbare JAR und starte CHRONOS...
jar --create --file dist\chronos-descent.jar --main-class de.chronos.Main -C out .
if errorlevel 1 (
    echo JAR konnte nicht erstellt werden!
    pause
    exit /b 1
)

java -jar dist\chronos-descent.jar
if errorlevel 1 (
    echo.
    echo Das Spiel wurde mit einem Fehler beendet. Siehe Meldung oben.
    echo Installierte Java-Version:
    java -version
    pause
    exit /b 1
)
endlocal
