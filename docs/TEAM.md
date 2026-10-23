# TEAM.md — TAIDE 分工与协作指导书

> **这是本项目的运行手册，不是一次性文档。**
> 项目期间所有关于「谁做什么、接口长什么样、什么时候交付」的问题，以本文档为准。
> 口头约定一律无效——没写进这里就等于没发生。

| 项 | 值 |
|:--|:--|
| 项目 | TAIDE — IntelliJ IDEA 课程助教插件 |
| 课程 | CST3492 Java Enterprise Application Development · AY2026/27 S1 |
| 组别 | **Group B**（F0 + F1） |
| 仓库 | https://github.com/Keith-hq/taide |
| 提交截止 | **2026-11-03（周二）23:00** |
| 演示 | **2026-11-04（周三）13:30–16:15** |
| 文档版本 | v1.0 · 2026-10-21 |

---

## 0. 开工前必须补齐的 3 个空白

这三项不补齐，文档里所有「A / B / C」都只是代号：

- [ ] **成员姓名与学号** —— 填进下方角色表，以及 README 的 Team 表
- [ ] **LLM API Key** —— DeepSeek 或同济 AGI 平台，谁去申请、什么时候拿到
- [ ] **课件来源** —— 课程 PDF/PPTX 放在哪个目录，是否允许提交进仓库（建议：**不入库**，只提交索引脚本）

---

## 1. 硬约束

先认清三个数字，后面所有取舍都由它们决定：

| 约束 | 数值 | 含义 |
|:--|:--|:--|
| **剩余时间** | **13 天**（10-21 → 11-03） | 不是「一个学期做插件」，是「两周做一个能演示的插件」 |
| **功能范围** | F0 四项 + F1 三项 | 七项都要能跑通，不能只做一半 |
| **评分权重** | 功能 35% · 技术设计 35% · 演示 10% · 代码 10% · 文档 10% | **技术设计分与功能分等重**——架构说不清，功能全做完也拿不到高分 |

由此推出三条铁律：

1. **先跑通，再优化。** 任何模块的第一个版本允许丑陋，但不允许跑不起来。
2. **接口先冻结，代码后填充。** 三人并行开发唯一不返工的办法是：先把方法签名定死，各自对着空实现写。
3. **演示优先于完备。** 演示里讲不出来的功能等于没做。做完一个功能，当天录一段 30 秒的屏。

---

## 2. 角色总览

| 代号 | 姓名 | 学号 | 角色 | 一句话职责 | 工作量 |
|:--:|:--|:--|:--|:--|:--:|
| **A** | `[待填]` | `[待填]` | **RAG & LLM Core Lead** | 让插件「查得到、答得准、引得出」 | 35% |
| **B** | `[待填]` | `[待填]` | **UI/UX & Context Lead** | 让插件「看得见、点得动、不卡死」 | 30% |
| **C** | `[待填]` | `[待填]` | **Automation & Release Lead** | 让插件「自动干三件事」并保证能交付 | 35% |

**为什么这样切。** 按「纵向功能切片」分，不按「前端/后端」分。理由是：按层分会造成 A 等 B 的接口、B 等 A 的数据，三人同时空转。按切片分，每人从 UI 到逻辑都能自己闭环，只在三个明确的接缝上握手（见 §4）。

---

## 3. 文件所有权矩阵

**规则：别人的目录你只能读，不能改。要改就提 PR，由 owner 合并。**

| 路径 | Owner | 说明 |
|:--|:--:|:--|
| `src/main/java/edu/cst3492/taide/rag/**` | **A** | 解析、切块、嵌入、索引、检索 |
| `src/main/java/edu/cst3492/taide/llm/**` | **A** | LLM 客户端、prompt 模板加载 |
| `src/main/resources/prompts/**` | **A** | 所有 prompt 文本，版本化 |
| `src/main/java/edu/cst3492/taide/settings/**` | **A** | `TaideSettings` 持久化配置 |
| `src/main/java/edu/cst3492/taide/service/**` | **A**（接口） | `TaideService` 及其实现——**全项目唯一的接缝** |
| `src/main/java/edu/cst3492/taide/ui/**` | **B** | Tool window、聊天面板、引用卡片、设置页 UI |
| `src/main/java/edu/cst3492/taide/action/AskTaideAction.java` | **B** | F0.4 右键问答 |
| `src/main/java/edu/cst3492/taide/context/**` | **B** | 代码上下文提取（选区、类名、行号） |
| `src/main/resources/META-INF/plugin.xml` | **B** ⚠️ | **独占维护**。任何人要注册 action，把类名 + 一行 XML 发给 B，不要自己改 |
| `src/main/java/edu/cst3492/taide/action/automation/**` | **C** | F1 三个动作 |
| `src/main/java/edu/cst3492/taide/git/**` | **C** | staged diff 提取 |
| `build.gradle.kts` / `gradle.properties` / `.github/**` | **C** | 构建、CI、打包 |
| `docs/TEAM.md` | 全员 | 各自改自己章节，改完在群里说一声 |
| `README.md` | **B** | 对外门面 |
| `docs/REPORT.md` | **B** 主笔 | A、C 供技术章节 |

