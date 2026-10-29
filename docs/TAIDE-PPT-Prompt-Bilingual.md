# TAIDE 答辩 PPT 生成提示词（中英双语）

> 用途：把下面任一语言的提示词整段复制给 PPT 生成工具（Claude / ChatGPT / Gamma / 讯飞智文等），
> 得到可直接排版的逐页大纲。两份提示词各自自包含，不依赖对方。
>
> **使用前必做两件事**
> 1. 把 `[成员B姓名]` `[成员B学号]` `[成员C姓名]` `[成员C学号]` 替换成真实信息（仓库里目前仍是待填状态）。
> 2. 演示前补一张真实 IDE 聊天界面截图——`build/b-verification/screenshots/` 下目前只有 2 张设置页，
>    4 张智能动作预览图在 `docs/automation/images/`，**聊天工具窗本身没有可用截图**。

---

## 一、中文版提示词

```
# 角色

你是一位既懂 IntelliJ 平台插件工程、又常年担任课程项目答辩评审的技术演示设计师。
你的目标不是"做一份好看的 PPT"，而是让评审在 20 分钟内相信两件事：
这个插件真的跑通了；每一个技术选择都能说出为什么不选另一个。

# 项目背景

课程 CST3492《Java 企业级应用开发》，Group B，作业要求交付 F0（课程助教基础能力）+ F1（智能动作）两组功能。
评分权重：功能 35% · 技术设计 35% · 演示 10% · 代码 10% · 文档 10%。
**技术设计与功能等重**——这是本 PPT 的第一设计原则：不能把 70% 的篇幅用来截图展示功能。

# 事实白名单

以下数据来自项目仓库的实测记录与文档。**只允许使用这里出现的事实和数字。**

## A. 项目定位

- 名称 TAIDE（TA + IDE），IntelliJ IDEA 插件，把课程助教放进 IDE。
- 从本地 PDF/PPTX 课件检索相关片段 → 调用 DeepSeek（OpenAI 兼容协议）→ 生成带页码引用的回答。
- 点击引用可在 IDE 内置只读预览器中直接定位到 PDF 的第 N 页 / PPTX 的第 N 张幻灯片。

## B. 功能范围（七项，全部有代码实现）

| 编号 | 功能 | 实现要点 |
|:--|:--|:--|
| F0.1 | 课程 RAG 问答 | PDF/PPTX 摄取、切块、本地 ONNX 嵌入、JSON 索引、检索、SSE 流式回答 |
| F0.2 | 来源引用 | 文档名 + 1-based 页码 + 命中片段 + 真实检索分数；点击在预览器定位 |
| F0.3 | 原生聊天工具窗 | 流式输出、代码块复制、停止生成、重试、新会话、演示/真实双模式 |
| F0.4 | 选区上下文 | 右键 `Ask TAIDE about This Code`；无选区时置灰；Java 文件补充所属类名 |
| F1.1 | JUnit 5 测试生成 | 自然语言场景 → 完整测试类 → JavaParser 语法校验 → 可编辑预览 → 写入并支持撤销 |
| F1.2 | 提交信息生成 | 只取暂存区差异 → Conventional Commit → 可编辑预览 → 用户二次确认后才提交 |
| F1.3 | 课件感知代码审查 | 检索课件 → 结构化问题清单（严重度/说明/建议/引用）→ 引用可点开对应页 |

## C. 技术栈（精确版本，不可改写）

- JDK 21；Gradle Wrapper 8.13；IntelliJ Platform Gradle Plugin 2.5.0
- 目标平台 IntelliJ IDEA Community 2024.2.4（`IC-242.23726.103`），`since-build=242` / `until-build=242.*`，依赖 `com.intellij.java`
- 界面：原生 Swing / IntelliJ UI 组件（非 WebView、非 JCEF）
- HTTP 与 JSON：JDK `java.net.http` + Gson 2.12.1
- 课件解析：PDFBox 3.0.5（PDF 逐页）、POI XSLF 5.4.1（PPTX 逐片，含备注）
- 本地嵌入：ONNX Runtime 1.20.0 + `all-MiniLM-L6-v2`，384 维，均值池化 + L2 归一化
- 索引：内存余弦检索 + JSON 原子落盘
- 自动化：JavaParser 3.26.3（生成代码校验）、JGit 7.1.0（暂存区差异）、JUnit 5.11.4
- 凭据：IntelliJ PasswordSafe，按「项目 + Provider + API 端点」三重隔离，不落配置文件
- 模型服务：**DeepSeek**，`https://api.deepseek.com` + `deepseek-chat`，自备 API Key；
  走 OpenAI 兼容 `/chat/completions` + SSE，30 s 超时、失败重试 2 次

