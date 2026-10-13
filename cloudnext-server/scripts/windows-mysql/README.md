# 独立 Windows MySQL 管理

部署时将本目录的 `mysql-*.ps1` 复制到云盘 D 盘 `ops/`；只管理云盘自己的可执行文件、defaults-file、端口和 PID，不调用 Windows `MySQL` 服务，不关联 Form 启动器。

配置均在私有 `config/`：`mysql-instance.json` 保存端口，`mysql.ini` 保存独立 basedir/datadir/log/tmp 配置，`mysql-admin.cnf` 提供仅本机管理认证；不得提交凭据。端口只绑定环回，关闭 MySQL X 协议，不添加数据库防火墙规则。

`mysql-start.ps1` / `mysql-stop.ps1` 管理独立数据库；API 的 `stop.ps1` 不停止数据库。云盘 API 启动前先执行 `mysql-start.ps1`。数据库监督进程与 API 监督进程分开，API 或 Form 重启不会连带关闭云盘 MySQL。

不注册 Windows 服务或计划任务，以免写入 C 盘；主机重启后手工执行云盘 `ops/start.ps1`（其会先启动数据库）。监督进程异常日志、数据库 PID、临时目录均在 D 盘。初始化与账号创建是独立部署步骤，不在启动脚本中重复执行。
