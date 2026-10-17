# BlindBox · 收藏一点惊喜

基于 React + Spring Boot 的盲盒收藏平台。后端沿用 Spring Boot 版本，旧 Express 后端保留为历史参考。

**核心规则：每种奖品的抽取概率 = 该款剩余实体数量 / 当前奖池剩余总量。抽走后概率改变，售罄奖品不再参与。**

## 交付文档

| 文档 | 内容 |
|---|---|
| [开发文档](docs/开发文档.md) | 目标、范围、业务不变量、架构、开发顺序 |
| [API契约](docs/API契约.md) | 请求、返回、金额、分页、幂等和错误码 |
| [接入与部署指南](docs/接入与部署指南.md) | MySQL/Redis、环境变量、迁移、管理员、启动、联调和Docker |
| [项目介绍与面试指南](docs/项目介绍与面试指南.md) | 核心算法、高并发、数据结构、表设计与面试追问 |
| [并发优化说明](docs/并发优化说明.md) | 并发准入、读写隔离、缓存、指标、配置和压测方法 |
| [Git版本控制指南](docs/Git版本控制指南.md) | 根仓库管理范围、旧历史备份和日常提交/同步方法 |
| [验收记录](docs/验收记录.md) | 实际执行的检查、待执行的集成验收和已知边界 |

## 本次升级

- 剩余库存驱动整数区间抽样，移除固定概率配置。
- MySQL事务中锁用户/奖池，校验条件更新，订单和资金/库存流水同事务。
- 抽奖、开发充值、建池和补货的请求幂等；前端保留超时请求，支持结果恢复。
- Spring Security JWT + 服务端管理员角色授权。
- Redis商品元数据缓存与Lua限流；库存、售价和状态批量查库，详情单条无锁快照。
- JWT前入口保护、事务前抽奖准入、提交后异步缓存失效和并发指标。
- 管理员商品编辑、配货、补货、上下架和归档；订单保留购买时快照。
- 收藏聚合、分页订单/社区/评论、唯一点赞关系、图片真实编码校验。
- 重构前端七类业务页面，复用根目录images图片并提供响应式布局。

## 无部件界面预览

~~~bash
cd BlindBox-frontend
npm ci
VITE_CATALOG_PREVIEW=true npm run dev
~~~

仅目录样例，交易禁用。正常运行改为 npm run dev；数据库、Redis和后端连接步骤见接入指南。

## 离线检查

~~~bash
cd blindbox-springboot
mvn -s .mvn/settings.xml test
mvn -s .mvn/settings.xml package
~~~

~~~bash
cd BlindBox-frontend
npm run lint
npm test
npm run build
~~~

这些检查不启动MySQL/Redis。Mock单测不能证明真实数据库回滚、多实例锁行为或实际Redis脚本执行。

## 目录

~~~text
blindbox-springboot/     当前Java后端、Flyway迁移、离线测试
BlindBox-frontend/       当前React界面、图片同步与金额测试
images/                 原始图片
docs/                   当前开发、接入、面试与验收文档
infra/                  未执行的Compose、Dockerfile、Nginx模板
scripts/                .env启动器、开发种子、只读对账、待执行k6脚本
BlindBox-backend/        历史Node/SQLite实现，不作为当前运行后端
~~~

升级前源码备份保存在本地 .upgrade-backup/source-before-upgrade.zip，包含旧配置，不应上传。2026-10-17起由根目录Git统一管理，原前后端独立Git历史已移至本地备份；操作方式见Git版本控制指南。不要运行旧Node脚本初始化当前MySQL库。

![深海来信系列](images/sea.png)

## 验证边界

已完成源码、文档、构建和离线验证；后续按用户要求接入本机MySQL/Redis并导入4个池、20款奖品、400个库存。本次并发改动的验证详见验收记录，尚无真实抽奖写压测或部署结果。真实支付、物流退款、MQ与Redis库存预占尚未实现；生产使用需先完成接入指南中的真实并发和故障验收。
