
@echo off
cd /d "D:\Springboot-Project\banking-system-web"

echo Starting Banking System transaction report...
java -jar target\banking-system-web-1.0.0-batch.jar

if errorlevel 1 (
    echo.
    echo Batch report failed. Check the error above.
    pause
    exit /b 1
)

echo.
echo Batch report completed successfully.
echo Report location: reports\daily-transactions.csv
pause
