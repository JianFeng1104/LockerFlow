# LockerFlow Architecture

LockerFlow 是单体后端加单页前端的分层应用，没有微服务、消息队列或分布式锁。

```text
Vue 3 SPA
  ├─ Vue Router：路由与角色入口
  ├─ Pinia：认证会话与 UI 通知
  └─ Axios：真实 REST 请求
          ↓ /api
Spring Security Filter Chain
  ├─ HS256 JWT 验证
  ├─ Stateless session policy
  └─ URL namespace RBAC
          ↓
Controller → Service → Repository → MySQL 8
               ├─ Flyway V1 / V2
               ├─ 事务与锁
               └─ 过期调度器
```

## Backend

- Controller 只负责 HTTP、DTO 校验和从 JWT 解析 actor ID。
- Service 负责角色/状态/所有权规则、事务和状态迁移。
- Repository 使用 Spring Data JPA，关键生命周期查询使用显式行锁。
- Hibernate 设置 `open-in-view=false` 与 `ddl-auto=validate`；Flyway 是唯一 Schema 演进机制。
- API 错误通过统一异常处理器输出 JSON。

## Authentication and authorization

登录使用 BCrypt 验证密码并签发 HS256 JWT。Token 含 subject、issuer、issued-at、expiry、JWT ID、roles 和 username。除 `/api/auth/login` 外所有 API 都要求认证；管理员、快递员和用户写接口按 URL namespace 做 RBAC。

快递员入柜身份与用户取件身份都来自 JWT subject。用户取件还会校验包裹所有权，客户端不能通过请求体伪造 actor ID。

## Storage transaction

入柜流程在新的事务中完成：

1. 校验唯一快递单号、有效用户/快递员和正常站点。
2. 按尺寸与柜格编号选择最佳适配空闲柜格。
3. 将柜格设为 `OCCUPIED`，包裹设为 `STORED`。
4. 生成一次性取件码并只持久化 BCrypt 哈希。
5. 任一步失败则事务整体回滚。

柜格实体带 `@Version`。发生乐观锁冲突时，协调服务最多执行三次新的事务尝试，避免在已经标记 rollback-only 的事务中重试。

## Pickup and expiration concurrency

取件先以 `PESSIMISTIC_WRITE` 锁定包裹，再锁定活动取件码，随后更新关联柜格。过期处理采用相同的生命周期顺序。统一的逻辑锁顺序为：

```text
Parcel → PickupCode → LockerCell
```

因此同一包裹的取件/过期竞争只能有一个有效结果；确定性集成测试覆盖竞争、回滚和重复请求。

## Frontend

前端 UI 已简体中文化，但 API 枚举保持英文，集中通过 `displayText.js` 映射。会话 Access Token 只保存在 `sessionStorage`；Axios 请求拦截器附加 Bearer Token，`401` 会清理会话并返回登录页。

三个角色使用独立导航和页面，均包含 Loading、Error、Empty 与操作反馈状态。入柜成功弹窗中的明文取件码只保存在 Vue 组件内存，关闭后清除。

## Runtime and deployment

后端使用环境变量注入数据库连接和 JWT 密钥，可打包为可执行 JAR，并与 MySQL 部署到 Railway。前端生产构建输出静态 `dist/` 并部署到 Vercel，通过同源 `/api` 反向代理访问 Railway 后端。当前部署不依赖 Docker。

生产地址为 `https://locker-flow.vercel.app`，API 地址为 `https://backend-production-f1f4f.up.railway.app`。已通过公网浏览器流程验证 Vercel rewrite、Spring Security/JWT、事务化入柜与取件、MySQL 状态持久化，以及取件完成后的柜格释放。
