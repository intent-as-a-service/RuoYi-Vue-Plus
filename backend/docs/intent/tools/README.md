# 意图交付前的离线校验工具

这两个单文件 Java 程序用**真实的意图 SDK 加载器**校验业务模块里的 YAML，
把"启动时才会暴露的契约错误"提前到交付前。不需要 Spring、不需要数据库。

## 为什么需要它

意图规范、事实规则、执行器档案都是**启动强校验**的：写错会让服务起不来。
这在生产是好事（静默失效最难排查），但在交付/CI 阶段就意味着"必须先跑一次才知道对不对"。
有了这两个工具，可以在提交前就跑完：

| 工具 | 校验对象 | 能抓到什么 |
|------|---------|-----------|
| `ValidateIntentYaml` | `resources/intent/*.yaml` + `resources/intent-rules/*.yaml` | 编号不合三段命名、缺必填字段、工具名非法、别名超限、参数不在入参 Schema 内、模板引用未知 `ctx.*` 字段、规则引用了不存在的意图…… |
| `CheckExecutor` | `resources/intent-executor/*.yaml` | 执行器 id 非法或与保留 id 冲突、skill 步骤缺工具、agent 型缺 model、`output` 模板结构问题 |

## 用法

需要先构建 next-agent 并 `mvn install`（依赖 `io.github.intent-as-a-service:intent-*:0.1.0-SNAPSHOT`）。

```powershell
# 1. 组装 classpath（SDK + Jackson + SnakeYAML）
$repo = "$env:USERPROFILE\.m2\repository"     # 或你的本地仓库（如 D:\repository）
$jars = @(
  "io\github\intent-as-a-service\intent-protocol\0.1.0-SNAPSHOT\intent-protocol-0.1.0-SNAPSHOT.jar",
  "io\github\intent-as-a-service\intent-sdk-core\0.1.0-SNAPSHOT\intent-sdk-core-0.1.0-SNAPSHOT.jar",
  "io\github\intent-as-a-service\intent-sdk-host\0.1.0-SNAPSHOT\intent-sdk-host-0.1.0-SNAPSHOT.jar",
  "io\github\intent-as-a-service\intent-sdk-pi\0.1.0-SNAPSHOT\intent-sdk-pi-0.1.0-SNAPSHOT.jar"
) | ForEach-Object { Join-Path $repo $_ }
$jars += (Get-ChildItem "$repo\com\fasterxml\jackson", "$repo\org\yaml\snakeyaml", "$repo\dev\pi" `
  -Recurse -Filter *.jar | Where-Object { $_.Name -notmatch 'sources|javadoc' } | % FullName)
$cp = $jars -join ';'

# 2. 校验意图与规则
java -cp $cp ValidateIntentYaml.java ..\..\..\backend\ruoyi-modules\ruoyi-intent

# 3. 校验执行器档案
java -cp $cp CheckExecutor.java ..\..\..\backend\ruoyi-modules\ruoyi-intent\src\main\resources\intent-executor\system-health-snapshot.yaml
```

单文件源码启动（`java Foo.java`）要求 JDK 11+；本项目用 JDK 21，满足。

预期输出（通过时）：

```
=== 意图规范 ===
  OK   monitor.health.snapshot          tools=[...] pages=[...] roles=[*]
  ...
=== 事实规则 ===
  OK   system.user.inactive         intent=system.user.risk-scan   dataset=system.inactive-users   badge=Y items=5
=== 规则 × 目录契约校验 ===
  OK   全部 3 条规则通过
结果：全部通过（意图 14 个，规则 3 条）
```

## 建议接进 CI

把上面第 2、3 步做成一个构建阶段（校验失败即 `exit 1`）。
这两个程序**只读**文件、不连库、不起 Spring，跑一次不到 2 秒，非常适合放进提交前检查。
