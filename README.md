# GSMV 海洋生物多样性智能管理系统

GSMV（Global Species & Marine Vision）是一套面向海洋生物多样性档案、生态观测、RAG 知识库、AI 复核、科研报告和多 Agent 协作审计的全栈 Web 系统。系统以 Spring Boot、Vue 3、MySQL、Qdrant、本地 Embedding 与外部大模型服务为基础，将传统业务数据管理、智能检索增强生成、图像识别、人工复核和可解释 AI 轨迹串成一个完整闭环。

当前版本：`v1.7.1`

## v1.7 版本重点

- 新增 **Agent 协作台**：集中查看 AI 助手、识图复核、观测质检、科研报告、知识库治理等任务的协作轨迹。
- 新增 Agent 轨迹落库：`ai_agent_run` 保存一次协作任务，`ai_agent_step` 保存每个 Agent 步骤的输入、输出、证据、置信度、耗时和错误信息。
- 新增 Agent Replay：可回放执行链路，并检查最终输出、Verifier 步骤、证据数量、步骤顺序和验证状态一致性。
- 新增知识库治理协作流：支持手动发起，也支持定时扫描失败文档、空分块文档和疑似重复标题。
- AI 助手、识图复核、观测质检、科研报告等 AI 功能接入统一 Agent 编排，输出更容易解释和复核。
- 用户权限模块升级为角色路由权限管理，支持新增角色、配置权限码、用户分配角色，并在前端菜单和后端接口同时生效。
- 前端构建增加 Agent 轨迹状态覆盖检查：`frontend/scripts/check-agent-trace-states.mjs`。

## 核心能力

| 模块 | 能力说明 |
| --- | --- |
| 用户与权限 | 登录、注册申请、管理员审核、用户启停、角色分配、角色权限配置、个人资料、审计日志 |
| 物种档案 | 物种增删改查、分类阶元、保护等级、IUCN 状态、图片视频、参考文献、归档管理 |
| 生态系统 | 近海、珊瑚礁、红树林、海草床等生态系统档案维护 |
| 观测记录 | 观测时间、地点、坐标、生态系统、观测人员、环境参数、物种数量和行为记录 |
| 生态地图 | 基于 Leaflet 展示观测点和生态系统点位，支持多种底图策略 |
| 统计报表 | 物种分布、观测活动、分类占比、保护等级、生态系统统计、Excel/PDF 导出 |
| AI 助手 | DeepSeek 问答、RAG 检索增强、SSE 流式输出、用户级对话历史、证据溯源 |
| AI 图像识别 | 调用阿里云百炼视觉模型识别海洋生物图片，并结合 RAG 证据校验结果 |
| AI 复核工单 | 保存低置信度识图结果、候选项、RAG 证据快照、Agent 轨迹和人工复核结论 |
| 物种智能补全 | 根据中文名、学名或描述补全分类、形态、习性、分布和保护状态 |
| 文本润色与翻译 | 对物种档案文本进行专业润色和多语种翻译 |
| 观测智能分析 | 根据时间、坐标、环境参数和关联物种生成标签、异常提示和质量建议 |
| AI 科研报告 | 基于系统统计和 RAG 证据生成科研简报，支持历史查看、PDF 导出和 Agent 轨迹 |
| RAG 知识中台 | 管理系统数据、上传文档、外部知识、向量化分块、Qdrant 状态和检索测试 |
| Agent 协作台 | 查看 Agent Run、Step、Replay、证据、置信度、验证结论和人工复核原因 |
| 数据版本回溯 | 物种档案和观测记录变更留痕，支持查看历史版本 |

## 技术栈

| 层级 | 技术 |
| --- | --- |
| 后端 | Spring Boot 4.0.5、Spring Security、Spring Actuator |
| 持久层 | MyBatis 4.0、Flyway |
| 数据库 | MySQL 8、utf8mb4、JSON 字段 |
| 向量库 | Qdrant，默认 collection 为 `gsmv_rag_chunks` |
| 向量模型 | Ollama `bge-m3`，默认 1024 维 |
| 前端 | Vue 3.5、TypeScript、Vite 8、Pinia、Vue Router |
| UI 与可视化 | Element Plus、Leaflet、ECharts |
| AI 服务 | DeepSeek Chat、阿里云百炼视觉模型、IUCN Red List API |
| 文档处理 | Apache PDFBox、Apache POI |
| 鉴权 | JWT、BCrypt、RBAC 权限码 |

