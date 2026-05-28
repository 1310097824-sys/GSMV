# GSMV 海洋生物多样性管理平台

GSMV 是一套面向海洋生物多样性管理、生态观测、物种档案维护、智能问答、RAG 知识中台和科研报告生成的全栈系统。

当前版本：`v1.5`

## 版本亮点

`v1.5` 重点完成了智能模块和 RAG 知识链路的真实化：

- AI 助手支持更自然的通用问答，回答前会先做 RAG 检索，但不会把闲聊或常识问题强行转成系统统计。
- AI 助手对话历史持久化到数据库，每个登录用户拥有独立历史记录，刷新页面后不会丢失。
- 向量模型切换为本地 Ollama `bge-m3`，Qdrant collection 使用 1024 维 Cosine 向量。
- RAG 知识中台支持上传文档、本地文件夹导入、OBIS、GBIF、WoRMS、IUCN 和网页文档采集。
- IUCN Red List 改为真实 API 采集，导入评估等级、种群趋势、栖息地、威胁、保护措施和引用信息。
- WoRMS、GBIF、OBIS 外部采集改为结构化知识摘要，不再把原始 JSON 摘要直接塞进 RAG 文档。
- 复核工单从 RAG 知识库中移除，复核工单仍保留自己的业务页面和证据快照，但不再作为知识文档参与检索。
- RAG 证据排序优化：物种介绍、保护状态、分布和栖息地类问题优先展示 IUCN、WoRMS 等正式外部来源。
- 证据来源链接支持直接打开 `/rag-knowledge?document=xxx` 对应的 RAG 文档详情。
- 实验六测试脚本补齐，白盒、功能、集成、性能测试脚本可执行并生成 Excel 报告。

## 功能模块

| 模块 | 能力 |
| --- | --- |
| 用户与权限 | 登录、注册申请、管理员审核、角色权限、个人资料、审计日志 |
| 物种档案 | 物种增删改查、分类阶元、保护等级、IUCN 状态、图片、视频、参考文献 |
| 生态系统 | 近海、珊瑚礁、红树林、海草床等生态系统档案维护 |
| 观测记录 | 观测时间、地点、坐标、生态系统、观测人员、环境参数、物种数量和行为记录 |
| 生态地图 | 基于 Leaflet 展示观测点和生态系统点位，支持国内外底图策略 |
| 统计报表 | 物种分布、观测活动、分类占比、保护等级、生态系统统计、Excel/PDF 导出 |
| AI 助手 | DeepSeek 问答、RAG 检索增强、SSE 流式输出、用户级对话历史、证据溯源 |
| AI 识图 | 调用阿里云百炼视觉模型识别海洋生物图片，并结合 RAG 证据做结果校准 |
| AI 档案补全 | 根据中文名、学名或描述补全分类、形态、习性、分布等字段 |
| AI 润色翻译 | 对物种档案文本进行专业润色和中英文翻译 |
| AI 复核工单 | 保存低置信度识图结果、候选项、RAG 证据快照和人工复核结论 |
| AI 科研报告 | 基于系统统计和 RAG 证据生成科研简报，支持历史查看和 PDF 导出 |
| RAG 知识中台 | 统一管理系统数据、上传文档、外部知识、向量化分块和检索测试 |
| 数据版本回溯 | 物种档案和观测记录变更留痕，支持查看历史和回滚 |

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
| 鉴权 | JWT、BCrypt、RBAC |

## 环境要求

| 依赖 | 建议版本 |
| --- | --- |
| JDK | 17 或更高 |
| Node.js | 20 或更高 |
| npm | 10 或更高 |
| MySQL | 8.0 或更高 |
| Docker Desktop | 可选，用于运行 Qdrant |
| Ollama | 用于本地 `bge-m3` 向量模型 |

## 快速启动

### 1. 创建数据库

```sql
CREATE DATABASE IF NOT EXISTS gsmv
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
```

默认数据库连接在 [src/main/resources/application.yml](src/main/resources/application.yml) 中：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/gsmv?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: 123456
```

Flyway 会在后端启动时自动执行 `src/main/resources/db/migration` 下的迁移脚本。

### 2. 准备本地向量模型

```powershell
ollama pull bge-m3
ollama serve
```

系统默认使用：

```text
Ollama 地址：http://localhost:11434
Embedding 模型：bge-m3
向量维度：1024
```

### 3. 配置 AI Key

不要把真实 API Key 写入仓库文件。可以使用环境变量：

```powershell
setx BAILIAN_API_KEY "your-bailian-key"
setx DASHSCOPE_API_KEY "your-bailian-key"
setx DEEPSEEK_API_KEY "your-deepseek-key"
setx IUCN_API_TOKEN "your-iucn-token"
```

也可以直接运行启动脚本，脚本会弹出输入框收集百炼和 DeepSeek Key。IUCN Token 会从环境变量读取。

### 4. 一键启动

```powershell
.\start-gsmv.cmd
```

启动脚本会自动处理：

- 停止旧的 `8080` 和 `5173` 端口进程。
- 启动后端和前端。
- 如果 Docker 可用，自动启动或创建 `gsmv-qdrant` 容器。
- 打开登录页。
- 将日志写入 `.gsmv-runtime/logs`。

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

## RAG 知识中台

RAG 是系统智能模块的证据层。当前接入范围包括：

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
-> AI 助手、识图、报告、补全等模块召回证据
```