## D. RAG 参数与实测数字

- 切块：目标 512 token、重叠 64 token、**绝不跨页**，chunk id 格式 `文档#页码#序号`
- 页码 1-based，与 PDF 阅读器显示一致（这是引用能不能用的前提）
- 检索链路：候选 24 条 → 相似度阈值 0.45 过滤 → MMR 去重（λ=0.7）→ 取 top-8
- 语料实测：9 份讲义、199 页 → 199 chunks（讲义每页文本稀疏，一页一块）
- 建索引耗时：全量 **4.0 s**；命中内容哈希缓存后重建 **1.1 s**（199 块全部复用，0 次重新嵌入）
- 检索评测：20 题评测集（15 题课件内有答案、5 题课件内无答案）

**调参实验（本 PPT 最有说服力的一页）：**

| 相似度阈值 | top-3 命中 | 空结果判定 | 问题 |
|:--|:--|:--|:--|
| 0.35（初值） | 15/15 = 100% | 4/5 = 80%（压线） | 一个课件外问题被误判为命中，最高假命中分 0.410 |
| **0.45（终值）** | **15/15 = 100%** | **5/5 = 100%** | 最弱真命中 0.481 与最强假命中 0.410 之间留出余量 |

## E. 验证证据（必须分层陈述，不能混为一谈）

| 层级 | 结果 |
|:--|:--|
| 单元 / 平台测试 | 仓库现有 42 个测试类；历史记录：完整套件 182 项（180 通过、2 项因 Windows 缺少符号链接权限跳过）；另一批次 170 项（168 通过、2 跳过） |
| 纯 JDK 界面行为检查 | 97 项通过，渲染出 14 张聊天界面截图 + 2 张设置页截图 |
| 真实模型 live 测试 | 4 个用例全过；生成的 JUnit 测试类**编译并成功运行 4 个测试方法** |
| 提示词反例测试 | 3 组诱导编造测试全部通过（真实 DeepSeek）：拒绝编造引用、纠正假前提、页码全部属于检索集 |
| 交付工具（Python） | 11 项检查通过 |
| CI | GitHub Actions run `40275125811`：`build` 与 `compatibility` 两个作业均成功；安装包、测试报告、兼容报告分别上传为 artifact |
| 插件二进制兼容性 | IntelliJ Plugin Verifier 对 `IC-242.23726.103` 报 `Compatible`，保留 6 处实验 API 提示（均为 `assertIsNonDispatchThread` 线程断言） |
| **人工端到端验收** | **六项 Windows IDEA 场景全部执行并通过**（E2E-01 至 E2E-06）：配置与课件问答、F1.1 带索引生成、F1.1 无索引独立模型访问、F1.2 仅暂存区提交、F1.3 课件审查与引用、F1.3 来源页导航 |

## F. 三个工程机制（技术设计的核心卖点，必须展开讲）

1. **引用防伪造**：检索到的片段先分配临时编号 `S1`…`Sn`，模型只能返回编号，不得返回文档名和页码；
   解析器逐个校验编号是否属于本次检索集，文档名/页码/片段/分数一律从原始 `RetrievedChunk` 回填。
   编造或缺失引用 → 直接校验失败，不给用户看。
2. **Git 暂存快照指纹**：`SHA-256(HEAD 对象 ID + 每个索引项的路径/暂存阶段/文件模式/blob ID)`，用 NUL 分隔。
   提交前重新计算指纹，与用户批准时的不一致就拒绝提交。包含 blob ID 是为了让「二进制文件更新」也能被检测到。
   patch 上限 180,000 字节、变更文件上限 500 个，超限提示拆分提交。
3. **资源租约**：聊天与 F1.3 共享同一套模型 + 检索器 + 索引。重建索引时，正在使用旧资源的请求持租约继续跑，
   新请求拿到新资源，旧资源等租约释放后才关闭——避免「重建索引把正在回答的会话打断」。

## G. 线程模型（答辩高频追问点）

- 所有模型调用、检索、索引构建、Git 操作都在后台任务线程，**绝不在 EDT**
- 流式 token 不逐条刷新，在 EDT 批量渲染；取消或关闭面板后丢弃迟到的回调
- 结果展示与用户确认在 EDT
- 错误提示不直接回显服务端异常正文（可能含凭据）

## H. 验收状态与残余限制（**如实呈现，既不夸大也不说软**）

