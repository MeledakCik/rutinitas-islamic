@echo off
rem Build rilis Rutinitas: unit test, APK dan AAB bertanda tangan (keystore.properties). Log: build-release.log
cd /d "%~dp0"
set "LOG=%~dp0build-release.log"
echo [%date% %time%] mulai build > "%LOG%"
if not defined JAVA_HOME (
  for %%D in ("C:\Program Files\Android\Android Studio\jbr" "C:\Program Files\Android\Android Studio1\jbr" "C:\Program Files\Eclipse Adoptium\jdk-17" "C:\Program Files\Java\jdk-17" "C:\Program Files\Java\jdk-21") do (
    if exist "%%~D\bin\java.exe" set "JAVA_HOME=%%~D"
  )
)
echo JAVA_HOME=%JAVA_HOME% >> "%LOG%"
where java >> "%LOG%" 2>&1
call gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest :app:assembleRelease :app:bundleRelease >> "%LOG%" 2>&1
set "RESULT=%ERRORLEVEL%"
echo EXIT=%RESULT% >> "%LOG%"
if "%RESULT%"=="0" (
  copy /y "app\build\outputs\apk\release\app-release.apk" "dist\Rutinitas-Android-v1.1.4.apk" >> "%LOG%"
  copy /y "app\build\outputs\bundle\release\app-release.aab" "dist\Rutinitas-Android-v1.1.4.aab" >> "%LOG%"
)
echo SELESAI >> "%LOG%"
