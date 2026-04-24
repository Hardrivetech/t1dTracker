@echo off
echo ---where---
where java 2>nul || echo no_java_on_path

echo ---java-version---
java -version 2>nul || echo java_not_runnable

echo ---scan---
for /d %%i in ("C:\Program Files\Java\*") do @if exist "%%i\bin\java.exe" echo candidate:%%i
for /d %%i in ("C:\Program Files\Eclipse Adoptium\*") do @if exist "%%i\bin\java.exe" echo candidate:%%i
for /d %%i in ("C:\Program Files\AdoptOpenJDK\*") do @if exist "%%i\bin\java.exe" echo candidate:%%i
for /d %%i in ("C:\Program Files\Amazon Corretto\*") do @if exist "%%i\bin\java.exe" echo candidate:%%i
for /d %%i in ("C:\Program Files\Zulu\*") do @if exist "%%i\bin\java.exe" echo candidate:%%i
for /d %%i in ("C:\Program Files\Microsoft\jdk\*") do @if exist "%%i\bin\java.exe" echo candidate:%%i
for /d %%i in ("C:\Program Files (x86)\Java\*") do @if exist "%%i\bin\java.exe" echo candidate:%%i

echo ---end---
