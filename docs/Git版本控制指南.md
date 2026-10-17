# Git 版本控制指南

2026-10-17 起，项目根目录 `/Users/zengyy/code/blindbox` 使用一个 Git 仓库统一管理，远程仓库为 https://github.com/ZengYYoung/BlindBox.git，默认开发基线为 main。

## 管理范围

跟踪 Spring Boot 后端、React 前端、旧 Node 后端参考源码、根目录 images 原图、文档、迁移脚本与配置模板。前端 public/images 是生成副本，由 npm run assets / predev / prebuild 复制，不重复提交。

实际 .env、数据库文件、旧 backup.json 数据导出、上传文件、node_modules、target、dist、IDE 配置和 .upgrade-backup 不进入版本控制。克隆后按照接入指南创建自己的 .env、安装依赖并构建。

根目录 .gitattributes 统一文本换行；初次纳入时清理了旧 Node 源码的行尾空白，未修改业务逻辑。

原先 BlindBox-frontend 和 BlindBox-backend 的独立 .git 元数据移至本地 .upgrade-backup/git-legacy-20261017/，保留原提交、分支、远程配置和索引。业务工作文件保留在原目录。根仓库从当前完整项目建立新基线，不把旧数据库或旧仓库历史中的文件重新发布；备份不上传。

如需只读查看旧历史，例如：

~~~bash
git --git-dir=.upgrade-backup/git-legacy-20261017/BlindBox-frontend.git log --oneline -10
~~~

## 日常修改

所有命令在项目根目录执行：

~~~bash
git status
git diff
git switch -c feature/your-change
# 修改代码后，明确选择本次要保存的路径
git add blindbox-springboot/src docs
git diff --cached --stat
git diff --cached --check
git commit -m "feat: describe your change"
git push -u origin feature/your-change
~~~

Git commit 保存本地版本，git push 同步到 GitHub。建议功能分支开发，验证后通过 GitHub Pull Request 合入 main。提交前确认未加入密码、令牌、真实用户数据和构建产物。不要使用强制推送覆盖已有历史。

## 获取项目

~~~bash
git clone https://github.com/ZengYYoung/BlindBox.git
cd BlindBox
~~~

后端使用 blindbox-springboot，前端使用 BlindBox-frontend；接入步骤见 [接入与部署指南](接入与部署指南.md)。不要运行旧 Node 初始化脚本操作当前 MySQL。

GitHub 账号认证取决于本机 Git 凭据设置；不要将密码或访问令牌写入 remote URL 或项目文件。提交作者沿用当前已配置的本机 Git 身份；如需不同的作者身份，只对本仓库设置 user.name / user.email。
