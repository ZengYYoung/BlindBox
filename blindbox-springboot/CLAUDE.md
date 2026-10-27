# Current project guidance
Backend: Java 17 / Spring Boot 3.4.4, MyBatis annotation mapper, MySQL, Flyway V1–V4, Spring Security JWT, Redis cache/rate limit.
Money is integer cents. Lottery uses remaining physical prize inventory, not fixed probabilities.
Idempotency queries are nonlocking and must be the first consistent reads AFTER the guarding user/box lock. Do not create an earlier RR snapshot or introduce writers bypassing parent locks.
Database transactions lock user then box, verify update counts and write immutable order snapshots plus wallet/stock ledgers.
Admission is outside DrawTransactionService; release only after the Spring transaction proxy returns. Detail is a single nonlocking snapshot query. Catalog caches metadata only; stock/status/price are fetched live in a batch. Draw/restock do not invalidate metadata. Metadata invalidation runs asynchronously after commit.
Redis cache and rate limiting use separate per-instance circuit breakers; ingress and user quotas share the rate-limit breaker. Write requests fail closed, reads may fall back. Redis is not the authoritative inventory or balance store. No stock pre-initialization on startup.
Read ../docs/关键技术文档.md, ../docs/接入与部署指南.md and ../docs/项目介绍与面试指南.md. Outdated documents are archived under ../outdate/ and are not current implementation guidance.
Offline tests use mocks and an actual Spring transaction proxy with a test transaction manager, no live database/Redis. Do not deploy or connect infrastructure without user authorization.
Build: mvn -s .mvn/settings.xml test / package. Configuration via environment; scripts/run-backend.py safely loads .env.
Do not commit credentials, tokens, uploaded images, database dumps or .upgrade-backup.
