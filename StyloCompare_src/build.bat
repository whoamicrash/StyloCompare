@echo off
javac -encoding UTF-8 --release 8 -d classes src\com\stylo\Main.java src\com\stylo\CliMain.java src\com\stylo\SelfTest.java src\com\stylo\core\*.java src\com\stylo\gui\*.java
if errorlevel 1 goto :err
jar --create --file StyloCompare.jar --main-class com.stylo.Main -C classes .
if errorlevel 1 goto :err
echo OK: StyloCompare.jar
exit /b 0
:err
echo BUILD FAILED
exit /b 1
