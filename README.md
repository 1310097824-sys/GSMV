# GSMV 海洋生物多样性管理台

GSMV 是一套面向海洋生物多样性调查、物种档案维护、生态系统观测、智能分析与科研报告生成的全栈管理系统。系统以“物种档案、生态系统、观测记录、统计报表、AI 辅助服务、用户权限、版本回溯”为主线，适用于课程实践、实验室数据管理、近海生态调查与科普展示等场景。

当前版本：`v1.3`

## 系统能力

| 模块 | 已实现能力 |
| --- | --- |
| 用户与权限 | 注册申请、管理员审核、登录登出、个人资料与头像、角色权限、活动审计日志 |
| 物种档案 | 创建、编辑、删除、检索、详情展示、图片与视频链接、参考文献、分类阶元、保护等级、濒危状态 |
| 生态系统 | 创建、编辑、删除、查询，支持珊瑚礁、红树林、海草床、近海、深海等生态系统类型 |
| 观测记录 | 时间、地点、生态系统、观测人员、环境参数、备注、观测物种关联、数量估算与行为记录 |
| 生态地图 | 国内外观测点可视化，国内优先低延迟地图，国外自动使用全球影像底图，避免海外卫星空白 |
| 统计报表 | 物种分布地图、观测地点地图、分类统计、保护等级统计、生态系统统计、观测活动统计、Excel/PDF 导出 |
| AI 助手 | 自然语言问答、结构化数据检索、模糊地点识别、结果缓存、观测与物种摘要分析 |
| AI 识图 | 海洋生物图片识别、候选物种返回、重复档案提示、低置信度人工复核工单 |
| AI 档案补全 | 根据中文名或学名补全分类、形态特征、生活习性、分布区域等字段 |
| AI 科研报告 | 按时间范围生成科研简报，支持报告历史、预览与 PDF 导出 |
| 多语言服务 | 物种描述翻译，支持英文等语言的科普与交流内容生成 |
| 数据版本回溯 | 物种档案和观测记录变更留痕，支持查看差异、追踪修改人、一键回滚历史版本 |

## 技术架构

| 层级 | 技术 |
| --- | --- |
| 后端 | Spring Boot 4.0.5、Spring Security、Spring Actuator |
| 持久层 | MyBatis 4.0、Flyway |
| 数据库 | MySQL 8.0、utf8mb4、JSON 字段、空间坐标数据 |
| 前端 | Vue 3.5、TypeScript、Vite 8 |
| UI | Element Plus、Leaflet、ECharts |
| 状态与路由 | Pinia、Vue Router |
| AI 服务 | 阿里云百炼 DashScope 兼容接口、DeepSeek Chat |
| 导出 | Apache POI、Apache PDFBox |
| 鉴权 | JWT、BCrypt、RBAC 权限控制 |

补充文档：

- [`tech.md`](tech.md)：技术选型与架构说明
- [`programingsign.md`](programingsign.md)：项目结构与文件说明
- [`docs/IMPLEMENTATION.md`](docs/IMPLEMENTATION.md)：实施说明

## 环境要求

| 依赖 | 建议版本 |
| --- | --- |
| JDK | 17 或更高 |
| Node.js | 20 或更高 |
| npm | 10 或更高 |
| MySQL | 8.0 或更高 |
| IDE | IntelliJ IDEA |

## 数据库配置

默认数据库连接位于 [`src/main/resources/application.yml`](src/main/resources/application.yml)：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/gsmv?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: 123456
```

首次启动前请创建数据库：

```sql
CREATE DATABASE IF NOT EXISTS gsmv
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
```

后端启动时 Flyway 会自动执行 [`src/main/resources/db/migration`](src/main/resources/db/migration) 下的迁移脚本，包含初始化表结构、样例物种、近期全球观测记录、AI 复核、数据版本回溯和 AI 科研报告等数据结构。

## 一键启动

Windows 下推荐直接使用项目根目录脚本：

```powershell
.\start-gsmv.cmd
```

启动脚本会弹出两个输入框，分别填写：

| 输入项 | 环境变量 |
| --- | --- |
| 阿里云百炼 API Key | `BAILIAN_API_KEY` |
| DeepSeek API Key | `DEEPSEEK_API_KEY` |

两个 Key 都可以留空。留空时系统仍可启动，非 AI 功能不受影响，AI 接口会按后端降级逻辑返回提示。

停止系统：

```powershell
.\stop-gsmv.cmd
```

脚本说明：

- 启动前会清理 `8080` 和 `5173` 端口占用。
- API Key 只作为本次启动环境变量传入，不写入仓库文件。
- 运行日志位于 `.gsmv-runtime/logs`。
- 前端地址为 `http://127.0.0.1:5173`。
- 后端地址为 `http://127.0.0.1:8080`。

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

前端开发服务器默认访问 `http://127.0.0.1:5173`，接口请求会代理到后端 `8080` 端口。

## IDEA 启动

