@echo off
setlocal
set "ROOT=%~dp0"
set "APP=%ROOT%go-client\dist\EduClient-Go-同课优先级版.exe"

if exist "%APP%" (
  start "EduClient Go" "%APP%"
  exit /b 0
)

where go >nul 2>nul
if errorlevel 1 (
  echo Go toolchain was not found. Please build go-client\dist\EduClient-Go.exe first.
  pause
  exit /b 1
)

pushd "%ROOT%go-client"
go run .\cmd\educlient
popd
