# LockerFlow Database

生产运行使用 MySQL 8，测试使用 H2 的 MySQL 兼容模式。Hibernate 只执行 Schema 校验（`ddl-auto=validate`），Flyway 是唯一数据库结构演进入口。

## Migration baseline

当前只有两个不可变迁移：

| Migration | 内容 | SHA-256 |
| --- | --- | --- |
| `V1__create_core_domain.sql` | 用户、站点、柜格、包裹及索引/外键 | `3F3680F5DD4E713D69432A4C8F1BCEA16BD78F28230D9BFFBA92005721E2ECAC` |
| `V2__create_pickup_codes.sql` | 一次性取件码及索引/外键 | `1013328CF672ECACB11FAEA7705A7B3A8C06CE9E127F0E8A807E317237599257` |

不存在 V3。已发布迁移不得修改；未来 Schema 变化必须新增后续迁移。

## Tables

### `app_users`

保存登录标识、联系方式、BCrypt 密码哈希、角色和状态。`username`、`email`、`phone` 分别唯一；`role,status` 有联合索引。

### `locker_stations`

保存站点名称、地址和运行状态。状态索引用于筛选正常/维护/停用站点。

### `locker_cells`

每个柜格通过 `station_id` 归属站点，保存编号、尺寸、状态和乐观锁 `version`。`(station_id, cell_code)` 唯一；站点、状态/尺寸均有查询索引。

### `parcels`

保存唯一快递单号、customer/courier 外键、可空的柜格外键、尺寸、状态及入柜/取件/到期时间。customer、courier、locker、status、`status+expires_at` 均有索引。

历史包裹在取件后仍保留柜格引用，用于追溯；柜格状态独立恢复为 `AVAILABLE`。

### `pickup_codes`

保存 parcel 外键、BCrypt `code_hash`、状态、失效时间和使用时间。表中不保存明文取件码。`parcel+status` 与 `status+expires_at` 索引分别支持取件和过期处理。

## Relationships

```text
app_users (CUSTOMER) 1 ── * parcels * ── 1 app_users (COURIER)
locker_stations       1 ── * locker_cells
locker_cells          1 ── * parcels
parcels               1 ── * pickup_codes
```

## Time and consistency

- 数据库存储的业务时间统一使用 UTC `Instant`。
- 基础实体通过 JPA 生命周期回调维护 `created_at` 与 `updated_at`。
- 柜格占用和包裹状态在事务中共同更新。
- 取件/过期的关键查询使用行锁，降低生命周期竞争的不一致风险。
