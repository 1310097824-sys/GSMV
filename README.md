# GSMV 海洋生物多样性智能管理系统

GSMV（Global Species & Marine Vision）是一套面向海洋生物多样性管理、生态观测、物种档案维护、智能问答、RAG 知识检索和科研报告生成的全栈 Web 系统。系统以 Spring Boot 后端、Vue 3 前端、MySQL 数据库、Qdrant 向量库和大模型服务为基础，将传统数据管理能力与 AI 辅助识别、检索增强生成、人工复核和科研分析能力结合起来。

当前版本：`v1.6`

## v1.6 版本重点

- 重写 README，修复原文档中文乱码，补充当前系统的部署、模块、RAG、测试和版本说明。
- 智能模块功能闭环进一步完善，覆盖 AI 助手、AI 识图、RAG 知识中台、AI 复核工单、观测智能分析和 AI 科研报告。
- RAG 文档详情支持查看每个知识分块的完整内容、摘要、元数据和向量化状态，便于追溯 AI 回答证据来源。
- 物种归档语义增强：归档物种不再允许被新观测记录或 AI 复核结论继续关联，历史记录保留可读状态。
- 物种保护等级和 IUCN 状态筛选增强，支持更符合中文输入习惯的保护等级查询。
- AI 复核工单保留为独立业务流程，不再作为普通 RAG 知识文档混入知识库列表。
- AI 助手按用户保存独立对话历史，刷新页面后仍可恢复当前用户的历史问答。
- RAG 向量模型采用本地 Ollama `bge-m3`，Qdrant collection 默认使用 1024 维 Cosine 向量。
- 增加智能模块 ER 图 Mermaid 文件：`docs/ai-module-er.drawio.mmd`。

## 核心能力

| 模块 | 能力说明 |
| --- | --- |
| 用户与权限 | 登录、注册申请、管理员审核、角色权限、个人资料、审计日志 |
| 物种档案 | 物种增删改查、分类阶元、保护等级、IUCN 状态、图片视频、参考文献、归档管理 |
| 生态系统 | 近海、珊瑚礁、红树林、海草床等生态系统档案维护 |
| 观测记录 | 观测时间、地点、坐标、生态系统、观测人员、环境参数、物种数量和行为记录 |
| 生态地图 | 基于 Leaflet 展示观测点和生态系统点位，支持多种底图策略 |
| 统计报表 | 物种分布、观测活动、分类占比、保护等级、生态系统统计、Excel/PDF 导出 |
| AI 助手 | DeepSeek 问答、RAG 检索增强、SSE 流式输出、用户级对话历史、证据溯源 |
| AI 图像识别 | 调用阿里云百炼视觉模型识别海洋生物图片，并结合 RAG 证据校验结果 |
| 物种智能补全 | 根据中文名、学名或描述补全分类、形态、习性、分布和保护状态 |
| 文本润色与翻译 | 对物种档案文本进行专业润色和多语言翻译 |
| 观测智能分析 | 根据时间、坐标、环境参数和关联物种生成标签、异常提示和质量建议 |
| AI 复核工单 | 保存低置信度识图结果、候选项、RAG 证据快照和人工复核结论 |
| AI 科研报告 | 基于系统统计和 RAG 证据生成科研简报，支持历史查看和 PDF 导出 |
| RAG 知识中台 | 管理系统数据、上传文档、外部知识、向量化分块、Qdrant 状态和检索测试 |
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
| 鉴权 | JWT、BCrypt、RBAC |

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

默认数据库连接位于 `src/main/resources/application.yml`：

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

如果使用一键启动脚本，脚本会自动尝试启动或创建 `gsmv-qdrant` 容器。也可以手动启动：

```powershell
docker run -d --name gsmv-qdrant -p 6333:6333 qdrant/qdrant
```

默认 Qdrant 配置：

```text
地址：http://localhost:6333
Collection：gsmv_rag_chunks
距离：Cosine
维度：1024
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
-> AI 助手、识图、报告、补全等模块召回证据
```

页面中的“文档详情”和“分块详情”展示的是 MySQL 中保存的原始可读文本、摘要、元数据和向量化状态；Qdrant 中保存的是对应分块的向量点。

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

## AI 助手运行逻辑

AI 助手主流程：

```text
前端发送问题
-> JWT 鉴权
-> 后端识别问题意图
-> 先进行 RAG 检索
-> 同时读取必要的系统业务数据
-> DeepSeek 结合上下文生成自然语言回答
-> 保存当前用户的对话历史
-> 前端展示回答、重点摘要和证据来源
```

说明：

- 日常问题、常识问题和开放式问题会按通用助手方式回答。
- 物种介绍、保护状态、分布、栖息地等问题会优先结合系统物种档案和 IUCN、WoRMS 等正式外部来源。
- “打开证据来源”会跳转到具体业务页面或具体 RAG 文档详情。
- 对话历史保存到 `ai_assistant_message` 表，不同用户互不影响。

## AI 图像识别与复核闭环

图像识别主流程：

```text
上传海洋生物图片
-> 文件类型校验
-> 阿里云百炼视觉模型识别候选物种
-> 使用候选中文名/学名进行 RAG 检索
-> 结合置信度和证据冲突判断是否需要人工复核
-> 可创建 AI 复核工单
-> 复核人员查看图片、候选项和证据快照
-> 提交确认、不匹配或无法确认结论
```

归档物种不能作为新的复核结论关联对象；历史记录仍保留原有关联信息，便于追溯。

## 外部知识采集

| 来源 | 用途 | 说明 |
| --- | --- | --- |
| IUCN | 保护等级、威胁、栖息地、种群趋势 | 需要 `IUCN_API_TOKEN` |
| WoRMS | 学名、分类阶元、AphiaID、有效名 | 中文名会尽量解析成学名后再查询 |
| GBIF | 全球出现记录摘要 | 用于分布和出现记录补充 |
| OBIS | 海洋观测出现记录摘要 | 用于海洋出现记录补充 |
| WEB_PDF | 白名单网页文档 | 支持 PDF、DOCX、TXT、MD |

外部采集结果会先转成可读文本块，再进入分块和向量化流程。

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

## 实验六测试脚本

实验六脚本位于 `scripts/experiment6`，可以生成 Excel 测试报告：

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

# 前端构建
cd frontend
npm.cmd run build

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
| `v1.6` | README 正常中文化；RAG 分块详情；归档物种关联限制；保护等级筛选增强；AI 复核工单独立化；智能模块 ER 图补充 |
| `v1.5` | 本地 `bge-m3` 向量、Qdrant 重建、真实 IUCN 导入、外部知识结构化、AI 助手用户级历史、RAG 证据排序和实验六脚本补齐 |
| `v1.4` | RAG 知识中台、Qdrant 向量检索、AI 助手自然问答、SSE 流式输出、数据库建表脚本 |
| `v1.3` | AI 科研报告、地图混合底图、启动脚本、版本回溯、AI 复核和 RAG 基础能力 |
| `v1.2` | AI 增强服务、数据版本与回溯、物种和观测核心闭环 |
| `v1.1` | 用户权限、物种档案、生态系统、观测记录、统计报表等基础模块 |
