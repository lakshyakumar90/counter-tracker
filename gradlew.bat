@echo off
set DIR=%~dp0
if "%DIR%" == "" set DIR=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIR%

if defined JAVA_HOME goto findJavaFromJavaHome
set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% EQU 0 goto execute

echo ERROR: JAVA_HOME is not set and no usable Java installation was found. 1>&2
exit /b 1

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%\bin\java.exe
if exist "%JAVA_EXE%" goto execute

echo ERROR: JAVA_HOME points to an invalid Java installation: %JAVA_HOME% 1>&2
exit /b 1

:execute
"%APP_HOME%gradle\wrapper\gradle-wrapper.jar" >NUL 2>&1
if %ERRORLEVEL% EQU 0 goto init

:init
"%JAVA_EXE%" -classpath "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
