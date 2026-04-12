# GSMV

GSMV 是一套面向海洋生物多样性管理的全栈系统，当前项目已经落成可运行版本。

后端采用 Spring Boot 4 + MyBatis，前端采用 Vue 3 + Vite，当前已经覆盖这些核心模块：

- 用户与权限管理
- 物种档案管理
- 生态系统管理
- 观测记录管理
- 生态地图与统计报表
- 审计日志
- AI 增强服务

默认生态系统已包含 `湛江近海`，地图默认中心也已切到湛江近海海域。

## 运行环境

- JDK `17`
- Node.js `20+`
- npm `10+`
- MySQL `8.0+`
- IntelliJ IDEA

## 默认配置

- 数据库地址：`localhost:3306/gsmv`
- MySQL 用户名：`root`
- MySQL 密码：`123456`
- 后端端口：`8080`
- 前端开发端口：`5173`

默认管理员：

- 用户名：`admin`
- 密码：`123456`

## 目录说明

- `src/`：Spring Boot 后端代码
- `frontend/`：Vue 3 前端代码
- `scripts/`：本地启动与构建脚本
- `uploads/`：附件上传目录
- `docs/`：补充文档
- `.run/`：IDEA 可共享运行配置

## AI 服务说明

当前 AI 增强模块已经覆盖以下能力：

1. 图像智能识别与物种鉴定
   - 上传海洋生物图片
   - 调用阿里云百炼视觉模型识别
   - 返回最可能物种、候选列表、置信度、人工复核提示
   - 关联推荐系统内已有物种档案
2. 文本辅助分类与补全
   - 输入中文名或学名后自动补全门纲目科属种
   - 自动补充形态特征、生活习性、栖息环境、分布区域等字段
   - 支持文本润色与摘要生成
3. 观测记录智能标签与异常检测
   - 根据时间、地点、生态系统、环境参数生成标签
   - 对观测点与物种既有分布点的显著冲突进行异常提示
4. 智能问答与科研助手
   - 支持自然语言问题
   - 自动转结构化查询
   - 结合物种、观测、报表数据生成回答与证据摘要
5. 物种描述多语言支持
   - 支持把物种描述翻译成英文、日文、西班牙文等目标语言

## AI 密钥配置

项目不会把第三方 API Key 写进仓库文件。AI 服务通过环境变量读取密钥：

- `BAILIAN_API_KEY`
- `DEEPSEEK_API_KEY`

后端配置位置在 `src/main/resources/application.yml`，当前读取规则是：

- 百炼：`${BAILIAN_API_KEY:${DASHSCOPE_API_KEY:}}`
- DeepSeek：`${DEEPSEEK_API_KEY:}`

Windows PowerShell 临时设置示例：

```powershell
$env:BAILIAN_API_KEY="你的百炼Key"
$env:DEEPSEEK_API_KEY="你的DeepSeek Key"
```

如果你希望 IDEA 启动时也能直接使用 AI 功能，建议在系统环境变量或 IDEA Run Configuration 里设置这两个变量。

## 在 IDEA 中启动

### 1. 导入项目

1. 用 IntelliJ IDEA 打开项目根目录 `D:\java\GSMV`
2. 等待 IDEA 识别 Maven 项目
3. 把 Project SDK 设置为 `JDK 17`
4. 确保 IDEA 已配置可用的 Node.js

### 2. 准备 MySQL

确认本机 MySQL 已启动，然后创建数据库：

```sql
CREATE DATABASE IF NOT EXISTS gsmv DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 3. 启动后端

后端启动类：

- `src/main/java/com/gsmv/GsmvApplication.java`

常用方式有两种：

1. 直接运行 `GsmvApplication`
2. 在 Maven 面板执行 `spring-boot:run`

启动成功后可访问：

- [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

如果返回 `{"status":"UP"}`，说明后端已正常启动。

### 4. 启动前端

前端目录：

- `frontend`

首次安装依赖：

```bash
cd frontend
npm install
```

启动方式：

```bash
cd frontend
npm run dev
```

前端启动成功后访问：

- [http://localhost:5173](http://localhost:5173)

### 5. 使用项目自带的 IDEA Run Configuration

项目里已经提供了可共享运行配置：

- `.run/GSMV Backend.run.xml`
- `.run/GSMV Frontend.run.xml`
- `.run/GSMV Full Stack.run.xml`

推荐直接运行 `GSMV Full Stack`。

如果第一次打开项目后没有立刻看到这些配置，执行一次 Maven Reload 或重启 IDEA 即可。

## 数据库迁移

当前项目已包含多份迁移脚本，位于：

- `src/main/resources/db/migration/`

正常情况下，后端启动后会自动执行 Flyway 迁移。

## 命令行启动

Windows：

- 后端：`scripts\dev-backend.bat`
- 前端：`scripts\dev-frontend.bat`
- 一键构建：`scripts\build-all.bat`

macOS / Linux：

- 后端：`sh scripts/dev-backend.sh`
- 前端：`sh scripts/dev-frontend.sh`
- 一键构建：`sh scripts/build-all.sh`

## 主要页面入口

- 登录页：`/login`
- 用户注册：`/register`
- 物种档案：`/species`
- 生态系统：`/ecosystems`
- 生态地图：`/eco-map`
- 观测记录：`/observations`
- AI 助手：`/assistant`
- 统计报表：`/reports`
- 用户权限：`/users`
- 个人中心：`/profile`

## 已验证命令

这些命令已在当前项目上通过：

- `./mvnw.cmd -DskipTests compile`
- `./mvnw.cmd -DskipTests package`
- `cd frontend && npm run build`
- `scripts\build-all.bat`

## 常见问题

### 1. 页面能打开但 AI 接口报不可用

优先检查：

- 后端是否已经启动在 `8080`
- `BAILIAN_API_KEY` 是否已设置
- `DEEPSEEK_API_KEY` 是否已设置
- [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health) 是否返回 `UP`

### 2. 新增观测记录时小地图显示不完整

这个问题已经修复。当前版本在弹窗打开后会自动刷新 Leaflet 地图尺寸，避免地图区域只显示一部分。

### 3. IDEA 看不到 `.run` 里的运行配置

可以按这个顺序处理：

1. 确认打开的是项目根目录 `D:\java\GSMV`
2. 执行一次 Maven Reload
3. 重启 IDEA

### 4. Chrome 页面文案和源码不一致

如果浏览器开启了自动翻译，可能会把中文页面错误翻译成别的词。建议关闭页面翻译后再刷新。