## 环境要求

| 依赖 | 建议版本 |
| --- | --- |
| JDK | 17 或更高 |
| Node.js | 20 或更高 |
| npm | 10 或更高 |
| MySQL | 8.0 或更高 |
| Docker Desktop | 用于运行 Qdrant |
| Ollama | 用于本地 `bge-m3` 向量模型 |

## 快速启动

### 1. 创建数据库

```sql
CREATE DATABASE IF NOT EXISTS gsmv
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
```

默认连接配置位于 `src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/gsmv?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: 123456
```

后端启动时 Flyway 会自动执行 `src/main/resources/db/migration` 下的迁移脚本。

### 2. 准备本地向量模型

```powershell
ollama pull bge-m3
ollama serve
```

默认配置：

```text
Ollama 地址：http://localhost:11434
Embedding 模型：bge-m3
Embedding 维度：1024
```

### 3. 启动 Qdrant

一键启动脚本会尝试自动启动或创建 `gsmv-qdrant` 容器，也可以手动启动：

```powershell
docker run -d --name gsmv-qdrant -p 6333:6333 qdrant/qdrant
```

默认配置：

```text
Qdrant 地址：http://localhost:6333
Collection：gsmv_rag_chunks
距离算法：Cosine
向量维度：1024
```

### 4. 配置 AI Key

不要把真实 API Key 写入仓库文件。建议使用环境变量：

```powershell
setx BAILIAN_API_KEY "your-bailian-key"
setx DASHSCOPE_API_KEY "your-bailian-key"
setx DEEPSEEK_API_KEY "your-deepseek-key"
setx IUCN_API_TOKEN "your-iucn-token"
```

### 5. 一键启动

```powershell
.\start-gsmv.cmd
```

启动脚本会处理：

- 停止旧的 `8080` 和 `5173` 端口进程。
- 启动后端和前端。
- 在 Docker 可用时启动或创建 `gsmv-qdrant` 容器。
- 打开登录页面。
- 将运行日志写入 `.gsmv-runtime/logs`。

停止系统：

```powershell
.\stop-gsmv.cmd
```

默认访问地址：

```text
前端：http://localhost:5173
后端：http://localhost:8080
Qdrant：http://localhost:6333
```

默认管理员账号：

| 账号 | 密码 |
| --- | --- |
| `admin` | `123456` |

## 手动启动

后端：

```powershell
.\mvnw.cmd spring-boot:run
```

前端：

```powershell
cd frontend
npm install
npm run dev
```

前端构建：

```powershell
cd frontend
npm.cmd run build
```

后端测试：

```powershell
.\mvnw.cmd test
```

## Agent 协作台

Agent 协作台是 GSMV 的 AI 可解释性和复核入口。它不是并发启动多个 AI 任务，而是把一次智能任务拆成可审计的多步骤流水线：每个步骤记录输入、输出、证据、置信度、耗时和验证状态，最后由 `Verifier Agent` 给出整体验证结论。

### 当前 Agent 流

| 工作流 | 业务含义 | 执行链路 |
| --- | --- | --- |
| `ASSISTANT_CHAT` | 智能问答 | Coordinator -> System Data -> RAG Evidence -> Verifier |
| `SPECIES_IDENTIFY` | 识图复核 | Coordinator -> Vision Review -> Taxonomy -> RAG Evidence -> Verifier |
| `SPECIES_PROFILE_ASSIST` | 物种档案辅助补全、润色、翻译 | Coordinator -> RAG Evidence -> Taxonomy -> Verifier |
| `OBSERVATION_QA` | 观测记录质检 | Coordinator -> Observation QA -> Taxonomy -> System Data -> Verifier |
| `RESEARCH_REPORT` | 科研报告生成与分析 | Coordinator -> System Data -> RAG Evidence -> Report Analyst -> Verifier |
| `KNOWLEDGE_GOVERNANCE` | 知识库治理 | Coordinator -> RAG Evidence -> Taxonomy -> Verifier |

