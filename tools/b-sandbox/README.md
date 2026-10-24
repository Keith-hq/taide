# B 模块辅助沙箱

此构建用于审查原生 IntelliJ 界面，编译仓库当前的共享源码、问答服务与可用自动化动作。全组的集成测试、正式打包和兼容性检查使用仓库根目录的 Gradle 构建。

## 环境与启动

需要 **JDK 21**、IntelliJ IDEA Community **2024.2.4 / Platform 242**（含 Java 插件）。首次构建需要网络下载 Gradle、SDK 和依赖。请从仓库根目录运行：

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-21'
.\tools\b-sandbox\run.ps1 buildPlugin
.\tools\b-sandbox\run.ps1 runIde
```

辅助包版本为 `0.1.0-b-demo`，输出在 `tools/b-sandbox/build/distributions/`。`b-demo` 是包版本后缀；是否使用固定示例由项目设置中的 **Use demo service** 决定，默认开启。

如需复用本地 IDEA SDK，直接执行辅助 Wrapper 并传属性：

```powershell
.\tools\b-sandbox\gradlew.bat -p tools/b-sandbox compileJava -PlocalPlatformPath='C:\path\to\idea-2024.2.4'
```

根构建推荐入口：

```powershell
.\gradlew.bat build buildPlugin verifyPluginStructure
.\gradlew.bat runIde
```

## 服务来源与能力边界

- Source set 包含 `src/main` 与 `integration-support/src/main/java`。同路径的正式类型存在时，以 `src/main` 为准，避免重复契约。
- 使用正式 `META-INF/plugin.xml`。聊天接口注册到项目级 `TaideServiceImpl`；`KnowledgeBaseController` 注册到 `ProjectKnowledgeBaseController`，代理同一个聊天服务实例，避免重建后仍使用旧索引。
- `Settings → Tools → TAIDE` 提供本地模型准备、连接、课件目录、演示开关和可取消的重建操作。设置、凭据或模型目录更新使服务缓存失效；API Key 不写入 XML，在 PasswordSafe 中按项目、Provider 和端点隔离保存。
- 应用级 `ModelSetupService` 管理固定版本模型，所有项目共享同一 IDE 配置目录的文件。下载与校验在后台进行，关闭设置页不取消下载；可通过 IDE 后台任务进度或设置页取消。
- 演示模式只提供标为示例的固定聊天回答。每条消息保留发送时的来源与课件目录，之后切换模式不会让演示引用变成真实引用，换目录也不会改变旧引用的打开位置。示例卡片不显示虚构相似度，也不打开真实课件。
- C 的三个动作来自 `META-INF/taide-automation.xml`。它们的真实模型、检索及引用导航仍需通过 `AutomationServices.bind(...)` 接入，详见 [自动化服务约定](../../docs/AUTOMATION.md#服务接入)。开启聊天演示不等于已配置 F1 动作。
- 真实引用只能打开本地文件并提示页码；当前没有 PDF/PPTX 指定页深链，文件预览能力取决于 IDE。

## 切换真实问答

1. 打开 TAIDE 工具窗，按首次使用引导点击 **下载并配置**。插件仅在用户点击后联网，后台下载约 **90 MB** 的官方固定版本 MiniLM 模型与词表，按文件大小与 SHA-256 校验后保存到 `<IDE config>/taide/model/`，供所有项目复用。
2. 离线或已有文件时，在 `Settings → Tools → TAIDE → 本地检索模型` 点击 **使用已有目录…**，选择包含 **model.onnx** 和 **vocab.txt** 的完整目录并等待校验。已有文件须与插件固定版本匹配；**使用默认目录** 可切回插件管理的目录。以上操作不需要设置环境变量或重启 IDE。
3. 在 TAIDE 设置中填写 Provider、API base URL、Model ID、API Key 和课件目录。把 PDF/PPTX 直接放在目录内，当前不递归子目录、不做 OCR。
4. 点 **Apply** 保存配置后，再点 **Rebuild Knowledge Base**。模型未就绪时重建禁用；发布前取消保留原索引，已完成的发布不会因取消设置对话框而回滚。
5. 准备完成后点击工具窗的 **切换真实问答**，或取消设置中的 **Use demo service** 并保存。下载模型不会自动关闭演示模式；发送新问题检查引用，旧演示历史仍清楚标为示例。

下载遵循 IDE 的 HTTP Proxy 配置。连接失败时，到 `Settings → Appearance & Behavior → System Settings → HTTP Proxy` 按本机网络环境配置，再重试或选择已有目录。模型源采用 Apache 2.0 许可，固定版本与离线准备说明见 [根 README](../../README.md#模型保存离线使用与下载失败)。已校验缓存可离线复用；不完整下载不会成为可用模型。

开发/CLI 继续兼容 `taide.model.dir` JVM 属性与 `TAIDE_MODEL_DIR` 环境变量。IDE 设置中显式选择的目录优先；未显式选择时依次读取 JVM 属性、环境变量和默认目录。引导中的 API 状态只表示所需配置已填写，不验证网络连通、Key 有效性或余额。

索引保存在 `<IDE config>/taide/index/<project-hash>.json`。切换 API 端点后要为新端点填写 Key；从旧版存储升级后需重新输入一次。留空保留当前端点已有 Key，勾选移除选项才会删除。

## 不依赖 IDE SDK 的检查与预览

```powershell
.\tools\verify-b.ps1 -JavaHome 'C:\path\to\jdk-21'
```

该脚本直接使用 JDK 编译并检查选区行号、聊天线程与取消、消息来源、引用路径和设置验证，不下载 IntelliJ SDK。它还用实际 `ChatPanel` 生成 14 张 Swing PNG，覆盖 390、768、1440 像素宽度、1366×768 项目检查尺寸和 420 像素停靠面板。

输出位于 `build/b-verification/`，截图在其 `screenshots/`。这些检查不覆盖 Java PSI、PasswordSafe 或 IDE 的实际布局；平台测试使用根构建：

```powershell
.\gradlew.bat test --tests '*CodeContextExtractorPlatformTest'
```

本次模型准备与引导验收（2026-10-24）：**97 项纯 JDK 行为检查通过**，生成 14 张原生聊天截图及 2 张设置页截图；目标 IDE 2024.2.4 的 Gradle 套件共 **170 项测试：168 通过、2 跳过**，跳过项均为 Windows 本机缺少符号链接创建权限的夹具。`buildPlugin`、`verifyPluginStructure` 与 `verifyPlugin` 均通过，兼容检查保留 6 处线程断言的实验性 API 提示。另用真实约 90 MB 模型验证了完整安装、SHA-256 校验、缓存复用及显式目录 ONNX 推理。安装器网络回归使用本地 HTTP/mock；本次没有调用真实问答 API，也未把该套件视为 IDE 内真实联网下载和完整课程问答的人工验收。PDF/PPTX 指定页深链仍未实现。 正式入口为根构建；全组状态见 [仓库 README](../../README.md)及 [TEAM §4.6](../../docs/TEAM.md#46-b-侧集成约定2026-10-24)。

根构建的 `verifyPlugin` 已完成，IC-242.23726.103 结果为 Compatible；6 处实验 API 使用均为 `Application.assertIsNonDispatchThread`。网络获取 documented incompatible changes 失败，只影响已知问题过滤的启用。`examples/junit-demo` 已可构建，其 `test NO-SOURCE` 不代表执行了示例测试。

交付工具另通过 11 项 Python 检查（Python 3.13.5、锁定的 pypdf 6.21.2 和 reportlab 4.5.1）；提交 ZIP 为所有 `gradlew` Wrapper 显式写入 `0755` 权限，支持从 Windows 打包后在 Linux 直接运行。