- 六项 Windows IDEA 人工验收**已全部执行并通过**——这是全项目最高层级的证据，必须正面讲，不要谦虚带过
- 模型接入使用**自备 DeepSeek API Key**（`https://api.deepseek.com` / `deepseek-chat`），不依赖学校平台；
  客户端走 OpenAI 兼容协议，换 provider 只是改 baseUrl
- 验收细节（执行人、日期、构建 commit、安装包 SHA-256、截图）记录在 `docs/e2e/E2E_RESULTS.md` 台账
- 课件摄取不递归扫描子目录，不对扫描版 PDF 做 OCR
- PPTX 预览不含幻灯片动画，复杂图形效果受本机字体与渲染库限制
- 成员 B、C 的姓名学号在仓库中仍为待填

## I. 团队分工

| 代号 | 姓名 | 角色 | 负责 | 工作量 |
|:--|:--|:--|:--|:--:|
| A | 刘鸿铭（2453608） | RAG & LLM Core Lead | 摄取、切块、嵌入、索引、检索、提示词、`TaideService` | 35% |
| B | [成员B姓名]（[成员B学号]） | UI/UX & Context Lead | 工具窗、聊天、设置、右键上下文、`plugin.xml`、README | 30% |
| C | [成员C姓名]（[成员C学号]） | Automation & Release Lead | F1 三动作、Git 集成、构建、CI、打包 | 35% |

协作机制：**按纵向功能切片分工，不按前后端分层**（按层分会互相等接口，三人同时空转）；
接口契约 10-22 冻结后各自对着空实现写；`plugin.xml` 由 B 独占维护，是唯一的物理冲突点。

# 陈述红线

第 1 条是**必须讲对**的（这是本项目最强的证据，别自己说软）；第 2–4 条是**绝对不能讲**的。

1. 「六项 Windows IDEA 端到端验收已全部执行并通过」——这是真实状态。第 15、16 页都要落在这上面，
   不要用「基本通过」「大部分跑通」这类含糊说法削弱它。
2. 不要说「接入同济 AGI 双后端」或任何「双后端已打通」——本项目**没有使用学校平台**，
   用的是**自备 DeepSeek API Key**。为了显得功能多而编一个没用过的后端，是最蠢的失分方式。
   被问「为什么只接一家」时的答案：需求只要求兼容 OpenAI 协议，换后端只是改 baseUrl；
   精力花在把链路做扎实，而不是堆接入数量。
3. 把 182 / 170 / 97 / 11 这些数字说成「当前 main 分支的测试统计」——它们是**历史组件验证记录**，
   必须标注「历史记录，不代表当前分支统计」。
4. 提 **LangChain4j** 或 **Apache Tika** 作为本项目技术栈——最终实现**没有使用**这两个库，
   实际是手写 OpenAI 兼容 HTTP 客户端（JDK HttpClient + SSE）+ PDFBox/POI 直接解析。
   如果被问到「为什么不用 LangChain4j」，答案是：需求只有「一次检索 + 一次补全 + 一条流」，
   引入框架会带来传递依赖和版本耦合，而手写客户端约 200 行就覆盖了超时、重试、SSE 和错误映射。

# 页面规划（18 页）

时间预算：总 20 分钟。开场 1 分钟 + 现场演示 5 分钟 + 技术讲述 12 分钟 + 收尾 2 分钟。
Q&A 单独计时，不占这 20 分钟。

