# LockerFlow Screenshot Guide

截图保存在 `docs/screenshots/`，全部来自本地真实运行页面，不使用生成图或静态伪造数据。截图文件不包含密码、JWT、数据库凭据、请求头、开发者工具或真实个人敏感信息。

## 文件与用途

| 文件 | Route | 角色 | 展示重点 |
| --- | --- | --- | --- |
| `01-login.png` | `/login` | 未登录 | 中文登录页与产品定位 |
| `02-admin-dashboard.png` | `/admin` | ADMIN | 实时总容量、四种柜格状态和运维入口 |
| `03-locker-grid.png` | `/admin/stations/{stationId}` | ADMIN | 核心柜格视图、筛选和状态汇总 |
| `04-courier-store-parcel.png` | `/courier/store` | COURIER | 真实入柜表单与最佳适配流程 |
| `05-pickup-code-result.png` | `/courier/store` | COURIER | 入柜成功、分配柜格和一次性取件码 |
| `06-customer-parcels.png` | `/customer/parcels` | CUSTOMER | 用户拥有的待取件包裹 |
| `07-customer-pickup-success.png` | `/customer/parcels` | CUSTOMER | 取件成功通知与已取件状态 |
| `08-station-management.png` | `/admin/stations` | ADMIN | 站点容量、状态和管理入口 |

## 数据状态

- 站点和柜格状态必须来自现有合法 UI/API 流程。
- 核心 Grid 截图优先同时展示 `AVAILABLE`、`OCCUPIED`、`MAINTENANCE`、`DISABLED`。
- `OCCUPIED` 必须来自真实包裹入柜，不能由 SQL 或管理员强制制造。
- 一次性取件码截图完成后立即执行真实取件，使截图中的码失效。
- 取件完成后验证包裹为 `PICKED_UP`、取件码为 `USED`、原柜格恢复 `AVAILABLE`。

## 推荐裁剪

使用桌面宽度，保留侧边导航、页面标题和主要业务面板；不包含浏览器开发者工具或桌面其他应用。Locker Grid 和管理员工作台使用完整页面截图，弹窗类截图保留足够背景以说明所属业务页面。

## README 选择

README 优先使用 `03`、`02`、`04`、`05`、`06`、`07` 六张。登录页和站点管理页保留在文档目录，适合简历或面试演示时补充展示。
