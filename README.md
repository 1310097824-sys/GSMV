# GSMV 海洋生物多样性管理台

GSMV 是一套面向海洋生物多样性调查、物种档案维护、生态系统观测、智能问答、RAG 知识中台和科研报告生成的全栈系统。系统围绕“物种、生态系统、观测记录、统计报表、AI 服务、用户权限、版本回溯、知识检索”形成闭环，适合课程实践、实验室数据管理、近海生态调查和科普展示。

当前版本：`v1.4`

## 核心能力

| 模块 | 当前能力 |
| --- | --- |
| 用户与权限 | 注册申请、管理员审核、登录登出、个人资料、头像、角色权限、活动日志 |
| 物种档案 | 物种创建、编辑、删除、组合检索、详情页、分类阶元、保护等级、濒危状态、图片、视频链接、参考文献 |
| 生态系统 | 珊瑚礁、红树林、海草床、近海、深海等生态系统的创建、编辑、删除、查询 |
| 观测记录 | 时间、经纬度、生态系统、观测人员、环境参数、备注、观测物种关联、数量估算和行为记录 |
| 生态地图 | Leaflet 地图展示观测点和生态系统点位，国内优先低延迟底图，国外使用全球影像底图 |
| 统计报表 | 物种分布、观测地点、分类占比、保护等级、生态系统统计、观测活动统计、Excel/PDF 导出 |
| AI 助手 | 像 GPT 一样自然问答，同时参考系统数据和 RAG 证据，支持流式输出、缓存、模糊地名识别 |
| AI 识图 | 上传海洋生物图片后调用多模态模型识别，结合 RAG 证据校准置信度，低置信度可发起人工复核 |
| AI 档案补全 | 根据中文名或学名补全分类、形态、习性、分布等字段，也支持润色和翻译 |
| AI 复核工单 | 保存识图原图、初始识别、RAG 证据快照和人工复核结论，形成闭环 |
| AI 科研报告 | 按时间范围生成科研简报，支持报告历史、预览、证据展示和 PDF 导出 |
| RAG 知识中台 | 系统数据、上传文档、本地文件夹、OBIS/GBIF/WoRMS/IUCN/网页资料采集，MySQL 元数据 + Qdrant 向量检索 |
| 数据版本回溯 | 物种档案和观测记录变更留痕，支持查看谁改了什么和一键回滚 |

## 技术栈

| 层级 | 技术 |
| --- | --- |
| 后端 | Spring Boot 4.0.5、Spring Security、Spring Actuator |
| 持久层 | MyBatis 4.0、Flyway |
| 数据库 | MySQL 8.0、utf8mb4、JSON 字段、空间坐标 |
| 向量库 | Qdrant，默认集合 `gsmv_rag_chunks`，1024 维 Cosine 向量 |
| 前端 | Vue 3.5、TypeScript、Vite 8、Pinia、Vue Router |
| UI 与可视化 | Element Plus、Leaflet、ECharts |
| AI 服务 | 阿里云百炼 DashScope 兼容接口、DeepSeek Chat |
| 文档处理 | Apache PDFBox、Apache POI |
| 鉴权 | JWT、BCrypt、RBAC 权限控制 |

## 环境要求

| 依赖 | 建议版本 |
| --- | --- |
| JDK | 17 或更高 |
| Node.js | 20 或更高 |
| npm | 10 或更高 |
| MySQL | 8.0 或更高 |
| Docker Desktop | 可选，用于运行 Qdrant |
| IntelliJ IDEA | 推荐 |

## 快速启动

先确认 MySQL 已启动，并创建数据库：

```sql
CREATE DATABASE IF NOT EXISTS gsmv
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
```

Windows 下推荐直接双击或执行：

```powershell
.\start-gsmv.cmd
```

启动脚本会弹出两个输入框：

| 输入项 | 环境变量 |
| --- | --- |
| 阿里云百炼 API Key | `BAILIAN_API_KEY`，同时兼容 `DASHSCOPE_API_KEY` |
| DeepSeek API Key | `DEEPSEEK_API_KEY` |

两个 Key 都可以留空。留空时非 AI 功能仍可使用，AI/RAG 向量化会按后端降级逻辑给出提示。

停止系统：

```powershell
.\stop-gsmv.cmd
```

启动脚本会自动处理：

- 清理旧的 `8080` 和 `5173` 端口进程。
- 启动后端和前端。
- 如果 Docker 可用，自动启动 `gsmv-qdrant` 容器。
- 默认打开登录页 `http://127.0.0.1:5173/login?fresh=1`。
- 日志写入 `.gsmv-runtime/logs`。

## 手动启动

后端：

```powershell
cd D:\java\GSMV
.\mvnw.cmd spring-boot:run
```

前端：

```powershell
cd D:\java\GSMV\frontend
npm install
npm run dev
```

访问地址：

```text
前端：http://127.0.0.1:5173
后端：http://127.0.0.1:8080
```

## IDEA 启动

项目包含 `.run` 目录下的共享运行配置。推荐选择：

```text
GSMV Full Stack
```

如果只调试后端或前端，可以分别选择 Backend / Frontend 配置。AI Key 建议放在 IDEA Run Configuration 的环境变量里，不要写入仓库文件。

