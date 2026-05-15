# GSMV 技术栈详解

## 总览

GSMV 采用 **前后端分离** 的 B/S 架构，后端基于 **Spring Boot 4** 生态，前端基于 **Vue 3** 生态，数据存储使用 **MySQL 8**，AI 能力接入 **阿里云百炼** 与 **DeepSeek** 大模型。

---

## 一、后端技术栈

### 1.1 核心框架：Spring Boot 4.0.5

**使用位置：** 整个后端项目的基座，`pom.xml` 中以 `spring-boot-starter-parent:4.0.5` 为父 POM。

**涉及模块：**

| Starter | 用途 | 使用位置 |
|---------|------|----------|
| `spring-boot-starter-webmvc` | RESTful API 的 Web 层（基于 Servlet 容器，默认嵌入 Tomcat） | 所有 Controller 层 |
| `spring-boot-starter-json` | Jackson 序列化/反序列化（Java 对象 ↔ JSON） | 全局 API 响应、请求体解析、版本快照 JSON 存储 |
| `spring-boot-starter-validation` | Jakarta Bean Validation 参数校验 | 所有 `@Valid` 标注的 Request DTO |
| `spring-boot-starter-security` | 认证与授权框架 | `SecurityConfig.java` 配置全栈安全策略 |
| `spring-boot-starter-oauth2-resource-server` | OAuth2 资源服务器，此处用作 JWT Bearer Token 验证 | `SecurityConfig.java` 中 `.oauth2ResourceServer()` 配置 |
| `spring-boot-starter-actuator` | 应用健康检查与监控端点（`/actuator/health`、`/actuator/info`、`/actuator/metrics`） | 运维监控、健康探针 |
| `spring-boot-starter-test` | 测试支持（JUnit 5、Mockito 等） | `src/test/` 目录下的测试代码 |
| `spring-security-test` | Spring Security 测试支持 | 安全相关单元测试 |

### 1.2 持久层：MyBatis + Flyway + MySQL

#### MyBatis 4.0.0 (`mybatis-spring-boot-starter`)

**使用位置：** 数据访问层，所有 `mapper/` 目录下的接口。

- 采用 **纯注解 SQL** 或 **XML Mapper** 方式编写 SQL
- 配置了 `map-underscore-to-camel-case: true`，实现数据库下划线命名到 Java 驼峰命名的自动映射
- `default-statement-timeout: 15` 秒
- `@MapperScan("com.gsmv.**.mapper")` 自动扫描所有 Mapper 接口

**涉及的所有 Mapper 文件：**
- `SpeciesMapper.java` — 物种 CRUD、分页查询、观测引用计数
- `TaxonMapper.java` — 分类学节点 CRUD、父子查询
- `ObservationMapper.java` — 观测记录 CRUD、分页查询、物种关联批量操作、空间查询
- `EcosystemMapper.java` — 生态系统 CRUD
- `UserMapper.java` — 用户 CRUD、按 username 查询
- `RoleMapper.java` — 角色 CRUD、用户-角色关联
- `PermissionMapper.java` — 权限查询
- `AuditMapper.java` — 审计日志分页查询
- `MediaFileMapper.java` — 媒体文件元数据 CRUD
- `ReportMapper.java` — 统计聚合查询（GROUP BY、JOIN、空间查询）
- `EntityVersionMapper.java` — 版本记录 CRUD
- `AiReviewTicketMapper.java` — AI 复核工单 CRUD

#### Flyway

**使用位置：** `src/main/resources/db/migration/` 目录下的 SQL 迁移脚本。

- 12 个迁移版本（V1 ~ V12），按版本号顺序执行
- 从初始化 schema 到增量功能（版本回溯、AI 复核工单等）逐步演进
- 确保不同环境数据库结构的一致性

#### MySQL 8.0+

**使用位置：** 数据存储引擎，通过 `mysql-connector-j` 驱动连接。

