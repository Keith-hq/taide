# TAIDE 自动化与发布

本文对应 Group B 的 Automation & Release（成员 C）职责。Group B 提交截止时间为 2026-11-03 23:00，演示时间为 2026-11-04 13:30–16:15。

## 功能

| 功能 | 动作 ID | 输入 | 结果 |
| --- | --- | --- | --- |
| 按需求生成 JUnit 测试 | `taide.generateTests` | Java 选区、完整源文件、自然语言场景 | 可编辑预览，确认后创建测试文件 |
| 生成提交信息 | `taide.generateCommitMessage` | 所选仓库的全部暂存区差异 | Conventional Commit 预览、复制、确认后本地提交 |
| 课件感知代码审查 | `taide.courseReview` | Java 选区、检索到的课程片段 | 严重度、问题说明、修正建议、可定位的来源引用 |

动作入口位于编辑器右键菜单和 `Tools → TAIDE`。测试生成与代码审查在 Java 选区可用时启用。提交信息生成使用当前文件所属仓库，未选文件时使用项目目录。

## 构建与安装

开发环境为 JDK 21、Gradle Wrapper 8.13、IntelliJ IDEA Community 2024.2.4。插件兼容范围为 IntelliJ Platform 242。

```bash
./gradlew build verifyPluginStructure
./gradlew verifyPlugin
./gradlew runIde
```

构建产物位于 `build/distributions/taide-0.1.0-SNAPSHOT.zip`。在 IDEA 的 `Settings → Plugins → Install Plugin from Disk` 中选择 ZIP，重启 IDE。

已有相同版本的本地 IDEA 安装时，可复用 SDK：

```bash
./gradlew build -PlocalPlatformPath=/path/to/idea-IC-242.23726.103
```

`src/standalone/resources/META-INF/plugin.xml` 提供独立沙箱入口。正式入口存在于 `src/main/resources/META-INF/plugin.xml` 时，构建自动使用正式入口。

独立沙箱提供动作入口和预览界面。真实生成使用下节约定的模型、检索与引用定位服务。

## 服务接入

共享接口沿用 `docs/TEAM.md` 的方法签名：

| 接口 | 用途 |
| --- | --- |
| `edu.cst3492.taide.llm.LlmClient` | 阻塞式模型生成 |
| `edu.cst3492.taide.rag.Retriever` | 召回相关课程片段 |
| `edu.cst3492.taide.rag.RetrievedChunk` | 文档 ID、文档名、1-based 页码、文本及分数 |
| `edu.cst3492.taide.service.CodeContext` | 文件、语言、选区、行号与所属类 |
| `edu.cst3492.taide.action.automation.SmartAction` | B 侧直接调用智能动作的入口 |

A 的模型与检索服务、B 的来源打开回调通过项目级绑定接入：

```java
import edu.cst3492.taide.action.automation.AutomationServices;

AutomationServices.getInstance(project).bind(
        llmClient,
        retriever,
        citationOpener
);
```

`citationOpener` 的类型为 `Consumer<RetrievedChunk>`。回调收到原始检索元数据，由 B 的引用导航器打开本地文件并定位页码。此回调在 EDT 执行。

`LlmClient` 和 `Retriever` 也支持注册为同名接口的 IDEA 项目服务。项目级 `bind` 优先于注册服务。引用打开回调通过 `bind` 提供。

模型调用与检索在后台任务中执行。`LlmClient` 负责网络超时、重试与请求取消；自动化层在任务开始和结果交付前检查取消状态。已取消任务会释放占用状态，并停止展示结果。模型密钥由 A 的设置和安全存储负责，自动化模块使用已配置的客户端。

B 的正式插件入口需要声明 Java 依赖并包含动作片段：

```xml
<idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
  <depends>com.intellij.modules.platform</depends>
  <depends>com.intellij.java</depends>
  <xi:include href="/META-INF/taide-automation.xml"
              xpointer="xpointer(/idea-plugin/*)"/>
</idea-plugin>
```

以上内容合入 B 的完整入口，保留其插件 ID、工具窗、设置页和其他扩展。三个动作的注册集中在 `src/main/resources/META-INF/taide-automation.xml`。

## JUnit 测试生成

