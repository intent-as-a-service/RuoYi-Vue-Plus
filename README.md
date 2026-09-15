**English** · [中文](#chinese)

<a name="english"></a>
# RuoYi-Vue-Plus × Intent as a Service

### Clone it, start it, click one button — and get an AI capability whose permissions, transactions and data scope are *already yours*

[![License](https://img.shields.io/badge/license-MIT%20%2B%20Apache--2.0-blue.svg)](#notes-and-credits)
![JDK](https://img.shields.io/badge/JDK-21-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-brightgreen.svg)
![Intents](https://img.shields.io/badge/intents%20to%20click-14-blueviolet.svg)
![Host tools](https://img.shields.io/badge/host%20tools-13-informational.svg)

**A complete, runnable reference host.** [RuoYi-Vue-Plus](https://github.com/dromara/RuoYi-Vue-Plus)
(backend) and [plus-ui](https://github.com/JavaLionLi/plus-ui) (frontend) in a single repository, with
**Intent as a Service** embedded end to end: **14 intents, 13 host tools, 3 declarative fact rules and
one zero-LLM executor profile**, plus a debug console you can click through moments after start-up.

> **No chat box, no second account system, no cross-domain calls.** The page declares what can be
> asked; the model runs **in-process inside the Spring Boot application**; tools call host services
> directly, so Sa-Token identity, permission checks and data-scope filtering follow the call stack.

### What you can click, a minute after start-up

| Try this | What happens |
|---|---|
| `http://localhost:8080/intent-ui/index.html` | The intent debug console — catalog, slot form, execution and trace, without writing a line of code |
| The floating AI button on any admin page | The intent drawer, scoped to the page you are on |
| System → User management | Todos with badges — e.g. accounts inactive for 30+ days, from your own data |

One release, both ends of the performance range: **19–23 s / ~10k tokens** for an agent-type intent,
**13–51 ms / 0 tokens** when the same work is frozen into a skill-type one.

### The four repositories

| Repository | What it is |
|---|---|
| [intent-sdk](https://github.com/intent-as-a-service/intent-sdk) | The framework: protocol, execution engine, host SPI, Spring Boot starter |
| [intent-ui-sdk](https://github.com/intent-as-a-service/intent-ui-sdk) | The framework-agnostic front end: floating button, drawer, result cards, trace |
| [ruoyi-office](https://github.com/intent-as-a-service/ruoyi-office) | The other reference host (yudao backend + Vben frontend, full CRM intent chain) |
| **this repository** | Reference host on RuoYi-Vue-Plus + plus-ui — 14 system / monitor intents |

---

## Repository layout

| Path | Origin | Contents |
|---|---|---|
| `backend/` | `dromara/RuoYi-Vue-Plus`, branch 6.X | Spring Boot 4.1 / JDK 21 backend **+ two new intent modules** |
| `frontend/` | `JavaLionLi/plus-ui`, branch 6.X-Vue | Vue 3 / Vite admin UI **+ the intent screens** |

## What was added on top of upstream

### Backend — two new modules

**`backend/ruoyi-common/ruoyi-common-intent/`** — the platform layer

- `IntentAutoConfiguration`: eight beans, every one `@ConditionalOnMissingBean`. LLM settings resolve
  in order: `intent.llm.*` → `PI_API_KEY` / `PI_BASE_URL` / `PI_MODEL_ID` → provider-specific
  variables → built-in defaults. Registered through `@AutoConfiguration` +
  `AutoConfiguration.imports`.
- Three Sa-Token bridges: identity (roles ∪ menu permissions), permission policy, and a **context
  bridge**. The context bridge is not optional — pi-agent runs tools on its own threads, and without
  carrying the request attributes across, `LoginHelper.getUserId()` returns null and data-scope
  filtering **silently stops working**. No exception, just someone else's data.
- Four controllers: `/intent`, `/intent/config`, `/intent/spec`, `/intent/executor`.
- Three tables with logic delete. Re-seeding uses `countBy*IncludeDeleted` on purpose, so an intent
  deleted in the admin UI does not come back after a restart.
- `intent-ui/**` static resources: the intent debug console.

**`backend/ruoyi-modules/ruoyi-intent/`** — the business intent pack

- 13 host tools (9 system, 4 monitor), all extending `BaseHostTool`.
- 14 intent YAML specs (`IntentSpec`: id / paramsSchema+context / promptTemplate+tools / outputSchema
  / policy), 3 declarative fact rules, 1 zero-LLM executor profile.
- Adding an intent means adding YAML — no platform code changes.

### Frontend

- Floating AI button mounted in `src/layout/index.vue`.
- Intent centre, intent configuration and executor profile screens.
- The panel resets itself on menu change; otherwise the previous page's result stays on screen.
- `intent-ui-sdk` is consumed through a relative `link:` dependency, which resolves inside this
  repository because `frontend/` and `backend/` are siblings.

### SQL

- `backend/script/sql/intent.sql` — three tables, twelve `sys_menu` rows and the role grants.

---

## Quick start

**Prerequisites**: JDK 21, Maven, MySQL 8, Redis, Node 20.19+ with pnpm 10.

**1. Install the intent SDK** (not published to Maven Central yet). This builds all six modules
including the executors — pi ships as a local package inside that repository, so nothing else is
needed:

```bash
git clone git@github.com:intent-as-a-service/intent-sdk.git
cd intent-sdk && mvn install -DskipTests
```

**2. Create the database and import the schema** (the database name `ry-vue` matches the default in
`application-dev.yml`):

```bash
mysql -uroot -p -e "CREATE DATABASE \`ry-vue\` DEFAULT CHARACTER SET utf8mb4"
mysql -uroot -p ry-vue < backend/script/sql/ry_vue.sql
mysql -uroot -p ry-vue < backend/script/sql/intent.sql
```

> `backend/script/sql/` also contains `ry_workflow.sql`, `ry_ai.sql` and `ry_job.sql` for the
> workflow, AI and job modules — import them if you keep those modules enabled.

**3. Build and run the backend.** No credentials are stored in the config files; pass them as
environment variables:

```bash
cd backend && mvn -DskipTests install

export PI_API_KEY="sk-..."            # LLM key: DeepSeek / OpenAI / Anthropic / any OpenAI-compatible
export MYSQL_PASSWORD="your-password" # defaults to root / password otherwise
java -jar ruoyi-admin/target/ruoyi-admin.jar --spring.profiles.active=dev
```

**4. Run the frontend:**

```bash
cd frontend && pnpm install && pnpm dev
```

## Trying the intents

| Where | What to expect |
|---|---|
| Startup log | `[intent]` lines: 13 host tools, 14 intents, 3 declarative fact rules |
| `http://localhost:8080/intent-ui/index.html` | The intent debug console — try the catalog, slot form, execution and trace without writing code |
| Any admin page, bottom right | The draggable AI button |
| System → User management | Todos with badges, e.g. accounts inactive for 30+ days |

```
   business page            in-process (the same Spring Boot app)            host services
   ┌─────────────┐          ┌───────────────────────────────┐              ┌───────────────────┐
   │ intent btn  │ ───────▶ │ intent-sdk-core               │ ──────────▶  │ SysUserService    │
   │ result card │ ◀─────── │  + pi reasoning / skill steps │ ◀──────────  │ permissions · tx  │
   └─────────────┘          └───────────────────────────────┘              └───────────────────┘
     intent request             result envelope (JSON)                     in-process call
```

The debug console shows the same flow end to end — catalog, slot form, result card and the
step-by-step execution trace. Screenshots of it are in the
[ruoyi-office](https://github.com/intent-as-a-service/ruoyi-office) README.

## Measured numbers

| Scenario | Duration | Tokens |
|---|---|---|
| agent-type intent (reasoning loop + 11 tool calls) | 19–23 s | 7.7k–10.8k |
| **skill-type intent (pure tool steps, zero LLM)** | **13–51 ms** | **0** |

## Documentation

Detailed delivery documentation lives in `backend/docs/intent/` (currently in Chinese):

| Document | Contents |
|---|---|
| [`README.md`](backend/docs/intent/README.md) | Architecture, modules, quick start, configuration, design trade-offs, test log |
| [`接入指南.md`](backend/docs/intent/接入指南.md) | SOP for adding an intent: tool → spec → rule → front-end context |
| [`意图清单.md`](backend/docs/intent/意图清单.md) | The 14 shipped intents, the 13 tools, the executor profile and the fact rules |
| [`tools/`](backend/docs/intent/tools/) | Two offline validators for intent YAML and executor profiles |

## Notes and credits

- **Upstream git history is not included.** This repository was imported as a snapshot: `backend/`
  and `frontend/` came from their upstream projects but without their commit history. To follow
  upstream, add it as a remote and merge by path.
- Only the intent-related paths differ from upstream; everything else is untouched upstream code.
- Licenses: RuoYi-Vue-Plus is MIT, plus-ui is MIT. The intent code added here is Apache-2.0, matching
  [intent-sdk](https://github.com/intent-as-a-service/intent-sdk).
- `intent-sdk` is `0.1.0-SNAPSHOT` and not on Maven Central yet — that is why step 1 builds it from
  source.

---

<a name="chinese"></a>
# RuoYi-Vue-Plus × 意图即服务

### 克隆、启动、点一下按钮 —— 拿到的 AI 能力，权限、事务、数据范围本来就是你自己的

[English](#english) · **中文**

![License](https://img.shields.io/badge/%E8%AE%B8%E5%8F%AF%E8%AF%81-MIT%20%2B%20Apache--2.0-blue.svg)
![JDK](https://img.shields.io/badge/JDK-21-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-brightgreen.svg)
![意图](https://img.shields.io/badge/%E5%8F%AF%E7%82%B9%E7%9A%84%E6%84%8F%E5%9B%BE-14-blueviolet.svg)
![宿主工具](https://img.shields.io/badge/%E5%AE%BF%E4%B8%BB%E5%B7%A5%E5%85%B7-13-informational.svg)

**一套开箱即跑的完整参考实现。** 本仓把 [RuoYi-Vue-Plus](https://github.com/dromara/RuoYi-Vue-Plus)
（后端）与 [plus-ui](https://github.com/JavaLionLi/plus-ui)（前端）放在一个仓库里，并把**意图即服务**
端到端植入：**14 个意图、13 个宿主工具、3 份声明式事实规则、1 个零 LLM 执行器档案**，
外加一个启动后几十秒就能点起来的意图调试台。

> **没有聊天框、没有第二套账号体系、不跨域。** 页面声明"能问什么"，模型**进程内**运行在
> Spring Boot 应用里，工具直调宿主 Service —— Sa-Token 登录态、权限校验、数据权限过滤
> 全程随调用栈走，不需要为 AI 重做一遍。

### 启动一分钟后能点什么

| 打开这个 | 会看到什么 |
|---|---|
| `http://localhost:8080/intent-ui/index.html` | 意图调试台：意图目录、槽位表单、执行、轨迹，一行代码都不用写 |
| 任意后台页面右下角的悬浮球 | 意图抽屉，按当前页面装载可用的意图 |
| 系统管理 → 用户管理 | 带徽标的待办（例如 30 天以上未登录的账号），数据来自你自己的库 |

同一份代码里两个极端都跑得出来：agent 型意图 **19~23s / 约 10k token**，
同样的活固化成 skill 型意图后 **13~51ms / 0 token**。

### 四个仓库

| 仓库 | 说明 |
|---|---|
| [intent-sdk](https://github.com/intent-as-a-service/intent-sdk) | 框架本体：协议、执行引擎、宿主 SPI、Spring Boot Starter |
| [intent-ui-sdk](https://github.com/intent-as-a-service/intent-ui-sdk) | 框架无关的前端：悬浮球、抽屉、结果卡片、执行轨迹 |
| [ruoyi-office](https://github.com/intent-as-a-service/ruoyi-office) | 另一个参考宿主（yudao 后端 + Vben 前端，CRM 全链路） |
| **本仓** | RuoYi-Vue-Plus + plus-ui 参考宿主 —— 14 个系统 / 监控意图 |

### 目录结构

| 路径 | 来源 | 内容 |
|---|---|---|
| `backend/` | `dromara/RuoYi-Vue-Plus` 分支 6.X | Spring Boot 4.1 / JDK 21 后端 **+ 两个新增意图模块** |
| `frontend/` | `JavaLionLi/plus-ui` 分支 6.X-Vue | Vue 3 / Vite 管理端 **+ 意图相关页面** |

### 相对上游新增了什么

- **平台层** `backend/ruoyi-common/ruoyi-common-intent/`：8 个 Bean 全部
  `@ConditionalOnMissingBean`；LLM 取值 `intent.llm.*` → `PI_API_KEY` 等环境变量 → 内置默认；
  4 个控制器；3 张表；`/intent-ui/**` 调试台。
- **业务意图包** `backend/ruoyi-modules/ruoyi-intent/`：13 个宿主工具、14 份意图 YAML、
  3 份声明式事实规则、1 个零 LLM 执行器档案。
- **前端**：`src/layout/index.vue` 挂悬浮球；意图中心 / 意图配置 / 执行器档案页面；
  切菜单自动复位面板（否则上一页结果会残留）。
- **建表**：`backend/script/sql/intent.sql`（3 张表 + 12 条 `sys_menu` + 角色授权）。

### 为什么必须有"上下文桥"

pi-agent 的推理循环在**独立线程**执行工具调用，而宿主的登录态与数据权限挂在原线程上。
不把请求上下文搬过去**不会报错**，而是**静默返回错误结果** —— 越权查到别人的数据，
或者什么都查不到。另外三处关键取舍（目录隐藏 ≠ 免鉴权、规范落库而非只读 classpath、
启动期强校验）详见 `backend/docs/intent/README.md`。

### 快速开始

```bash
# 1) 先装意图 SDK（尚未发布中央仓）。会一并构建执行器模块；
#    pi 以本地包形式随该仓分发，无需任何其它前置。
git clone git@github.com:intent-as-a-service/intent-sdk.git
cd intent-sdk && mvn install -DskipTests

# 2) 建库导表（库名 ry-vue 与 application-dev.yml 默认值一致）
mysql -uroot -p -e "CREATE DATABASE \`ry-vue\` DEFAULT CHARACTER SET utf8mb4"
mysql -uroot -p ry-vue < backend/script/sql/ry_vue.sql
mysql -uroot -p ry-vue < backend/script/sql/intent.sql

# 3) 启动后端：凭据一律走环境变量，配置文件里不落盘
cd backend && mvn -DskipTests install
export PI_API_KEY="sk-..."
export MYSQL_PASSWORD="你的口令"
java -jar ruoyi-admin/target/ruoyi-admin.jar --spring.profiles.active=dev

# 4) 启动前端
cd frontend && pnpm install && pnpm dev
```

### 上游血缘与许可

本仓为**快照导入**，未携带上游 git 历史；除意图相关路径外均为上游原样代码。
RuoYi-Vue-Plus 与 plus-ui 均为 MIT，本仓新增的意图代码为 Apache-2.0，
与 [intent-sdk](https://github.com/intent-as-a-service/intent-sdk) 一致。
