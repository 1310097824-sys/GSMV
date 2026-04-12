@echo off
cd /d %~dp0..
call mvnw.cmd -DskipTests package
cd frontend
call npm.cmd run build