- 字符集：`utf8mb4`，排序规则：`utf8mb4_unicode_ci`
- 时区：`Asia/Shanghai`
- 使用 MySQL 空间数据类型 `POINT` + `SPATIAL INDEX`（`observation` 表的 `location_point` 字段 SRID 4326）
- JSON 列类型：`observation.env_json`、`audit_log.detail_json`

### 1.3 安全：Spring Security + JWT (HMAC-SHA256)

**使用位置：** `config/SecurityConfig.java`、`security/` 包。

**安全架构：**

```
                      ┌─────────────────┐
                      │   前端 Vue App   │
                      │  (localStorage   │
                      │   存 JWT Token)  │
                      └────────┬────────┘
                               │ Authorization: Bearer <token>
                               ▼
                      ┌─────────────────┐
                      │  SecurityFilter  │
                      │  (无状态 Session) │
                      └────────┬────────┘
                               │
                      ┌────────▼────────┐
                      │ OAuth2 Resource  │
                      │ Server (JWT)     │
                      │ → JwtDecoder     │
                      │ → HMAC-SHA256    │
                      └────────┬────────┘
                               │
                      ┌────────▼────────┐
                      │ @EnableMethod    │
                      │ Security         │
                      │ (注解级权限)      │
                      └─────────────────┘
```

- **认证方式：** JWT Bearer Token，由 `JwtService` 使用 `NimbusJwtEncoder` 签发，HMAC-SHA256 签名
- **密码编码：** `BCryptPasswordEncoder(12)` — 12 轮 BCrypt 哈希
- **会话策略：** `SessionCreationPolicy.STATELESS` — 完全无状态
- **权限模型：** RBAC（Role-Based Access Control）—— 用户 → 角色 → 权限
- **CSRF：** 已禁用（前后端分离 + 无状态 Token 场景下不需要）
- **CORS：** 允许 `localhost:*` 跨域请求
- **公开端点：** `/api/v1/auth/login`、`/api/v1/auth/register`、头像/图片获取、Actuator health/info
- **JWT Claims 结构：** `sub`(username)、`userId`、`displayName`、`roles`、`authorities`、`iss`、`iat`、`exp`
- **Token 有效期：** 30 分钟（通过 `gsmv.security.jwt.access-token-ttl-minutes` 配置）

### 1.4 AI 集成：多模型网关

**使用位置：** `ai/AiModelGateway.java`、`ai/AiProperties.java`。

**架构设计：**

```
┌──────────────────────────────────────┐
│           AiModelGateway             │
│  (统一网关，封装所有 AI 调用)          │
├──────────────────────────────────────┤
│  ┌────────────┐  ┌────────────────┐  │
│  │ 阿里云百炼   │  │   DeepSeek     │  │
│  │ (视觉+文本)  │  │   (纯文本)     │  │
│  │ qwen-vl-plus │  │ deepseek-chat  │  │
│  └────────────┘  └────────────────┘  │
└──────────────────────────────────────┘
```

- **阿里云百炼（DashScope）：**
  - 视觉模型 `qwen-vl-plus`：图像识别（海洋生物物种鉴定）
  - 调用方式：通过 Spring `RestClient` 发 HTTP POST 到 `https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions`
  - 图片预处理：缩放到最大 1600px、JPEG 压缩（质量 0.72~0.82）、Base64 Data URL 传输
  - API Key 通过环境变量 `BAILIAN_API_KEY` 或 `DASHSCOPE_API_KEY` 注入

- **DeepSeek：**
  - 文本模型 `deepseek-chat`：物种档案补全、文本润色、翻译、观测分析
  - 调用方式：通过 Spring `RestClient` 发 HTTP POST 到 `https://api.deepseek.com/chat/completions`
  - 使用 `response_format: json_object` 要求结构化 JSON 输出
  - API Key 通过环境变量 `DEEPSEEK_API_KEY` 注入

