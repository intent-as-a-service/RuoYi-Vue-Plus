# 意图即服务开发框架（Intent as a Service）

> 把 **next-agent 的内嵌 AI 意图 SDK** 植入 RuoYi-Vue-Plus，得到一套"没有聊天框"的 AI 接入方式：
> 业务页面放一排意图按钮，点击即执行，结果卡片内嵌返回；AI 能力以**原生 SDK 进程内嵌入**，
> 权限、事务、数据权限完全沿用宿主，不建独立账号体系、不跨域、数据不出域。

本目录是这套框架的交付说明，共三份：

| 文档 | 读它做什么 |
|------|-----------|
| **README.md**（本文） | 架构、模块、快速开始、配置项、设计取舍、**实测验证记录** |
| [接入指南.md](./接入指南.md) | 新增一个意图的完整 SOP（工具 → 规范 → 规则 → 前端上下文） |
| [意图清单.md](./意图清单.md) | 已开箱即用的 14 个意图、13 个宿主工具、1 个执行器档案、3 条事实规则 |

---

## 1. 它解决什么问题

传统"AI 接入"是把大模型塞进一个聊天框，用户要自己想清楚该怎么问、问完还要自己把结论搬回业务操作里。
结果是：**AI 与业务是两张皮**——人在聊天框里问，手在业务系统里点。

意图即服务换了个方向：**AI 不再是入口，而是能力**。

| 传统聊天框 | 意图即服务 |
|-----------|-----------|
| 入口：一个全局聊天框 | 入口：业务页面上的意图按钮（按页面装载） |
| 用户组织语言 | 系统已声明意图（编号 / 入参 / 工具白名单 / 输出契约） |
| 输出是一段话 | 输出是**标准结果信封**（title / summary / blocks / followups / nextIntents），UI 直接渲染 |
| 结果需要人工搬运 | 结果卡里的「下一步」可直接点击执行（补充要求重跑 / AI 推荐意图 / 宿主待办） |
| 权限、事务、数据范围要重新做 | 工具**进程内直调**宿主 Service，权限与事务天然随调用栈 |
| 每个 AI 能力都要发版 | 意图规范（YAML）+ 事实规则（YAML）后台可改，**保存即生效** |

一句更短的话：**把"能问什么、该怎么问、结果长什么样"从用户脑子里挪进系统里。**

### 目录增强：从"能力列表"到"待办列表"

默认形态下意图入口对每个人都一样（「账号风险体检」「登录异常分析」）。目录增强让入口带上**业务事实**：

```
同一颗 AI 图标，打开就是：
  📌 待办 · 账号风险体检      5 / 展开
     · 账号「张三」已 91 天未登录      zhangsan · 研发部 · 点一下做账号体检
     · 账号「李四」已 63 天未登录      lisi · 市场部 · 点一下做账号体检
  徽标：登录异常分析  [近 24 小时 17 次登录失败]
```

事实由业务模块的 `IntentFactProvider` 供给（只取数），**条件与文案写在 YAML 里**——
改阈值、改文案、改挂载页面只动一份 YAML，不用改 Java、不用发版。

---

## 2. 架构：三条边界

