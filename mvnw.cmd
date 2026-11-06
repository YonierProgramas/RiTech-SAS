@echo off
setlocal
set "BASE_DIR=%~dp0"
set "MAVEN_VERSION=3.9.16"
set "MAVEN_HOME=%BASE_DIR%.mvn\apache-maven-%MAVEN_VERSION%"

if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
  echo [RiTech] Maven no esta instalado globalmente. Descargando Maven %MAVEN_VERSION% una sola vez...
  if not exist "%BASE_DIR%.mvn" mkdir "%BASE_DIR%.mvn"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $ProgressPreference='SilentlyContinue'; $url='https://dlcdn.apache.org/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.zip'; $zip=Join-Path $env:TEMP 'apache-maven-3.9.16-bin.zip'; Invoke-WebRequest -Uri $url -OutFile $zip; Expand-Archive -Path $zip -DestinationPath '%BASE_DIR%.mvn' -Force; Remove-Item $zip -Force"
  if errorlevel 1 (
    echo [RiTech] No se pudo descargar Maven. Revisa tu conexion a Internet.
    exit /b 1
  )
)

call "%MAVEN_HOME%\bin\mvn.cmd" %*
endlocal
