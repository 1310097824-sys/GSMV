# GSMV 项目结构与文件说明

## 项目概览

GSMV（Global Species & Marine Vision）是一套面向海洋生物多样性管理与科研支撑的全栈 Web 系统，采用前后端分离架构。

```
GSMV/
├── src/                          # 后端 Java 源码 (Spring Boot)
│   ├── main/java/com/gsmv/       # 业务代码
│   ├── main/resources/           # 配置、数据库迁移脚本
│   └── test/                     # 测试代码
├── frontend/                     # 前端 Vue 3 工程
│   └── src/                      # 前端源码
├── docs/                         # 项目文档
├── scripts/                      # 开发/构建脚本
├── request/                      # 需求文档
├── uploads/                      # 本地上传文件存储目录
├── .run/                         # IDEA 共享运行配置
├── pom.xml                       # Maven 项目定义
└── README.md                     # 项目说明
```

---

## 一、后端源码结构 (`src/main/java/com/gsmv/`)

### 1. 应用入口

| 文件 | 说明 |
|------|------|
| `GsmvApplication.java` | Spring Boot 启动类，启用 MyBatis Mapper 扫描和配置属性扫描 |

### 2. `common/` — 通用基础设施

| 文件 | 说明 |
|------|------|
| `ApiResponse.java` | 统一 API 响应体：`{code, message, data, traceId, timestamp}`，提供 `success()` / `failure()` 静态工厂方法 |
| `PageResponse.java` | 分页响应体，封装 `items`、`total`、`page`、`size` |
| `ErrorCode.java` | 全局错误码常量定义（OK、BAD_REQUEST、UNAUTHORIZED、FORBIDDEN、NOT_FOUND、CONFLICT、VALIDATION_ERROR、INTERNAL_ERROR 等） |
| `TraceIdContext.java` | 基于 ThreadLocal 的 traceId 上下文传递，用于请求全链路追踪 |
| `exception/BusinessException.java` | 业务异常类，携带 code、message、HttpStatus |
| `exception/NotFoundException.java` | 资源不存在异常 |
| `handler/GlobalExceptionHandler.java` | `@RestControllerAdvice` 全局异常处理，将各类异常统一转换为 `ApiResponse` 格式 |

### 3. `config/` — 配置类

| 文件 | 说明 |
|------|------|
| `SecurityConfig.java` | Spring Security 核心配置：无状态会话、JWT 资源服务器、BCrypt 密码编码、HMAC-SHA256 签名、基于权限注解的方法级授权 |
| `WebConfig.java` | CORS 跨域配置（允许 localhost 任意端口）、RestClient Bean 配置（连接超时 15s、读取超时 90s） |
| `JacksonConfig.java` | Jackson 序列化配置（Java 8 时间模块等） |
| `JwtProperties.java` | `@ConfigurationProperties` 绑定 JWT 配置（issuer、secret、accessTokenTtlMinutes） |
| `AdminProperties.java` | `@ConfigurationProperties` 绑定管理员初始化配置（用户名、密码、是否启用） |
| `StorageProperties.java` | `@ConfigurationProperties` 绑定文件存储目录配置 |

### 4. `security/` — 认证与安全

| 文件 | 说明 |
|------|------|
| `JwtService.java` | JWT 令牌签发服务：生成包含 userId、displayName、roles、authorities 的签名令牌 |
| `CurrentUser.java` | 当前登录用户记录（userId、username、displayName） |
| `SecurityUtils.java` | 安全工具类：从 SecurityContext 提取当前用户 |
| `RestAuthenticationEntryPoint.java` | 认证失败时的 REST 响应处理（返回 401 JSON） |
| `RestAccessDeniedHandler.java` | 权限不足时的 REST 响应处理（返回 403 JSON） |
| `TraceIdFilter.java` | 请求过滤器：为每个请求生成/传递 traceId |

### 5. `auth/` — 登录注册

