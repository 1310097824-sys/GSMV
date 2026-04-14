# GSMV v1.2

GSMV 是一套面向海洋生物多样性管理与科研支撑的全栈系统，当前版本已经覆盖物种档案、生态系统、观测记录、地图报表、AI 增强服务、人工复核、用户权限以及数据版本回溯等核心能力。

本项目当前采用：

- 后端：Spring Boot 4 + MyBatis + Flyway + MySQL
- 前端：Vue 3 + Vite + Element Plus + ECharts + Leaflet
- AI 服务：阿里云百炼、DeepSeek

## v1.2 版本重点

- 物种档案管理：创建、编辑、删除、详情、图片上传、AI 识图与档案补全
- 生态系统管理：创建、编辑、删除、查询
- 观测记录管理：创建、编辑、删除、详情、观测与物种关联、地图点选坐标
- 数据可视化与报表：物种分布地图、观测地点地图、统计图表、Excel/PDF 导出
- AI 增强服务：图像识别、文本补全、文本润色、翻译、AI 助手、AI 复核工单
- 用户与权限：注册审核、角色权限、个人中心、活动日志
- 数据版本与回溯：物种档案和观测记录支持版本差异查看与一键回滚

## 运行环境

- JDK 17
- Node.js 20+
- npm 10+
- MySQL 8.0+
- IntelliJ IDEA 2025+

## 默认配置

- 数据库：`gsmv`
- 数据库地址：`localhost:3306`
- MySQL 用户名：`root`
- MySQL 密码：`123456`
- 后端地址：[http://127.0.0.1:8080](http://127.0.0.1:8080)
- 前端开发地址：[http://127.0.0.1:5173](http://127.0.0.1:5173)

默认管理员账号：

- 用户名：`admin`
- 密码：`123456`

## AI 环境变量

项目不会把第三方密钥写入仓库，AI 功能通过环境变量读取：

- `BAILIAN_API_KEY`
- `DEEPSEEK_API_KEY`

PowerShell 临时设置示例：

```powershell
$env:BAILIAN_API_KEY="你的百炼 Key"
$env:DEEPSEEK_API_KEY="你的 DeepSeek Key"
```

如果要在 IDEA 中直接使用 AI 功能，建议把这两个变量配置到系统环境变量或 Run Configuration 中。

## 项目结构

- `src/main/java`：后端业务代码
- `src/main/resources`：配置文件、Flyway 迁移脚本
- `frontend/src`：前端页面、组件、接口封装
- `uploads`：本地上传文件目录
- `scripts`：本地开发和构建脚本
- `.run`：IDEA 共享运行配置

## 当前主要模块

### 1. 用户与权限

- 登录、登出
- 学生/公众注册申请与管理员审核
- 用户角色与权限控制
- 个人资料与头像维护
- 用户活动日志

### 2. 物种档案

- 中文名、学名、门纲目科属种
- 形态特征、生活习性、栖息环境、分布区域、地理范围
- 保护等级、濒危状态
- 图片上传、视频链接、参考文献
- 独立详情页

### 3. 生态系统与观测记录

- 生态系统创建、编辑、删除、查询
- 观测记录创建、编辑、删除、详情
- 地图点选经纬度
- 环境参数录入
- 多物种关联、估算数量、行为、备注

### 4. 地图与报表

- 物种分布地图
- 观测地点地图
- 物种统计、生态系统统计、观测活动统计
- 综合数据看板
- Excel / PDF 导出

### 5. AI 增强服务

- AI 图像识别与物种候选推荐
- AI 物种档案补全
- AI 文本润色
- AI 多语言翻译
- AI 观测分析与异常提示
- AI 问答与科研助手
- AI 人工复核工单

### 6. 数据版本与回溯

当前版本已支持：

- 物种档案版本记录
- 观测记录版本记录
- 每次创建、更新、删除、回滚自动留痕
- 查看“谁在什么时候改了什么字段”
- 一键回滚到历史版本

数据库迁移脚本：

- [V12__entity_versioning.sql](src/main/resources/db/migration/V12__entity_versioning.sql)

## 本地启动

### 方式一：使用 IDEA

项目自带了 3 个可共享运行配置：

- [GSMV Backend.run.xml](.run/GSMV%20Backend.run.xml)
- [GSMV Frontend.run.xml](.run/GSMV%20Frontend.run.xml)
- [GSMV Full Stack.run.xml](.run/GSMV%20Full%20Stack.run.xml)

推荐直接运行 `GSMV Full Stack`。

### 方式二：命令行

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

也可以使用脚本：

- `scripts\dev-backend.bat`
- `scripts\dev-frontend.bat`
- `scripts\build-all.bat`

## 数据库准备

确保 MySQL 已启动，然后创建数据库：

```sql
CREATE DATABASE IF NOT EXISTS gsmv
DEFAULT CHARACTER SET utf8mb4
DEFAULT COLLATE utf8mb4_unicode_ci;
```

项目启用 Flyway，后端启动时会自动执行 `src/main/resources/db/migration` 下的迁移脚本。

## 常用访问地址

- 登录页：`/login`
- 注册页：`/register`
- 仪表盘：`/dashboard`
- 物种档案：`/species`
- 生态系统：`/ecosystems`
- 生态地图：`/eco-map`
- 观测记录：`/observations`
- AI 助手：`/assistant`
- AI 复核：`/ai-reviews`
- 统计报表：`/reports`
- 用户权限：`/users`
- 个人中心：`/profile`

## 已验证命令

当前版本已经验证通过：

```powershell
cd D:\java\GSMV
.\mvnw.cmd -DskipTests compile
.\mvnw.cmd -DskipTests package

cd D:\java\GSMV\frontend
npm run build
```

## 常见问题

### 1. AI 补全或识图提示超时

当前版本已经将 AI 请求超时放宽：

- 前端 AI 请求超时：`90000ms`
- 后端外部 AI 读超时：`90000ms`

如果仍然超时，优先检查：

- 后端是否正常运行
- 百炼 / DeepSeek API Key 是否可用
- 当前网络是否能访问外部 AI 服务

### 2. 页面文案与源码不一致

如果 Chrome 开启了自动翻译，可能会把中文页面错误翻译成别的文案。建议关闭当前页翻译后再刷新。

### 3. 运行配置看不到

如果 IDEA 中暂时看不到 `.run` 里的配置，执行一次 Maven Reload 或重启 IDEA 即可。

## 版本说明

当前仓库将此版本保存为：

- Git 标签：`v1.2`

如果你后续继续扩展批量导入、预警订阅、专题故事地图或自动周报，可以从这个版本继续演进。