| 页 | 标题方向 | 屏幕上必须出现的内容 | 讲述时长 |
|:--:|:--|:--|:--:|
| 1 | 封面 | TAIDE 全称、一句话定位、课程/组别/三名成员姓名学号、日期 | 20s |
| 2 | 一句话价值 + 三个数字 | 「课程助教住进 IDE」；三个数字：7 项功能、199 个课件分块、4.0 s 建索引 | 40s |
| 3 | 问题定义 | 三个具体痛点：切窗口找课件、通用 AI 答得不贴课程、回答无法溯源到第几页 | 50s |
| 4 | 范围与验收口径 | F0.1–F1.3 七项表格，每项一行，标注实现状态 | 60s |
| 5 | 现场演示（或录屏） | 4 步脚本：课件问答带引用 → 点引用跳到 PDF 第 N 页 → 选区生成 JUnit → 课件审查 | 5min |
| 6 | 架构总览 | 一张矢量图：UI 层 / 服务层 / RAG 层 / 外部模型；标出三个接缝（TaideService、AutomationRuntimeAccess、CourseSourceNavigator） | 90s |
| 7 | RAG 管道六阶段 | 摄取 → 切块 → 嵌入 → 索引 → 检索 → 生成，每阶段标一个关键参数 | 90s |
| 8 | **调参实验（核心数据页）** | 0.35 vs 0.45 的对比表 + 一句话结论：阈值不是拍的，是用假命中分数卡出来的 | 90s |
| 9 | 引用完整性 | `S1..Sn` 临时编号 + 回填机制；一句话：模型没有能力编造一个不存在的页码 | 80s |
| 10 | F1.3 最难的一个 prompt | 输入 = 代码 + 检索片段 + 审查维度清单；输出 = JSON；无命中时明确降级为通用审查 | 70s |
| 11 | F1.1 / F1.2 的安全设计 | 先预览再落盘（写错文件是演示事故头号来源）；暂存快照指纹防止「批准后又被改动」 | 70s |
| 12 | 三个工程机制 | 引用防伪造 / 快照指纹 / 资源租约，每个一句话 + 一张小图 | 80s |
| 13 | 技术选型论证 | 表格：PDFBox vs Tika、POI vs Tika、手写客户端 vs LangChain4j、JSON 索引 vs 向量库、JGit vs Git4Idea——每行写「选了什么/放弃了什么/为什么」 | 90s |
| 14 | 协作工程 | 纵向切片分工的理由 + 接口冻结 + 文件所有权矩阵 + `plugin.xml` 单点维护 | 60s |
| 15 | 验证证据分层 | 六层证据（单元/平台/界面/真实模型/CI/人工 E2E），每层写实际数字；人工 E2E 置于最上层并标明六项全过 | 80s |
| 16 | **验收结果与残余限制** | 一张 PASS 表格，一行一项，不铺截图（细节留给现场演示）；残余限制：无 OCR、不递归子目录、PPTX 无动画 | 45s |
| 17 | 后续路线 | 三件事：多轮对话与增量索引、扫描版 PDF 的 OCR、更大语料上的检索评测 | 40s |
| 18 | Q&A（内部页，不投屏） | 8 个必被问的问题 + 每题 3 行答题要点 | — |

# 视觉规范

- 浅色背景 + 深色文字；主色用靛蓝/紫（`#6366F1` → `#8B5CF6` 渐变），与项目图标一致
- 参数、代码、命令一律用等宽字体，并加浅灰底
- 每页只讲一个论点；正文不超过 6 行、每行不超过 20 个汉字
- 有数字的页必须有**对比**（改前 vs 改后、选 vs 弃）；孤立数字不算证据
- 架构图、管道图必须**矢量重绘**，不要贴 IDE 截图
- 截图类页面统一加细边框 + 圆角，避免深浅主题混排
- 禁止使用：彩虹渐变、立体阴影、艺术字、项目符号堆砌

# 输出格式

先输出一页「目录总表」（页码 / 中英文标题 / 该页论点一句话），然后逐页输出：

```
【第 N 页】中文标题 / English Title
屏幕文字：
  - 要点 1（≤20 字）
  - 要点 2
  - 要点 3
视觉元素：图/表/截图的具体内容与布局（左文右图 / 上表下图）
演讲备注：（50–90 字，口语化，第一人称，说清"为什么"而不是"是什么"）
事实来源：白名单第 X 节
```

最后单独输出「Q&A 预案」，8 个问题，每题 3 行答题要点。必须覆盖：

1. 为什么不用 LangChain4j / Spring AI？
2. 为什么不用向量数据库（FAISS / Milvus / pgvector）？
3. 199 个分块是不是太少，检索质量可信吗？
4. 0.45 这个阈值是不是对 20 题评测集过拟合？
5. 引用会不会是模型编的？
6. 本地 embedding 模型 90 MB 值不值？
7. 六项 E2E 是你们自己跑的，怎么证明不是走形式？
8. 三个人怎么保证不冲突？
9. 为什么用 DeepSeek，不用 GPT-4 / Claude？

# 语言要求

中文版 PPT：正文简体中文，专有名词、类名、参数保留英文原形（如 `TaideService`、`MMR λ=0.7`）。
讲稿用口语，不用书面语；不写「我们深入研究了」这类空话，直接给结论和依据。
```

---

## 二、English Prompt

```
# Role

You are a technical presentation designer who understands IntelliJ Platform plugin engineering
and has sat on the grading side of university project defences many times.
Your goal is not "a good-looking deck". It is to make the panel believe two things within 20 minutes:
the plugin actually works, and every technical choice can be defended against the alternative.

# Project Context

Course CST3492 Java Enterprise Application Development, Group B.
Deliverable scope: F0 (course assistant baseline) plus F1 (smart actions).
Marking weights: Functionality 35% · Technical Design 35% · Presentation 10% · Code 10% · Documentation 10%.
**Technical design carries the same weight as functionality.** That is the first design rule of this deck:
do not spend 70% of the slides showing screenshots of features.