### 验证状态

| 状态 | 含义 |
| --- | --- |
| `VERIFIED` | 当前证据可以支撑结论 |
| `INSUFFICIENT_EVIDENCE` | 证据不足，需要补充系统数据或知识库资料 |
| `NEEDS_REVIEW` | 存在失败、冲突、弱证据或显式复核要求，需要人工复核 |

### 置信度逻辑

每个 Agent 步骤会产生局部置信度。工作流最终置信度优先采用最后一个 `Verifier Agent` 的置信度；如果 Verifier 没有给出置信度，才回退到所有步骤置信度的平均值。Verifier 会基于输入置信度、证据支撑情况、弱支持结论、未支持结论和复核发现项扣分，并对 `NEEDS_REVIEW`、`INSUFFICIENT_EVIDENCE` 设置上限。

### 轨迹存储

| 表 | 用途 |
| --- | --- |
| `ai_agent_run` | 一次 Agent 协作任务，记录工作流类型、业务对象、用户、提示词、最终输出、验证状态和总置信度 |
| `ai_agent_step` | 单个 Agent 步骤，记录步骤序号、Agent 名称、角色、输入、输出、证据、错误、置信度和耗时 |

AI 科研报告和 AI 复核工单通过 `agent_run_id` 关联到对应协作轨迹。

## RAG 知识中台

RAG 是系统智能模块的证据层，当前接入范围包括：

- 物种档案、观测记录、生态系统、AI 科研报告。
- 上传的 PDF、DOCX、TXT、MD 文档。
- 本地文件夹批量导入。
- OBIS、GBIF、WoRMS、IUCN 外部公开资料。
- 白名单网页文档。

知识进入 RAG 的流程：

```text
系统数据 / 上传文档 / 外部资料
-> 文本抽取
-> 文本分块
-> Ollama bge-m3 生成向量
-> MySQL 保存文档、分块、任务和元数据
-> Qdrant 保存向量点
-> AI 助手、识图、报告、补全、Agent 协作流召回证据
```

常用 Qdrant 检查命令：

```powershell
Invoke-RestMethod http://localhost:6333/collections/gsmv_rag_chunks | ConvertTo-Json -Depth 10
```

查看向量 payload：

```powershell
$body = @{ limit = 8; with_payload = $true; with_vector = $false } | ConvertTo-Json
Invoke-RestMethod -Method Post `
  -Uri http://localhost:6333/collections/gsmv_rag_chunks/points/scroll `
  -ContentType 'application/json' `
  -Body $body | ConvertTo-Json -Depth 20
```

## AI 功能闭环

### AI 助手

```text
前端发送问题
-> JWT 鉴权
-> 后端识别问题意图
-> 读取系统业务数据
-> RAG 检索证据
-> DeepSeek 结合上下文生成回答
-> Agent 协作流验证证据与结论
-> 保存当前用户对话历史
-> 前端展示回答、摘要、证据来源和协作轨迹
```

### AI 图像识别与复核

```text
上传海洋生物图片
-> 文件类型校验
-> 阿里云百炼视觉模型识别候选物种
-> 结合候选中文名/学名进行 RAG 检索
-> Taxonomy Agent 校验命名、系统档案和证据冲突
-> Verifier Agent 判断是否需要人工复核
-> 可创建 AI 复核工单
-> 复核人员提交确认、不匹配或无法确认结论
```

### 观测质检

```text
观测记录输入
-> Observation QA Agent 检查时间、地点、坐标、环境参数和字段完整性
-> Taxonomy Agent 校验关联物种和命名线索
-> System Data Agent 补充站内事实
-> Verifier Agent 输出可确认、证据不足或需要复核
```

### AI 科研报告