1. 在 Java 编辑器中选中类或方法。
2. 执行 `Generate JUnit Tests`，输入具体场景。
3. 在预览中检查、编辑生成代码，并选择测试源目录。
4. 确认保存后，IDE 创建并打开新测试文件。

模型输入包含自然语言场景、选区、完整当前文件和包名。JavaParser 校验生成内容的语法、包名、单一公开具体类、`Test` 类名后缀以及 JUnit 5 `@Test` 方法。保存前会再次校验编辑后的内容。

测试源目录优先使用已有 Java 测试源根。Gradle 的 `main`、`test` 独立模块按最近的 `build.gradle.kts`、`build.gradle` 或 `pom.xml` 关联；缺少测试源目录时使用所属构建模块的 `src/test/java`。文件路径由解析后的包名和类名生成。

已有同名文件会触发提示，用户可修改生成类名。目标路径会检查目录范围与符号链接。文件创建使用 `WriteCommandAction`，支持 IDE 撤销。模块缺少 JUnit 5 时，预览会显示依赖提示。

`examples/junit-demo/` 提供可独立导入的 Java 21/JUnit 5 示例项目，附带 Gradle 8.13 Wrapper 与分发包校验和。可演示的场景为：

```text
Reject a null or blank name with IllegalArgumentException, and trim a valid name.
```

保存生成测试后，在该目录运行：

```bash
./gradlew test
```

生成结果的语义由用户结合场景审阅，编译和执行使用目标项目的依赖与测试环境。

## Git 提交信息

JGit 对比 `HEAD` 树与暂存区 `DirCache`。首次提交使用空树作为基线。差异涵盖新增、修改、删除和重命名文件；二进制及超过 1 MiB 的文件使用差异元数据。

用户先查看暂存区差异，确认后发送给模型。全部差异上限为 180,000 字节、500 个文件。超限时提示拆分提交，保持每次模型输入覆盖当前暂存区的完整差异。

生成结果采用 Conventional Commits，摘要最长 100 字符。用户可以编辑和复制，也可以通过二次确认创建本地提交。提交前重新比较由 `HEAD`、暂存路径、stage、文件模式及 blob ID 计算的 SHA-256 指纹。

工作区中尚未暂存的改动保持原样。提交使用用户已有 Git 身份与仓库设置，钩子或签名失败会显示错误提示。Git 提交通过仓库历史管理，确认对话框会说明其与编辑器撤销的关系。

## 课件感知代码审查

检索器返回最多八个相关片段。自动化层为片段分配 `S1`、`S2` 等临时 ID，并将文本与代码发送给模型。模型只返回引用 ID；文档名和页码由原始检索元数据生成。

每条课件审查结果包含 `ERROR`、`WARNING` 或 `INFO` 严重度、标题、原因、建议及至少一个已召回来源。未知来源 ID、缺失来源、无效页码、异常分数及非法 JSON 都会触发可读错误。

检索结果为空时，审查进入通用知识模式，引用列表保持为空，并显示：

> Response is based on general knowledge; no specific course material is referenced.

来源列表使用绑定的导航回调打开文档。结果复制保留问题说明、建议和引用页码。

## 验证

```bash
./gradlew test
python3 -m venv .venv
.venv/bin/python -m pip install -r requirements-tools.txt
.venv/bin/python -m unittest discover -s scripts/tests -v
```

Java 测试涵盖生成输入、JUnit 编译、非法输出、引用核验、暂存区隔离、首次提交、重命名、二进制变更、冲突、暂存区变化、路径安全和多模块定位。平台测试覆盖动作注册、选区行号、后台线程与 EDT 回调、取消状态、VFS 创建及撤销，并生成界面截图。

测试报告位于 `build/reports/tests/test/`，界面样本位于 `build/reports/ui/`。自动化测试使用合成示例与固定模型响应。真实模型、真实课件及来源导航在三方服务绑定后执行集成验收。

