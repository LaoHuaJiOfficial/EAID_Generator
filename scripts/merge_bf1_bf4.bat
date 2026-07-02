@echo off
setlocal
python "%~dp0mergeBundles.py" %*
if errorlevel 1 exit /b 1
echo BF1^&4Bundle.txt generated successfully.
pause
