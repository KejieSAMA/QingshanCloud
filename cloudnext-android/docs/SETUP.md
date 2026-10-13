# Setup

## 一次性准备

1. **安装 Android Studio Koala+（2024.1.1+）**，自带 AGP 8.5 兼容工具链。
2. **安装 JDK 17**（Android Studio 内置即可）。
3. **复制 `local.properties.template` → `local.properties`**，填入 `sdk.dir` 和后端地址。
4. **使用项目自带 Gradle Wrapper**：`gradlew`、`gradlew.bat` 和 `gradle/wrapper/gradle-wrapper.jar` 均保留并应提交到新仓库，当前版本由 `gradle-wrapper.properties` 指定为 8.9。无需从 O2 或其他项目复制 Wrapper。

5. **Android Studio 打开根目录** → Sync → 第一次会下载 ~700MB 依赖。

## 启动后端

```bash
cd ../cloudnext-server
java -jar target/cloudnext-server-0.1.0-SNAPSHOT.jar --server.port=8088
```

使用已有 JAR；如需编译后端，先由开发者明确发起 Maven 构建。本项目没有 Maven Wrapper。启动前按 `../cloudnext-server/CONFIGURATION.md` 核对真实配置、Form 集成及数据库/存储路径。模拟器通过 `10.0.2.2` 访问宿主机的 `localhost:8088`。

## 跑起来

- 选择 `app` configuration
- 选择目标设备（API 24+ 模拟器或真机）
- ▶ Run

冷启动 → Login（使用 Form 账号；客户端认证能力以当前实现为准） → Home。以下 M1 清单是早期客户端设计系统的开发记录，不表示当前生产功能已全部验收。

## M1 验收

启动后在 Home 页能看到下列内容，即视为 M1 通过：

- Typography 5 个字号（17 / 15 / 14 / 14 / 12 sp）
- 4 组 ColorSwatch（Primary、Background、Text、Error/Success/Warning）
- 7 个文件类型图标（folder/word/excel/ppt/pdf/video/image），颜色与 `MobileFileCard.vue` 一致
- Spacing 5 阶 4/8/12/16/20 dp
- Touch targets / 高度 6 项
- Shapes 4 个（small/medium/large/pill）

把同一台手机的 `cloudnext-web` 移动端打开，并排对比，应肉眼一致。

## 常见问题

- **401 一直跳登录**：确认后端 `cloudnext-server` 已启动，且 `local.properties` 里 `cloudnext.api.base.url.debug` 正确。
- **明文 HTTP 报错**：仅 `10.0.2.2` 和 `localhost` 在 debug 下允许明文，自定义内网地址需要修改 `app/src/main/res/xml/network_security_config.xml`。
- **EncryptedSharedPreferences 报错**：清除 App 数据后重新登录（密钥与 App 数据绑定，卸载或清数据时丢失是预期行为）。
