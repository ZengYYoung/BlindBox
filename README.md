# BlindBox · 收藏一点惊喜

React + Spring Boot 的盲盒收藏平台，包含商品浏览、库存驱动抽奖、钱包、订单、收藏、晒单与管理员配货。当前运行后端为 `blindbox-springboot`，`BlindBox-backend` 是旧 Node/SQLite 参考源码。

**每款中奖概率 = 该款剩余实体数量 / 奖池剩余总量。抽走后概率随库存变化，稀有度不设置独立固定概率。**

## 文档导航

| 文档 | 阅读内容 |
|---|---|
| [关键技术文档](docs/关键技术文档.md) | 抽奖机制、事务/锁、Redis缓存、限流与并发；逐项关联源码、SQL和测试 |
| [接入与部署指南](docs/接入与部署指南.md) | MySQL/Redis、环境变量、迁移、管理员、联调及Docker模板 |
| [API契约](docs/API契约.md) | 请求、金额、分页、幂等、响应和错误码 |
| [项目介绍与面试指南](docs/项目介绍与面试指南.md) | 使用方式、数据表、设计取舍与面试追问 |
| [并发优化说明](docs/并发优化说明.md) | 并发预算、配置、代理来源额度、指标和压测方法 |
| [消息队列接入方案](docs/消息队列接入方案.md) | RabbitMQ 本机接入、抽奖通知、Outbox 与可靠性边界 |
| [Git版本控制指南](docs/Git版本控制指南.md) | 根仓库、旧Git历史备份和提交同步方式 |
| [验收记录](docs/验收记录.md) | 有日期的验证证据、本次文档检查及未验证范围 |
| [历史文档归档](outdate/README.md) | 过期面试资料、升级计划与旧验收记录；包含归档原因和替代入口 |

旧资料中的 Redis 库存预扣、initStock 回补、CAS 重试和 JwtInterceptor 不代表当前实现。查技术细节请从关键技术文档开始。

## 当前实现

- 按剩余数量划分整数票号区间，无放回抽样。
- MySQL事务锁定用户、奖池和奖品库存；条件扣款/扣库存、订单及资金/库存流水共同提交。
- 抽奖、开发充值、建池和补货幂等；前端持久化未知结果的请求ID以查询和重试。
- Spring Security JWT与数据库当前角色授权。
- Redis缓存商品元数据；库存、售价和状态每次批量查库，详情使用单条无锁快照查询。
- JWT前入口保护、事务前全局/同用户/同池准入、Sentinel保护Redis缓存与限流、异步缓存失效与指标。
- 管理员编辑、配货、补货、上下架和归档；订单保存成交时商品快照。
- 可选 RabbitMQ 抽奖通知：事务内 Outbox、发布确认、消费幂等与死信；启用和限制见接入方案。
- 社区、唯一点赞关系、分页查询与真实图片编码校验；前端复用根目录images原图。

## 本地运行

先按 [接入指南](docs/接入与部署指南.md) 准备数据库、Redis和权限。后端需要 JDK 17+ / Maven，前端 Node 22.12+ / npm。环境变量填入 `blindbox-springboot/.env`，不要提交实际密码和JWT密钥。已有配置不必重复创建；已有数据库不要重复导入种子数据。

首次配置时复制模板并编辑：

```bash
cp -n blindbox-springboot/.env.example blindbox-springboot/.env
```

终端一，在项目根目录启动后端：

```bash
python3 scripts/run-backend.py
```

终端二，启动前端：

```bash
cd BlindBox-frontend
npm ci
npm run dev
```

浏览器打开 [前端页面](http://localhost:5173/)。5173 提供界面并代理 `/api`、`/uploads` 到后端；8080 提供 Spring Boot API。开发充值默认关闭，只有隔离演示环境按指南启用；尚未接入真实支付。

只想看界面时，可不连接数据库和Redis：

```bash
cd BlindBox-frontend
npm ci
VITE_CATALOG_PREVIEW=true npm run dev
```

预览只展示明确标注的样例目录，交易禁用。正常运行时停止预览进程，去掉该变量后重新启动。`predev/prebuild` 自动将根目录32个PNG复制到 `public/images`，生成副本不重复提交。

## 检查与验证边界

```bash
cd blindbox-springboot
mvn -s .mvn/settings.xml test
```

```bash
cd BlindBox-frontend
npm run lint
npm test
npm run build
```

这些测试/构建不需要启动MySQL或Redis。Mock、事务代理测试与只读联调不能证明真实数据库并发写入不超卖，也不能给出生产最大QPS。

历史记录：2026-10-13后端62项离线测试通过，并完成24次只读列表/详情检查；当时导入的4个池、20款、400件是历史数据快照。2026-10-19只整理文档；2026-10-27新增 Redis 熔断，经 Java 17 离线测试和隔离 Redis 断连恢复测试验证，未执行实际交易或容量压测。详见验收记录。

真实支付、退款物流、Redis库存预占、MQ异步订单与生产部署尚无已验证交付结论。上线前按接入指南完成隔离环境的并发写、故障恢复和资金/库存对账。

## 目录

```text
blindbox-springboot/     当前Java后端、Flyway迁移和离线测试
BlindBox-frontend/       当前React界面、资源同步和金额测试
images/                 原始图片（版本化）
docs/                   当前有效文档
outdate/                历史资料（不作为当前实现依据）
infra/                  Compose、Dockerfile和Nginx模板
scripts/                .env启动器、开发种子、只读对账及k6脚本
BlindBox-backend/        旧Node/SQLite参考源码，不作为当前后端
```

根目录统一进行Git版本控制，远程仓库为 [ZengYYoung/BlindBox](https://github.com/ZengYYoung/BlindBox)。原独立仓库历史、本地升级备份保存在被忽略的 `.upgrade-backup/`；实际.env、数据库、上传文件和构建产物均不提交。不要运行旧Node脚本初始化当前MySQL。

![深海来信系列](images/sea.png)
