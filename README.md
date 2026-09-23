# 华为路由器命令行正则白名单管理

一个前后端分离的命令白名单配置应用，支持场景、视图、命令和可复用正则片段管理。

## 技术栈

- 前端：Vue 3、TypeScript、Vite、Element Plus、TipTap
- 后端：JDK 21、Spring Boot 3.5、MyBatis-Plus
- 数据库：H2（MySQL 兼容模式），Flyway 管理表结构

## 运行

后端：

```bash
cd backend
mvn spring-boot:run
```

前端：

```bash
cd frontend
npm install
npm run dev
```

浏览器访问 `http://localhost:5173`。前端开发服务器会把 `/api` 请求代理至 `http://localhost:8080`。

H2 数据保存在 `backend/data/`，开发控制台地址为 `http://localhost:8080/h2-console`。JDBC URL 与 `application.yml` 中保持一致。

## 登录与权限

管理员账号不写入数据库，直接读取 `backend/src/main/resources/application.yml` 中的配置：

```yaml
app:
  admin:
    username: admin
    password: admin123
    display-name: 系统管理员
```

修改管理员密码需要更新该配置并重启后端。管理员可在人员管理中创建开发人员和普通用户；开发人员可以维护命令，普通用户仅可查看，场景导出及场景、视图、正则片段和人员维护仅限管理员。

## GitHub 数据同步

管理员可在“数据迁移”页面将当前业务数据一键提交到 Git 仓库。后端运行环境需安装 Git，并为运行账号配置好 SSH 密钥和 `known_hosts`。

```yaml
app:
  github-sync:
    enabled: true
    repository-url: git@github.company.local:team/asset-data.git
    branch: main
    work-directory: ./data/github-sync
    data-directory: asset-data
    author-name: 资产管理系统
    author-email: asset-system@company.local
```

上述配置均可分别通过 `GITHUB_SYNC_ENABLED`、`GITHUB_SYNC_REPOSITORY_URL`、`GITHUB_SYNC_BRANCH`、`GITHUB_SYNC_WORK_DIRECTORY`、`GITHUB_SYNC_DATA_DIRECTORY`、`GITHUB_SYNC_AUTHOR_NAME` 和 `GITHUB_SYNC_AUTHOR_EMAIL` 环境变量覆盖。同步不会强制推送；远端分支发生并发变更时，本次推送会失败并要求重新同步。

## 主要规则

- 命令可以属于多个场景、存在于多个当前视图，并可选一个目标视图。
- 场景支持多选导出：每个场景生成一个 Excel，并统一打包为 ZIP 文件。
- 命令表达式只保留加粗、斜体、删除线三种展示格式。
- 正则片段使用 `${NAME}` 引用，名称统一大写，片段之间禁止嵌套引用。
- 每条命令可以分别设置开头 `^` 和结尾 `$` 匹配边界，默认两者都启用。
- 后端使用最终正则配合 Java `Matcher.find()` 执行匹配测试。
- 场景、视图或正则片段被命令引用时禁止删除。
- H2 使用 MySQL 兼容模式，业务 SQL 避免依赖 H2 专有能力，方便后续迁移 MySQL。