```text
报告主题和统计范围
-> System Data Agent 汇总系统统计、趋势和分布
-> RAG Evidence Agent 召回知识库证据
-> Report Analyst Agent 生成趋势、风险、行动项和证据映射
-> Verifier Agent 验证结论支撑度
-> 保存报告、PDF 导出、展示协作轨迹
```

## 外部知识采集

| 来源 | 用途 | 说明 |
| --- | --- | --- |
| IUCN | 保护等级、威胁、栖息地、种群趋势 | 需要 `IUCN_API_TOKEN` |
| WoRMS | 学名、分类阶元、AphiaID、有效名 | 中文名会尽量解析为学名后再查询 |
| GBIF | 全球出现记录摘要 | 用于分布和出现记录补充 |
| OBIS | 海洋观测出现记录摘要 | 用于海洋出现记录补充 |
| WEB_PDF | 白名单网页文档 | 支持 PDF、DOCX、TXT、MD |

外部采集结果会先转成可读文本块，再进入分块和向量化流程。

## 权限与路由

前端路由会根据当前用户的 `authorities` 隐藏不可访问菜单；后端接口继续通过 `@PreAuthorize` 校验同一套权限码。

| 路由 | 页面 | 主要权限 |
| --- | --- | --- |
| `/login` | 登录 | 公开 |
| `/register` | 注册 | 公开 |
| `/dashboard` | 仪表盘 | `REPORT_READ` |
| `/species` | 物种档案 | `SPECIES_READ` |
| `/species/:id` | 物种详情 | `SPECIES_READ` |
| `/ecosystems` | 生态系统 | `ECOSYSTEM_READ` |
| `/eco-map` | 生态地图 | `OBS_READ` |
| `/observations` | 观测记录 | `OBS_READ` |
| `/assistant` | AI 助手 | 登录用户 |
| `/agent-runs` | Agent 协作台 | 登录用户 |
| `/ai-reviews` | AI 复核 | `AI_REVIEW_READ` |
| `/ai-reports` | AI 科研报告 | `REPORT_READ` |
| `/rag-knowledge` | RAG 知识中台 | `RAG_READ` |
| `/rag-knowledge?document=ID` | 指定 RAG 文档详情 | `RAG_READ` |
| `/reports` | 统计报表 | `REPORT_READ` |
| `/audits` | 审计日志 | `AUDIT_READ` |
| `/users` | 用户与权限 | `USER_ADMIN` |
| `/profile` | 个人中心 | 登录用户 |

角色管理接口：

| 接口 | 用途 | 权限 |
| --- | --- | --- |
| `GET /api/v1/roles` | 角色列表及权限码 | `USER_ADMIN` |
| `GET /api/v1/roles/permissions` | 可配置权限列表 | `USER_ADMIN` |
| `POST /api/v1/roles` | 新增角色 | `USER_ADMIN` |
| `PUT /api/v1/roles/{id}` | 更新角色权限 | `USER_ADMIN` |
| `DELETE /api/v1/roles/{id}` | 删除非内置且未被使用的角色 | `USER_ADMIN` |

## 项目结构

```text
GSMV/
├─ src/main/java/com/gsmv/
│  ├─ ai/                 # AI 助手、识图、RAG、Agent、复核、科研报告
│  ├─ audit/              # 审计日志
│  ├─ auth/               # 登录、注册、JWT
│  ├─ bootstrap/          # 运行期结构补齐
│  ├─ common/             # 通用响应、异常、TraceId
│  ├─ config/             # 安全、CORS、属性配置
│  ├─ ecosystem/          # 生态系统
│  ├─ media/              # 文件上传
│  ├─ observation/        # 观测记录
│  ├─ report/             # 统计报表
│  ├─ security/           # 当前用户和安全工具
│  ├─ species/            # 物种档案
│  ├─ user/               # 用户、角色、权限
│  └─ versioning/         # 数据版本与回溯
├─ src/main/resources/db/migration/
├─ src/test/java/
├─ frontend/src/
├─ docs/
├─ scripts/
├─ start-gsmv.cmd
├─ stop-gsmv.cmd
└─ pom.xml
```

