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

H2 数据保存在 `backend/data/`。H2 控制台默认关闭；仅在本地排查时可设置
`H2_CONSOLE_ENABLED=true`，控制台地址为 `http://localhost:8080/h2-console`，JDBC URL 与
`application.yml` 中保持一致。

## AI 助手

命令编辑器提供三类 AI 能力：在不改变命令文字的前提下标注固定关键字、可替换参数和废弃内容；根据命令表达式生成 Java 正则以及正反测试数据；在创建或编辑时从同一所属场景、同一当前视图下的已生效和待审批命令中召回疑似重复、包含或交叉的候选。重复检测会由后端使用实际 Java 正则复核 AI 给出的共同匹配样例，并区分目标视图冲突。管理员还可以在待审批详情中调用 AI 审批助手，查看变更摘要、风险等级、风险点、核对清单和审批建议。首尾匹配边界始终由用户和系统控制，AI 不会修改；AI 审批和重复检测结论都只供参考，不会阻止保存、自动通过或驳回申请。AI 结果会先预览，用户确认后才写入表单；后端还会检查命令原文和自动运行生成的正反例。

默认不启用 AI。本地可先用无网络依赖的模拟模式验证界面和业务流程：

```bash
cd backend
AI_ENABLED=true AI_PROTOCOL=mock mvn spring-boot:run
```

DeepSeek 官方 API 推荐使用 OpenAI 兼容配置：

```bash
AI_ENABLED=true \
AI_PROTOCOL=openai \
AI_BASE_URL=https://api.deepseek.com \
AI_OPENAI_PATH=/chat/completions \
AI_MODEL=deepseek-flash \
AI_API_KEY=your-key \
AI_AUTH_MODE=bearer \
AI_STRUCTURED_OUTPUT_MODE=prompt-only \
AI_THINKING_MODE=disabled \
mvn spring-boot:run
```

如需验证 DeepSeek 的 Anthropic 兼容接口，将协议改为 `anthropic`、基地址改为 `https://api.deepseek.com/anthropic`、鉴权改为 `x-api-key`；默认的 `/v1/messages` 路径可直接使用。

公司 Agent 提供 OpenAI 兼容接口时：

```bash
AI_ENABLED=true \
AI_PROTOCOL=openai \
AI_BASE_URL=http://agent.company.local:8000 \
AI_MODEL=deepseek-v4-flash \
AI_API_KEY=your-key \
AI_AUTH_MODE=bearer \
AI_STRUCTURED_OUTPUT_MODE=prompt-only \
AI_THINKING_MODE=disabled \
mvn spring-boot:run
```

提供 Anthropic 兼容接口时：

```bash
AI_ENABLED=true \
AI_PROTOCOL=anthropic \
AI_BASE_URL=http://agent.company.local:8000 \
AI_MODEL=deepseek-v4-flash \
AI_API_KEY=your-key \
AI_AUTH_MODE=x-api-key \
AI_STRUCTURED_OUTPUT_MODE=prompt-only \
AI_THINKING_MODE=disabled \
mvn spring-boot:run
```

`AI_PROTOCOL` 明确选择 `openai` 或 `anthropic`，运行时不会在两种协议间猜测或降级。`AI_AUTH_MODE` 支持 `auto`、`bearer`、`x-api-key` 和 `none`；`AI_STRUCTURED_OUTPUT_MODE` 支持通用性最好的 `prompt-only`，以及网关明确支持时可用的 `json-object` 和 `json-schema`。`AI_THINKING_MODE` 支持 `auto`、`enabled` 和 `disabled`；DeepSeek 这类默认深度思考的模型用于短结构化任务时建议设为 `disabled`。如果公司网关使用自定义路径，可通过 `AI_OPENAI_PATH` 或 `AI_ANTHROPIC_PATH` 覆盖；请求超时和最大输出可分别用 `AI_TIMEOUT_SECONDS` 和 `AI_MAX_OUTPUT_TOKENS` 调整。

## 登录与权限

管理员账号不写入数据库，直接读取 `backend/src/main/resources/application.yml` 中的配置：

```yaml
app:
  admin:
    username: admin
    password: admin123
    display-name: 系统管理员
```

修改管理员密码需要更新该配置并重启后端。管理员可在人员管理中创建开发人员和普通用户；开发人员对命令的新增、修改和删除会先生成审批申请，由管理员通过后才影响正式命令数据；管理员自己的命令操作直接生效。普通用户仅可查看，场景导出及场景、视图、正则片段和人员维护仅限管理员。

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
- 重复与冲突检测只比较至少共享一个所属场景和一个当前视图的已生效和待审批命令；除完全相同的实际正则外，只有找到后端验证通过的共同匹配样例才会展示结果。所有结果均为非阻断提示。
- 命令关键字段修改必须填写原因，并记录修改人、时间以及修改前后的字段快照；描述变化不进入审计。
- 命令使用版本号防止多人编辑时发生静默覆盖，正则片段变化导致的实际正则变化也会写入命令审计。
- 开发人员的命令新增、修改、删除申请与正式命令数据隔离，审批通过前不会出现在列表、导出或 Git 同步中。
- 场景、视图或正则片段被命令引用时禁止删除。
- H2 使用 MySQL 兼容模式，业务 SQL 避免依赖 H2 专有能力，方便后续迁移 MySQL。
