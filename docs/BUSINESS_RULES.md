# LockerFlow Business Rules

本文档描述当前实现的领域规则。前端显示中文，但后端枚举和 API 值保持英文。

## 用户与角色

- 角色为 `ADMIN`、`COURIER`、`CUSTOMER`，状态为 `ACTIVE` 或 `DISABLED`。
- 只有有效账号可以登录和参与业务流程。
- 管理员维护站点/柜格和执行过期处理；快递员入柜并查看自己的包裹；用户查看并领取自己的包裹。
- 快递员与用户 actor ID 来自 JWT subject，而不是请求体。

## 站点

- 新站点初始为 `ACTIVE`。
- 合法转换：`ACTIVE → MAINTENANCE|DISABLED`，`MAINTENANCE|DISABLED → ACTIVE`。
- 存在占用柜格或仍物理阻塞的包裹时，站点不能进入维护或停用。
- 只有 `ACTIVE` 站点可接受新包裹。

## 柜格

- 尺寸：`SMALL`、`MEDIUM`、`LARGE`。
- 状态：`AVAILABLE`、`OCCUPIED`、`MAINTENANCE`、`DISABLED`。
- 管理员只能执行 `AVAILABLE → MAINTENANCE|DISABLED` 与 `MAINTENANCE|DISABLED → AVAILABLE`。
- `OCCUPIED` 由入柜/取件流程管理，不能手动设置或释放。
- 同一站点内柜格编号唯一，保存时标准化为大写。

## 最佳适配分配

- `SMALL` 包裹依次尝试 `SMALL → MEDIUM → LARGE`。
- `MEDIUM` 包裹依次尝试 `MEDIUM → LARGE`。
- `LARGE` 包裹只使用 `LARGE`。
- 每个尺寸内按柜格编号升序选择第一个 `AVAILABLE` 柜格。
- 无兼容柜格时返回冲突，不创建半完成包裹。

## 包裹入柜

- 快递单号全局唯一并标准化为大写。
- customer 必须是有效 `CUSTOMER`；当前 JWT actor 必须是有效 `COURIER`。
- 成功后包裹为 `STORED`、柜格为 `OCCUPIED`，默认有效期为入柜后 48 小时。
- 包裹、柜格和取件码处于同一业务事务，失败整体回滚。

## 取件码与取件

- 取件码是 `SecureRandom` 生成的 6 位数字。
- 明文只在入柜成功响应中返回一次；数据库只保存 BCrypt 哈希。
- 取件要求：当前用户拥有包裹；包裹为 `STORED` 且未到期；柜格为 `OCCUPIED`；活动取件码未到期且哈希匹配。
- 成功后取件码为 `USED`、包裹为 `PICKED_UP`、柜格为 `AVAILABLE`。
- 重复取件、错误所有者、错误/过期码都不会产生部分状态更新。

## 过期

- 到期边界为 `expiresAt <= 当前服务器时间`。
- `STORED` 包裹变为 `EXPIRED`，`ACTIVE` 取件码变为 `EXPIRED`。
- 过期操作幂等，可由调度器或管理员手动触发。
- 逻辑过期不等于物理清柜：柜格继续保持 `OCCUPIED`。
- 物理清柜与退回快递员流程当前未实现。

## 并发

- 分柜采用乐观锁和最多三次的新事务重试。
- 取件和过期采用悲观行锁，避免同一包裹产生双重终态。
- 生命周期锁定顺序统一为 `Parcel → PickupCode → LockerCell`。