| 文件 | 说明 |
|------|------|
| `AuthController.java` | `/api/v1/auth/login`、`/api/v1/auth/register` 端点 |
| `AuthService.java` | 登录验证与注册业务逻辑 |
| `dto/LoginRequest.java` | 登录请求体（username、password） |
| `dto/LoginResponse.java` | 登录响应体（accessToken、expiresInSeconds、user） |
| `dto/RegisterRequest.java` | 注册请求体（username、password、displayName 等） |

### 6. `user/` — 用户与权限管理

| 文件 | 说明 |
|------|------|
| `model/SysUser.java` | 用户实体（id、username、passwordHash、displayName、email、phone、status、lastLoginAt 等） |
| `model/SysRole.java` | 角色实体（id、code、name、description） |
| `UserController.java` | 用户 CRUD、审批、头像上传等 REST 端点 |
| `UserService.java` | 用户管理业务逻辑 |
| `UserAvatarUrls.java` | 头像 URL 工具类 |
| `mapper/UserMapper.java` | 用户表 MyBatis Mapper |
| `mapper/RoleMapper.java` | 角色表 MyBatis Mapper |
| `mapper/PermissionMapper.java` | 权限表 MyBatis Mapper |
| `dto/UserView.java`、`UserCreateRequest.java` 等 | 用户相关 DTO |

### 7. `species/` — 物种档案管理（核心模块）

| 文件 | 说明 |
|------|------|
| `model/Species.java` | 物种实体（taxonId、protectionLevel、iucnStatus、description、morphology、habitat 等） |
| `model/Taxon.java` | 分类学节点实体（parentId、rank、scientificName、chineseName），支持门纲目科属种树形结构 |
| `SpeciesController.java` | 物种 CRUD、列表查询、图片上传、版本历史、回滚等 REST 端点 |
| `SpeciesService.java` | 物种核心业务逻辑：包含分类树自动创建（findOrCreateTaxon）、版本快照、审计日志等 |
| `mapper/SpeciesMapper.java` | 物种表 MyBatis Mapper |
| `mapper/TaxonMapper.java` | 分类表 MyBatis Mapper |
| `dto/` | 物种相关 DTO：`SpeciesView`（列表）、`SpeciesDetailView`（详情含图片）、`SpeciesSaveRequest`、`SpeciesVersionSnapshot`、`TaxonOption` 等 |

### 8. `ecosystem/` — 生态系统管理

| 文件 | 说明 |
|------|------|
| `model/Ecosystem.java` | 生态系统实体（name、type、description） |
| `EcosystemController.java` | 生态系统 CRUD REST 端点 |
| `EcosystemService.java` | 生态系统业务逻辑 |
| `mapper/EcosystemMapper.java` | 生态系统表 MyBatis Mapper |
| `dto/EcosystemSaveRequest.java` | 生态系统保存请求体 |

### 9. `observation/` — 观测记录管理（核心模块）

| 文件 | 说明 |
|------|------|
| `model/Observation.java` | 观测记录实体（ecosystemId、observerUserId、observedAt、经纬度、空间坐标点、环境参数 JSON、备注） |
| `ObservationController.java` | 观测记录 CRUD、版本历史、回滚等 REST 端点 |
| `ObservationService.java` | 观测记录核心业务逻辑：支持物种关联、去重校验、环境参数、版本快照 |
| `mapper/ObservationMapper.java` | 观测记录表 MyBatis Mapper |
| `dto/` | 观测相关 DTO：`ObservationView`、`ObservationDetailView`（含关联物种列表）、`ObservationSaveRequest`、`ObservationSpeciesInput`、`ObservationVersionSnapshot` 等 |

### 10. `audit/` — 审计日志