⚠️ `plugin.xml` 是三人唯一的物理冲突点。**B 是唯一有权提交它的人**，C 的三个 action 由 B 代为注册。这条不遵守，第一天就会冲突。

---

## 4. 接口契约（v1.0 · 冻结）

**冻结时间：2026-10-22 24:00。** 之后任何签名变更走 §7 的变更流程。

这套接口的作用是：A 写实现、B 写 UI、C 写动作，三人在没有对方代码的情况下都能编译通过。

### 4.1 数据模型

```java
package edu.cst3492.taide.service;

/** 编辑器上下文——由 B 产出，A 消费。 */
public record CodeContext(
        String filePath,
        String language,
        String selectedText,
        int startLine,
        int endLine,
        String enclosingClass
) {
    public static CodeContext empty() { ... }
}

/** 一条引用——由 A 产出，B 渲染。 */
public record Citation(
        String documentName,   // "Lecture05-Spring-Core.pdf"
        int slideNumber,       // 1-based；PDF 为页码
        String snippet,        // 命中片段，用于卡片预览
        double score           // 0..1
) {}

/** 一次回答。grounded=false 时 citations 必须为空。 */
public record TaAnswer(
        String text,
        List<Citation> citations,
        boolean grounded,
        String model
) {}
```

### 4.2 核心服务

```java
package edu.cst3492.taide.service;

public interface TaideService {
    /** 阻塞调用。调用方必须在非 EDT 线程执行。 */
    TaAnswer ask(String question, CodeContext context) throws TaideException;

    /** 流式。onToken 在后台线程被调用，UI 侧自行切回 EDT。 */
    void askStreaming(String question, CodeContext context,
                      Consumer<String> onToken,
                      Consumer<TaAnswer> onComplete,
                      Consumer<Throwable> onError);
}
```

**A 必须额外提供一个 `FakeTaideService`**：固定返回一段假回答 + 两条假引用，`Thread.sleep(800)` 模拟延迟。B 从第一天起就用它开发 UI，不必等 A 的 RAG 跑通。这是整个分工能并行的前提。

### 4.3 检索与 LLM（A 内部，但 C 会用到）

```java
public interface Retriever {
    List<RetrievedChunk> retrieve(String query, int k);
}

public record RetrievedChunk(
        String docId, String documentName, int slideNumber,
        String text, double score) {}

public interface LlmClient {
    String complete(String systemPrompt, String userPrompt, double temperature);
    void completeStreaming(String systemPrompt, String userPrompt, Consumer<String> onToken);
}
```

### 4.4 智能动作（C 实现，B 注册）

```java
package edu.cst3492.taide.action.automation;

public interface SmartAction {
    String id();            // "taide.generateTests"
    String displayName();   // "Generate JUnit Tests…"
    void perform(Project project, Editor editor, CodeContext ctx);
}
```

### 4.5 索引产物格式

```json
{
  "version": 1,
  "model": "all-MiniLM-L6-v2",
  "dim": 384,
  "chunks": [
    { "id": "lec05#s12#0", "doc": "Lecture05-Spring-Core.pdf",
      "slide": 12, "text": "…", "vec": [0.013, -0.207, …] }
  ]
}
```

落盘位置：`<IDE config>/taide/index/<project-hash>.json`。**不写进项目目录**，避免污染仓库。

---

## 5. 三人职责书

### 5.1 A — RAG & LLM Core Lead

**定位**：整条链路的发动机。A 的进度决定 B 能否从假数据切到真数据，也决定 C 的三个动作能不能拿到检索能力。

**负责模块**：`rag/`、`llm/`、`settings/`、`service/`、`prompts/`

**交付物与验收标准（DoD）**

