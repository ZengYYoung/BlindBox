# BlindBox 前端

React + Vite。页面位于 src/pages，API默认同源/api，开发代理到localhost:8080。后端使用Spring Boot，不启动旧Express服务。

npm run dev/build 自动同步根目录 images 的PNG到 public/images。VITE_CATALOG_PREVIEW=true 仅启用明确标注的目录样例，交易禁用。检查命令：npm run lint、npm test、npm run build。

完整步骤见 [根README](../README.md) 与 [接入指南](../docs/接入与部署指南.md)；抽奖、锁、缓存和并发实现见 [关键技术文档](../docs/关键技术文档.md)。旧说明位于 [outdate](../outdate/README.md)。