```
┌──────────────────────────────────────────────────────────────────────┐
│  前端（Vue 3 + Element Plus）                                          │
│                                                                      │
│  intent-ui-sdk.js（框架无关原生 JS，唯一一份）                          │
│    悬浮球 / 抽屉 / 意图菜单 / 槽位表单 / 待办分组 / 结果卡 / 轨迹 / 历史 / 反馈 │
│         ▲ 注入 + 挂载（宿主薄适配器 ≈ 100 行）                          │
│  views/intent/{sdk.ts, pageContext.ts, components/IntentFloating.vue} │
│         ▲ 业务页面 4 行：registerIntentPageContext('system/user', …)   │
└─────────┼────────────────────────────────────────────────────────────┘
          │ 同源同鉴权（复用宿主 token，无独立登录、无跨域）
┌─────────┼────────────────────────────────────────────────────────────┐
│  后端                                                                 │
│                                                                      │
│  ruoyi-common-intent（平台层，与业务零耦合）                            │
│    · IntentController       /intent/{catalog,execute,history,trace,…} │
│    · IntentConfigController 上架 / 角色 / 执行器（管理员）               │
│    · IntentSpecController   意图规范 YAML 增删改查 + 校验               │
│    · IntentExecutorController 执行器档案（保存即热更新）                 │
│    · Sa-Token 三件套        身份 / 权限 / 上下文桥                       │
│    · IntentSpecRegistry     意图规范：classpath 播种 → DB 单一事实来源    │
│    · ExecutorProfileRegistry 执行器档案：DB → 运行时热注册/注销          │
│    · /intent-ui/**          前端组件静态资源（演示页 / script / npm 同一份）│
│         ▲ 依赖 5 个 jar                                               │
│  ┌──────┴───────────────────────────────────────────────────────┐    │
│  │ intent-as-a-service 意图 SDK（io.github.intent-as-a-service）  │    │
│  │  intent-protocol  协议 DTO（本地与远程返回同构）                  │    │
│  │  intent-sdk-core  规范加载校验 / 编排 / 目录装配 / 工具契约        │    │
│  │  intent-sdk-host  宿主 SPI + 声明式事实规则引擎                   │    │
│  │  intent-sdk-pi    执行器（builtin-agent / skill / flow）+ LLM     │    │
│  │  intent-sdk-gateway  跨系统网关客户端（可组装，不装=本地闭环）       │    │
│  └──────────────────────────────────────────────────────────────┘    │
│         ▲ 只依赖两个契约                                              │
│  业务模块（声明式接入，平台零改动）                                     │
│    · IntentTool Bean         把内部 Service 包成工具（进程内直调）       │
│    · resources/intent/*.yaml 意图规范（五要素）                         │
│    · resources/intent-rules/*.yaml 事实规则（待办/徽标）                 │
│    · IntentFactProvider      只负责取数，不做判断                       │
│  参考实现：ruoyi-intent（14 个意图 / 13 个工具 / 1 个执行器档案 / 3 条规则）│
└──────────────────────────────────────────────────────────────────────┘
```

**三条边界，决定了这套东西能不能长期活下去：**

1. **平台与业务解耦**：删掉 `ruoyi-intent` 模块，框架照常运行（只是没有那些意图）。
   业务接入只有声明（工具 Bean + YAML），平台代码零改动。
2. **SDK 与宿主框架解耦**：`intent-sdk-core` / `intent-sdk-host` 无 Spring 依赖；
   身份、权限、上下文桥全部由宿主实现 SPI 提供。换宿主只需重写这三个类（本框架里约 150 行）。
3. **规范与代码解耦**：意图的五要素（标识 / 输入 / 编排 / 输出 / 治理）写在 YAML，
   启动强校验，不合规**直接起不来**——静默失效是这类系统最难排查的故障。

---

## 3. 模块与文件清单

### 后端

| 模块 | 路径 | 职责 |
|------|------|------|
| `ruoyi-common-intent` | `backend/ruoyi-common/ruoyi-common-intent` | 平台层：装配、适配、注册中心、控制器、静态资源 |
| `ruoyi-intent` | `backend/ruoyi-modules/ruoyi-intent` | 意图注入包：宿主工具 + 意图规范 + 事实规则（**可整包删除**） |
| `intent.sql` | `backend/script/sql/intent.sql` | 三张表 DDL + 菜单与权限种子 |

平台层关键类：

