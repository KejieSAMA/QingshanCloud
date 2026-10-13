param([string]$Root='D:\QingshanCloud')
$ErrorActionPreference='Stop'
if($Root -notmatch '^D:\\[A-Za-z0-9_\\-]+$'){throw 'MySQL root must be a D: path without spaces or shell characters'}
$settings=Get-Content -LiteralPath "$Root\config\mysql-instance.json" -Raw | ConvertFrom-Json
$listeners=@(Get-NetTCPConnection -LocalPort $settings.port -State Listen -ErrorAction SilentlyContinue)
if($listeners.Count){
 $process=Get-WmiObject Win32_Process -Filter ('ProcessId='+$listeners[0].OwningProcess)
 if($process.ExecutablePath -ieq "$Root\runtime\mysql8\bin\mysqld.exe" -and $process.CommandLine.Contains("$Root\config\mysql.ini")){Write-Output 'Dedicated cloud MySQL is already running';exit 0}
 throw 'Dedicated MySQL port belongs to another process'
}
if(Test-Path -LiteralPath "$Root\ops\mysql-stop.request"){Remove-Item -LiteralPath "$Root\ops\mysql-stop.request"}
$env:TEMP="$Root\tmp\mysql";$env:TMP=$env:TEMP
$env:APPDATA="$Root\home\AppData\Roaming";$env:LOCALAPPDATA="$Root\home\AppData\Local"
$env:PSModuleAnalysisCachePath="$Root\cache\PSModuleAnalysisCache"
$startup=([wmiclass]'Win32_ProcessStartup').CreateInstance();$startup.ShowWindow=0
$command=$env:SystemRoot+'\System32\WindowsPowerShell\v1.0\powershell.exe -NoLogo -NoProfile -NonInteractive -Command "[IO.File]::WriteAllText('''+$Root+'\logs\mysql-launcher.log'',''starting''); try { & '''+$Root+'\ops\mysql-bootstrap.ps1'' } catch { [IO.File]::AppendAllText('''+$Root+'\logs\mysql-launcher.log'', $_.ToString()) }"'
$created=([wmiclass]'Win32_Process').Create($command,$Root,$startup)
if($created.ReturnValue -ne 0){throw 'Could not launch dedicated MySQL supervisor'}
for($attempt=0;$attempt -lt 40;$attempt++){
 if(@(Get-NetTCPConnection -LocalPort $settings.port -State Listen -ErrorAction SilentlyContinue).Count){Write-Output ('Dedicated cloud MySQL listening on '+$settings.port);exit 0}
 Start-Sleep -Seconds 1
}
throw 'Dedicated MySQL did not become ready; inspect only D: cloud logs'
