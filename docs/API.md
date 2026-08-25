# LockerFlow API

本文档对应当前 Spring Boot REST API。除登录接口外，所有接口都需要 `Authorization: Bearer <access-token>`。后端从 JWT subject 解析当前用户 ID，从 `roles` claim 解析角色；快递员和用户接口不接受可替代当前身份的 actor ID。

## 认证

### `POST /api/auth/login`

公开接口。请求体：

```json
{
  "username": "<local-username>",
  "password": "<local-password>"
}
```

成功返回 `accessToken`、`tokenType`、`expiresAt` 和当前用户信息。登录失败统一返回 `401`，不区分用户名不存在、密码错误或账号停用。

### `GET /api/auth/me`

角色：`ADMIN`、`COURIER`、`CUSTOMER`。返回当前 JWT 对应的有效用户。

## 站点与柜格

| Method | Path | Role | 说明 |
| --- | --- | --- | --- |
| `POST` | `/api/admin/stations` | ADMIN | 新建站点，初始状态为 `ACTIVE` |
| `GET` | `/api/stations?status=ACTIVE` | 任意已认证角色 | 查询站点，可选状态筛选 |
| `GET` | `/api/stations/{stationId}` | 任意已认证角色 | 查询站点及容量统计 |
| `PUT` | `/api/admin/stations/{stationId}` | ADMIN | 更新名称和地址 |
| `PATCH` | `/api/admin/stations/{stationId}/status` | ADMIN | 修改站点状态 |
| `GET` | `/api/stations/{stationId}/grid` | 任意已认证角色 | 查询实时柜格和四类状态汇总 |
| `POST` | `/api/admin/stations/{stationId}/cells` | ADMIN | 新建柜格 |
| `GET` | `/api/stations/{stationId}/cells` | 任意已认证角色 | 查询站点柜格 |
| `GET` | `/api/stations/{stationId}/cells/{cellId}` | 任意已认证角色 | 查询单个柜格 |
| `PATCH` | `/api/admin/stations/{stationId}/cells/{cellId}/status` | ADMIN | 修改非占用柜格的运维状态 |

站点状态：`ACTIVE`、`MAINTENANCE`、`DISABLED`。柜格状态：`AVAILABLE`、`OCCUPIED`、`MAINTENANCE`、`DISABLED`。`OCCUPIED` 只能由包裹流程自动设置和释放。

## 快递员包裹

### `GET /api/courier/parcels`

角色：`COURIER`。只返回当前 JWT subject 对应快递员的包裹，按创建时间倒序。

### `POST /api/courier/parcels`

角色：`COURIER`。执行真实入柜事务，请求体：

```json
{
  "trackingNumber": "LF-DEMO-YYYYMMDD-XXXX",
  "customerId": 1,
  "stationId": 1,
  "size": "SMALL"
}
```

成功返回 `201`，响应包含包裹、已分配柜格、一次性明文取件码和失效时间。明文取件码只在该响应中出现，数据库仅保存 BCrypt 哈希。

## 用户包裹与取件

### `GET /api/customer/parcels`

角色：`CUSTOMER`。只返回当前 JWT subject 对应用户的包裹。

### `POST /api/customer/parcels/{parcelId}/pickup`

角色：`CUSTOMER`。请求体：

```json
{
  "pickupCode": "<six-digit-one-time-code>"
}
```

后端验证当前用户所有权、包裹和取件码状态、双方失效时间及 BCrypt 哈希。成功后包裹变为 `PICKED_UP`、取件码变为 `USED`、柜格恢复 `AVAILABLE`。

## 管理员运维

### `POST /api/admin/operations/expiration/run`

角色：`ADMIN`。按服务器时钟处理到期的 `STORED` 包裹和 `ACTIVE` 取件码。操作幂等；过期包裹对应柜格仍保持 `OCCUPIED`。

## 错误响应

错误统一为 JSON：

```json
{
  "timestamp": "2026-08-25T00:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/example",
  "fieldErrors": {
    "field": "validation message"
  }
}
```

常用状态码：`400` 校验失败、`401` 未认证、`403` 角色不允许、`404` 资源不存在、`409` 状态或并发冲突。
