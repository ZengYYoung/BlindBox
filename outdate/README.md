# 历史文档归档

整理日期：2026-10-19。以下资料保留用于查看演进过程，不是当前开发、部署或面试的实现依据。文件按原目录归档，正文保留，并增加归档提示、修正移动后的相对链接。

当前技术入口：[关键技术文档](../docs/关键技术文档.md)。使用方式见 [项目 README](../README.md)，实际检查范围见 [验收记录](../docs/验收记录.md)。

| 原位置 | 归档文件 | 归档原因 / 当前替代 |
|---|---|---|
| blindbox-springboot/docs/JWT令牌认证-面试整理.md | [JWT 旧整理](blindbox-springboot/docs/JWT令牌认证-面试整理.md) | 描述已移除的 JwtInterceptor；当前使用 Spring Security JwtFilter |
| blindbox-springboot/docs/事务与锁-面试整理.md | [事务与锁旧整理](blindbox-springboot/docs/事务与锁-面试整理.md) | CAS 重试等交易说明与当前父行锁、事务准入不同 |
| blindbox-springboot/docs/图片与文件上传-面试整理.md | [图片上传旧整理](blindbox-springboot/docs/图片与文件上传-面试整理.md) | 旧上传实现与当前 ImageService 尺寸/解码/重编码校验不同 |
| blindbox-springboot/docs/面试八股整理.md | [旧八股整理](blindbox-springboot/docs/面试八股整理.md) | 旧项目时期资料；通用知识不等于当前项目已实现的机制 |
| blindbox-springboot/docs/项目全局-面试整理.md | [旧项目全局整理](blindbox-springboot/docs/项目全局-面试整理.md) | 旧源码结构、概率和 CAS 流程不适用 |
| blindbox-springboot/docs/项目分析-优缺点与难点.md | [旧项目分析](blindbox-springboot/docs/项目分析-优缺点与难点.md) | Redis/CAS、权限和一致性判断基于旧实现 |
| blindbox-springboot/华为主管面准备.md | [旧主管面准备](blindbox-springboot/华为主管面准备.md) | Redis DECR 预扣、initStock 回补与吞吐改善表述不适用，缺少当前实测依据 |
| blindbox-springboot/Redis实现说明.md | [Redis 阶段说明](blindbox-springboot/Redis实现说明.md) | 内容大体仍适用，但已被带源码、SQL和测试链接的新技术文档取代 |
| docs/开发文档.md | [升级开发计划](docs/开发文档.md) | 2026-10-12/13 阶段交付计划与“本轮不推送”等时态已过期；当前入口为 README 和关键技术文档 |
| docs/验收记录.md | [旧完整验收记录](docs/验收记录.md) | 保留旧构建哈希、历史阶段状态和不存在于当前仓库的截图记录；当前 docs/验收记录.md 只列明证据日期及边界 |

当前保留的 API 契约、接入与部署指南、项目介绍与面试指南、并发优化说明、Git 指南和 CLAUDE.md 仍与现行代码有关，已修正入口和过期时态。不要把整份旧文档中的结论直接搬进简历或项目介绍。
