@echo off
REM Turnright Database MCP Server Startup Script
REM Usage:
REM   start.bat            -> transport & port read from mcp-config.env
REM   start.bat 9001       -> keep transport from mcp-config.env, override port
REM   start.bat http       -> force HTTP (overrides env)
REM   start.bat stdio      -> force stdio (overrides env)
REM   start.bat http 9001  -> force HTTP + port 9001

set SCRIPT_DIR=%~dp0
set MODE=
set HAS_MODE=
set PORT=

REM Parse arguments
:parse_args
if "%~1"=="" goto :end_parse
if /i "%~1"=="http" ( set MODE=http && set HAS_MODE=1 && shift && goto :parse_args )
if /i "%~1"=="stdio" ( set MODE=stdio && set HAS_MODE=1 && shift && goto :parse_args )
echo %~1| findstr /r "^[0-9][0-9]*$" >nul
if %errorlevel%==0 ( set PORT=%~1 && shift && goto :parse_args )
shift
goto :parse_args
:end_parse

REM Jar: prefer target/ build artifact, else jar placed next to this script
set JAR_FILE=%SCRIPT_DIR%target\turnright-database-mcp-server-0.0.1-SNAPSHOT.jar
set "DEPLOY_JAR="
if not exist "%JAR_FILE%" (
    for %%f in ("%SCRIPT_DIR%turnright-database-mcp-server-*.jar") do set "DEPLOY_JAR=%%f"
)
if not exist "%JAR_FILE%" if exist "%DEPLOY_JAR%" set "JAR_FILE=%DEPLOY_JAR%"
if not exist "%JAR_FILE%" (
    echo JAR not found. Put the jar next to this script or under target\.
    exit /b 1
)
echo Using JAR: %JAR_FILE%

REM Force transport only when http/stdio given explicitly;
REM otherwise Java resolves it from mcp-config.env / MCP_TRANSPORT.
set "TRANSPORT_ARG="
if "%MODE%"=="http" set "TRANSPORT_ARG=-Dmcp.transport=http"
if "%MODE%"=="stdio" if "%HAS_MODE%"=="1" set "TRANSPORT_ARG=-Dmcp.transport=stdio"

set "PORT_ARG="
if not "%PORT%"=="" set "PORT_ARG=-p %PORT%"

if "%HAS_MODE%"=="1" echo Transport mode: %MODE%
echo Starting MCP Server...
java %TRANSPORT_ARG% -jar "%JAR_FILE%" %PORT_ARG%
