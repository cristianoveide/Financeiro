@echo off
chcp 65001 >nul
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Build-Apk.ps1"
if errorlevel 1 (
  echo.
  echo A compilacao falhou. Leia a mensagem acima e pressione uma tecla para fechar.
  pause >nul
)