## 默认账号

| 账号 | 密码 | 说明 |
| --- | --- | --- |
| `admin` | `123456` | 默认管理员 |

注册用户默认需要管理员审核后才能使用系统。

## 数据库与建表脚本

后端默认连接配置在 [`src/main/resources/application.yml`](src/main/resources/application.yml)：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/gsmv?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: 123456
```

推荐做法是让 Flyway 自动迁移空库，迁移脚本位于：

```text
src/main/resources/db/migration
```

如果只需要把当前数据库表结构一次性复制到 MySQL，可使用：

```text
docs/database/gsmv_schema_v1.4.sql
```

这份 SQL 是基于当前 `gsmv` 数据库导出的建表脚本，包含 `CREATE DATABASE`、`USE gsmv`、`DROP TABLE IF EXISTS`、`CREATE TABLE`、索引和外键约束。它只建表，不导入样例数据。

## RAG 知识中台

RAG 是系统的 AI 证据层，不只服务 AI 助手。当前接入范围包括：

- AI 助手问答。
- AI 科研报告。
- 物种补全、润色和翻译。
- 观测记录分析和异常检测。
- 图像识别结果校准。
- AI 复核工单证据快照。

知识进入 RAG 的流程：

```text
系统数据 / 上传文档 / 本地文件夹 / 公开资料
-> 文本抽取
-> 约 600 字分块
-> 阿里云百炼 text-embedding-v4 生成 1024 维向量
-> MySQL 保存元数据和任务状态
-> Qdrant 保存向量点
-> AI 模块召回证据
```

Qdrant 默认信息：

```text
容器名：gsmv-qdrant
地址：http://localhost:6333
集合：gsmv_rag_chunks
距离：Cosine
维度：1024
```

常用检查命令：

```powershell
Invoke-RestMethod http://localhost:6333/collections/gsmv_rag_chunks | ConvertTo-Json -Depth 10
```

查看前 8 条向量 payload：

```powershell
$body = @{ limit = 8; with_payload = $true; with_vector = $false } | ConvertTo-Json
Invoke-RestMethod -Method Post `
  -Uri http://localhost:6333/collections/gsmv_rag_chunks/points/scroll `
  -ContentType 'application/json' `
  -Body $body | ConvertTo-Json -Depth 20
```

Qdrant 数据存放在 Docker volume 中：

```text
volume：gsmv-qdrant_data
容器内路径：/qdrant/storage
```

## 地图说明

地图统一由 [`frontend/src/utils/mapProvider.ts`](frontend/src/utils/mapProvider.ts) 管理。默认采用混合策略：

- 中国区域优先使用国内访问稳定、延迟低的底图。
- 海外区域自动使用全球影像/标注底图。
- 中国境内点位会自动处理 WGS84 与 GCJ-02 坐标偏移。

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
| `/reports` | 统计报表 |
| `/audits` | 审计日志 |
| `/users` | 用户权限 |
| `/profile` | 个人中心 |

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

## 项目结构

```text
GSMV/
├─ src/main/java/com/gsmv/
│  ├─ ai/               # AI 助手、识图、补全、复核、科研报告、RAG
│  ├─ audit/            # 审计日志
│  ├─ auth/             # 登录、注册、JWT
│  ├─ bootstrap/        # 运行期补齐数据结构
│  ├─ common/           # 通用响应、异常、TraceId
│  ├─ config/           # 安全、CORS、属性配置
│  ├─ ecosystem/        # 生态系统
│  ├─ media/            # 文件上传
│  ├─ observation/      # 观测记录
│  ├─ report/           # 统计报表与导出
│  ├─ species/          # 物种档案
│  ├─ user/             # 用户与权限
│  └─ versioning/       # 数据版本与回溯
├─ src/main/resources/db/migration/ # Flyway 迁移脚本
├─ frontend/src/        # Vue 前端
├─ docs/database/       # 数据库建表脚本
├─ scripts/             # 一键启动/停止脚本
├─ uploads/             # 本地上传文件
├─ start-gsmv.cmd
├─ stop-gsmv.cmd
└─ pom.xml
```

## 安全约定

- 不要把真实 API Key 写入 `application.yml`、`.run/*.xml`、README 或脚本默认值。
- `.gitignore` 已忽略 `.env`、`frontend/.env` 等本地密钥文件。
- 如果 Key 曾被提交到 Git 历史，请立即在服务商后台轮换。
- 生产环境请修改数据库密码、JWT 密钥、CORS、上传目录、日志策略和 AI Key 注入方式。

## 版本记录

| 版本 | 说明 |
| --- | --- |
| `v1.4` | 接入大规模 RAG 知识中台、Qdrant 向量检索、AI 助手自然问答、流式输出、当前数据库建表脚本 |
| `v1.3` | AI 科研报告、地图混合底图、启动脚本、版本回溯、AI 复核和 RAG 基础能力 |
| `v1.2` | AI 增强服务、数据版本与回溯、物种/观测核心闭环 |
| `v1.1` | 用户权限、物种档案、生态系统、观测记录、统计报表等基础模块 |
