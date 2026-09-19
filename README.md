# 华为路由器命令行正则白名单管理

一个前后端分离的命令白名单配置应用，支持场景、视图、命令和可复用正则片段管理。

## 技术栈

- 前端：Vue 3、TypeScript、Vite、Element Plus
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

## 主要规则

- 命令可以属于多个场景、存在于多个当前视图，并可选一个目标视图。
- 场景支持多选导出：每个场景生成一个 Excel，并统一打包为 ZIP 文件。
- 命令表达式只保留加粗、斜体、删除线三种展示格式。
- 正则片段使用 `${NAME}` 引用，名称统一大写，片段之间禁止嵌套引用。
- 每条命令可以分别设置开头 `^` 和结尾 `$` 匹配边界，默认两者都启用。
- 后端使用最终正则配合 Java `Matcher.find()` 执行匹配测试。
- 场景、视图或正则片段被命令引用时禁止删除。
- H2 使用 MySQL 兼容模式，业务 SQL 避免依赖 H2 专有能力，方便后续迁移 MySQL。