- **重试机制：** 最多 2 次重试，间隔 350ms × 尝试次数，对 5xx 和 429 状态码重试
- **JSON 解析容错：** 三层降级——标准 ObjectMapper → 宽松模式 ObjectMapper（支持注释、单引号、无引号字段名）→ 正则表达式结构化文本兜底解析
- **超时配置：** 连接超时 15s，读取超时 90s（`WebConfig.java` 中配置 `RestClient.Builder`）

### 1.5 报告导出

**使用位置：** `report/export/` 包。

#### Apache POI 5.4.1 (`poi-ooxml`)

- **格式：** Excel `.xlsx`（XSSFWorkbook）
- **导出内容：** 多 Sheet 报表（Summary、保护等级分布、IUCN 分布、门/纲统计、观测趋势、观测人员活跃度、生态系统统计、物种地图点位、观测地图点位）
- **使用位置：** `ReportExcelExporter.java`

#### Apache PDFBox 2.0.32

- **格式：** PDF 文档
- **中文字体：** 自动查找 Windows 系统字体（`simhei.ttf` → `NotoSansSC-VF.ttf` → `simsunb.ttf` → 退回 Helvetica）
- **布局：** A4 横向、自动分页、文本自动换行
- **使用位置：** `ReportPdfExporter.java`

### 1.6 其他依赖

| 依赖 | 用途 | 使用位置 |
|------|------|----------|
| `jackson-datatype-jsr310` | Java 8 时间 API（LocalDateTime 等）与 JSON 互转 | 全局 Jackson 序列化 |
| `lombok` | 编译期代码生成（getter/setter 等），`optional=true` | 各 Model 实体类（部分使用） |

### 1.7 项目基础设施

| 组件 | 技术实现 | 位置 |
|------|----------|------|
| 统一响应格式 | `ApiResponse<T>` record 类 | `common/ApiResponse.java` |
| 全链路追踪 | `TraceIdContext`（ThreadLocal）+ `TraceIdFilter` + 日志 MDC `%X{traceId}` | `common/TraceIdContext.java`、`security/TraceIdFilter.java` |
| 全局异常处理 | `@RestControllerAdvice` + `ResponseEntity` | `common/handler/GlobalExceptionHandler.java` |
| 系统初始化 | `ApplicationRunner` 启动时创建默认管理员 | `bootstrap/AdminBootstrapRunner.java` |
| 配置属性绑定 | `@ConfigurationProperties` + `@ConfigurationPropertiesScan` | `config/` 包下各 Properties 类 |
| 审计日志 | AOP 风格的手动埋点 `AuditService.record()` | 各 Service 层（`SpeciesService`、`ObservationService`、`AssistantAiService` 等） |
| 数据版本管理 | 通用 `EntityVersionService`，支持 SPECIES 和 OBSERVATION 两种实体类型 | `versioning/` 包 |

---

## 二、前端技术栈

### 2.1 核心框架

| 技术 | 版本 | 用途 | 使用位置 |
|------|------|------|----------|
| **Vue 3** | 3.5.32 | 前端 MVVM 框架，Composition API + `<script setup>` 语法 | 所有 `.vue` 组件 |
| **TypeScript** | 6.0.2 | 类型安全的前端开发 | 所有 `.ts` 文件 + Vue SFC 的 `<script setup lang="ts">` |
| **Vite** | 8.0.4 | 开发服务器（HMR）+ 生产构建（Rolldown） | `vite.config.ts` |
| **Vue Router** | 5.0.4 | 前端路由（History 模式） | `router/index.ts` |
| **Pinia** | 3.0.4 | 状态管理（替代 Vuex） | `stores/auth.ts` |

### 2.2 UI 组件库：Element Plus 2.13.6

**使用位置：** 全局 UI 组件（表格、表单、对话框、按钮、标签、头像、消息提示等）。

**涉及组件：** `el-table`、`el-form`、`el-dialog`、`el-button`、`el-tag`、`el-avatar`、`el-icon`、`el-input`、`el-select`、`el-pagination`、`el-upload`、`el-card`、`el-tabs`、`el-popconfirm`、`el-descriptions` 等。