项目包含 `.run` 目录下的共享运行配置。推荐在 IDEA 中选择：

```text
GSMV Full Stack
```

如果只想单独调试后端或前端，可以分别选择对应的 Backend / Frontend 配置。AI Key 建议通过运行配置的环境变量填写，不要写入代码或配置文件。

## 默认账号

| 账号 | 密码 | 说明 |
| --- | --- | --- |
| `admin` | `123456` | 默认管理员账号 |

登录后可以在个人中心维护显示名称和头像。注册用户默认需要管理员审核通过后才能正常使用系统。

## 前端路由

| 路由 | 页面 |
| --- | --- |
| `/login` | 系统登录 |
| `/register` | 用户注册 |
| `/dashboard` | 仪表盘 |
| `/species` | 物种档案 |
| `/species/:id` | 物种详情 |
| `/ecosystems` | 生态系统 |
| `/eco-map` | 生态地图 |
| `/observations` | 观测记录 |
| `/assistant` | AI 助手 |
| `/ai-reviews` | AI 复核工单 |
| `/ai-reports` | AI 科研报告 |
| `/reports` | 统计报表 |
| `/audits` | 审计日志 |
| `/users` | 用户权限 |
| `/profile` | 个人中心 |

## 地图说明

系统统一通过 [`frontend/src/utils/mapProvider.ts`](frontend/src/utils/mapProvider.ts) 管理 Leaflet 底图。

默认 `VITE_MAP_PROVIDER=amap` 为混合模式：

- 全球区域使用 Esri 全球影像和边界标注，适合海外观测点放大查看。
- 中国区域在较高缩放级别叠加高德矢量瓦片，国内访问更稳定、延迟更低。
- 坐标在中国境内会自动进行 WGS84 与 GCJ-02 转换，保证点位与国内底图对齐。

可选前端环境配置见 [`frontend/.env.example`](frontend/.env.example)。

## AI 配置

后端读取环境变量：

```powershell
$env:BAILIAN_API_KEY="你的阿里云百炼 Key"
$env:DEEPSEEK_API_KEY="你的 DeepSeek Key"
```

兼容变量：

```powershell
$env:DASHSCOPE_API_KEY="你的阿里云百炼 Key"
```

安全约定：

- 不要把真实 API Key 写入 `application.yml`、`.run/*.xml`、README 或脚本默认值。
- `.gitignore` 已忽略 `.env`、`frontend/.env` 等本地密钥文件。
- 如果曾经把 Key 提交进 Git 历史，建议立即在服务商后台轮换密钥。

## 常用命令

```powershell
# 前端构建
cd D:\java\GSMV\frontend
npm.cmd run build

# 后端编译
cd D:\java\GSMV
.\mvnw.cmd -DskipTests compile

# 后端打包
.\mvnw.cmd -DskipTests package

# 查看 Git 状态
git status -sb
```

## 项目结构

```text
GSMV/
├─ src/main/java/com/gsmv/
│  ├─ ai/               # AI 识图、补全、问答、复核、科研报告
│  ├─ audit/            # 审计日志
│  ├─ auth/             # 登录、注册、JWT
│  ├─ bootstrap/        # 初始化数据
│  ├─ common/           # 通用响应、异常、TraceId
│  ├─ config/           # 安全、CORS、属性配置
│  ├─ ecosystem/        # 生态系统管理
│  ├─ media/            # 文件上传
│  ├─ observation/      # 观测记录管理
│  ├─ report/           # 统计报表与导出
│  ├─ security/         # 安全工具
│  ├─ species/          # 物种档案
│  ├─ user/             # 用户与权限
│  └─ versioning/       # 数据版本与回溯
├─ src/main/resources/
│  ├─ application.yml
│  └─ db/migration/     # Flyway 迁移脚本
├─ frontend/src/
│  ├─ api/              # API 封装
│  ├─ components/       # 公共组件
│  ├─ layouts/          # 页面布局
│  ├─ router/           # 路由
│  ├─ stores/           # Pinia 状态
│  ├─ types/            # TypeScript 类型
│  ├─ utils/            # 地图、下载等工具
│  └─ views/            # 业务页面
├─ scripts/             # 一键启动/停止辅助脚本
├─ uploads/             # 本地上传文件
├─ start-gsmv.cmd
├─ stop-gsmv.cmd
└─ pom.xml
```

## 版本记录

| 版本 | 说明 |
| --- | --- |
| `v1.3` | 完善 README；整合 AI 科研报告、地图混合底图、启动脚本、版本回溯、AI 复核与近期 UI 优化 |
| `v1.2` | 完成 AI 增强服务、数据版本与回溯、物种/观测核心闭环 |
| `v1.1` | 完成用户权限、物种档案、生态系统、观测记录、统计报表等基础模块 |

## 说明

本项目为本地课程/实践型系统，默认配置偏向开发环境。若部署到生产环境，请修改数据库密码、JWT 密钥、跨域策略、上传目录、日志策略和 AI Key 注入方式。
