@echo off
setlocal

set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "GRADLE_USER_HOME=%~dp0..\.gradle"

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo Android Studio Java was not found at:
    echo %JAVA_HOME%
    echo Update JAVA_HOME in this file to your installed JDK folder.
    exit /b 1
)

call "%~dp0gradlew.bat" run
exit /b %ERRORLEVEL%
