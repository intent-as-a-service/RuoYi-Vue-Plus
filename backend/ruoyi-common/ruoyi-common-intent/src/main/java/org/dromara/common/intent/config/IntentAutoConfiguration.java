package org.dromara.common.intent.config;

import dev.intent.gateway.HttpGatewayClient;
import dev.intent.protocol.IntentSpec;
import dev.intent.sdk.catalog.IntentCatalogEnricher;
import dev.intent.sdk.executor.IntentExecutor;
import dev.intent.sdk.gateway.GatewayClient;
import dev.intent.sdk.host.IntentCatalogEntries;
import dev.intent.sdk.host.IntentContextBridge;
import dev.intent.sdk.host.IntentPermissionPolicy;
import dev.intent.sdk.host.IntentPrincipalProvider;
import dev.intent.sdk.host.rule.CompositeIntentFactProvider;
import dev.intent.sdk.host.rule.IntentFactProvider;
import dev.intent.sdk.host.rule.IntentRuleException;
import dev.intent.sdk.host.rule.IntentRuleLoader;
import dev.intent.sdk.host.rule.IntentSuggestionRule;
import dev.intent.sdk.host.rule.RuleBasedIntentCatalogEnricher;
import dev.intent.sdk.llm.LlmConfig;
import dev.intent.sdk.pi.IntentRuntime;
import dev.intent.sdk.store.InMemoryTraceStore;
import dev.intent.sdk.store.JsonlTraceStore;
import dev.intent.sdk.store.TraceStore;
import dev.intent.sdk.tool.HostToolRegistry;
import dev.intent.sdk.tool.IntentTool;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.intent.bridge.SaTokenIntentContextBridge;
import org.dromara.common.intent.bridge.SaTokenIntentPermissionPolicy;
import org.dromara.common.intent.bridge.SaTokenIntentPrincipalProvider;
import org.dromara.common.intent.mapper.IntentConfigMapper;
import org.dromara.common.intent.mapper.IntentExecutorMapper;
import org.dromara.common.intent.mapper.IntentSpecMapper;
import org.dromara.common.intent.service.ExecutorProfileRegistry;
import org.dromara.common.intent.service.IntentSpecRegistry;
import org.dromara.common.intent.util.IntentResources;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 内含式 AI 意图 SDK 的宿主装配（平台侧，与业务完全解耦）。
 *
 * <p>本类把 next-agent 的意图 SDK 接到 RuoYi-Vue-Plus 的安全体系上：</p>
 * <ul>
 *   <li><b>身份</b>：{@link SaTokenIntentPrincipalProvider} 从 Sa-Token 登录态取 userId / userName / roles；</li>
 *   <li><b>权限</b>：{@link SaTokenIntentPermissionPolicy} 按 Sa-Token 角色判定意图可见性；</li>
 *   <li><b>上下文桥</b>：{@link SaTokenIntentContextBridge} 把登录态与请求属性搬到执行器的工具线程，
 *       保证"进程内直调，权限与事务沿用宿主"；</li>
 *   <li><b>种子</b>：各业务模块 {@code classpath*:intent/*.yaml} 启动播种进 {@code intent_spec} 表，
 *       之后以 DB 为准，后台增删改即时生效；</li>
 *   <li><b>目录增强</b>：{@code classpath*:intent-rules/*.yaml} 声明式事实规则 + 各模块
 *       {@link IntentFactProvider} 供给的事实数据，让意图入口带业务待办。</li>
 * </ul>
 *
 * <p>全部 Bean 均为 {@link ConditionalOnMissingBean}：宿主声明同名 Bean 即可覆盖任何一环。</p>
 *
 * @author RuoYi-Vue-Plus
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(IntentProperties.class)
@ConditionalOnProperty(prefix = "intent", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IntentAutoConfiguration {

    // ------------------------------------------------------------ 宿主适配 SPI

    /**
     * 身份提供者：SDK 由此获知"当前是谁在操作"，不再自己解析宿主安全框架。
     */
    @Bean
    @ConditionalOnMissingBean
    public IntentPrincipalProvider intentPrincipalProvider() {
        return new SaTokenIntentPrincipalProvider();
    }

    /**
     * 意图可见性策略：角色维度判定（超管放行、未声明角色不限制）。
     */
    @Bean
    @ConditionalOnMissingBean
    public IntentPermissionPolicy intentPermissionPolicy() {
        return new SaTokenIntentPermissionPolicy();
    }

    /**
     * 上下文桥：把 Sa-Token 登录态 / Spring 请求上下文搬到 pi-agent 的工具工作线程。
     *
     * <p>不做这层会出现最难排查的一类故障：工具里读不到登录用户，
     * 表现为"越权查到别人的数据"或"查不到任何数据"。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public IntentContextBridge intentContextBridge() {
        return new SaTokenIntentContextBridge();
    }

    // ------------------------------------------------------------ 工具与留痕

    /**
     * 宿主工具注册表：自动收集容器内所有 {@link IntentTool} Bean。
     *
     * <p>业务模块声明工具 Bean 即接入，平台零改动。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public HostToolRegistry intentHostToolRegistry(ObjectProvider<IntentTool> toolsProvider) {
        HostToolRegistry registry = new HostToolRegistry();
        toolsProvider.orderedStream().forEach(tool -> {
            registry.register(tool);
            log.info("[intent] 注册宿主工具: {} - {}", tool.name(), tool.description());
        });
        log.info("[intent] 宿主工具总数: {}", registry.size());
        return registry;
    }

    /**
     * 执行留痕存储：默认 JSONL 文件（可换 DB / 内存实现，接口不变）。
     */
    @Bean
    @ConditionalOnMissingBean
    public TraceStore intentTraceStore(IntentProperties properties) {
        String dir = properties.getTrace().getDir();
        if (dir == null || dir.isBlank()) {
            log.info("[intent] 留痕存储: 内存（重启即失）");
            return new InMemoryTraceStore();
        }
        log.info("[intent] 留痕存储: JSONL @ {}", Path.of(dir).toAbsolutePath());
        return new JsonlTraceStore(Path.of(dir));
    }

    // ------------------------------------------------------------ 注册中心（DB 化单一事实来源）

    /**
     * 意图规范注册中心：classpath YAML 播种 + DB 存储与缓存。
     */
    @Bean
    @ConditionalOnMissingBean
    public IntentSpecRegistry intentSpecRegistry(IntentSpecMapper specMapper) {
        IntentSpecRegistry registry = new IntentSpecRegistry(specMapper);
        int seeded = registry.seedFromClasspath();
        registry.reload();
        registry.getAll().forEach(spec -> log.info("[intent] 注册意图: {} v{} scope={} executor={} ({})",
                spec.getId(), spec.getVersion(), spec.getScope(),
                spec.getExecutor() == null || spec.getExecutor().isBlank() ? "builtin-agent" : spec.getExecutor(),
                spec.getName()));
        log.info("[intent] 意图规范就绪: {} 个（本次新播种 {} 个）", registry.getAll().size(), seeded);
        return registry;
    }

    /**
     * 执行器档案注册中心：classpath YAML 播种 + DB 存储与缓存 + 运行时热更新。
     */
    @Bean
    @ConditionalOnMissingBean
    public ExecutorProfileRegistry intentExecutorProfileRegistry(
            IntentExecutorMapper executorMapper,
            HostToolRegistry tools,
            IntentSpecRegistry specRegistry,
            ObjectProvider<IntentExecutor> executorsProvider) {
        // 保留 id：内置推理循环 + 宿主 Bean 执行器（档案不可占用，热更新不会误注销）
        LinkedHashSet<String> reserved = new LinkedHashSet<>();
        reserved.add(IntentExecutor.BUILTIN_ID);
        executorsProvider.orderedStream().map(IntentExecutor::id).forEach(reserved::add);
        ExecutorProfileRegistry registry =
                new ExecutorProfileRegistry(executorMapper, tools, specRegistry::getAll, reserved);
        registry.seedFromClasspath();
        registry.reload();
        registry.entries().forEach(entry -> log.info("[intent] 注册执行器档案: {} type={} model={}",
                entry.profile().id(), entry.profile().type(),
                entry.profile().model() == null ? "（纯工具技能）" : entry.profile().model().modelId()));
        return registry;
    }

    // ------------------------------------------------------------ 目录增强（声明式事实规则）

    /**
     * 声明式事实规则引擎：业务模块只写「一条规则 = 查询 + 条件 + 输出模板 + 参数映射」，
     * 事实由各模块的 {@link IntentFactProvider} 供给。
     *
     * <p>规则写错（意图不存在、参数不在入参 Schema 内）直接抛异常让服务起不来
     * —— 静默不出待办是最难排查的一类故障。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public IntentCatalogEnricher intentRuleCatalogEnricher(
            IntentSpecRegistry specRegistry,
            ObjectProvider<IntentFactProvider> factsProvider,
            IntentProperties properties) {
        CompositeIntentFactProvider facts =
                new CompositeIntentFactProvider(factsProvider.orderedStream().toList());
        List<IntentSuggestionRule> rules = new ArrayList<>();
        for (Resource resource : IntentResources.scan("classpath*:intent-rules/*.yaml")) {
            try (InputStream in = resource.getInputStream()) {
                rules.addAll(IntentRuleLoader.parse(
                        new String(in.readAllBytes(), StandardCharsets.UTF_8), resource.getFilename()));
            } catch (Exception e) {
                throw new IllegalStateException("意图事实规则加载失败: " + resource + " - " + e.getMessage(), e);
            }
        }
        if (properties.getRulesDir() != null && !properties.getRulesDir().isBlank()) {
            rules.addAll(IntentRuleLoader.loadDir(Path.of(properties.getRulesDir())));
        }
        if (!rules.isEmpty()) {
            try {
                IntentRuleLoader.validate(rules, id -> specRegistry.get(id)
                        .map(IntentCatalogEntries::of).orElse(null));
            } catch (IntentRuleException e) {
                throw new IllegalStateException("意图事实规则校验失败（" + e.getMessage() + "）: " + e.problems(), e);
            }
        }
        log.info("[intent] 声明式事实规则: {} 条，事实源 {} 个", rules.size(), facts.size());
        return new RuleBasedIntentCatalogEnricher(rules, facts);
    }

    // ------------------------------------------------------------ 运行时

    /**
     * 意图运行时：通用编排（入参校验 / 执行器路由 / 结果规范化 / 执行留痕）。
     *
     * <p>执行内核为可替换的 {@link IntentExecutor} SPI —— 宿主声明 Bean（工作流 / 规则引擎 /
     * 多智能体编排）与执行器档案页维护的 DB 档案（agent / skill / flow 型）并存，
     * 意图规范用 {@code executor: <id>} 指定，缺省走内置 builtin-agent 推理循环。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public IntentRuntime intentRuntime(IntentProperties properties,
            HostToolRegistry tools,
            IntentSpecRegistry specRegistry,
            ExecutorProfileRegistry executorRegistry,
            TraceStore traceStore,
            ObjectProvider<IntentExecutor> executorsProvider,
            ObjectProvider<GatewayClient> gatewayProvider) {
        LlmConfig llm = buildLlm(properties.getLlm());
        List<IntentExecutor> customs = new ArrayList<>(executorsProvider.orderedStream().toList());
        customs.addAll(executorRegistry.executors());
        customs.forEach(executor -> log.info("[intent] 注册自定义执行器: {}", executor.id()));
        GatewayClient gateway = gatewayProvider.getIfAvailable();
        if (gateway == null && properties.getGateway().isEnabled()
                && properties.getGateway().getBaseUrl() != null
                && !properties.getGateway().getBaseUrl().isBlank()) {
            gateway = new HttpGatewayClient(properties.getGateway().getBaseUrl(),
                    properties.getGateway().getAppKey(), properties.getGateway().getAppSecret());
            log.info("[intent] 已装配意图网关: {}", properties.getGateway().getBaseUrl());
        }
        if (gateway == null) {
            log.info("[intent] 未装配意图网关：remote / composite 意图将返回 REMOTE_UNAVAILABLE");
        }
        log.info("[intent] LLM 接入: {} / {} @ {}", properties.getLlm().getProvider(),
                llm.modelId(), llm.baseUrl());
        warnUnknownToolRefs(specRegistry, tools);
        return new IntentRuntime(llm, tools, specRegistry::getAll, customs, gateway, traceStore,
                properties.getLlm().getMaxTurns(), properties.getLlm().getOutputMaxRetries());
    }

    /**
     * 启动期校验意图声明的宿主工具是否都已注册。
     *
     * <p>只告警不阻断：某个业务模块临时下线，不应该让整个服务起不来；
     * 但必须在日志里说清楚 —— 否则要等用户点了按钮才发现"工具不存在"。</p>
     */
    private void warnUnknownToolRefs(IntentSpecRegistry specRegistry, HostToolRegistry tools) {
        for (IntentSpec spec : specRegistry.getAll()) {
            for (String tool : spec.getTools() == null ? List.<String>of() : spec.getTools()) {
                if (!tools.has(tool)) {
                    log.error("[intent] 意图 {} 声明的宿主工具未注册: {}（该意图执行时会失败，请检查业务模块是否已引入）",
                            spec.getId(), tool);
                }
            }
        }
    }

    // ------------------------------------------------------------ helpers

    /**
     * 按配置构建 LLM 接入参数。
     *
     * <p>取值优先级（后者为前者的兜底）：</p>
     * <ol>
     *   <li>{@code intent.llm.*} 配置项；</li>
     *   <li>环境变量 {@code PI_API_KEY / PI_BASE_URL / PI_MODEL_ID}
     *       —— 与 pi-ai 自身的环境变量口径一致，便于在容器里注入而不落盘密钥；</li>
     *   <li>供应商内置默认值（deepseek / openai / anthropic）。</li>
     * </ol>
     *
     * <p>刻意支持"供应商 + 覆盖端点/模型"的组合：例如 {@code provider: deepseek} 配
     * {@code base-url: https://api.deepseek.com} 与 {@code model-id: deepseek-flash}，
     * 既能走 DeepSeek 的协议适配，又能选具体型号。</p>
     */
    private LlmConfig buildLlm(IntentProperties.Llm llm) {
        String provider = valueOr(llm.getProvider(), "custom");
        String apiKey = firstNonBlank(llm.getApiKey(), env("PI_API_KEY"),
                env(providerApiKeyEnv(provider)), env("INTENT_LLM_API_KEY"));
        String baseUrl = firstNonBlank(llm.getBaseUrl(), env("PI_BASE_URL"),
                defaultBaseUrl(provider));
        String modelId = firstNonBlank(llm.getModelId(), env("PI_MODEL_ID"),
                defaultModelId(provider));
        String api = "anthropic".equals(provider) ? "anthropic-messages" : "openai-completions";
        return new LlmConfig(baseUrl, api, provider, modelId, modelId, apiKey,
                llm.getContextWindow(), llm.getMaxTokens());
    }

    /**
     * 供应商标识 → 对应的 API Key 环境变量名
     */
    private static String providerApiKeyEnv(String provider) {
        return switch (provider) {
            case "deepseek" -> "DEEPSEEK_API_KEY";
            case "openai" -> "OPENAI_API_KEY";
            case "anthropic" -> "ANTHROPIC_API_KEY";
            default -> "INTENT_LLM_API_KEY";
        };
    }

    /**
     * 供应商默认端点
     */
    private static String defaultBaseUrl(String provider) {
        return switch (provider) {
            case "deepseek" -> "https://api.deepseek.com";
            case "openai" -> "https://api.openai.com/v1";
            case "anthropic" -> "https://api.anthropic.com";
            default -> "https://api.deepseek.com";
        };
    }

    /**
     * 供应商默认模型
     */
    private static String defaultModelId(String provider) {
        return switch (provider) {
            case "deepseek" -> "deepseek-chat";
            case "openai" -> "gpt-4o-mini";
            case "anthropic" -> "claude-sonnet-4-5";
            default -> "deepseek-chat";
        };
    }

    /**
     * 读环境变量（空串按未设置处理）
     */
    private static String env(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? null : value;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

}
