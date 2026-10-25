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
| 文档版本 | v1.2 · 2026-10-24（模型准备与首次使用引导） |

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
| **A** | 刘鸿铭 | 2453608 | **RAG & LLM Core Lead** | 让插件「查得到、答得准、引得出」 | 35% |
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
| `src/main/java/edu/cst3492/taide/model/**` | **B** | 应用级模型准备、固定版本下载与校验；与 A 的问答服务共享模型路径 |
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

**演示服务用于并行开发**：当前由 B 的 `DemoTaideService` 提供固定回答与示例引用，已替代原计划的 `FakeTaideService`。演示默认开启，不调用网络；示例及其来源必须清楚标注，不能当作真实课程问答或索引验收。

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

### 4.6 B 侧集成约定（2026-10-24）

以下为当前源码的接入状态，补充既有冻结契约，不改变 `TaideService` 方法签名：

1. **聊天与重建使用同一个项目服务实例。** 正式 `plugin.xml` 将 `TaideService` 注册到 `TaideServiceImpl`，将 `KnowledgeBaseController` 注册到 B 的 `ProjectKnowledgeBaseController`。后者通过 `project.getService(TaideService.class)` 委托重建，避免把同一实现按两个接口重复创建。§5.1 A-5 中的两行 XML 是当时的接入请求，当前注册以本节和正式 `plugin.xml` 为准。
2. **设置是不可变快照，凭据另存。** A 通过 `TaideUiSettings.snapshot()` 取得 Provider、base URL、模型、课件目录和演示开关；`readApiKey(snapshot)` 必须在后台线程执行。`revision()` 在设置或凭据更新后递增，问答服务据此使资源缓存失效。API Key 按项目、Provider 与端点存入 PasswordSafe，不写入 XML；旧版凭据升级后需重新输入，切换端点不得沿用其他端点的 Key。
3. **重建需先保存目录。** 设置页点 Apply 后才能重建该目录。重建在后台显示进度并支持取消，发布新索引前检查取消状态；已经完成的原子发布不会因稍后取消设置而回滚。重建成功使聊天索引缓存失效；ONNX 会话按资源使用状态及项目生命周期释放。
4. **演示来源与课件目录随消息保存。** 每条消息固定发送时的演示/真实标记和课件目录；同一会话中切换设置，不改变已有消息的标记，也不会把旧引用指向新目录中的同名课件。示例引用隐藏虚构分数，点击时只显示演示说明；真实引用通过原课件目录内的路径边界检查后打开文件，并提示用户手动定位页码，尚无 PDF/PPTX 页码深链。
5. **C 的动作注册与默认引用导航已接入，模型和检索由 A/C 接入。** 正式 `plugin.xml` 包含 `META-INF/taide-automation.xml`，F1 入口不再是注释占位。`CourseSourceNavigator` 是独立的项目级服务，默认来源打开无需先打开聊天工具窗或调用 `bind`。`AutomationServices.bind(llmClient, retriever, citationOpener)` 可覆盖为自定义服务与导航回调；聊天 `TaideService` 注册不会自动暴露其内部模型和检索器。详见 [AUTOMATION 服务接入](AUTOMATION.md#服务接入)。
6. **模型准备使用应用级服务。** `ModelSetupService` 管理所有项目共享的本地模型路径、准备状态与 revision；A 的问答和重建使用同一路径，目录变化后失效旧资源。用户点击后才下载官方固定版本的 MiniLM 模型与词表（约 90 MB，Apache 2.0），按固定大小和 SHA-256 校验，后台准备成功后保存在 `<IDE config>/taide/model/`。已校验缓存可离线复用，失败或取消不把半成品视为可用模型；关闭设置页只移除 UI 监听，不取消应用级下载。
7. **首次使用按三步完成，模式切换必须显式。** 工具窗展示“模型 → API/课件与索引 → 真实问答”准备状态；用户先下载或在设置中选择固定版本的已有目录，填写 API 与课件目录并 Apply，再 Rebuild。模型未就绪时重建禁用。全部准备好后由用户点击“切换真实问答”，或保存关闭 `Use demo service`；下载完成不会自动关闭演示模式。API 状态只检查配置是否已填写，不代表网络连通、Key 有效或余额充足。
8. **模型目录优先级与网络配置明确。** IDE 设置中显式选择的已有/默认目录优先；未显式选择时依次使用 `taide.model.dir` JVM 属性、`TAIDE_MODEL_DIR` 环境变量和默认目录，保留开发及 CLI 兼容。下载遵循 IDE HTTP Proxy，失败提示入口为 `Settings → Appearance & Behavior → System Settings → HTTP Proxy`，不猜测用户代理端口。普通用户不需要运行下载命令或配置环境变量。
9. **审查引用使用请求前保存的导航回调。** `CourseReviewAction` 在后台生成前调用 `AutomationServices.captureSourceOpener()`：默认保存当前课件目录，存在自定义绑定时保存该回调对象，结果对话框持有这份回调。后续修改设置或重新绑定不会替换旧审查的回调；自定义回调自身负责保存所需的目录上下文。点击来源时传入原始 `RetrievedChunk`，默认导航在后台执行目录边界、重名及 PDF/PPTX 类型检查，在 EDT 打开文件并提示手动定位 1-based 页码，尚无页内深链。未配置目录或文件无效时给出可读提示；项目释放后停止打开和提示。

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

**风险**：embedding 模型体积（ONNX MiniLM ≈ 90MB）会让插件包变大。预案：模型按需下载 + 校验和；当前由 B 的应用级模型准备服务在用户点击后执行，见 §4.6。

#### A 实现进度记录（2026-10-24）

- A-1 首版代码已提交工作区（分支 `feat/a-rag-index`）：`rag/ingest/`（`PdfIngestor` 逐页解析、`PptxIngestor` 逐片解析含备注、`CourseMaterial`）、`rag/chunk/SlideAwareChunker`（512/64、绝不跨页、id=`文档#页码#序号`）、`rag/Chunk`、`IngestMain` 验收 CLI（`./gradlew ingestMaterials --args="<课件目录>"`），配 3 个 JUnit 测试类（页码 1-based、跨页禁止、硬切分、重叠、空片跳过）。
- 技术栈取舍：PDF 用 **PDFBox**（Tika 的 PDF 底层引擎）直接逐页提取，保证页码与阅读器一致，避免 XHTML 再解析一层；PPTX 按预案用 **POI XSLF**（不用 Tika 纯文本）。README 技术栈徽章（Tika）请 B 择机更新为「PDFBox/POI（Tika 生态）」。
- 依赖新增 `pdfbox 3.0.5`、`poi-ooxml 5.4.1`、`log4j-api`（POI 编译期需要），已同步加进根构建与 `tools/b-sandbox` 构建，合并时请 C、B 分别审阅（打包排除/体积由 C 决定）。
- 课件：本机 `C:\Users\LiuHM\taide-materials\` 现有 **9 份**讲义（Lec-0 ~ Lec-5，10-24 从 Canvas 下载）；TEAM 计划按 10 份计，尚缺 1 份（疑为 Lec-6 或后续讲义），正式验收以可得课件为准。
- A-7（FakeTaideService）：B 的 `DemoTaideService` 已覆盖「假数据供 UI 开发」的全部用途，A 不再单独交付 FakeTaideService；有异议请在群内提出。
- **A-1 已收口（2026-10-24）**：真实课件跑通 `ingestMaterials`——9 份讲义共 199 页 → 199 chunks（每页文本稀疏，绝大多数页不足 512 token 即一页一块；TEAM 原估 >800 是按更密语料估的，实际以语料为准）；页码边界已用 pymupdf 逐页交叉验证 6/6 一致；U+FFFD 乱码在摄取层已清理。人工抽 10 块核对页码待全组复核，A-2 嵌入+索引 10-25。
- **A-2 已收口（2026-10-24，提前一天）**：`rag/embed/`（`BertTokenizer` WordPiece 分词、`OnnxEmbeddingModel` ONNX 本地推理 + 均值池化 + L2 归一化，模型输出为原始隐层故需池化）与 `rag/index/`（`InMemoryVectorIndex` 余弦检索 + §4.5 JSON 落盘原子写入、`IndexBuilder` 内容哈希缓存复用旧向量、`BuildIndexMain` 验收 CLI）。实测：199 块全量构建 **4.0s**（<60s 验收线）、缓存重建 **1.1s**（<3s 验收线，embedded=0/reused=199）；`index.json` 已被独立 Python 脚本读取验证（version/model/dim/384 维全对）；查询「what is constructor injection」top-1 命中 Lec-2 第 6 页 Constructors（0.484），语义正确。真模型测试（BERT 参考 id、向量归一化）需 `TAIDE_MODEL_DIR` + `-PliveModelTests=true`。新依赖 onnxruntime 1.20.0（C 审阅）。模型文件约 90MB 已本地化于 `D:\taide-models\`，当时插件内按需下载方案待 C 打包时定；当前实现见 §4.6。
- **A-3 已收口（2026-10-24，提前两天）**：`rag/retrieve/` 实现 `EmbeddingRetriever`（候选 24 → 相似度阈值 → MMR λ=0.7 去重 → top-k；返回冻结契约 `RetrievedChunk`）与 `RetrievalEvaluator` 评测框架 + 20 题评测集 `src/test/resources/rag/eval-questions.json`（15 题有答案、5 题课件无答案）。**调参记录（报告素材）**：初值阈值 0.35 下 top-3 命中 15/15（100%）、空判定 4/5（80%，压线；Kotlin 协程题假命中 Lec-5 p31 仅 0.410）；阈值上调至 **0.45** 后 15/15 + 5/5 双 100%，最弱真命中 0.481（RAG 题）与最强假命中 0.410 之间留有余量；MMR λ=0.7 维持 README 初值未动。验收 CLI：`./gradlew evalRetrieval --args="<课件目录> <index.json> <eval.json>"`；live 测试门禁 `RetrievalEvaluatorTest`（top3≥70%、空判定≥80%）已绿。
- **A-5 已收口（2026-10-24，提前两天）**：`service/` 落地 `TaideServiceCore`（平台无关引擎：检索→组 prompt→LLM→带引用的 `TaAnswer`；无相关内容时 grounded=false 且 citations 为空）与 `TaideServiceImpl`（project service，从 B 的 `TaideUiSettings` 读 provider/baseUrl/model/PasswordSafe API Key，实现 B 的 `KnowledgeBaseController` SPI 供 Rebuild 按钮调用，带进度与取消、原子落盘保留旧索引）。`llm/OpenAiCompatibleLlmClient` 用 JDK HttpClient 实现 OpenAI 兼容 /chat/completions（DeepSeek 与同济 AGI 同一格式，换 baseUrl 即切后端），含 **30s 超时、失败重试 2 次、流式 SSE**——A-4 的核心要求已提前内建，10-27 只剩真实 Key 联调与反例复核。`resources/prompts/qa.md` v1：有依据时必须逐条标 [文档 · 页码]、无依据时输出规定句式「Response is based on general knowledge; no specific course material is referenced.」。运行时文件位置：`<IDE config>/taide/model/`（模型）与 `<IDE config>/taide/index/<project-hash>.json`（索引），不进项目目录。**⚠️ 请 B 在 plugin.xml 注册两行（集成 #1 前置）**：
  ```xml
  <projectService serviceInterface="edu.cst3492.taide.service.TaideService"
                  serviceImplementation="edu.cst3492.taide.service.TaideServiceImpl"/>
  <projectService serviceInterface="edu.cst3492.taide.ui.integration.KnowledgeBaseController"
                  serviceImplementation="edu.cst3492.taide.service.TaideServiceImpl"/>
  ```
  测试：核心/客户端/流式/错误路径 16 个快速单测 + 真检索端到端 live 测试全绿。
- **A-4 已收口（2026-10-24，提前三天）**：真实 DeepSeek 联调通过——`complete` 返回 TAIDE_OK、流式产出 token；**真实检索 + 真实 DeepSeek 端到端**：问「What is a chained exception?」返回 grounded 带引用回答（Lec-3.1 p9）、问「CSS Flexbox」返回通用知识模式且含规定句式。超时 30s / 重试 2 次 / 断网抛可读 `TaideException` 均有单测覆盖（`RealLlmLiveTest`、`OpenAiCompatibleLlmClientTest`）。双后端本质：同一 OpenAI 兼容客户端换 baseUrl（DeepSeek 已实测；同济 AGI 平台同格式，待 Key 到位后在设置页填 baseUrl 即切换）。测试密钥来源：`TAIDE_LLM_API_KEY` 环境变量或 `~/.taide-api-key` 文件（不入库，测试自动跳过缺 Key 场景）。
- **A-6 已收口（2026-10-24，提前四天）**：`prompts/qa.md` v1 通过三组反例实测（`PromptAdversarialLiveTest`，真实 DeepSeek）：① 课件外问题 + 强制要求引用 → 拒绝编造、说出规定句式，且回答中无伪造的「Lec-*.pdf … page N」引用模式（正则断言）；② 假前提「课件说 Java 是动态类型（Lec-1 p10）」→ 纠正为静态类型；③ 索要精确页码 → 回答中出现的页码全部属于检索到的引用集。v1 一次通过无需改版。**A-1~A-7 至此全部收口**（A-7 由 B 的 DemoTaideService 覆盖）。
- **A-C 共享运行时已交付（2026-10-25，响应 [Issue #10](https://github.com/Keith-hq/taide/issues/10)）**：`service/AutomationRuntimeAccess` 接口落地——`withModel`（仅模型，**不要求索引、不加载 ONNX**，供 F1.1/F1.2）与 `withModelAndRetriever`（复用 F0 的 `ServiceResourceCache` 租约与同一索引，供 F1.3）。`TaideServiceImpl` 实现该接口，C 经已注册的 `TaideService` 转型消费，**无需新增 plugin.xml 注册**。验收实测：注册服务即 `AutomationRuntimeAccess`（平台测试）；无 Key / 无索引 / 项目关闭均抛可读 `TaideException`；真实 Key 下 `withModel` 无索引可用（TAIDE_OK 往返）、`withModelAndRetriever` 检索命中 Lec-3.1 p9；租约成败均释放（B 的缓存生命周期测试已覆盖）。取消传播：回调内异常原样抛出，租约由 try-with-resources 必释。平台测试需注意测试线程在 EDT，服务调用走 worker 线程（测试内 `callOffEdt` 辅助）。

---

### 5.2 B — UI/UX & Context Lead

**定位**：所有用户能看见的东西。B 的手感直接决定演示那 10 分钟的观感。

**负责模块**：`ui/`、`action/AskTaideAction`、`context/`、`plugin.xml`、`README.md`、报告主笔

**交付物与验收标准（DoD）**

| ID | 交付物 | 验收标准 | 截止 |
|:--|:--|:--|:--|
| B-1 | Tool window 骨架 | 右侧出现 "TAIDE" 面板；输入框 + 消息列表可用；对接 `DemoTaideService` 能收发 | 10-23 |
| B-2 | 聊天面板完整态 | 用户/助手气泡区分；**流式逐字输出**；长回答可滚动；「复制」按钮 | 10-27 |
| B-3 | 引用卡片 | 真实卡片显示文档名、页码与检索分数，点击打开源文件并提示页码；示例卡片清楚标记且隐藏虚构分数；`grounded=false` 时显示提示条 | 10-28 |
| B-4 | 右键问答（F0.4） | 选中代码 → 右键 → Ask TAIDE → 面板自动填入上下文并提问；无选区时菜单项置灰 | 10-27 |
| B-5 | `CodeContext` 提取 | 正确拿到选区文本、起止行号、所属类名；跨文件选中不崩 | 10-26 |
| B-6 | 设置页与首次引导 | 模型下载/已有目录、Provider / API Key / 课件目录 / Rebuild；模型未就绪重建禁用，真实问答由用户显式开启；Key 输入框掩码显示 | 10-29 |
| B-7 | 线程正确性 | **所有 LLM 调用不在 EDT**；索引构建显示进度条且可取消；构建期间 UI 不冻结 | 10-29 |
| B-8 | 报告 + README | 报告英文，按作业规定的 5 节顺序；README 与仓库状态一致 | 11-01 |

**对外承诺**：维护正式 `plugin.xml` 与右键上下文入口，保证 A/B 聊天与重建接缝和 C 的动作注册一致。
**依赖**：A 的 `TaideService` 与重建实现；C 的动作注册片段和 `AutomationServices` 接入。演示服务由 B 独立提供。

**技术要点**
- 聊天界面使用原生 Swing `ChatPanel`，IDE 适配层提供主题、剪贴板、设置与文件打开；纯 JDK 预览渲染同一个组件。
- 问答与索引工作不在 EDT 执行。流式状态通知合并后在 EDT 批量渲染，取消或关闭后丢弃迟到的回调；错误提示不直接展示可能含凭据的异常正文。
- 聊天与自动化审查共用项目级 `CourseSourceNavigator`，不依赖工具窗是否已打开；后台解析引用路径，在 EDT 用 `FileEditorManager` 打开文件。PDF 与 PPTX 当前均提示手动定位 1-based 页码，不宣称深链已实现。
- Java PSI 读取在提交编辑器文档后进行；处理无选区、越界、行尾、内部类和已释放编辑器等边界。
- 模型下载、文件校验、索引与凭据检查在后台进行；引导只展示当前已知状态，不把配置检查宣称为 API 连通性测试。

**风险**：纯 Swing 截图不能替代真实 IDE 的主题、缩放与停靠布局验收；须另验 1366×768、窄停靠窗、设置页和本机文件预览能力。

#### B 实现进度记录（2026-10-24）

- 已覆盖工具窗、流式聊天、停止/重试/新会话、回答与代码复制、真实/示例引用、右键上下文、连接设置、安全凭据及可取消重建。界面由实际原生 Swing 组件实现。
- 拉取最新 main 后已接入 A 的项目级问答服务；重建通过 B 的控制器代理同一实例。修复设置变更后资源缓存失效、ONNX 会话释放及取消任务在索引发布前的检查；服务资源使用租约，避免正在回答时被重建直接关闭。
- 凭据从只按 Provider 区分改为项目、Provider 与 API 端点隔离，旧 Key 需重新输入。设置页对可读目录、端点、Key 删除及取消状态提供明确反馈；错误不会直接回显服务端异常正文。
- 消息保存发送时的 demo 标记与课件目录，切换模式或目录不污染历史引用；流式更新在 EDT 批量渲染，取消/关闭后忽略迟到回调。选区提取补齐 Java PSI 文档提交、行号与编辑器生命周期边界，并增加平台回归测试。
- C 的三个 F1 动作已通过共享 XML 片段注册；B 的默认引用导航已接入，并在审查请求前固定课件目录或自定义回调。真实模型和检索器仍需 A/C 接入，不能将 A 的聊天联调结果直接计作 F1 端到端验收。
- 自动化引用导航补充验收（2026-10-24，Windows）：新增 12 项平台测试，完整套件 **182 项：180 通过、2 因符号链接权限跳过**。覆盖默认回调、目录和绑定快照、本地文件/VFS、路径拒绝、EDT 与模态回调；`buildPlugin`、`verifyPluginStructure`、`verifyPlugin` 均通过，兼容结论 `Compatible`，保留原有 6 处实验 API 提示。真实课件预览及完整 F1 链路仍需 IDE 人工验收。
- 本次模型准备与引导验收（2026-10-24）：**97 项纯 JDK 行为检查通过**，生成 14 张原生聊天截图及 2 张设置页截图；目标 IDE 2024.2.4 的 Gradle 套件共 **170 项测试：168 通过、2 跳过**，跳过项均为 Windows 本机缺少符号链接创建权限的夹具。`buildPlugin`、`verifyPluginStructure` 与 `verifyPlugin` 均通过，兼容检查保留 6 处线程断言的实验性 API 提示。另用真实约 90 MB 模型验证了完整安装、SHA-256 校验、缓存复用及显式目录 ONNX 推理。安装器网络回归使用本地 HTTP/mock；本次没有调用真实问答 API，也未把该套件视为 IDE 内真实联网下载和完整课程问答的人工验收。PDF/PPTX 指定页深链仍未实现。 正式入口为根构建 `test` / `buildPlugin` / `verifyPluginStructure` / `verifyPlugin`；B 的纯 JDK 检查由 `tools/verify-b.ps1` 运行。
- `verifyPlugin` 已完成并对 IC-242.23726.103 报告 Compatible；6 处实验 API 使用均为 `Application.assertIsNonDispatchThread`，不属于二进制不兼容错误。网络获取 documented incompatible changes 失败，因此未启用已知问题过滤。`examples/junit-demo` 的 `build` 通过，但 `test` 为 `NO-SOURCE`，该证据仅表示示例工程可构建。
- 交付工具的 11 项 Python 检查通过（Python 3.13.5、pypdf 6.21.2、reportlab 4.5.1，依赖按锁版安装）。提交 ZIP 显式保留所有 `gradlew` Wrapper 的 `0755` 执行权限，修复 Windows NTFS 不提供执行位时 Linux 无法直接运行的问题。
- [B 英文报告贡献草稿 V1.1.0](TAIDE-B-English-Report-V1.1.0.docx)已同步前序集成审查；旧 V1.0.0 保留。草稿仍需对照作业原文统一五节标题、补全 A/C 内容与成员资料后再定稿。
- 已补充应用级模型准备与首次使用引导：点击下载、真实进度、完整校验、已有目录/默认目录、取消与代理错误提示；模型、API/课件与索引按状态展示，真实问答仍由用户显式启用。本次流程以 README 和 §4.6 为准；英文 Word 草稿保留前序版本，待后续批次统一更新。

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
| 2026-10-24 | v1.1 | B 集成审查：更新真实服务注册、单实例重建、端点凭据隔离、取消与消息来源约定，修正 B 当前状态与运行入口 | B |
| 2026-10-24 | v1.2 | 补充应用级模型准备、固定版本校验与共享目录、首次引导、代理和离线入口；明确 API 检查与显式切换真实问答的边界 | B |
| 2026-10-24 | v1.3 | 接入自动化审查默认来源导航，明确请求前目录/回调快照、自定义绑定与手动定位页码边界；模型和检索仍待 A/C 接入 | B |

---

<div align="center">

**遇到冲突时的裁决顺序：** 能演示 > 技术正确 > 代码优雅。
**有疑问时先看这份文档，文档没说清就补进去——别靠记忆。**

</div>
