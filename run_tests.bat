@echo off
setlocal
cd /d "%~dp0"

if not exist out-test mkdir out-test

dir /s /b src\main\java\*.java > test_sources.txt
dir /s /b src\test\java\*.java >> test_sources.txt

javac --release 21 -encoding UTF-8 -d out-test @test_sources.txt
if errorlevel 1 (
    del test_sources.txt
    echo Test-Kompilierung fehlgeschlagen!
    pause
    exit /b 1
)
del test_sources.txt

java -cp out-test de.chronos.TestSuite
set EXIT_CODE=%errorlevel%
if %EXIT_CODE% equ 0 (
    java -cp out-test de.chronos.Main --smoke-test
)
pause
exit /b %EXIT_CODE%