## 数据库迁移

核心迁移文件位于 `src/main/resources/db/migration`：

| 文件 | 说明 |
| --- | --- |
| `V1__init_schema.sql` | 初始业务表结构 |
| `V2__seed_data.sql` | 初始化角色、权限、示例数据 |
| `V10__backfill_sousa_chinensis_distribution.sql` | 中华白海豚分布数据补齐 |
| `V11__ai_review_ticket.sql` | AI 复核工单 |
| `V12__entity_versioning.sql` | 实体版本回溯 |
| `V13__ai_report_workflow.sql` | AI 科研报告 |
| `V14__rag_knowledge_base.sql` | RAG 知识库基础结构 |
| `V15__rag_knowledge_center.sql` | RAG 知识中台增强 |
| `V16__assistant_chat_history.sql` | AI 助手对话历史 |
| `V17__ai_agent_collaboration.sql` | Agent 协作轨迹 |

`RagSchemaBootstrapRunner` 会在运行期对 RAG 和 Agent 相关结构做兼容性补齐，便于已有环境升级。

## 实验六测试脚本

实验六脚本位于 `scripts/experiment6`，可生成 Excel 测试报告：

```powershell
scripts\experiment6\run_all_excel_reports.cmd
```

也可以单独运行：

```powershell
scripts\experiment6\run_whitebox_excel.cmd
scripts\experiment6\run_functional_excel.cmd
scripts\experiment6\run_integration_excel.cmd
scripts\experiment6\run_performance_excel.cmd
```

白盒 JUnit 测试类：

```text
src/test/java/com/gsmv/ai/Experiment6WhiteboxExecutionTests.java
```

运行：

```powershell
.\mvnw.cmd -Dtest=Experiment6WhiteboxExecutionTests test
```

Agent 编排相关测试：

```text
src/test/java/com/gsmv/ai/agent/AgentOrchestratorServiceTests.java
src/test/java/com/gsmv/ai/agent/KnowledgeGovernanceSchedulerTests.java
```

## 常用命令

```powershell
# 后端编译
.\mvnw.cmd -DskipTests compile

# 后端测试
.\mvnw.cmd test

# 前端构建
cd frontend
npm.cmd run build

# Agent 轨迹状态覆盖检查
cd frontend
node scripts/check-agent-trace-states.mjs

# 查看 Git 状态
git status -sb
```

## 安全约定

- 不要把真实 API Key 写进 `application.yml`、README、脚本默认值或提交历史。
- `.gitignore` 应忽略 `.env`、`frontend/.env`、运行日志、上传文件、构建产物和运行期目录。
- 如果 Key 曾经暴露在聊天、日志或 Git 历史中，应立即到服务商后台轮换。
- 生产环境应修改数据库密码、JWT 密钥、CORS、上传目录、日志策略和 AI Key 注入方式。

## 版本记录

| 版本 | 说明 |
| --- | --- |
| `v1.7.1` | 发布 Agent 协作台、角色路由权限管理、知识库治理调度及配套前后端代码 |
| `v1.7` | Agent 协作台、Agent Run/Step 轨迹落库、Replay 一致性检查、知识库治理协作流、角色路由权限管理、Agent 构建状态检查 |
| `v1.6` | README 中文化；RAG 分块详情；归档物种关联限制；保护等级筛选增强；AI 复核工单独立化；智能模块 ER 图补齐 |
| `v1.5` | 本地 `bge-m3` 向量、Qdrant 重建、真实 IUCN 导入、外部知识结构化、AI 助手用户级历史、RAG 证据排序和实验六脚本补齐 |
| `v1.4` | RAG 知识中台、Qdrant 向量检索、AI 助手自然问答、SSE 流式输出、数据库建表脚本 |
| `v1.3` | AI 科研报告、地图混合底图、启动脚本、版本回溯、AI 复核和 RAG 基础能力 |
| `v1.2` | AI 增强服务、数据版本与回溯、物种和观测核心闭环 |
| `v1.1` | 用户权限、物种档案、生态系统、观测记录、统计报表等基础模块 |
