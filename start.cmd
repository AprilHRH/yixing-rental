@echo off
chcp 65001 >nul
cd /d "%~dp0"
if "%~1"=="" if exist "易行租车启动器.exe" (
    start "" "%~dp0易行租车启动器.exe"
    exit /b
)
where python >nul 2>nul
if errorlevel 1 (
    echo 未找到 Python。请安装 Python 3 并勾选加入 PATH，然后重试。
    pause
    exit /b 1
)
python scripts\run.py --open %*
if errorlevel 1 pause