Qdrant 默认配置：

```text
容器名：gsmv-qdrant
地址：http://localhost:6333
Collection：gsmv_rag_chunks
距离：Cosine
维度：1024
```

常用检查命令：

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

## AI 助手运行逻辑

AI 助手的主流程：

```text
前端发送问题
-> JWT 鉴权
-> 后端识别问题意图
-> 先做 RAG 检索
-> 同时读取必要的系统业务数据
-> DeepSeek 结合上下文生成自然语言回答
-> 保存当前用户的对话历史
-> 前端展示回答、重点摘要和证据来源
```

说明：

- 日常问题、常识问题和开放式问题会按通用助手方式回答。
- 物种介绍、保护状态、分布、栖息地等问题会优先展示 IUCN 和 WoRMS 证据。
- “打开证据来源”会跳转到具体业务页面或具体 RAG 文档详情。
- 对话历史保存在 `ai_assistant_message` 表中，不同用户互不影响。

## 外部知识采集说明

| 来源 | 用途 | 说明 |
| --- | --- | --- |
| IUCN | 保护等级、威胁、栖息地、种群趋势 | 需要 `IUCN_API_TOKEN` |
| WoRMS | 学名、分类阶元、AphiaID、有效名 | 中文名会尽量解析成学名后再查询 |
| GBIF | 全球出现记录摘要 | 用于分布和出现记录补充 |
| OBIS | 海洋观测出现记录摘要 | 用于海洋出现记录补充 |
| WEB_PDF | 白名单网页文档 | 支持 PDF、DOCX、TXT、MD |

外部采集到的数据会先转成可读文本块，再向量化。页面里的“文档详情”展示的是 MySQL 中保存的原始可读文本块，不是 Qdrant 里的向量数组。

## 实验六测试脚本

实验六脚本位于 [scripts/experiment6](scripts/experiment6)。

可生成 Excel 测试报告：

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

## 常用命令

```powershell
# 后端编译
.\mvnw.cmd -DskipTests compile

# 后端测试
.\mvnw.cmd test

# 实验六白盒测试
.\mvnw.cmd -Dtest=Experiment6WhiteboxExecutionTests test

# 前端构建
cd frontend
npm.cmd run build

# 查看 Git 状态
git status -sb
```

## 前端路由

| 路由 | 页面 |
| --- | --- |
| `/login` | 登录 |
| `/register` | 注册 |
| `/dashboard` | 仪表盘 |
| `/species` | 物种档案 |
| `/species/:id` | 物种详情 |
| `/ecosystems` | 生态系统 |
| `/eco-map` | 生态地图 |
| `/observations` | 观测记录 |
| `/assistant` | AI 助手 |
| `/ai-reviews` | AI 复核 |
| `/ai-reports` | AI 科研报告 |
| `/rag-knowledge` | RAG 知识中台 |
| `/rag-knowledge?document=ID` | 指定 RAG 文档详情 |
| `/reports` | 统计报表 |
| `/audits` | 审计日志 |
| `/users` | 用户权限 |
| `/profile` | 个人中心 |

## 项目结构

```text
GSMV/
├─ src/main/java/com/gsmv/
│  ├─ ai/                 # AI 助手、识图、RAG、复核、科研报告
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
│  ├─ user/               # 用户与权限
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

## 安全约定

- 不要把真实 API Key 写进 `application.yml`、README、脚本默认值或提交历史。
- `.gitignore` 已忽略 `.env`、`frontend/.env`、运行日志、上传文件、构建产物和运行期目录。
- 如果 Key 曾经暴露在聊天、日志或 Git 历史中，应立即到服务商后台轮换。
- 生产环境请修改数据库密码、JWT 密钥、CORS、上传目录、日志策略和 AI Key 注入方式。

## 版本记录

| 版本 | 说明 |
| --- | --- |
| `v1.5` | 本地 `bge-m3` 向量、Qdrant 重建、真实 IUCN 导入、外部知识结构化、AI 助手用户级历史、RAG 证据排序和证据来源跳转、实验六脚本补齐 |
| `v1.4` | 大规模 RAG 知识中台、Qdrant 向量检索、AI 助手自然问答、流式输出、数据库建表脚本 |
| `v1.3` | AI 科研报告、地图混合底图、启动脚本、版本回溯、AI 复核和 RAG 基础能力 |
| `v1.2` | AI 增强服务、数据版本与回溯、物种和观测核心闭环 |
| `v1.1` | 用户权限、物种档案、生态系统、观测记录、统计报表等基础模块 |