```
org.dromara.common.intent
├── config/
│   ├── IntentAutoConfiguration      装配 8 个 Bean（全部 @ConditionalOnMissingBean，可整体覆盖）
│   ├── IntentProperties             intent.* 配置项
│   └── IntentWebConfiguration       /intent-ui/** → classpath:/intent-ui/
├── bridge/
│   ├── SaTokenIntentPrincipalProvider  LoginUser → IntentPrincipal（角色编码 ∪ 权限串）
│   ├── SaTokenIntentPermissionPolicy   角色判定（超管放行；roles 支持写权限串）
│   └── SaTokenIntentContextBridge      请求上下文搬运到 pi-agent 的工具线程 ★关键
├── controller/                      Intent / IntentConfig / IntentSpec / IntentExecutor
├── domain/ mapper/                  IntentSpecDO / IntentConfigDO / IntentExecutorDO
├── service/
│   ├── IntentSpecRegistry           意图规范：播种 + 缓存 + CRUD
│   ├── ExecutorProfileRegistry      执行器档案：播种 + 构建 + 热更新
│   └── IntentService                目录（角色/页面过滤 + 增强）/ 执行 / 留痕 / 反馈 / CRUD
└── resources/intent-ui/             intent-ui-sdk.js + css + 调试台页面
```

### 前端

| 文件 | 作用 |
|------|------|
| `src/api/intent/{index.ts,types.ts}` | 意图 API 与协议类型（与 `dev.intent.protocol` 一一对应） |
| `src/views/intent/sdk.ts` | 宿主注入点（幂等）：apiPrefix / 鉴权头 / 实体下拉 / 层级 / 语言 |
| `src/views/intent/pageContext.ts` | **页面上下文注册中心**（面板与页面看到同一份数据的关键） |
| `src/views/intent/components/IntentFloating.vue` | 全站浮标（薄适配器，挂到 `layout/index.vue`） |
| `src/views/intent/center/index.vue` | 意图调试台（全量目录 + debug 面板） |
| `src/views/intent/config/index.vue` | 意图管理（上架 / 可见角色 / 执行器 / 规范 YAML） |
| `src/views/intent/executor/index.vue` | 执行器档案（YAML 编辑 + 校验 + 热更新 + 工具清单） |
| `src/views/system/user/index.vue` | 业务页面集成示范：注册 `system/user` 的页面上下文 |

---

## 4. 快速开始

### 4.1 前置：把意图 SDK 装进本地仓库

`io.github.intent-as-a-service:*` 与 `dev.pi:*` 目前是 `0.1.0-SNAPSHOT`，未发布到公共仓库，
需要先构建 pi-java 与 intent-sdk 并 `mvn install` 到本地仓库（详见下方 4.1.1）：

```bash
cd D:/project/myopensource/pi-agent/pi-java && mvn install -DskipTests
cd D:/project/myopensource/next-agent      && mvn install -DskipTests
```

> **坐标说明**：Maven groupId 是 `io.github.intent-as-a-service`（对应 GitHub 组织，无需自有域名即可发布中央仓）；
> Java 包名沿用 **`dev.intent.*`**（包名不需要与 groupId 有域名对应关系，短且已经稳定）。

### 4.2 建表与菜单

在业务库（默认 `ry-vue`；库名不同时按 4.4 设 `MYSQL_DATABASE`）执行：

```bash
mysql -uroot -proot ry-vue < backend/script/sql/intent.sql
```

### 4.3 配置大模型

开发环境写在 `backend/ruoyi-admin/src/main/resources/application-dev.yml` 的 `intent.llm`：

```yaml
intent:
  llm:
    provider: deepseek                      # deepseek | openai | anthropic | custom（任意 OpenAI 兼容端点）
    base-url: https://api.deepseek.com      # 留空 = 按供应商取默认
    model-id: deepseek-flash                # 留空 = 按供应商取默认（deepseek → deepseek-chat）
    api-key:                                # 一律留空 → 回退环境变量（见下），仓库里不写死密钥
    context-window: 131072
    max-tokens: 8192
    max-turns: 12                           # 单次执行最大推理轮数
    output-max-retries: 1                   # 输出不合规时的整体重试次数（0 = 不重试）
```

**取值优先级**（后者为前者的兜底）：

```
intent.llm.api-key / base-url / model-id
  → 环境变量 PI_API_KEY / PI_BASE_URL / PI_MODEL_ID        （与 pi-ai 口径一致）
  → 供应商专用变量 DEEPSEEK_API_KEY / OPENAI_API_KEY / ANTHROPIC_API_KEY
  → INTENT_LLM_API_KEY
  → 供应商内置默认值（端点 + 模型）
```

