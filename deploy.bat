@echo off
setlocal

:: 项目基本信息
set APP_NAME=EAID-Generator
set MAIN_JAR=EAID-Generator.jar
set MAIN_CLASS=Main.Main

:: 目录设置
set JDK_PATH=C:\Program Files\Java\jdk-17
set INPUT_DIR=out\artifacts\EAID_Generator_jar
set RESOURCE_DIR=resources
set OUTPUT_DIR=out\package

:: 执行打包
"%JDK_PATH%\bin\jpackage.exe" ^
  --input "%INPUT_DIR%" ^
  --main-jar "%MAIN_JAR%" ^
  --main-class %MAIN_CLASS% ^
  --name "%APP_NAME%" ^
  --type exe ^
  --dest "%OUTPUT_DIR%" ^
  --resource-dir "%RESOURCE_DIR%" ^
  --java-options "-Xmx512m" ^
  --win-console

echo finished in %OUTPUT_DIR%
pause