| ID | 交付物 | 验收标准 | 截止 |
|:--|:--|:--|:--|
| A-1 | 课件解析 + 切块 | 对 10 份课件跑通，产出 chunk 数 > 800；**随机抽 10 个 chunk，人工核对 slide 号 100% 正确** | 10-24 |
| A-2 | 嵌入 + 索引构建 | 全量构建 < 60s；重复构建命中缓存 < 3s；`index.json` 可被独立脚本读取 | 10-25 |
| A-3 | `Retriever` 可用 | 准备 20 题评测集（含 5 题「课件里没有答案」），top-3 命中率 ≥ 70%，空结果判定准确率 ≥ 80% | 10-26 |
| A-4 | `LlmClient` 双后端 | DeepSeek + AGI 均可切换；超时 30s、失败重试 2 次；断网时抛 `TaideException` 而非崩溃 | 10-27 |
| A-5 | `TaideService` 实现 | 端到端问答返回带 `Citation` 的 `TaAnswer`；无相关内容时 `grounded=false` 且 `citations` 为空 | 10-26 |
| A-6 | Prompt 工程 | 系统 prompt 强制「只依据给定片段作答，逐条标注 [文档·页码]」；准备 3 组反例测试（诱导编造） | 10-28 |
| A-7 | `FakeTaideService` | 10-22 前交付给 B，否则 B 空转 | **10-22** |

**对外承诺**：10-22 交出接口空实现 + Fake；10-26 交出可用真实现。
**依赖**：B 提供 `CodeContext` 的填充逻辑（A 只消费不实现）；C 提供 API Key 的可用性验证。

**技术要点（自己要想清楚的）**
- PPTX 的 slide 边界用 POI 的 XSLF 直接读，**不要**指望 Tika 的纯文本输出保留页序。
- 页码 1-based，与 PDF 阅读器显示一致，否则引用全是错的。
- MMR 的 λ 调参写进文档，演示时是加分项。
- 所有 prompt 存 `resources/prompts/*.md`，不要在代码里拼字符串——报告里要贴，评审要看。

**风险**：embedding 模型体积（ONNX MiniLM ≈ 90MB）会让插件包变大。预案：模型按需下载 + 校验和，首次启动时拉取。

---

### 5.2 B — UI/UX & Context Lead

**定位**：所有用户能看见的东西。B 的手感直接决定演示那 10 分钟的观感。

**负责模块**：`ui/`、`action/AskTaideAction`、`context/`、`plugin.xml`、`README.md`、报告主笔

**交付物与验收标准（DoD）**

| ID | 交付物 | 验收标准 | 截止 |
|:--|:--|:--|:--|
| B-1 | Tool window 骨架 | 右侧出现 "TAIDE" 面板；输入框 + 消息列表可用；对接 `FakeTaideService` 能收发 | 10-23 |
| B-2 | 聊天面板完整态 | 用户/助手气泡区分；**流式逐字输出**；长回答可滚动；「复制」按钮 | 10-27 |
| B-3 | 引用卡片 | 每条引用渲染为卡片（文档名 · 第 N 页 · 相似度）；点击打开源文件；`grounded=false` 时显示醒目提示条 | 10-28 |
| B-4 | 右键问答（F0.4） | 选中代码 → 右键 → Ask TAIDE → 面板自动填入上下文并提问；无选区时菜单项置灰 | 10-27 |
| B-5 | `CodeContext` 提取 | 正确拿到选区文本、起止行号、所属类名；跨文件选中不崩 | 10-26 |
| B-6 | 设置页 | Provider / API Key / 课件目录 / Rebuild 按钮；Key 输入框掩码显示 | 10-29 |
| B-7 | 线程正确性 | **所有 LLM 调用不在 EDT**；索引构建显示进度条且可取消；构建期间 UI 不冻结 | 10-29 |
| B-8 | 报告 + README | 报告英文，按作业规定的 5 节顺序；README 与仓库状态一致 | 11-01 |

**对外承诺**：10-22 交出 `plugin.xml` 初版（含全部 action 占位）；10-26 交出 `CodeContext` 实现。
**依赖**：A 的 `FakeTaideService`（10-22）；C 的 action 类名清单（用于注册）。

