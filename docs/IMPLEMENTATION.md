# GSMV 实施说明

## 后端

- 技术栈: Spring Boot 4.0.5、MyBatis、Flyway、Spring Security、MySQL 8、Actuator
- 包结构:
  - `com.gsmv.auth`: 登录认证
  - `com.gsmv.user`: 用户、角色、权限
  - `com.gsmv.species`: 分类与物种
  - `com.gsmv.ecosystem`: 生态系统
  - `com.gsmv.observation`: 观测记录
  - `com.gsmv.audit`: 审计日志
  - `com.gsmv.report`: 统计报表
  - `com.gsmv.media`: 附件上传
- 数据迁移:
  - `V1__init_schema.sql`: 初始化完整业务表
  - `V2__seed_data.sql`: 初始化角色、权限、基础分类和示例数据

## 前端

- 技术栈: Vue 3、Vite、Pinia、Vue Router、Element Plus、Axios、Leaflet、ECharts
- 页面:
  - `/login`
  - `/dashboard`
  - `/species`
  - `/ecosystems`
  - `/observations`
  - `/reports`
  - `/audits`
  - `/users`

## 约定

- 前后端统一使用 `/api/v1` 前缀
- 响应结构统一为 `{ code, message, data, traceId, timestamp }`
- 开发期通过 `frontend/vite.config.ts` 将 `/api` 代理到 `http://localhost:8080`
- 附件默认保存到 `${user.dir}/uploads`

## 后续可扩展点

- 观测记录编辑与删除
- 附件上传在前端界面的接入
- 物种详情页与附件预览
- 更细的统计筛选和导出能力
- OpenAPI 文档与集成测试补全