> **仓库里的 `api-key` 一律留空**，密钥由启动时注入的环境变量提供（容器 / IDE Run Configuration / shell），避免密钥落盘进版本库。
> `provider` 与 `base-url` / `model-id` 是<b>可组合</b>的：例如 `provider: deepseek` +
> `model-id: deepseek-flash` 既走 DeepSeek 的协议适配，又能选具体型号。

其余配置项（完整版见 `application.yml` 末尾的「意图即服务」文档块）：

```yaml
intent:
  enabled: true
  system-name: RuoYi-Vue-Plus
  time-zone: Asia/Shanghai
  trace:
    dir: ./data/intent-traces   # 留痕（JSONL）；留空 = 内存，重启即失
  gateway:
    enabled: false              # 跨系统意图网关；不装配 = remote 意图明确返回 REMOTE_UNAVAILABLE
  suggestions:
    enabled: true               # 目录增强（待办与徽标）
    max: 5
    cache-seconds: 30
```

### 4.4 启动

```bash
cd backend && mvn -DskipTests install
```

密钥、库名与数据库口令都走环境变量，不写进配置文件：

```powershell
cd ruoyi-admin
$env:PI_API_KEY      = "sk-xxx"        # 大模型密钥；Bash: PI_API_KEY=sk-xxx java -jar ...
$env:MYSQL_DATABASE  = "你的库名"       # 默认取上游的 ry-vue，本机库名不同时必须给
$env:MYSQL_PASSWORD  = "你的库口令"     # 默认取 root/password，本机口令不同时必须给
java -jar target/ruoyi-admin.jar --spring.profiles.active=dev
```

```bash
cd ../frontend && pnpm install && pnpm dev
```

**验证点**

| 位置 | 期望 |
|------|------|
| 启动日志 | `[intent] 注册宿主工具: …` ×13、`[intent] 注册意图: …` ×14、`[intent] 声明式事实规则: 3 条，事实源 1 个` |
| `http://localhost:8080/intent-ui/index.html` | 意图调试台（自动读取登录令牌） |
| 后台任意页面右下角 | AI 浮标（可拖动，位置记在 localStorage） |
| 「意图中心 → 意图调试台」 | 全量 14 个意图目录，点意图先弹槽位表单再执行 |
| 「系统管理 → 用户管理」 | 打开面板应看到带徽标的待办（30 天未登录账号） |

### 4.5 本地起服务时容易卡住的三件事

这三条都是本机实测踩出来的，与意图功能无关，但会挡住第一次启动：

| 现象 | 原因 | 处理 |
|------|------|------|
| `ERR Client sent AUTH, but no password is set` | 本机 Redis 没设密码，而 `application-dev.yml` 里有 `password: ruoyi123`。**注意不能简单留空**（`password:` 会被绑定成空字符串，Redisson 仍发 AUTH） | 把 `spring.data.redis.password` 整行注释掉 |
| 登录返回 `验证码已失效` | `captcha.enable: true` | 走后台页面登录会自动取验证码；纯 API 联调可临时 `--captcha.enable=false` |
| 登录返回 `没有访问权限，请联系管理员授权` | `/auth/login` 带 `@ApiEncrypt`，需要前端那套 RSA+AES 报文加密 | 纯 API 联调可临时 `--api-decrypt.enabled=false`（意图接口本身不加密，不受影响） |

> **数据库**：`ry_vue.sql` 只有 `create table`、**没有 `drop table`**，请导入到一个**全新的空库**；
> 若同名库已有数据（例如旧版 RuoYi-Vue-Plus 的 `ry-vue`），务必另建库名，不要覆盖。

### 4.6 前端组件 `intent-ui-sdk` 的单一真源

悬浮球 / 抽屉 / 结果卡片的实现在独立仓库 **`intent-ui-sdk`**。后端把它作为静态资源对外提供（`classpath:/intent-ui/**` → `http://localhost:8080/intent-ui/`），前端 dev 时用 `link:` 指向同一份产物：

```jsonc
// frontend/package.json
"intent-ui-sdk": "link:../backend/ruoyi-common/ruoyi-common-intent/src/main/resources/intent-ui"
```

