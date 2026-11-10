@echo off
chcp 65001 >nul
title SiteMeasureAR one-click install
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\install.ps1"
if errorlevel 1 pause