本地验收环境为 WSL2、JDK 21 和 IDEA Community 2024.2.4：57 项 Java 测试、11 项 Python 测试、插件结构校验通过，Plugin Verifier 的结论为 `Compatible`。英文技术资料收录了实际预览组件的[界面样本](automation/TECHNICAL_NOTES.md#ui-samples)，可用于团队报告。

### 模型连通性

密钥通过 `TAIDE_LLM_API_KEY` 环境变量提供。检查请求只包含合成探针文本：

```bash
.venv/bin/python scripts/check_llm.py \
  --base-url https://api.deepseek.com/v1 \
  --model deepseek-chat
```

同济 AGI 使用平台提供的兼容 API 基址和模型名称。脚本支持 HTTPS，以及本地测试用 HTTP；输出包含连接状态与模型名。响应正文和密钥保持在进程内。

### 真实模型验收

设置 `TAIDE_LLM_BASE_URL`、`TAIDE_LLM_MODEL` 和 `TAIDE_LLM_API_KEY` 后运行：

```bash
./gradlew test -PliveModelTests=true --tests '*LiveAutomationTest'
```

验收覆盖真实模型生成的 JUnit 编译与执行、真实暂存区提交信息、课件引用完整性和通用知识审查。输入为示例代码、临时 Git 仓库和合成 JDBC 片段。默认构建使用固定模型样本，真实模型验收由上述参数显式开启，每次重新请求服务。

报告位于 `build/reports/tests/live-model/`，JUnit XML 位于 `build/test-results/live-model/`，脱敏的生成结果及执行摘要位于 `build/reports/live-model/`。

### 人工验收

| 场景 | 观察结果 |
| --- | --- |
| 取消场景输入或预览 | 文件和 Git 历史保持原样 |
| 缺少 Java 选区 | Java 智能动作置灰 |
| 缺少模型或检索绑定 | 可读的服务配置提示 |
| 模型超时或失败 | 可读错误，任务释放占用 |
| 保存同名测试 | 提示更换类名 |
| 修改后保存生成测试 | 使用编辑后的内容重新校验 |
| 无 Git 仓库或空暂存区 | 对应状态提示 |
| 预览后重新暂存文件 | 提示重新生成提交信息 |
| 课件命中 | 每条问题附原始文档与页码 |
| 课件无命中 | 通用知识提示，引用为空 |
| 点击来源 | B 的导航器打开对应课件及页码 |
| IDEA 1366×768 | 对话框、文本与操作控件完整显示 |

## CI 与发布

PR 和 `main` 分支触发构建及测试。CI 同时运行插件结构验证、IntelliJ 二进制兼容性检查和 Python 交付工具测试，并上传插件 ZIP、测试报告及界面样本。

正式发布使用 `v<pluginVersion>` 标签。`gradle.properties` 中的版本应为正式版本；发布工作流先校验标签和版本，再构建插件并上传 GitHub Release 附件。插件分发 ZIP 与 Canvas 提交 ZIP 分别管理。

## Canvas 打包

准备英文报告 PDF、英文演示 PDF，以及存放真实团队资料的本地 JSON 文件。JSON 顶层字段为 `teamNo` 和 `members`；每个成员包含：

| 字段 | 内容 |
| --- | --- |
| `fullName` | 英文全名 |
| `matriculationNumber` | 学号 |
| `contactNumber` | 联系电话 |
| `email` | 邮箱 |

资料文件可放在已忽略的 `submission/` 目录。报告包含所有成员资料，演示首页包含所有成员姓名和学号。

```bash
.venv/bin/python scripts/package_submission.py \
  --report /path/to/report.pdf \
  --slides /path/to/slides.pdf \
  --team-file /path/to/team.json \
  --plugin build/distributions/taide-0.1.0-SNAPSHOT.zip \
  --output submission/TAIDE-Group-B.zip
```

脚本检查干净的源码工作区、Gradle Wrapper、插件版本、B 组团队编号、报告资料和演示首页；随后输出：

```text
TAIDE-Group-B.zip
├── source/                 完整构建源码
├── documents/
│   ├── report.pdf
│   ├── slides.pdf
│   └── reference/         仓库技术文档
├── plugin/                 可安装插件 ZIP
└── manifest.json           Git commit、版本及文件 SHA-256
```

团队按作业要求确认报告的五节顺序、英文表达、幻灯片内容和演示分工，然后将 ZIP 提交 Canvas。

## 英文技术资料

报告与演示可使用 [Automation and Release Technical Notes](automation/TECHNICAL_NOTES.md)。该资料覆盖 C 侧设计、数据结构、算法、交互和演示顺序。
