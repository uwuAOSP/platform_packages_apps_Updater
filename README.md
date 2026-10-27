<!-- SPDX-FileCopyrightText: The LineageOS Project
     SPDX-FileCopyrightText: 2026 The uwuAOSP Project
     SPDX-License-Identifier: Apache-2.0 -->
# uwuAOSP Updater

uwuAOSP 的原生系统更新器，基于 LineageOS `lineage-24.0` 的 `6d50e0bf1b16fe7bca818375a3e734f4959e2e57`。保留上游历史、下载服务、Room 数据库、A/B 安装和 Recovery 安装流程。界面使用 Compose Material 3 Expressive、Spa 和 uwuSDK，不嵌入网页。

## 产品集成

源码位于 `packages/apps/Updater`，产品包添加 `Updater`。应用包名为 `org.uwuaosp.updater`，使用平台签名，安装到特权 `system_ext` 分区。标准 `android.settings.SYSTEM_UPDATE_SETTINGS` Intent 打开更新页面。

模块携带通知默认权限、特权权限白名单和 init 配置。init 创建 `/data/uwuaosp_updates`，权限为 `system:cache 0770`。产品 SELinux 必须将该目录标记为 `ota_package_file`，并将应用映射到 `updater_app` 域。中文定制资源由 `vendor/uwu-translations` 的 `uwuUpdaterTranslations` 提供。

| 系统属性 | 用途 |
| --- | --- |
| `ro.uwu.release` | 当前安装版本和更新日志地址 |
| `ro.uwu.device` | 设备代号和更新接口占位符 |
| `ro.uwu.maintainer` | 顶部维护者胶囊，只显示图标与名称；为空时隐藏 |
| `ro.uwu.releasetype` | 更新渠道过滤，不显示 Official 标记 |
| `ro.build.date.utc` | 当前构建时间及更新筛选 |
| `ro.build.ab_update` | A/B 安装能力 |

维护者通过产品的 `UWU_MAINTAINER` 配置，不在应用中写死。版本、补丁、日期和 OTA 大小均读取实际属性或元数据。

## 更新源

`updater_server_url` 默认留空，通过产品资源覆盖配置 HTTPS 地址。支持 `{device}`、`{type}`、`{incr}` 占位符。没有配置时显示未配置状态，不访问 LOS 服务器，不安排后台更新检查，本地导入仍可用。失败、离线和未检查状态不显示系统已是最新版本。

接口使用 LOS 的 JSON 数组格式。每项包含正整数 `datetime`、非空 `type` 和 `version`，以及恰好一个元素的 `files`。文件元素包含 `filename`、`sha256`、正整数 `size`、HTTPS `url` 和正整数 `os_sdk_level`；可选字段为 `os_patch_level` 和 `ota_property_files`。

接口版本直接用于更新卡片，不从当前版本推算。`type` 与 `ro.uwu.releasetype` 忽略大小写比较；同一或更早构建以及更低 SDK 被过滤。接口响应限制为 1 MiB，禁止接口重定向。字段或 payload 范围无效时整次检查失败，不清理已有下载。

流式更新使用 ZIP 元数据中的 `ota-property-files`，必须包含 `payload_metadata.bin`、`payload.bin` 和 `payload_properties.txt` 的真实偏移、长度。范围不得重复或越界；payload 属性限制为 64 KiB，payload 元数据限制为 16 MiB。发布端必须从实际 OTA 产物提取这些值，不使用示例偏移。

完整包和本地包保留 `RecoverySystem.verifyPackage` 校验，并检查设备、增量包源构建、OTA 类型、时间戳、SDK 和安全补丁。拒绝声明擦除或降级的包。高 SDK 更新保留手动升级限制。流式安装仍由系统 `update_engine` 校验和执行，不宣称其等同于完整 ZIP 的预先校验。

## 界面与日志

原生界面按 SystemUpdatesDemo 的最终设计移植。标题栏、信息行和偏好分组复用 uwuSDK；检查更新使用 Google `LoadingIndicator`，进度使用 `LinearWavyProgressIndicator`。波浪描边 6dp、确定进度波长 28dp、波速 40dp/s；只对真实进度进行平滑插值。

维护者显示在插画与检查更新控件之间，不再占用下方信息卡。检查按钮使用原状态文字的占位，不在底部重复显示。手动检查没有更新时通过 Toast 提示；有更新时变为不可点击的“发现新版本”胶囊。前台短提示使用 Toast，不使用 Snackbar，后台通知及安装确认弹窗保留。

品牌插画使用项目提供的 SVG 转换资源。三个装饰形状保留固定轮廓和 logo 颜色；后台停止装饰动画，并遵循系统动画设置。宽度达到 600dp 时使用双栏布局。

更新日志按当前安装的 `ro.uwu.release` 请求 `https://uwuaosp.uwuniverse.org/blog/uwu-{版本}/`。jsoup 提取并核对正文版本，转换为原生文本，不加载远程脚本、样式或嵌入媒体。关闭面板取消请求；成功结果短期缓存。未获取到对应版本时，中文显示“这个版本好像没有更新日志”。

组件参考：[Google 加载指示器](https://m3.material.io/components/loading-indicator/overview)、[Google 进度条](https://m3.material.io/components/progress-indicators/overview)、[Google 芯片](https://m3.material.io/components/chips/overview)。维护者胶囊是非交互信息，使用 Material `Surface` 承载图标和文字，不添加无效点击动作。

## 构建与设备验证

在已初始化并选择产品的 uwuAOSP 环境中执行：

```sh
uni -j$(nproc) Updater uwuUpdaterTranslations selinux_policy
```

Android Studio 调试需要相同源码版本的系统接口和 uwuSDK 产物。`pull-system-libs.sh` 只复制既有产物，不触发构建。系统签名、特权权限、init 和 SELinux 仍需产品集成，普通侧载不能替代该步骤。

`push-update.sh OTA.zip [SERIAL]` 将包放入设备的 Download 目录并打开更新器，由用户选择本地导入。脚本不直接写数据库，也不跳过签名验证。

设备验收包括更新源未配置、断网、检查失败、无更新、下载暂停恢复、签名失败、错误设备包、本地导入、安装、待重启及服务重连。界面另检查深浅主题、平板布局、字体放大、TalkBack 和关闭动画。静态检查不代表已通过构建或真实 OTA 安装验证。
