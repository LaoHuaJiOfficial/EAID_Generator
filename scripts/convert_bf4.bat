@echo off
setlocal
python "%~dp0csvToBundle.py" %*
if errorlevel 1 exit /b 1
echo BF4Bundle.txt generated successfully.
pause