**技术要点**
- Swing 在 IntelliJ 里写聊天界面，用 `JBList` + 自定义 `ListCellRenderer`，比手搓 `JPanel` 布局省一半时间。
- 流式输出必须 `ApplicationManager.getApplication().invokeLater(...)` 回 EDT，否则随机崩。
- 引用点击：PDF 用 `FileEditorManager` 打开；PPTX 无法定位页码，降级为打开文件 + 提示页码。
- 别做花哨动画。演示时卡一下比朴素难看得多。

**风险**：Swing 布局调试极耗时间。预案：第一天就把界面画成固定三块（历史区 / 输入区 / 状态区），不做响应式。

#### B 实现进度记录（2026-10-23）

- 已提交到工作区的 B 源码覆盖工具窗、对话与引用、代码上下文、右键问答、Provider/API Key/课件目录设置，以及可选的 A 知识库重建适配；具体类见 `src/main/java/edu/cst3492/taide/`。API Key 不进入持久化 XML，使用 IntelliJ `PasswordSafe`。
- 离线演示实现为 `DemoTaideService`，所有回答和引用均标记为示例，不代表实际课程资料。A 的 `TaideService` 与重建服务尚未在此工作区提供；C 的 action 类名尚未交付，XML 注册保持注释状态。
- `tools/verify-b.ps1` 对选区行号、聊天线程/取消/引用状态、引用路径边界及设置验证运行了 53 项纯 Java 检查，并用原生 Swing 组件生成八张截图。UI 与设置源码也已用本机 IntelliJ 2025.1.3.1 平台类库及 JBR 21 编译。
- 插件面向 IntelliJ 2024.2；隔离 Gradle Wrapper 已准备，但本机下载 Gradle 发行包超时，且已安装的 PyCharm 不含 Java PSI 插件。因此 `runIde`、`buildPlugin`、Java PSI 上下文类的完整编译、IDE 内 1366×768 验收与 PDF/PPTX 页码深链均尚未验证，不能将 B-1 至 B-8 标记为全项验收完成。
- [B 英文报告贡献草稿](TAIDE-B-English-Report-V1.0.0.docx)供全组整合使用。作业要求的报告五节标准标题未包含在当前仓库文档中；草稿用五个临时标题起草，需在报告定稿时对照作业原文调整并补全 A、C 内容及成员信息。

---

### 5.3 C — Automation & Release Lead

**定位**：三个 F1 动作 + 保证项目能打包、能跑、能交。

**负责模块**：`action/automation/`、`git/`、`build.gradle.kts`、`.github/`

**交付物与验收标准（DoD）**

| ID | 交付物 | 验收标准 | 截止 |
|:--|:--|:--|:--|
| C-1 | 构建骨架 | `./gradlew runIde` 能起沙箱 IDE；`buildPlugin` 产出 zip | **10-22** |
| C-2 | JUnit 生成（F1.1） | 弹窗收自然语言场景 → 选中类/方法 → 生成 JUnit 5 测试 → 写入 `src/test/java` 并打开；**对示例类生成的测试能编译通过** | 10-29 |
| C-3 | Commit message（F1.2） | 取 staged diff（无 staged 时提示）；生成 Conventional Commits 格式；弹窗可编辑后提交 | 10-30 |
| C-4 | 课件感知代码审查（F1.3） | 选中代码 → 检索课件 → 输出结构化问题清单（严重度 + 说明 + 引用）；每条引用可点 | 10-31 |
| C-5 | 异常兜底 | 三个动作在「无选区 / 无 Git 仓库 / API 失败 / 生成内容非法」四种情况下都给出可读提示，不抛栈 | 10-31 |
| C-6 | CI + 打包 | GitHub Actions 跑 `build`；release 附件含插件 zip；版本号与 `plugin.xml` 一致 | 11-01 |
| C-7 | 演示环境 | 一台干净的机器上按 README 从零装到跑通，全程计时 < 10 分钟 | 11-02 |

**对外承诺**：10-22 交出可运行的构建骨架（这是全组的第一块地基，晚一天全线推迟）；10-25 交出 action 类名清单给 B 注册。
**依赖**：A 的 `LlmClient` 与 `Retriever`（用于 C-4）；B 的 `plugin.xml` 注册。

**技术要点**
- Git 用 **JGit** 还是 IntelliJ 自带 `Git4Idea`？倾向 JGit：不依赖 IDE 内部 API，版本兼容性好。`git diff --cached` 等价于 `DiffCommand.setCached(true)`。
- 生成测试要判断项目里有没有 JUnit 5 依赖，没有就在提示里说明，别生成一堆红字。
- F1.3 的 prompt 是三个动作里最难的：要让它「按课件标准审代码」，而不是泛泛而谈。建议 prompt 结构：`代码 + 检索到的课件片段 + 审查维度清单`，输出 JSON 再渲染。
- 生成内容一律**先预览再落盘**，不要直接写文件——写错文件是演示事故的头号来源。

