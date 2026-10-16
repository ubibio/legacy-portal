@echo off
setlocal

rem legacy-portal launcher (port 8080)
rem DB: uses MariaDB if running, otherwise falls back to H2 in-memory (DataSourceConfig)
rem Keep this file ASCII-only: cmd parses .bat files with the system code page (CP949).

cd /d "%~dp0"

where java > nul 2>&1
if errorlevel 1 (
    echo [ERROR] java not found. Install Java 21 and add it to PATH.
    pause
    exit /b 1
)

echo ============================================
echo  legacy-portal
echo  App        : http://localhost:8080
echo  H2 console : http://localhost:8080/h2-console
echo               JDBC URL jdbc:h2:mem:portal / user sa
echo  Stop       : Ctrl+C
echo ============================================

call mvnw.cmd spring-boot:run
set EXIT_CODE=%ERRORLEVEL%

if not "%EXIT_CODE%"=="0" (
    echo.
    echo [ERROR] Failed to start. Exit code: %EXIT_CODE%
    pause
)

endlocal & exit /b %EXIT_CODE%
