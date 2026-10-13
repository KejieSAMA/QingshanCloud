param([string]$Root='D:\QingshanCloud')
$ErrorActionPreference='Stop'
if($Root -notmatch '^D:\\[A-Za-z0-9_\\-]+$'){throw 'MySQL root must be a D: path without spaces or shell characters'}
$env:TEMP="$Root\tmp\mysql";$env:TMP=$env:TEMP
$env:HOME="$Root\home";$env:USERPROFILE=$env:HOME
$env:APPDATA="$Root\home\AppData\Roaming";$env:LOCALAPPDATA="$Root\home\AppData\Local"
$env:PSModuleAnalysisCachePath="$Root\cache\PSModuleAnalysisCache"
try{ & "$Root\ops\mysql-supervise.ps1" -Root $Root *> "$Root\logs\mysql-supervisor.log" }
catch{[IO.File]::AppendAllText("$Root\logs\mysql-launcher.log",$_.ToString()+[Environment]::NewLine);exit 1}