# Fact Whitelist

The following figures come from the project repository's measured records and documentation.
**Use only the facts and numbers that appear here.**

## A. Positioning

- Name: TAIDE (TA + IDE), an IntelliJ IDEA plugin that puts a course teaching assistant inside the IDE.
- Pipeline: retrieve relevant chunks from local PDF/PPTX lecture material → call DeepSeek (OpenAI-compatible
  protocol) → produce an answer with page-level citations.
- Clicking a citation opens an in-IDE read-only preview positioned at the exact 1-based PDF page or PPTX slide.

## B. Scope (seven features, all implemented in code)

| ID | Feature | Implementation highlights |
|:--|:--|:--|
| F0.1 | Course RAG Q&A | PDF/PPTX ingestion, chunking, local ONNX embedding, JSON index, retrieval, SSE streaming |
| F0.2 | Source citations | Document name + 1-based page + matched excerpt + real retrieval score; click to jump in preview |
| F0.3 | Native chat tool window | Streaming, code copy, stop, retry, new session, demo/real dual mode |
| F0.4 | Selection context | Right-click `Ask TAIDE about This Code`; greyed out without a selection; enclosing class for Java files |
| F1.1 | JUnit 5 test generation | Natural-language scenario → full test class → JavaParser validation → editable preview → undoable write |
| F1.2 | Commit message generation | Staged diff only → Conventional Commit → editable preview → commit only after explicit second confirmation |
| F1.3 | Course-aware code review | Retrieve lecture chunks → structured findings (severity / explanation / suggestion / citation) → citations open the exact page |

## C. Technology Stack (exact versions, do not alter)

- JDK 21; Gradle Wrapper 8.13; IntelliJ Platform Gradle Plugin 2.5.0
- Target: IntelliJ IDEA Community 2024.2.4 (`IC-242.23726.103`), `since-build=242` / `until-build=242.*`, depends on `com.intellij.java`
- UI: native Swing / IntelliJ UI components (no WebView, no JCEF)
- HTTP and JSON: JDK `java.net.http` + Gson 2.12.1
- Material parsing: PDFBox 3.0.5 (PDF, page by page), POI XSLF 5.4.1 (PPTX, slide by slide, including notes)
- Local embeddings: ONNX Runtime 1.20.0 + `all-MiniLM-L6-v2`, 384 dimensions, mean pooling + L2 normalisation
- Index: in-memory cosine search + atomic JSON persistence
- Automation: JavaParser 3.26.3 (generated-code validation), JGit 7.1.0 (staged diff), JUnit 5.11.4
- Credentials: IntelliJ PasswordSafe, isolated by project + provider + API endpoint, never written to config files
- Model service: **DeepSeek**, `https://api.deepseek.com` with `deepseek-chat`, team's own API key;
  OpenAI-compatible `/chat/completions` + SSE, 30 s timeout, 2 retries

## D. RAG Parameters and Measured Numbers

- Chunking: target 512 tokens, 64-token overlap, **never across pages**, chunk id format `document#page#index`
- Page numbers are 1-based, matching what a PDF reader displays — the precondition for usable citations
- Retrieval chain: 24 candidates → similarity threshold 0.45 → MMR dedup (λ=0.7) → top-8
- Measured corpus: 9 lecture decks, 199 pages → 199 chunks (pages are text-sparse, roughly one chunk per page)
- Index build: full build **4.0 s**; content-hash cached rebuild **1.1 s** (all 199 reused, zero re-embedding)
- Retrieval eval: 20-question set (15 answerable from the material, 5 deliberately unanswerable)

**The threshold-tuning experiment (the most persuasive slide in the deck):**

| Similarity threshold | top-3 hit rate | Empty-result accuracy | Observation |
|:--|:--|:--|:--|
| 0.35 (initial) | 15/15 = 100% | 4/5 = 80% (just passing) | One out-of-scope question falsely matched, highest false score 0.410 |
| **0.45 (final)** | **15/15 = 100%** | **5/5 = 100%** | Weakest true match 0.481 leaves a margin above the 0.410 false match |

## E. Verification Evidence (must be presented in layers, never conflated)