| 文件 | 说明 |
|------|------|
| `model/AuditLog.java` | 审计日志实体（userId、module、action、entityType、entityId、requestId、ip、success、detailJson） |
| `AuditController.java` | 审计日志分页查询端点 |
| `service/AuditService.java` | 审计日志记录服务 |
| `mapper/AuditMapper.java` | 审计日志表 MyBatis Mapper |
| `dto/AuditLogView.java` | 审计日志视图 |

### 11. `media/` — 文件/媒体管理

| 文件 | 说明 |
|------|------|
| `model/MediaFile.java` | 媒体文件实体（businessType、businessId、originalFilename、storedFilename、contentType、sizeBytes、storagePath、sha256） |
| `MediaFileController.java` | 文件上传/下载 REST 端点 |
| `MediaFileService.java` | 文件存储服务（本地文件系统保存） |
| `mapper/MediaFileMapper.java` | 媒体文件表 MyBatis Mapper |

### 12. `report/` — 统计报表

| 文件 | 说明 |
|------|------|
| `ReportController.java` | 报表数据查询、Excel/PDF 导出端点 |
| `ReportService.java` | 报表数据聚合服务（DashboardSummary、分布统计、趋势分析等） |
| `mapper/ReportMapper.java` | 报表查询 MyBatis Mapper |
| `dto/` | 报表相关 DTO：`DashboardSummary`、`NameValuePoint`、`SpeciesDistributionPoint`、`ObservationMapPoint`、`EcosystemAnalyticsPoint`、`ReportExportSnapshot` |
| `export/ReportExcelExporter.java` | Excel（XLSX）报表导出，使用 Apache POI，生成多个 Sheet |
| `export/ReportPdfExporter.java` | PDF 报表导出，使用 Apache PDFBox，支持中文字体、自动分页 |

### 13. `ai/` — AI 增强服务

| 文件 | 说明 |
|------|------|
| `AiProperties.java` | `@ConfigurationProperties` 绑定 AI 配置：百炼/DeepSeek 的 api-key、base-url、model 名称、低置信度阈值等 |
| `AiModelGateway.java` | AI 模型网关：封装对阿里云百炼（视觉模型）和 DeepSeek（文本模型）的 HTTP 调用，支持重试、图片压缩、JSON 解析容错、结构化文本兜底解析 |
| `AiController.java` | AI 功能 REST 端点：图像识别、物种自动补全、文本润色、翻译、观测分析 |
| `SpeciesAiService.java` | 物种 AI 服务：调用视觉模型识别图片中的海洋生物、调用文本模型进行物种档案补全/润色/翻译 |
| `ObservationAiService.java` | 观测 AI 服务：分析观测记录异常、生成观测摘要 |
| `AssistantAiService.java` | AI 科研助手：本地规则引擎驱动的自然语言问答，支持意图识别、多轮对话上下文继承、结构化查询、缓存、观测活动统计、物种分布分析、趋势摘要等 |
| `AssistantQueryCache.java` | AI 助手查询缓存（内存 LRU） |
| `dto/` | AI 相关 DTO：`SpeciesAiDtos`、`ObservationAiDtos`、`AssistantAiDtos` |
| `review/` | AI 复核工单子系统：`AiReviewTicketController`、`AiReviewTicketService`、`AiReviewTicket`（模型）、`AiReviewTicketMapper`、`AiReviewTicketDtos` |

### 14. `versioning/` — 数据版本回溯

| 文件 | 说明 |
|------|------|
| `model/EntityVersionRecord.java` | 版本记录实体（entityType、entityId、versionNo、action、snapshotJson、diffJson、changedBy、rollbackSourceVersionId） |
| `EntityVersionService.java` | 版本管理服务：记录版本、查询版本列表、读取历史快照、回滚 |
| `mapper/EntityVersionMapper.java` | 版本记录表 MyBatis Mapper |
| `dto/` | 版本相关 DTO：`EntityVersionView`、`EntityVersionRow`、`VersionFieldChangeView` |

### 15. `bootstrap/` — 系统初始化

