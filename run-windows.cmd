@echo off
setlocal
cd /d "%~dp0"
if "%RITECH_DB_PASSWORD%"=="" (
  set /p RITECH_DB_PASSWORD=Escribe la contrasena de MySQL para root y presiona Enter:
)
call mvnw.cmd spring-boot:run
endlocal