| Layer | Result |
|:--|:--|
| Unit / platform tests | 42 test classes in the repository; historical record: full suite of 182 (180 passed, 2 skipped due to missing Windows symlink permission); another batch of 170 (168 passed, 2 skipped) |
| Pure-JDK UI behaviour checks | 97 passed, rendering 14 chat screenshots + 2 settings-page screenshots |
| Live-model tests | All 4 cases passed; the generated JUnit class **compiled and ran 4 test methods successfully** |
| Adversarial prompt tests | 3 fabrication-inducement cases passed against real DeepSeek: refused to invent citations, corrected a false premise, all cited page numbers belonged to the retrieved set |
| Delivery tooling (Python) | 11 checks passed |
| CI | GitHub Actions run `40275125811`: both `build` and `compatibility` jobs succeeded; installable ZIP, test reports and compatibility report uploaded as separate artifacts |
| Plugin binary compatibility | IntelliJ Plugin Verifier reports `Compatible` for `IC-242.23726.103`, with 6 experimental-API notices (all `assertIsNonDispatchThread` thread assertions) |
| **Manual end-to-end acceptance** | **All six Windows IDEA scenarios executed and passed** (E2E-01 to E2E-06): configuration and course Q&A, F1.1 generation with an index, F1.1 independent model access without an index, F1.2 staged-only commit, F1.3 course-aware review with citations, F1.3 source-page navigation |

## F. Three Engineering Mechanisms (the core of the technical-design score — expand on these)

1. **Citation anti-fabrication.** Retrieved chunks are assigned temporary identifiers `S1…Sn`. The model may only
   return identifiers — never document names or page numbers. The parser validates every identifier against the
   retrieved set, and all displayed metadata (document, page, excerpt, score) is back-filled from the original
   `RetrievedChunk`. An invented or missing reference fails validation outright and is never shown to the user.
2. **Git staging snapshot fingerprint.** `SHA-256(HEAD object id + path / stage / file mode / blob id of every index entry)`,
   NUL-separated. The fingerprint is recomputed immediately before the commit; any mismatch against the snapshot the
   user approved aborts the commit. Blob ids are included so that binary-only updates are still detected.
   Patch size is capped at 180,000 bytes and 500 changed files, with a split-commit prompt when either bound is hit.
3. **Resource leases.** Chat and F1.3 share one model, retriever and index. During a reindex, in-flight requests hold a
   lease and keep using the old resources; new requests get the new ones; old resources close only after their leases
   are released. This is what prevents "rebuilding the index kills the conversation in progress".

## G. Threading Model (a frequent defence question)

- All model calls, retrieval, index builds and Git operations run on background task threads, **never on the EDT**
- Streaming tokens are batched and rendered on the EDT; callbacks arriving after cancellation or panel disposal are dropped
- Result presentation and user approval happen on the EDT
- Error messages never echo raw server exception bodies (they may contain credentials)

## H. Acceptance Status and Residual Limitations (**state these plainly — neither inflated nor understated**)

- All six Windows IDEA manual acceptance scenarios **have been executed and passed**. This is the strongest
  evidence in the project — present it head-on, do not understate it
- The model backend is **DeepSeek** (`https://api.deepseek.com`, `deepseek-chat`) using the team's own API key,
  not the university platform; switching providers is a base-URL change
- Execution details (tester, date, build commit, installer SHA-256, screenshots) live in the
  `docs/e2e/E2E_RESULTS.md` ledger
- Course ingestion does not recurse into subdirectories and performs no OCR on scanned PDFs
- PPTX preview has no slide animations; complex graphics depend on local fonts and the rendering library
- Members B and C's names and matriculation numbers are still placeholders in the repository

## I. Team

| Code | Name | Role | Owns | Effort |
|:--|:--|:--|:--|:--:|
| A | Liu Hongming (2453608) | RAG & LLM Core Lead | Ingestion, chunking, embedding, index, retrieval, prompts, `TaideService` | 35% |
| B | [Member B name] ([Member B ID]) | UI/UX & Context Lead | Tool window, chat, settings, right-click context, `plugin.xml`, README | 30% |
| C | [Member C name] ([Member C ID]) | Automation & Release Lead | Three F1 actions, Git integration, build, CI, packaging | 35% |