**风险**：C 的活最杂。预案：C-3（commit message）最简单，如果 10-29 还没开始，先做它拿分，F1.3 降级为「简化版审查」。

---

## 6. 里程碑与逐日计划

**集成点只有两个，其余时间各自为战。** 这是刻意的——集成次数越多，返工越多。

| 日期 | 星期 | A | B | C | 集成 |
|:--|:--:|:--|:--|:--|:--|
| 10-21 | 三 | 读课件、定 chunk 策略 | 画 UI 线框、定面板结构 | 搭 Gradle 骨架、跑通 hello-world 插件 | — |
| 10-22 | 四 | **交接口空实现 + Fake**；Tika 解析 | `plugin.xml` 初版；tool window 空壳 | **`runIde` 跑通** | 🔒 接口冻结 |
| 10-23 | 五 | 切块 + 页码校验 | 面板对接 Fake，能收发 | JGit 取 staged diff | — |
| 10-24 | 六 | A-1 完成（10 份课件） | 消息气泡 + 流式输出 | JUnit 动作骨架（硬编码输出） | — |
| 10-25 | 日 | 嵌入 + 索引构建 | 引用卡片（假数据） | action 清单交 B | — |
| 10-26 | 一 | **A-3/A-5 完成** | `CodeContext` 完成 | 测试写入逻辑 | 🔗 **集成 #1：F0 端到端** |
| 10-27 | 二 | 双后端 + 重试 | 右键问答打通 | JUnit 接真 LLM | — |
| 10-28 | 三 | Prompt 调优 + 反例测试 | 引用可点击 + 未命中提示 | Commit message 完成 | — |
| 10-29 | 四 | 检索质量调参 | 设置页 + 线程审查 | F1.1 编译验证通过 | — |
| 10-30 | 五 | 性能优化（缓存、异步） | 报告初稿（1–3 节） | **F1.2 完成** | 🔗 **集成 #2：F0+F1 全通** |
| 10-31 | 六 | 报告第 4 节（架构/RAG/算法） | 报告 4–5 节 + UI 手册 | F1.3 完成 + 异常兜底 | — |
| 11-01 | 日 | 代码 review + 注释 | 报告定稿 + slides 初稿 | 打包 + CI 绿 | 📦 冻结功能 |
| 11-02 | 一 | 修 bug | slides 定稿 | 干净机器演练 | 🎤 **彩排（计时 20 分钟）** |
| 11-03 | 二 | 最终修复 | 最终修复 | 打 ZIP、检查清单 | 🚀 **23:00 提交** |
| 11-04 | 三 | — | — | — | 🎯 13:30 演示 |

**缓冲说明**：表里没有留空白天。真实执行中一定会延期，所以——**10-30 的集成 #2 是硬死线**，届时没做完的功能直接砍掉，剩余时间全部用于打磨已有的部分。

---

## 7. 协作机制

### 7.1 分支与合并

```
main                  ← 永远可编译、可运行
├── feat/a-rag-index
├── feat/b-chat-panel
└── feat/c-junit-action
```

- 分支命名：`feat/<代号>-<主题>`、`fix/<代号>-<问题>`
- `main` 保护：**至少 1 人 review 才能合并**
- 合并前必须 `./gradlew build` 本地通过
- 每天至少 push 一次（哪怕半成品），分支可以脏，`main` 不能脏

### 7.2 Commit 规范

```
feat(rag): add slide-aware chunker
fix(ui): keep streaming updates on EDT
docs(team): update milestone for integration #2
refactor(llm): extract retry logic
```

> 顺带一提：F1.2 做完之后，就用它自己生成 commit message。这是最好的自测。

### 7.3 节奏

| 机制 | 时间 | 形式 | 产出 |
|:--|:--|:--|:--|
| 每日站会 | 每天 21:30 | 线上 10 分钟 | 三问：昨天做了什么 / 今天做什么 / 卡在哪 |
| 集成会 | 10-26、10-30 | 线上 60 分钟 | 当场跑通，跑不通就砍 |
| 周自检 | 每周日 21:00 | 各自对照 §9 | 打勾，未完成项进下周计划 |