| 文件 | 说明 |
|------|------|
| `AdminBootstrapRunner.java` | `ApplicationRunner` 实现，系统首次启动时自动创建默认管理员账号 |

---

## 二、后端资源文件 (`src/main/resources/`)

| 文件 | 说明 |
|------|------|
| `application.yml` | Spring Boot 主配置文件：数据源、Flyway、MyBatis、文件上传、日志格式、自定义 gsmv 配置块 |
| `db/migration/V1__init_schema.sql` | Flyway 迁移 V1：初始化全部业务表（sys_user、sys_role、sys_permission、sys_user_role、sys_role_permission、taxon、species、ecosystem、observation、observation_species、media_file、audit_log） |
| `db/migration/V2__seed_data.sql` | Flyway 迁移 V2：初始化角色、权限、基础分类学数据和示例数据 |
| `db/migration/V3~V12` | 后续增量迁移脚本：新增生态系统、用户注册增强、物种字段扩展、种子数据补充、行为支持、全球观测数据、AI 复核工单表、实体版本表等 |

---

## 三、前端源码结构 (`frontend/src/`)

### 入口与配置

| 文件 | 说明 |
|------|------|
| `main.ts` | Vue 应用入口：创建 App、注册 Pinia、Router、Element Plus |
| `App.vue` | 根组件 |
| `style.css` | 全局样式与 CSS 变量定义 |

### `router/index.ts` — 路由定义

| 路由 | 页面组件 | 说明 |
|------|----------|------|
| `/login` | `LoginView.vue` | 登录页（公开） |
| `/register` | `RegisterView.vue` | 注册页（公开） |
| `/dashboard` | `DashboardView.vue` | 综合仪表盘 |
| `/species` | `SpeciesView.vue` | 物种档案列表 |
| `/species/:id` | `SpeciesDetailView.vue` | 物种详情 |
| `/ecosystems` | `EcosystemView.vue` | 生态系统管理 |
| `/eco-map` | `EcoMapView.vue` | 生态地图 |
| `/observations` | `ObservationView.vue` | 观测记录管理 |
| `/assistant` | `AiAssistantView.vue` | AI 科研助手 |
| `/ai-reviews` | `AiReviewTicketsView.vue` | AI 复核工单 |
| `/reports` | `ReportsView.vue` | 统计报表 |
| `/audits` | `AuditView.vue` | 审计日志 |
| `/users` | `UsersView.vue` | 用户管理 |
| `/profile` | `ProfileView.vue` | 个人中心 |

路由守卫：未登录重定向到 `/login`，无权限路由重定向到 `/dashboard`。

### `stores/auth.ts` — 认证状态管理 (Pinia)

管理 token、用户档案（Profile）的持久化（localStorage），提供 `performLogin`、`logout`、`patchProfile` 等操作。

### `api/` — 后端 API 封装层

| 文件 | 说明 |
|------|------|
| `http.ts` | Axios 实例配置：baseURL、超时、JWT Bearer Token 注入、401 拦截自动跳转登录、统一错误提取 |
| `auth.ts` | 登录/注册 API |
| `species.ts` | 物种档案 CRUD、图片上传、AI 识图/补全/润色/翻译 API |
| `observations.ts` | 观测记录 CRUD、AI 分析 API |
| `ecosystems.ts` | 生态系统 CRUD API |
| `reports.ts` | 仪表盘、统计图表、地图数据、Excel/PDF 导出 API |
| `users.ts` | 用户 CRUD、审批、角色列表 API |
| `media.ts` | 文件上传 API |
| `audits.ts` | 审计日志查询 API |
| `ai.ts` | AI 助手问答、识图 API |
| `aiReview.ts` | AI 复核工单 API |

### `types/gsmv.ts` — TypeScript 类型定义

统一定义所有前后端交互数据结构：API 响应体、分页、用户、角色、物种、生态系统、观测记录、版本历史、审计日志、报表统计、AI 服务等类型。

