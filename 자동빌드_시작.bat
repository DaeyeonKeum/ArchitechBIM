@echo off
chcp 65001 >nul
cd /d "%~dp0"
title BIM 대쉬보드 - 배포용 단일파일 자동 갱신 (끄려면 이 창을 닫으세요)
where python >nul 2>nul
if %errorlevel%==0 (
  python watch_build.py
) else (
  py watch_build.py
)
echo.
echo 감시가 종료되었습니다. 창을 닫으려면 아무 키나 누르세요.
pause >nul