### 2.3 图表可视化

#### ECharts 6.0.0

**使用位置：** `components/ChartPanel.vue`（封装组件）、各报表/仪表盘页面。

**涉及图表类型：** 柱状图、饼图、折线图（趋势）、散点图（地图点位分布）。

#### Leaflet 1.9.4 + `@types/leaflet`

**使用位置：** `components/LeafletPicker.vue`（地图点选组件）、`components/ReportMapPanel.vue`（报表地图面板）、`views/EcoMapView.vue`（生态地图页面）。

**功能：** 交互式地图展示、经纬度点选、物种分布点位标注、观测地点标注。

### 2.4 HTTP 通信：Axios 1.15.0

**使用位置：** `api/http.ts` 统一封装 + `api/` 目录下各业务模块 API。

- 实例配置：`baseURL: '/api'`（开发环境通过 Vite proxy 转发到 `http://localhost:8080`）
- 默认超时：15 秒
- 请求拦截器：自动注入 `Authorization: Bearer <token>`
- 响应拦截器：401 自动清除登录态并跳转登录页；提取错误消息
- `unwrap<T>()` 工具函数：根据 `ApiResponse.code === 'OK'` 判断业务成功，提取 `data` 字段

### 2.5 图标库：`@element-plus/icons-vue`

**使用位置：** `layouts/AppLayout.vue` 的导航菜单图标（`DataAnalysis`、`Document`、`Location`、`MapLocation`、`Notebook`、`ChatDotRound`、`Finished`、`Histogram`、`Setting`、`User`）。

---

## 三、数据库设计关键技术点

### 3.1 空间数据支持

```sql
location_point POINT NOT NULL SRID 4326,
SPATIAL INDEX sp_idx_obs_point (location_point)
```

- MySQL 空间扩展用于存储观测经纬度
- 使用 WGS 84 坐标系（SRID 4326）
- 空间索引支持高效的地理范围查询

### 3.2 自引用分类树

`taxon` 表通过 `parent_id` 自引用外键构建门→纲→目→科→属→种的树形分类结构。应用层通过 `findOrCreateTaxon()` 方法自动维护分类层级。

### 3.3 JSON 灵活字段

- `observation.env_json`：存储环境参数（水温、盐度、pH、溶解氧、透明度、深度、天气、海况等）
- `audit_log.detail_json`：存储审计详情
- `entity_version_record.snapshot_json` / `diff_json`：存储实体版本的完整快照和字段变更差异

---

## 四、部署架构

```
┌──────────────────────────────────────────┐
│              用户浏览器                    │
│         http://localhost:5173             │
└──────────────────┬───────────────────────┘
                   │ 开发: Vite Dev Server
                   │ 生产: Nginx / 静态文件
                   ▼
┌──────────────────────────────────────────┐
│          前端 (Vue 3 + Vite)              │
│  /api → proxy → http://localhost:8080    │
└──────────────────┬───────────────────────┘
                   │ REST API (JSON)
                   ▼
┌──────────────────────────────────────────┐
│       后端 (Spring Boot 4 + Tomcat)       │
│              http://localhost:8080        │
│                                           │
│  Controller → Service → Mapper → DB      │
│                    ↓                      │
│              AiModelGateway              │
│               → 阿里云百炼 / DeepSeek     │
└──────────────────┬───────────────────────┘
                   │ JDBC
                   ▼
┌──────────────────────────────────────────┐
│            MySQL 8.0 (gsmv)              │
│              localhost:3306              │
└──────────────────────────────────────────┘
```

---

## 五、开发工具链

| 工具 | 用途 |
|------|------|
| Maven Wrapper (`mvnw`) | 无需预装 Maven 即可构建 |
| Flyway | 数据库版本迁移管理 |
| IntelliJ IDEA 2025+ | 推荐 IDE，提供共享运行配置 |
| Vite Dev Server | 前端热更新开发服务器 |
| ESLint / vue-tsc | TypeScript + Vue 类型检查（构建时） |
