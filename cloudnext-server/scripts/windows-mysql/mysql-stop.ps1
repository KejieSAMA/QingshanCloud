param([string]$Root='D:\QingshanCloud')
$ErrorActionPreference='Stop'
if($Root -notmatch '^D:\\[A-Za-z0-9_\\-]+$'){throw 'MySQL root must be a D: path without spaces or shell characters'}
$settings=Get-Content -LiteralPath "$Root\config\mysql-instance.json" -Raw | ConvertFrom-Json
$listeners=@(Get-NetTCPConnection -LocalPort $settings.port -State Listen -ErrorAction SilentlyContinue)
foreach($listener in $listeners){
 $process=Get-WmiObject Win32_Process -Filter ('ProcessId='+$listener.OwningProcess)
 if($process.ExecutablePath -ine "$Root\runtime\mysql8\bin\mysqld.exe" -or !$process.CommandLine.Contains("$Root\config\mysql.ini")){throw 'Refusing to stop a non-cloud MySQL process'}
}
New-Item -ItemType File -Path "$Root\ops\mysql-stop.request" -Force | Out-Null
for($attempt=0;$attempt -lt 40;$attempt++){
 if(@(Get-NetTCPConnection -LocalPort $settings.port -State Listen -ErrorAction SilentlyContinue).Count -eq 0){Write-Output 'Dedicated cloud MySQL stopped';exit 0}
 Start-Sleep -Seconds 1
}
throw 'Dedicated cloud MySQL shutdown pending; other processes were not stopped'
