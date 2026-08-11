# wtybill

`wtybill` 是只面向斗鱼房间 `57321` 的原生 Android 看播客户端，最低支持 API 33。

## 构建与验证

在 Android SDK、JDK 17 和 Gradle 依赖可用的环境中运行：

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
./gradlew :app:bundleRelease
node scripts/verify-crypto-js.mjs
```

当前仓库已提交 Gradle Wrapper、Version Catalog 和依赖 SHA-256 verification metadata。若受限环境无法下载 Wrapper 分发包，可使用等版本 Gradle 发行版执行同名任务。

Release 默认生成 unsigned APK/AAB；本地可通过被忽略的 `keystore.properties`，或由 CI 注入四个 `ANDROID_*` 签名变量启用正式签名，详见 [docs/RELEASE_SIGNING.md](docs/RELEASE_SIGNING.md)。缺少正式密钥时不会回退到 Debug 签名。

## 架构边界

- `data/douyu`：房间 API、动态脚本签名、取流参数及 CDN/清晰度候选。
- `player`：唯一的 Media3 ExoPlayer、MediaSession 和恢复协调器。
- `danmaku`：斗鱼二进制协议、WebSocket 生命周期、有限消息缓冲和轨道分配。
- `ui/room`：RoomViewModel、Compose 播放界面和弹幕覆盖层。
- `settings`：Preferences DataStore 中的用户偏好。

播放 URL、签名结果和临时接口响应不会写入长期资产；CryptoJS 4.2.0 及许可证来自官方发布包并固定入库。

## 当前限制

实体 Android 设备、真实开播流、真实弹幕字段和正式发布密钥需要在具备设备与密钥的环境中完成。当前环境已完成 JVM/Gradle 层测试及 Release 构建，但不宣称完成这些外部验收。