所以 `backend/ruoyi-common/ruoyi-common-intent/src/main/resources/intent-ui/` 下的 `package.json` / `js/*` / `css/*` 是**生成产物，不要手工改**：改了会在下次同步时被覆盖，而且会和 ruoyi-office 那份各自漂移（本仓库合并前就发生过一次：两份 JS 差了 2.7 KB，类型声明还漏了 `reset()`）。真源在 `intent-ui-sdk/`，改完执行：

```bash
cd ../intent-ui-sdk
node scripts/sync.mjs            # 同步到 ruoyi-vue-plus 与 ruoyi-office 两个宿主
node scripts/sync.mjs --check    # 只校验；有漂移则退出码 1（可放进 CI）
```

**宿主自持、不参与同步**的两个文件：`index.html`（调试台，各宿主的 API 前缀不同）、`js/demo-app.js`（演示数据）。

> 等 `intent-ui-sdk` 发布到 npm 后，把 `link:` 换成 `"intent-ui-sdk": "^0.4.0"` 就能去掉这份内嵌副本。

---

## 5. 实测验证记录

以下结论来自本机真实运行（Spring Boot 4.1.0 + MySQL 5.7 + Redis，2026-09-14）：

| 验证项 | 结果 |
|--------|------|
| 后端编译 | `mvn install` 全量 **BUILD SUCCESS**（含 `ruoyi-common-intent`、`ruoyi-intent`、`ruoyi-admin`） |
| 前端构建 | `pnpm build:dev` **built in 24s**；`vue-tsc --noEmit` **0 error** |
| 16 份 YAML 契约 | 用真实 SDK 加载器离线校验：13 意图 + 3 规则全部通过，规则×目录交叉校验通过 |
| 启动播种 | `意图规范就绪: 14 个（本次新播种 14 个）`、`播种内置执行器: system-health-snapshot`、`声明式事实规则: 3 条` |
| 目录接口 | `GET /intent/catalog?page=system/user` 只返回命中该页面的 5 个意图（**页面过滤生效**） |
| 目录增强 · 徽标 | 真实登录日志驱动：`monitor.login.anomaly -> 近 24 小时 2 次登录失败 [danger] count=2` |
| 目录增强 · 待办 | 把某账号 `login_date` 改成 91 天前 → `system.user.risk-scan -> 1 个账号超过 30 天未登录 [warning]`，并产出可点条目「账号「…」已 91 天未登录 · test · 上次登录 2026-06-15」（`days` 过滤器与参数映射均正确） |
| 执行 · 缺参 | `POST /intent/execute`（缺 `status`）→ `NEED_INPUT` + `missingParams=[status]` + `MISSING_PARAMS`（**不烧 token**） |
| 执行 · 错误码 | 不存在的意图 → `INTENT_NOT_FOUND`；参数类型不符 → `VALIDATION_ERROR`；无模型密钥 → `LLM_ERROR: No API key for provider: deepseek`（全部以结果返回，不抛异常） |
| 执行 · **零 LLM 全链路** | `monitor.health.snapshot`（skill 型执行器）→ `SUCCESS`、**13ms**、**0 token**，2 个工具步骤均 `ok=true`，返回结构化卡片（kv + 2× text），数据来自真实 Redis 会话与缓存统计 |
| 留痕 | `GET /intent/trace/{traceId}` 返回 userId / userName / durationMs；`GET /intent/history` 含成功与失败记录 |
| 规范校验 | 故意写两段式 id → 被拦；三段 id + 未注册工具 + 未注册执行器 → 两条错误都被列出 |
| 执行器热更新 | `POST /intent/executor` 新增 skill 档案后，立即出现在列表与下拉选项中并可执行（**无需重启**） |
| 后端全部接口 | `/intent/{status,catalog,config/list,spec/list,executor/list,executor/tools,config/executor-options}` 全部 `code=200`；前端组件静态资源 `/intent-ui/{index.html,js,intent-ui-sdk.js,css}` 全部 HTTP 200 |

### 5.1 接入真实大模型后的冒烟结果

