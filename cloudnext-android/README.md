# CloudNext Android

CloudNext 网盘服务的原生 Android 客户端，UI/交互对齐 cloudnext-web 在手机视口下的体验。

## 技术栈

- Kotlin 2.0 + Jetpack Compose（Compose Compiler Plugin）
- Hilt 2.52 + Coroutines + Flow
- Retrofit + OkHttp + Kotlinx Serialization
- WorkManager + Foreground Service（后台上传/下载）
- Room + DataStore + EncryptedSharedPreferences
- Media3 ExoPlayer + PdfRenderer + Coil + Telephoto Zoomable

## 模块结构

```
app/                      壳：Application、MainActivity、根 NavHost
core/
  designsystem/           CnTheme + CnTokens（映射 cloudnext-web/src/mobile/styles/mobile.css）
  ui/                     复刻 m-* 组件：CnTopBar / CnBottomTabs / CnFileCard / CnFAB ...
  network/                Retrofit、OkHttp、CloudNextApi、拦截器、SessionExpiredBus
  database/               Room：TransferTask / DriveCache / RecentSearch
  datastore/              TokenStore（EncryptedSharedPreferences）+ Preferences
  common/                 FileSize / MimeType / DateFormatter
data/                     Repositories：auth / drive / share / link / workspace / trash / search / transfer
feature/                  login / home / myfiles / shared / workspaces / links / trash / search / preview / transfer / settings
build-logic/              Convention plugins（统一 Android/Compose/Hilt 配置）
```

## 构建

```
./gradlew :app:assembleDebug
```

## 配置

`local.properties`：

```
sdk.dir=/path/to/Android/Sdk
cloudnext.api.base.url.debug=http://10.0.2.2:8088/
cloudnext.api.base.url.release=https://your-prod-host/
```

## 对照标准

实现时严格对照 cloudnext-web 移动端：

- 视觉令牌：`cloudnext-web/src/mobile/styles/mobile.css`
- 移动壳层：`cloudnext-web/src/components/mobile/MobileShell.vue`
- 文件卡：`cloudnext-web/src/components/mobile/MobileFileCard.vue`（长按 480ms / slop 10px）
- API：`cloudnext-web/src/api/cloudnext.js`
- 鉴权：`cloudnext-web/src/session.js`（X-CloudNext-Token）
