param([string]$Root='D:\QingshanCloud')
$ErrorActionPreference='Stop'
if($Root -notmatch '^D:\\[A-Za-z0-9_\\-]+$'){throw 'MySQL root must be a D: path without spaces or shell characters'}
$settings=Get-Content -LiteralPath "$Root\config\mysql-instance.json" -Raw | ConvertFrom-Json
$env:TEMP="$Root\tmp\mysql";$env:TMP=$env:TEMP
$env:HOME="$Root\home";$env:USERPROFILE=$env:HOME
$env:APPDATA="$Root\home\AppData\Roaming";$env:LOCALAPPDATA="$Root\home\AppData\Local"
Set-Location -LiteralPath $Root
$lock=[IO.File]::Open("$Root\data\mysql-supervisor.lock",[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
$database=$null
try {
 while(!(Test-Path -LiteralPath "$Root\ops\mysql-stop.request")){
  if($null -eq $database -or $database.HasExited){
   if(@(Get-NetTCPConnection -LocalPort $settings.port -State Listen -ErrorAction SilentlyContinue).Count){throw 'Dedicated MySQL port already occupied'}
   $stamp=Get-Date -Format 'yyyyMMdd-HHmmss'
   $database=Start-Process -FilePath "$Root\runtime\mysql8\bin\mysqld.exe" -ArgumentList @("--defaults-file=$Root\config\mysql.ini") -WorkingDirectory $Root -WindowStyle Hidden -RedirectStandardOutput "$Root\logs\mysql-$stamp.out.log" -RedirectStandardError "$Root\logs\mysql-$stamp.err.log" -PassThru
  }
  [pscustomobject]@{SupervisorPid=$PID;MysqlPid=$database.Id;Port=$settings.port;UpdatedAt=(Get-Date).ToString('o')} | ConvertTo-Json | Set-Content -LiteralPath "$Root\config\mysql-processes.json" -Encoding UTF8
  Start-Sleep -Seconds 5
 }
} finally {
 # API/Form stop operations must never enter this path. mysql-stop.ps1 requests this explicitly.
 if($null -ne $database -and !$database.HasExited){
  $identity=Get-WmiObject Win32_Process -Filter ('ProcessId='+$database.Id)
  if($identity.ExecutablePath -ieq "$Root\runtime\mysql8\bin\mysqld.exe" -and $identity.CommandLine.Contains("$Root\config\mysql.ini")){
   $stop=Start-Process -FilePath "$Root\runtime\mysql8\bin\mysqladmin.exe" -ArgumentList @("--defaults-file=$Root\config\mysql-admin.cnf",'shutdown') -WorkingDirectory $Root -WindowStyle Hidden -RedirectStandardOutput "$Root\logs\mysql-stop.out.log" -RedirectStandardError "$Root\logs\mysql-stop.err.log" -PassThru -Wait
   if($stop.ExitCode -ne 0){throw 'Dedicated MySQL shutdown failed; inspect owned process, do not stop other MySQL instances'}
  }
 }
 $lock.Dispose()
}