模型：`deepseek-flash` @ `https://api.deepseek.com`（`provider: deepseek` + 显式 `model-id`）。

| 场景 | 结果 |
|------|------|
| **agent 型意图** `system.client.audit` | `SUCCESS`，23.1s，**10798 token**，12 步；产出 kv（结论先行）+ table（逐客户端明细，秒已换算成分钟/天）+ list（收敛建议）；`clientSecret` 全程掩码且明确声明"未做任何推断" |
| **agent 型意图** `system.dict.audit` | `SUCCESS`，19.5s，**7712 token**，15 步；blocks = text / table / list / text |
| **skill 型意图** `monitor.health.snapshot` | `SUCCESS`，**51ms**，**0 token**，blocks = kv / text / text（与 agent 型同一条执行链路，只是绕开模型） |
| 步骤级轨迹 | `GET /intent/trace/{id}` 完整可回放：`llm-turn` ×3 + 工具调用 ×11 + `submit_result`（输出闸门）×1，全部 `ok=true` |
| 成本对比（同一件事） | agent 型 ≈ 20s / 8k~11k token；skill 型 ≈ 50ms / 0 token —— **这正是执行器档案存在的意义** |

> 两次 agent 型执行的单次成本约 0.008~0.011 元（deepseek-flash 量级），适合按"用户主动点击"触发，
> 不要做成定时批量跑。

---

## 6. 设计取舍（为什么这么做）

### 6.1 为什么必须有"上下文桥"

pi-agent 的推理循环在**独立线程**执行工具调用（`CompletableFuture`）。宿主的登录态（Sa-Token）、
数据权限、请求级信息都挂在当前线程上。不搬运的后果不是报错，而是**静默的错误结果**：

- 工具里 `LoginHelper.getUserId()` 拿到 null → 数据权限失效 → "越权查到别人的数据"；
- 或者查不到任何数据 → 用户以为系统里没这条记录。

`SaTokenIntentContextBridge` 在意图执行入口捕获 `RequestAttributes`，
在每个工具线程里恢复（用完还原）。Sa-Token 的 Spring 上下文正是通过 `RequestContextHolder`
解析请求与 token，因此恢复请求属性即可让全链路正常工作；token 会话仍走 Redis（`PlusSaTokenDao`），天然支持集群。

### 6.2 为什么"目录隐藏 ≠ 免鉴权"

目录过滤（上架 + 角色 + 页面）只影响前端展示。知道 `intentId` 的人可以直接 POST `/intent/execute`，
所以 `IntentService.execute` 在目录过滤之外**独立再校验一次**，返回 `FORBIDDEN`。

同理，写操作工具在工具内部**再做一次权限校验**（`SysUserToggleStatusTool` 里的 `StpUtil.checkPermission`）——
即使模型被提示词注入诱导，也越不过宿主权限。

### 6.3 为什么"保存即生效"而不是"发版即生效"

意图规范与执行器档案都落库（`intent_spec` / `intent_executor`），启动时由 classpath YAML **播种**，
之后以 DB 为准：

- 播种只在**物理不存在**时写入（含逻辑删除态判定）→ 运营在后台删掉的意图不会因为重启被"复活"；
- 后台改完即时刷新缓存（执行器还会热注册/注销到运行时）→ 不发版、不重启；
- 规范与规则在**启动时强校验**（意图不存在、参数不在入参 Schema 内、模板引用未知字段）→ 服务直接起不来。

### 6.4 为什么把"事实"和"判断"分开

`IntentFactProvider` 只取数（有界查询），条件、阈值、文案全在 `intent-rules/*.yaml`。
好处是运营能自己改（30 天改成 60 天、warning 改成 danger、只在某些页面提示），
而且规则写错会在启动时被拦住，不会静默不出待办。

> ⚠️ 一个必须知道的细节：徽标里的 `{{count}}` 用的是 `IntentFactProvider#count` 的口径，
> **不是** `filter` 之后的条数。两者要表达同一件事时，阈值必须在 FactProvider 里对齐
> （本框架的 `SystemIntentFactProvider.countInactive()` 就与规则里的 `idleDays >= 30` 对齐）。