### 7.4 阻塞处理

**卡住超过 2 小时，必须喊人。** 不要一个人闷头debug到半夜——13 天的项目里，一天的低效就是 8% 的工期。

### 7.5 接口变更流程

冻结后要改接口，走这三步，缺一不可：

1. 在群里说明：改什么、为什么、影响谁
2. 更新本文档 §4 与 §11 变更记录
3. 24 小时内完成合并，超时则回滚

---

## 8. 集成与验收

### 8.1 集成 #1（10-26）— F0 端到端

必须现场跑通这条链：

> 打开一个 Java 项目 → 选中一段代码 → 右键 Ask TAIDE → 面板弹出 → 提问 → 流式回答 → 底部出现 2 条以上引用卡片 → 点击卡片打开源课件

**跑不通的处理**：优先保「有回答」，引用可以是低质量的；但**不能没有引用**——那是 F0 的核心评分点。

### 8.2 集成 #2（10-30）— F0 + F1 全通

七项功能逐项过一遍，每项 2 分钟，跑不通的当场标记「砍」。

### 8.3 提交前检查清单（11-03）

- [ ] `./gradlew build` 绿
- [ ] 干净机器按 README 装成功
- [ ] 断网情况下插件不崩（提示可读）
- [ ] 无 API Key 时给出引导，不抛异常
- [ ] `plugin.xml` 版本号 = README 版本号
- [ ] 报告 PDF 英文、5 节顺序正确、含全部成员姓名学号
- [ ] slides 首页含全部成员姓名学号，PDF 格式
- [ ] 源码与文档分两个文件夹，打成**一个** ZIP
- [ ] ZIP 上传 Canvas 成功（截图留证）

---

## 9. 每周自检清单

每周日 21:00，各自对照自己的部分打勾。

**A**
- [ ] 我的代码里没有硬编码的 prompt 字符串
- [ ] 所有对外方法都有 `throws TaideException` 且有可读错误信息
- [ ] 索引构建在后台线程，有进度反馈
- [ ] 我给出的每个 chunk 的 slide 号都验证过

**B**
- [ ] 没有任何 LLM 调用跑在 EDT 上
- [ ] 所有 UI 状态变化都在 `invokeLater` 里
- [ ] 面板在 1366×768 分辨率下不溢出
- [ ] 每个按钮点下去都有反馈（哪怕只是禁用 + 转圈）

**C**
- [ ] 每个动作都能用 Ctrl+Z 撤销（或明确提示不可撤销）
- [ ] 写文件前有预览
- [ ] 四种异常场景都试过
- [ ] `main` 分支上的 `buildPlugin` 是绿的

---

## 10. 风险登记表

| # | 风险 | 概率 | 影响 | 应对 | Owner |
|:--:|:--|:--:|:--:|:--|:--:|
| R1 | 13 天做不完七项功能 | **高** | 高 | 10-30 硬死线，砍 F1.3 复杂度保 F0 | 全员 |
| R2 | LLM API 限流 / 欠费 / 校园网不稳 | 中 | 高 | 双后端 + 结果缓存 + **演示录屏兜底** | A |
| R3 | 课件解析丢页码，引用全错 | 中 | **致命** | 不用 Tika 纯文本做 PPTX，改 POI XSLF；抽检 10 个 chunk | A |
| R4 | 三人环境不一致（JDK/Gradle 版本） | 中 | 中 | Gradle Wrapper 入库；`.editorconfig`；统一 JDK 21 | C |
| R5 | `plugin.xml` 冲突 | 中 | 中 | B 独占维护，其他人只提需求 | B |
| R6 | Swing 界面调不完 | 中 | 中 | 固定三块布局，不做响应式，不带动画 | B |
| R7 | 演示当场断网 / API 挂 | 中 | 高 | 提前录 3 分钟全流程视频；本地索引预热 | C |
| R8 | 报告写不完（10% 权重） | 中 | 中 | 10-30 就开始写，不等功能做完 | B |

---

## 11. 变更记录

| 日期 | 版本 | 变更 | 提出人 |
|:--|:--|:--|:--|
| 2026-10-21 | v1.0 | 初版：角色划分、接口契约、里程碑 | — |

---

<div align="center">

**遇到冲突时的裁决顺序：** 能演示 > 技术正确 > 代码优雅。
**有疑问时先看这份文档，文档没说清就补进去——别靠记忆。**

</div>