### `constants/` — 常量定义

| 文件 | 说明 |
|------|------|
| `auth.ts` | 认证相关常量（localStorage key 名称） |
| `ecosystem.ts` | 生态系统类型选项 |

### `utils/` — 工具函数

| 文件 | 说明 |
|------|------|
| `dataSync.ts` | 数据同步工具 |
| `download.ts` | 文件下载工具（Blob 下载） |
| `observationEnv.ts` | 观测环境参数工具 |

### `components/` — 公共组件

| 文件 | 说明 |
|------|------|
| `ChartPanel.vue` | ECharts 图表面板组件 |
| `LeafletPicker.vue` | Leaflet 地图点选组件（经纬度选取） |
| `ReportMapPanel.vue` | 报表地图面板组件 |
| `StatCard.vue` | 统计卡片组件 |
| `VersionHistoryPanel.vue` | 版本历史面板组件 |

### `layouts/AppLayout.vue` — 主布局

左侧品牌卡片 + 导航菜单，顶部欢迎头部 + 用户操作，中央路由视图区。支持响应式布局。

### `views/` — 业务页面 (14 个)

| 文件 | 说明 |
|------|------|
| `LoginView.vue` | 登录页面 |
| `RegisterView.vue` | 注册页面 |
| `DashboardView.vue` | 综合仪表盘（统计卡片 + 图表 + 地图） |
| `SpeciesView.vue` | 物种列表（搜索、筛选、分页、CRUD 操作） |
| `SpeciesDetailView.vue` | 物种详情页（完整档案、图片、版本历史、AI 操作入口） |
| `EcosystemView.vue` | 生态系统管理页面 |
| `EcoMapView.vue` | 生态地图页面 |
| `ObservationView.vue` | 观测记录管理页面 |
| `ReportsView.vue` | 统计报表页面（图表 + 导出） |
| `AuditView.vue` | 审计日志查看页面 |
| `UsersView.vue` | 用户与权限管理页面 |
| `ProfileView.vue` | 个人中心页面 |
| `AiAssistantView.vue` | AI 科研助手对话页面 |
| `AiReviewTicketsView.vue` | AI 复核工单管理页面 |

---

## 四、数据库表一览

| 表名 | 说明 |
|------|------|
| `sys_user` | 系统用户 |
| `sys_role` | 角色定义 |
| `sys_permission` | 权限定义 |
| `sys_user_role` | 用户-角色关联 |
| `sys_role_permission` | 角色-权限关联 |
| `taxon` | 分类学树形节点（门纲目科属种） |
| `species` | 物种档案 |
| `ecosystem` | 生态系统 |
| `observation` | 观测记录（含空间坐标） |
| `observation_species` | 观测-物种关联 |
| `media_file` | 媒体文件元数据 |
| `audit_log` | 审计日志 |
| `entity_version_record` | 实体版本记录 |
| `ai_review_ticket` | AI 复核工单 |

---

## 五、部署与脚本

| 路径 | 说明 |
|------|------|
| `.run/GSMV Backend.run.xml` | IDEA 后端运行配置 |
| `.run/GSMV Frontend.run.xml` | IDEA 前端运行配置 |
| `.run/GSMV Full Stack.run.xml` | IDEA 全栈运行配置 |
| `scripts/dev-backend.bat` / `.sh` | 后端开发启动脚本 |
| `scripts/dev-frontend.bat` / `.sh` | 前端开发启动脚本 |
| `scripts/build-all.bat` / `.sh` | 全量构建脚本 |
| `mvnw` / `mvnw.cmd` | Maven Wrapper（无需预装 Maven） |
| `frontend/vite.config.ts` | Vite 构建配置（含 API 代理到后端 8080） |
| `frontend/tsconfig.json` | TypeScript 编译配置 |
| `frontend/package.json` | 前端依赖与脚本定义 |