### 6.5 已知局限（透明清单）

| 项 | 现状 | 影响 |
|----|------|------|
| 流式输出 | 整卡返回（10~15s，约 3~4k token） | 长意图等待感明显；可改用宿主自带的 SSE（`PushHelper`）做阶段流 |
| 并发会话 | 每个意图一次独立推理，无多轮上下文 | 想追问要走「补充要求重跑」通道，不是聊天 |
| 网关 | 客户端已实现，网关服务端未实现 | `REMOTE` / `COMPOSITE` 意图会明确返回 `REMOTE_UNAVAILABLE` |
| 留痕存储 | JSONL 文件（`JsonlTraceStore`） | 量大时建议换 DB 实现（`TraceStore` 接口不变） |
| 多租户 | 框架 6.0.0 已移除多租户 | 若自行加回租户，需把三张 intent 表加入租户忽略名单 |
| 前端 SDK 版本 | 从 ruoyi-office 复制并按本框架适配（`unwrap` 兼容 `code=200`） | 上游 SDK 更新时需同步一次；建议尽快把 `intent-ui` 迁到 next-agent 统一发布 |

---

## 7. 安全边界（逐条说明）

| 边界 | 实现 |
|------|------|
| 登录 | 复用宿主 Sa-Token；前端 SDK 每次请求**现取** token（刷新后无需重挂） |
| 客户端校验 | SDK 请求头带 `clientid`，与 `SecurityConfig` 的客户端校验一致 |
| 意图可见性 | `intent_config.roles`（后台可配）覆盖规范里的 `policy.roles`；支持写**角色编码或权限串** |
| 执行鉴权 | 目录过滤之外独立再校验（见 5.2） |
| 工具鉴权 | 工具内直调宿主 Service：数据权限、事务、超管保护全部沿用；写操作再叠加二次权限校验 |
| 敏感数据 | 参数查询工具对 `password/secret/token/key` 等键名一律掩码；缓存工具只返回数量不返回值 |
| 留痕归属 | `GET /intent/trace/{id}` 校验归属，非本人（且非超管）拒绝 |
| 写操作 | 唯一写工具需 `confirm=true` 才落库，缺省只返回影响预览 |
| 提示词注入 | 工具白名单按意图收窄（规范 `tools` + 执行器档案 `tools` 双层）；跨系统意图需显式装配网关 |

---

## 8. 与 ruoyi-office 植入模式的对照

本框架完全沿用 ruoyi-office 已验证的植入模式，差异只来自两个宿主的技术栈不同：

| 维度 | ruoyi-office（yudao） | 本框架（ruoyi-vue-plus 6.0.0） |
|------|----------------------|------------------------------|
| 安全框架 | Spring Security + `SecurityFrameworkUtils` | Sa-Token + `LoginHelper` |
| 租户 | `TenantContextHolder` 需要搬运 | 6.0.0 已移除多租户，无需处理 |
| 上下文桥 | SecurityContext + TenantContext + RequestAttributes | RequestAttributes（Sa-Token 经它解析请求） |
| 响应码 | `CommonResult` code=0 | `R<T>` code=200 → **SDK 的 `unwrap` 已适配两者** |
| 装配方式 | 组件扫描 | `@AutoConfiguration` + `AutoConfiguration.imports`（本框架的模块化约定） |
| 规范存储 | 序列化 JSON | **保留原始 YAML 文本**（后台编辑器与开发写的是同一份） |
| 分页类型 | 自研 PageResult | `PageResult<T>`（`getRows()` 返回 `Collection`） |

---

## 9. 下一步

- **接入自己的业务意图**：读 [接入指南.md](./接入指南.md)，一个意图约 1~1.5 人日。
- **看现成能力**：读 [意图清单.md](./意图清单.md)。
- **要做跨系统编排**：实现网关服务端，配置 `intent.gateway.*`，把规范里的 `scope` 改成 `remote` / `composite`。
- **要做流式体验**：复用宿主 SSE（`PushHelper.sendMessage`）把执行阶段推给前端。