Collaboration model: **vertical feature slices, not front-end/back-end layers** (layer-based splits leave everyone
blocked on everyone else's interfaces). The interface contract was frozen on 22 October, after which each member coded
against empty implementations. `plugin.xml` is owned exclusively by B and is the single physical conflict point.

# Statements to Get Right and Statements to Avoid

Statement 1 is true and must be delivered firmly. Statements 2-4 would be caught by the panel.

1. "All six Windows end-to-end scenarios have been executed and passed." This is the actual status.
   Slides 15 and 16 must land on it. Do not soften it into "mostly working" or "largely passing".
2. Never claim a "dual backend with Tongji AGI" or anything implying the university platform is integrated.
   The project uses DeepSeek with the team's own API key. Inventing an unused backend to look
   more complete is the cheapest way to lose marks. If asked "why only one provider?", the answer is: the requirement
   is OpenAI-protocol compatibility, so switching is a base-URL change; the effort went into making the pipeline
   solid rather than stacking provider integrations.
3. Presenting 182 / 170 / 97 / 11 as "the current test statistics on main". They are **historical component
   verification records** and must be labelled as such.
4. Naming **LangChain4j** or **Apache Tika** as part of this project's stack. Neither is used. The implementation is a
   hand-written OpenAI-compatible HTTP client (JDK HttpClient + SSE) plus direct PDFBox/POI parsing.
   If asked "why not LangChain4j?", the answer is: the requirement is one retrieval, one completion and one stream;
   a framework would add transitive dependencies and version coupling, while roughly 200 lines of client code already
   cover timeouts, retries, SSE and error mapping.

# Slide Plan (18 slides)

Time budget: 20 minutes total. 1 min opening + 5 min live demo + 12 min technical narrative + 2 min closing.
Q&A is timed separately.

| # | Slide | Must appear on screen | Time |
|:--:|:--|:--|:--:|
| 1 | Title | Full name, one-line positioning, course/group/three members with IDs, date | 20s |
| 2 | Value proposition + three numbers | "A course TA that lives in the IDE"; 7 features, 199 course chunks, 4.0 s index build | 40s |
| 3 | Problem statement | Three concrete pains: window-switching to find slides, generic AI that ignores the course, answers that cannot be traced to a page | 50s |
| 4 | Scope and acceptance criteria | F0.1–F1.3 table, one row each, with implementation status | 60s |
| 5 | Live demo (or recording) | 4-step script: course Q&A with citation → click citation, jump to PDF page → selection to JUnit → course-aware review | 5 min |
| 6 | Architecture overview | One vector diagram: UI / service / RAG / external model; mark the three seams (`TaideService`, `AutomationRuntimeAccess`, `CourseSourceNavigator`) | 90s |
| 7 | Six-stage RAG pipeline | Ingest → chunk → embed → index → retrieve → generate, one key parameter per stage | 90s |
| 8 | **Threshold experiment (core data slide)** | The 0.35 vs 0.45 comparison table + one-line conclusion: the threshold was derived from the false-match score, not guessed | 90s |
| 9 | Citation integrity | `S1..Sn` identifier scheme and back-filling; the model has no way to invent a page number | 80s |
| 10 | F1.3: the hardest prompt | Input = code + retrieved excerpts + review-dimension checklist; output = JSON; explicit degradation to general-knowledge review on zero hits | 70s |
| 11 | F1.1 / F1.2 safety design | Preview before write (writing the wrong file is the number-one demo disaster); snapshot fingerprint prevents post-approval edits | 70s |
| 12 | Three engineering mechanisms | Anti-fabrication / snapshot fingerprint / resource leases — one sentence and one small diagram each | 80s |
| 13 | Technology selection defence | Table: PDFBox vs Tika, POI vs Tika, hand-written client vs LangChain4j, JSON index vs vector DB, JGit vs Git4Idea — each row states what was chosen, what was rejected, and why | 90s |
| 14 | Engineering collaboration | Why vertical slices, contract freezing, file ownership matrix, single-owner `plugin.xml` | 60s |
| 15 | Layered verification evidence | Six layers (unit / platform / UI / live model / CI / manual E2E) with real numbers; manual E2E on top, all six passed | 80s |
| 16 | **Acceptance results and residual limits** | A single PASS table, one row per scenario — no screenshots here, the detail belongs to the live demo; residual limits: no OCR, no subdirectory recursion, no PPTX animations | 45s |
| 17 | Roadmap | Three items: multi-turn dialogue and incremental indexing, OCR for scanned PDFs, retrieval evaluation on a larger corpus | 40s |
| 18 | Q&A (internal, not projected) | Eight likely questions with three-line answers each | — |

# Visual Specification

- Light background, dark text; accent gradient indigo to violet (`#6366F1` → `#8B5CF6`) matching the plugin icon
- Parameters, code and commands in a monospace font on a light grey block
- One argument per slide; body text no more than 6 lines, no more than 12 words per line
- Any slide with numbers must show a **comparison** (before vs after, chosen vs rejected); a lone number is not evidence
- Architecture and pipeline diagrams must be **redrawn as vectors**, never a pasted IDE screenshot
- Screenshots get a thin border and rounded corners; do not mix light and dark theme captures
- Forbidden: rainbow gradients, drop shadows, word art, bullet-point walls

# Output Format

First output a contents table (slide number / bilingual title / one-sentence argument). Then output each slide:

```
[Slide N] English Title / 中文标题
On-screen text:
  - point 1 (<= 12 words)
  - point 2
  - point 3
Visuals: exact figure/table/screenshot content and layout (text-left image-right, etc.)
Speaker notes: (40-70 words, spoken register, first person, explain "why" not "what")
Source: Fact Whitelist section X
```

Finish with a separate Q&A brief: eight questions, three bullet answers each. Must cover:

1. Why not LangChain4j or Spring AI?
2. Why not a vector database (FAISS / Milvus / pgvector)?
3. Only 199 chunks — is the retrieval quality credible?
4. Is 0.45 overfitted to a 20-question eval set?
5. Could the citations be hallucinated by the model?
6. Is a 90 MB local embedding model worth it?
7. You ran the six E2E scenarios yourselves — how do we know it was not a formality?
8. How do three people avoid stepping on each other?
9. Why DeepSeek rather than GPT-4 or Claude?

# Language Requirements

Deck text in English. Keep class names and parameters in their original form (`TaideService`, `MMR λ=0.7`).
Speaker notes in conversational English, first person. No filler such as "we deeply researched"; state the conclusion
and the evidence behind it.
```

---

## 三、两份提示词共用的补充说明

### 1. 现场演示脚本（对应第 5 页）

| 步骤 | 操作 | 屏幕要看到的结果 | 时长 |
|:--|:--|:--|:--:|
| 1 | 在 TAIDE 面板提问一个课件内有答案的问题 | 流式输出 + 引用卡片显示文档名与页码 | 90s |
| 2 | 点击引用卡片 | 内置预览标签打开，直接落在目标 PDF 页 | 40s |
| 3 | 提问一个课件外问题（如 CSS Flexbox） | 明确显示通用知识提示、引用为空 | 30s |
| 4 | 选中 `GreetingService` 生成 JUnit → 保存 → 运行 | 测试类写入 `src/test/java`，JUnit 全绿 | 90s |
| 5 | 选中一段 JDBC 代码做课件审查 → 点引用 | 结构化问题清单 + 引用跳转到课件页 | 70s |

> 演示前必须预热：模型已下载校验、索引已构建、API Key 已保存、IDE 已重启过一次。
> 断网兜底：提前录好全流程录屏，风险登记表里 R7 就是这个。

### 2. 答辩最容易被问倒的四个点，提前准备

1. **「199 个 chunk 太少，20 题评测集太小」** —— 诚实回答：语料本身就是 199 页稀疏讲义，chunk 数由语料决定；
   评测集 20 题是课程规模下的合理样本，且阈值调整依据是「假命中分数」而不是反复试凑，
   同时 live 门禁写进了测试（top-3 ≥ 70%、空判定 ≥ 80%），不是口头承诺。
2. **「引用会不会是模型编的」** —— 这题是送分题，直接把 `S1..Sn` 校验机制讲出来：
   模型只能返回编号，元数据全部回填，编造即校验失败。这是本项目的技术亮点，不是风险点。
3. **「E2E 是你们自己跑的，怎么证明不是走形式」** —— 这题从减分项变成了送分题。回答：
   六项场景都有明确的输入和可观察结果，不是「点开看看没崩」；台账记了构建 commit、安装包 SHA-256、
   执行人与截图；其中 F1.1 的生成测试是**编译并运行通过**、F1.2 验证了未暂存文件未被触碰、
   F1.3 验证了引用能落到正确的课件页。可复核，不是口头声明。
4. **「为什么用 DeepSeek 不用 GPT-4」** —— 按你们的真实理由答，可用的角度：自备 Key 成本可控、
   国内网络直连稳定（演示不必赌校园网）、OpenAI 兼容协议所以客户端零改动、课程场景不需要顶配模型。
   **不要答「效果更好」**——没有横向评测数据支撑，这种话一问就露。

### 3. 交付前检查

- [ ] 第 1 页三名成员的姓名与学号已填真实值（打包脚本会校验首页成员信息）
- [ ] 第 15 页的测试数字标注了「历史记录」
- [ ] 第 15、16 页的六项 E2E 结论与 `docs/e2e/E2E_RESULTS.md` 台账一致（台账已更新为全部 PASS）
- [ ] 全篇未出现 LangChain4j / Tika
- [ ] 架构图是矢量重绘，不是 IDE 截图
- [ ] 最终导出 PDF（作业要求 slides 为 PDF），并按 `scripts/package_submission.py` 打包
