package org.dromara.common.intent.service;

import dev.intent.protocol.CatalogResponse;
import dev.intent.protocol.ExecutionTraceRecord;
import dev.intent.protocol.GatewayStatus;
import dev.intent.protocol.IntentCatalogEntry;
import dev.intent.protocol.IntentError;
import dev.intent.protocol.IntentErrorCodes;
import dev.intent.protocol.IntentFeedback;
import dev.intent.protocol.IntentRequest;
import dev.intent.protocol.IntentResult;
import dev.intent.protocol.IntentSpec;
import dev.intent.protocol.IntentStatus;
import dev.intent.protocol.UsageInfo;
import dev.intent.sdk.catalog.IntentCatalogAssembler;
import dev.intent.sdk.catalog.IntentCatalogContext;
import dev.intent.sdk.catalog.IntentCatalogEnricher;
import dev.intent.sdk.executor.ExecutorProfile;
import dev.intent.sdk.executor.ExecutorProfileException;
import dev.intent.sdk.host.IntentCatalogEntries;
import dev.intent.sdk.host.IntentContextBridge;
import dev.intent.sdk.host.IntentPermissionPolicy;
import dev.intent.sdk.host.IntentPrincipal;
import dev.intent.sdk.pi.IntentRuntime;
import dev.intent.sdk.spec.IntentSpecException;
import dev.intent.sdk.spec.IntentSpecLoader;
import dev.intent.sdk.spec.IntentSpecValidator;
import dev.intent.sdk.store.TraceStore;
import dev.intent.sdk.tool.IntentTool;
import dev.intent.sdk.tool.HostToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.intent.config.IntentProperties;
import org.dromara.common.intent.domain.IntentConfigDO;
import org.dromara.common.intent.mapper.IntentConfigMapper;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.system.api.model.LoginUser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 意图服务：目录（按角色过滤 + 目录增强）、执行（鉴权 + 上下文桥 + 留痕）、
 * 执行器档案与运营配置的读写。
 *
 * <p><b>可见性三重口径</b>（缺一不可）：</p>
 * <ol>
 *   <li>上架：{@code intent_config.enabled} 为 false 的意图，目录不展示、执行也拒绝；</li>
 *   <li>角色：{@code intent_config.roles} 覆盖 {@code IntentSpec.policy.roles}，
 *       由 {@link IntentPermissionPolicy} 判定；未配置则沿用规范声明；</li>
 *   <li>页面：{@code IntentSpec.pages} 声明挂载点的意图只出现在匹配页面（{@code crm/customer/*} 前缀通配），
 *       未声明 = 全局意图。</li>
 * </ol>
 *
 * <p><b>目录隐藏 ≠ 免鉴权</b>：{@link #execute} 在目录过滤之外<b>独立再校验一次</b>，
 * 否则知道 intentId 的人可以直接 POST 执行。</p>
 *
 * @author RuoYi-Vue-Plus
 */
@Slf4j
@Service
@RequiredArgsConstructor
// 与 IntentAutoConfiguration 同一开关：关掉 intent.enabled 时，本类与控制器一起下线
// （否则组件扫描会把它装配起来，却找不到运行时 Bean，反而让服务起不来）
@ConditionalOnProperty(prefix = "intent", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IntentService {

    private final IntentSpecRegistry specRegistry;

    private final ExecutorProfileRegistry executorProfileRegistry;

    private final IntentRuntime intentRuntime;

    private final TraceStore traceStore;

    private final IntentProperties properties;

    private final IntentConfigMapper intentConfigMapper;

    private final IntentPermissionPolicy permissionPolicy;

    private final IntentContextBridge contextBridge;

    private final HostToolRegistry hostToolRegistry;

    /**
     * 目录增强器（业务模块声明 Bean 即接入；缺省无增强 = 纯静态目录，行为不变）
     *
     * <p>用 {@link ObjectProvider} 而不是 {@code List<IntentCatalogEnricher>}：
     * 前者在"一个增强器都没有"时也能正常启动，后者会让容器在构造期直接报缺依赖。</p>
     */
    private final ObjectProvider<IntentCatalogEnricher> catalogEnrichers;

    /**
     * 目录增强结果缓存：同一用户 + 页面在 TTL 内复用，执行意图后立即失效
     */
    private final CatalogCache catalogCache = new CatalogCache();

    // ------------------------------------------------------------ 目录

    /**
     * 获得意图目录（按当前用户角色与页面过滤，含动态徽标与待办建议）。
     *
     * @param page 页面标识（如 {@code system/user}）；空 = 意图中心（全量）
     * @return 目录响应
     */
    public CatalogResponse getCatalog(String page) {
        IntentPrincipal principal = currentPrincipal();
        // ① 当前用户可见的意图（上架 + 角色）：建议通道复用该集合做准入，
        //    下架/无权限的意图不得从"动态建议"漏出
        List<IntentCatalogEntry> visible = specRegistry.getAll().stream()
                .filter(this::isEnabled)
                .filter(spec -> visibleTo(principal, spec))
                .map(IntentCatalogEntries::of)
                .toList();
        // ② 当前页面装载的意图（前端渲染主体）
        List<IntentCatalogEntry> entries = visible.stream()
                .filter(entry -> matchesPage(entry.pages(), page))
                .toList();
        // ③ 目录增强：动态提示（徽标）与动态待办建议，带缓存与失败降级
        IntentCatalogAssembler.Result enriched = enrich(principal, page, entries, visible);
        return new CatalogResponse(properties.getSystemName(),
                properties.getGateway().isEnabled() ? GatewayStatus.ENABLED : GatewayStatus.UNAVAILABLE,
                enriched.entries(), enriched.suggestions());
    }

    /**
     * 目录增强：调用宿主 {@link IntentCatalogEnricher} 求值用户相关的事实
     * （如"3 个账号连续登录失败"），求值失败/超时不影响意图菜单可用性。
     */
    private IntentCatalogAssembler.Result enrich(IntentPrincipal principal, String page,
            List<IntentCatalogEntry> entries, List<IntentCatalogEntry> visible) {
        IntentProperties.Suggestions config = properties.getSuggestions();
        List<IntentCatalogEnricher> enrichers = catalogEnrichers.orderedStream().toList();
        if (!config.isEnabled() || enrichers.isEmpty()) {
            return IntentCatalogAssembler.Result.of(entries);
        }
        IntentCatalogContext context = buildCatalogContext(principal, page);
        String cacheKey = context.cacheKey();
        IntentCatalogAssembler.Result cached = catalogCache.get(cacheKey, config.getCacheSeconds());
        if (cached != null) {
            return cached;
        }
        IntentCatalogAssembler.Result result = IntentCatalogAssembler.assemble(
                context, entries, visible, enrichers, config.getMax());
        catalogCache.put(cacheKey, result, config.getCacheSeconds());
        return result;
    }

    /**
     * 目录求值上下文（用户 / 页面 / 时区）。
     *
     * @param page 页面标识
     * @return 上下文
     */
    public IntentCatalogContext buildCatalogContext(String page) {
        return buildCatalogContext(currentPrincipal(), page);
    }

    private IntentCatalogContext buildCatalogContext(IntentPrincipal principal, String page) {
        return new IntentCatalogContext(principal.userId(), principal.userName(), principal.tenantId(),
                page, null, null, null, properties.getTimeZone());
    }

    /**
     * 当前用户可见的目录项（不按页面过滤）—— 供规则预览等场景复用同一口径。
     *
     * @return 目录项
     */
    public List<IntentCatalogEntry> getVisibleEntries() {
        IntentPrincipal principal = currentPrincipal();
        return specRegistry.getAll().stream()
                .filter(this::isEnabled)
                .filter(spec -> visibleTo(principal, spec))
                .map(IntentCatalogEntries::of)
                .toList();
    }

    /**
     * 页面装载的目录项（可见集合 + 页面挂载点过滤）。
     *
     * @param page 页面标识
     * @return 目录项
     */
    public List<IntentCatalogEntry> getPageEntries(String page) {
        return getVisibleEntries().stream()
                .filter(entry -> matchesPage(entry.pages(), page))
                .toList();
    }

    // ------------------------------------------------------------ 执行

    /**
     * 执行意图。
     *
     * <p>失败一律以 {@link IntentResult} 返回（不抛异常）：调用方只需要看 status 与 error.code。</p>
     *
     * @param request 意图执行请求
     * @return 执行结果
     */
    public IntentResult execute(IntentRequest request) {
        IntentSpec spec = specRegistry.get(request.intentId()).orElse(null);
        if (spec == null) {
            return failure(request.intentId(), IntentErrorCodes.INTENT_NOT_FOUND,
                    "意图不存在: " + request.intentId());
        }
        IntentPrincipal principal = currentPrincipal();
        if (!isEnabled(spec)) {
            return failure(spec.getId(), IntentErrorCodes.FORBIDDEN, "该意图已下架");
        }
        if (!visibleTo(principal, spec)) {
            return failure(spec.getId(), IntentErrorCodes.FORBIDDEN, "当前角色无权使用该意图");
        }
        log.info("[intent] 执行意图: {} user={}", spec.getId(), principal.userName());
        // 桥接宿主上下文（登录态 / 请求属性）到执行器的工具线程：
        // 进程内直调，权限随宿主调用栈生效；留痕由运行时统一落盘
        IntentResult result = intentRuntime.execute(spec, request.params(), request.context(),
                toUserInfo(principal), contextBridge.toolDecorator());
        // 用户刚办完一件事：目录增强（待办计数 / 建议）立即失效，下次打开即刷新
        catalogCache.evictUser(principal.userId());
        return result;
    }

    // ------------------------------------------------------------ 留痕与反馈

    /**
     * 当前用户的执行历史。
     *
     * @param intentId 意图编号（可空 = 全部）
     * @param limit    条数
     * @return 执行记录
     */
    public List<ExecutionTraceRecord> getHistory(String intentId, int limit) {
        String userFilter = currentPrincipal().userId();
        return traceStore.list(userFilter, limit).stream()
                .filter(record -> intentId == null || intentId.isBlank()
                        || intentId.equals(record.intentId()))
                .toList();
    }

    /**
     * 执行留痕详情（步骤级转录，可回放）。
     *
     * <p>只允许查看本人的留痕：留痕里含工具入参与业务数据，越权可读等于泄露。</p>
     *
     * @param traceId 留痕编号
     * @return 执行记录
     */
    public ExecutionTraceRecord getTrace(String traceId) {
        ExecutionTraceRecord record = traceStore.get(traceId)
                .orElseThrow(() -> new IntentSpecException(traceId, List.of("执行留痕不存在: " + traceId)));
        String currentUserId = currentPrincipal().userId();
        if (!LoginHelper.isSuperAdmin() && currentUserId != null
                && record.userId() != null && !currentUserId.equals(record.userId())) {
            throw new IntentSpecException(traceId, List.of("无权查看他人的执行留痕"));
        }
        return record;
    }

    /**
     * 结果评价反馈（回流评测库）。
     *
     * @param feedback 反馈
     */
    public void saveFeedback(IntentFeedback feedback) {
        IntentPrincipal principal = currentPrincipal();
        traceStore.saveFeedback(new IntentFeedback(feedback.traceId(), feedback.intentId(),
                principal.userId(), feedback.rating(), feedback.comment()));
    }

    /**
     * 平台状态（系统名 / 网关装配状态 / 意图与工具规模）。
     *
     * @return 状态
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("systemName", properties.getSystemName());
        status.put("gatewayEnabled", properties.getGateway().isEnabled());
        status.put("gatewayStatus", (properties.getGateway().isEnabled()
                ? GatewayStatus.ENABLED : GatewayStatus.UNAVAILABLE).name());
        status.put("intentCount", specRegistry.getAll().size());
        status.put("toolCount", hostToolRegistry.size());
        status.put("executorCount", executorProfileRegistry.entries().size());
        status.put("llmProvider", properties.getLlm().getProvider());
        status.put("llmModel", properties.getLlm().getModelId());
        return status;
    }

    /**
     * 宿主工具清单（执行器档案页勾选工具白名单用）。
     *
     * @return 工具清单
     */
    public List<Map<String, Object>> getToolOptions() {
        List<Map<String, Object>> items = new ArrayList<>();
        hostToolRegistry.all().values().stream()
                .sorted(Comparator.comparing(IntentTool::name))
                .forEach(tool -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", tool.name());
                    item.put("description", tool.description());
                    items.add(item);
                });
        return items;
    }

    // ------------------------------------------------------------ 运营配置（上架 / 角色 / 执行器）

    /**
     * 管理端列表：全部意图 + 运营配置合并视图。
     *
     * @return 配置项
     */
    public List<Map<String, Object>> getConfigList() {
        List<Map<String, Object>> items = new ArrayList<>();
        for (IntentSpec spec : specRegistry.getAll()) {
            IntentConfigDO config = intentConfigMapper.selectByIntentId(spec.getId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("intentId", spec.getId());
            item.put("name", spec.getName());
            item.put("description", spec.getDescription());
            item.put("scope", spec.getScope() == null ? "LOCAL" : spec.getScope().name());
            item.put("version", spec.getVersion());
            item.put("pages", spec.getPages());
            item.put("source", specRegistry.sourceOf(spec.getId()));
            item.put("executor", config != null && StringUtils.isNotBlank(config.getExecutor())
                    ? config.getExecutor()
                    : (StringUtils.isNotBlank(spec.getExecutor()) ? spec.getExecutor() : "builtin-agent"));
            item.put("enabled", config == null || !Boolean.FALSE.equals(config.getEnabled()));
            item.put("roles", config != null ? parseRoles(config.getRoles())
                    : normalizeRoles(spec.getPolicy() == null ? null : spec.getPolicy().getRoles()));
            item.put("configured", config != null);
            items.add(item);
        }
        return items;
    }

    /**
     * 更新运营配置（上架状态 + 可见角色 + 执行器引用）。
     *
     * @param intentId   意图编号
     * @param enabled    是否上架
     * @param roleCodes  可见角色；空 = 不限制
     * @param executorId 执行器标识（空 / builtin-agent = 回到缺省）
     * @param remark     备注
     */
    public void updateConfig(String intentId, Boolean enabled, List<String> roleCodes,
            String executorId, String remark) {
        IntentSpec spec = requireSpec(intentId);
        List<String> roles = roleCodes == null || roleCodes.isEmpty()
                ? List.of("*") : List.copyOf(roleCodes);
        if (StringUtils.isNotBlank(executorId) && !"builtin-agent".equals(executorId.trim())) {
            validateExecutorRef(executorId.trim());
        }
        IntentConfigDO config = intentConfigMapper.selectByIntentId(intentId);
        if (config == null) {
            config = new IntentConfigDO();
            config.setIntentId(intentId);
        }
        config.setEnabled(enabled == null || enabled);
        config.setRoles(toJson(roles));
        config.setExecutor(StringUtils.isBlank(executorId) || "builtin-agent".equals(executorId.trim())
                ? null : executorId.trim());
        config.setRemark(remark);
        if (config.getId() == null) {
            intentConfigMapper.insert(config);
        } else {
            intentConfigMapper.updateById(config);
        }
        evictCatalogCache();
        log.info("[intent] 更新意图配置: {} enabled={} roles={} executor={}", spec.getId(),
                config.getEnabled(), roles, config.getExecutor());
    }

    /**
     * 目录增强缓存全量失效（配置变更后调用：待办面板要立刻变，而不是等 TTL）。
     */
    public void evictCatalogCache() {
        catalogCache.clear();
    }

    // ------------------------------------------------------------ 意图规范 CRUD

    /**
     * 意图规范列表（含来源与执行器）。
     *
     * @return 规范列表
     */
    public List<Map<String, Object>> getSpecList() {
        List<Map<String, Object>> items = new ArrayList<>();
        for (IntentSpec spec : specRegistry.getAll()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("intentId", spec.getId());
            item.put("name", spec.getName());
            item.put("description", spec.getDescription());
            item.put("scope", spec.getScope() == null ? "LOCAL" : spec.getScope().name());
            item.put("version", spec.getVersion());
            item.put("brief", StringUtils.isBlank(spec.getDescription()) ? spec.getName() : spec.getDescription());
            item.put("source", specRegistry.sourceOf(spec.getId()));
            item.put("executor", StringUtils.isBlank(spec.getExecutor()) ? "builtin-agent" : spec.getExecutor());
            item.put("toolCount", spec.getTools() == null ? 0 : spec.getTools().size());
            item.put("pages", spec.getPages());
            item.put("aliases", spec.getAliases());
            items.add(item);
        }
        return items;
    }

    /**
     * 意图规范原文（编辑器回显）。
     *
     * @param intentId 意图编号
     * @return YAML 文本
     */
    public String getSpecYaml(String intentId) {
        return specRegistry.getYaml(intentId);
    }

    /**
     * 新增意图规范。
     *
     * @param yaml YAML 文本
     * @return 意图编号
     */
    public String createSpec(String yaml) {
        IntentSpec spec = specRegistry.create(yaml);
        validateSpecReferences(spec);
        log.info("[intent] 新增意图: {}", spec.getId());
        return spec.getId();
    }

    /**
     * 修改意图规范。
     *
     * @param intentId 意图编号
     * @param yaml     YAML 文本
     * @return 意图编号
     */
    public String updateSpec(String intentId, String yaml) {
        IntentSpec spec = specRegistry.update(intentId, yaml);
        validateSpecReferences(spec);
        log.info("[intent] 修改意图: {}", intentId);
        return spec.getId();
    }

    /**
     * 删除意图规范（同步清理运营配置）。
     *
     * @param intentId 意图编号
     */
    public void deleteSpec(String intentId) {
        specRegistry.delete(intentId);
        intentConfigMapper.deleteByIntentId(intentId);
        log.info("[intent] 删除意图: {}", intentId);
    }

    /**
     * 校验意图规范的引用完整性（执行器已注册、工具已在宿主注册）。
     *
     * <p>保存时拦截，而不是等执行时报错 —— 避免"点了按钮才发现工具不存在"。</p>
     *
     * @param spec 意图规范
     */
    public void validateSpecReferences(IntentSpec spec) {
        List<String> errors = new ArrayList<>();
        if (StringUtils.isNotBlank(spec.getExecutor())
                && !intentRuntime.executorIds().contains(spec.getExecutor().trim())) {
            errors.add("意图声明的执行器未注册: " + spec.getExecutor()
                    + "（已注册: " + intentRuntime.executorIds() + "）");
        }
        for (String tool : spec.getTools() == null ? List.<String>of() : spec.getTools()) {
            if (!hostToolRegistry.has(tool)) {
                errors.add("意图声明的宿主工具未注册: " + tool);
            }
        }
        if (!errors.isEmpty()) {
            throw new IntentSpecException(spec.getId(), errors);
        }
    }

    /**
     * 校验意图规范（后台编辑器"校验"按钮；不落库）。
     *
     * @param yaml YAML 文本
     * @return 错误列表（空 = 通过）
     */
    public List<String> validateSpecYaml(String yaml) {
        try {
            IntentSpec spec = IntentSpecLoader.parse(yaml, true);
            List<String> errors = new ArrayList<>(IntentSpecValidator.validate(spec));
            if (StringUtils.isNotBlank(spec.getExecutor())
                    && !intentRuntime.executorIds().contains(spec.getExecutor().trim())) {
                errors.add("执行器未注册: " + spec.getExecutor());
            }
            for (String tool : spec.getTools() == null ? List.<String>of() : spec.getTools()) {
                if (!hostToolRegistry.has(tool)) {
                    errors.add("宿主工具未注册: " + tool);
                }
            }
            return errors;
        } catch (Exception e) {
            return List.of(e.getMessage() == null ? "规范解析失败" : e.getMessage());
        }
    }

    // ------------------------------------------------------------ 执行器档案 CRUD

    /**
     * 执行器档案列表（含来源与构建信息）。
     *
     * @return 档案列表
     */
    public List<Map<String, Object>> getExecutorList() {
        List<Map<String, Object>> items = new ArrayList<>();
        for (ExecutorProfileRegistry.Entry entry : executorProfileRegistry.entries()) {
            ExecutorProfile profile = entry.profile();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("executorId", profile.id());
            item.put("name", profile.name());
            item.put("type", profile.type());
            item.put("description", profile.description());
            item.put("model", profile.model() == null ? null : profile.model().modelId());
            item.put("tools", profile.tools());
            item.put("stepCount", profile.steps() == null ? 0 : profile.steps().size());
            item.put("nodeCount", profile.flow() == null ? 0 : profile.flow().size());
            item.put("maxTurns", profile.limits() == null ? null : profile.limits().maxTurns());
            item.put("source", entry.source());
            items.add(item);
        }
        return items;
    }

    /**
     * 执行器下拉选项：内置推理循环 + 档案执行器（宿主 Bean 执行器为代码级 SPI，不在此列）。
     *
     * @return 选项列表
     */
    public List<Map<String, Object>> getExecutorOptions() {
        List<Map<String, Object>> options = new ArrayList<>();
        Map<String, Object> builtin = new LinkedHashMap<>();
        builtin.put("executorId", "builtin-agent");
        builtin.put("name", "内置推理循环（缺省）");
        builtin.put("type", "agent");
        options.add(builtin);
        for (ExecutorProfileRegistry.Entry entry : executorProfileRegistry.entries()) {
            ExecutorProfile profile = entry.profile();
            Map<String, Object> option = new LinkedHashMap<>();
            option.put("executorId", profile.id());
            option.put("name", StringUtils.isBlank(profile.name()) ? profile.id() : profile.name());
            option.put("type", profile.type());
            options.add(option);
        }
        return options;
    }

    /**
     * 执行器档案原文（编辑器回显）。
     *
     * @param executorId 执行器标识
     * @return YAML 文本
     */
    public String getExecutorYaml(String executorId) {
        return executorProfileRegistry.getYaml(executorId);
    }

    /**
     * 新增执行器档案并热更新到运行时。
     *
     * @param yaml YAML 文本
     * @return 执行器标识
     */
    public String createExecutor(String yaml) {
        ExecutorProfile profile = executorProfileRegistry.create(yaml);
        executorProfileRegistry.applyTo(intentRuntime);
        log.info("[intent] 新增执行器: {}", profile.id());
        return profile.id();
    }

    /**
     * 修改执行器档案并热更新到运行时。
     *
     * @param executorId 执行器标识
     * @param yaml       YAML 文本
     * @return 执行器标识
     */
    public String updateExecutor(String executorId, String yaml) {
        executorProfileRegistry.update(executorId, yaml);
        executorProfileRegistry.applyTo(intentRuntime);
        log.info("[intent] 修改执行器: {}", executorId);
        return executorId;
    }

    /**
     * 删除执行器档案并热更新到运行时。
     *
     * @param executorId 执行器标识
     */
    public void deleteExecutor(String executorId) {
        executorProfileRegistry.delete(executorId);
        executorProfileRegistry.applyTo(intentRuntime);
        log.info("[intent] 删除执行器: {}", executorId);
    }

    /**
     * 校验执行器档案（后台编辑器"校验"按钮；不落库）。
     *
     * @param yaml YAML 文本
     * @return 错误列表（空 = 通过）
     */
    public List<String> validateExecutorYaml(String yaml) {
        try {
            dev.intent.sdk.executor.ExecutorProfile profile =
                    dev.intent.sdk.executor.ExecutorProfileLoader.parse(yaml, true);
            return dev.intent.sdk.executor.ExecutorProfileValidator.validate(profile);
        } catch (Exception e) {
            return List.of(e.getMessage() == null ? "档案解析失败" : e.getMessage());
        }
    }

    // ------------------------------------------------------------ helpers

    /**
     * 当前身份（SDK 视角的"你是谁"）。
     */
    private IntentPrincipal currentPrincipal() {
        LoginUser loginUser = LoginHelper.getLoginUser();
        if (loginUser == null || loginUser.getUserId() == null) {
            Long userId = LoginHelper.getUserId();
            if (userId == null) {
                return IntentPrincipal.anonymous();
            }
            return IntentPrincipal.of(String.valueOf(userId), LoginHelper.getUsername(), null, List.of());
        }
        return IntentPrincipal.of(String.valueOf(loginUser.getUserId()),
                loginUser.getNickname() == null ? loginUser.getUsername() : loginUser.getNickname(),
                null, org.dromara.common.intent.bridge.SaTokenIntentPrincipalProvider.rolesOf(loginUser));
    }

    private static dev.intent.protocol.UserInfo toUserInfo(IntentPrincipal principal) {
        return new dev.intent.protocol.UserInfo(principal.userId(), principal.userName(),
                LoginHelper.getDeptName(), Map.of());
    }

    /**
     * 页面挂载过滤：pages 声明了挂载点的意图只出现在匹配页面；未声明 = 全局意图。
     * page 为空 = 意图中心（全量）。
     */
    private boolean matchesPage(List<String> pages, String page) {
        if (page == null || page.isBlank()) {
            return true;
        }
        if (pages == null || pages.isEmpty()) {
            return true;
        }
        // 精确匹配，或前缀通配（system/user/* 命中 system/user/detail/1）
        for (String pattern : pages) {
            if (pattern == null || pattern.isBlank()) {
                continue;
            }
            if (pattern.equals(page)) {
                return true;
            }
            if (pattern.endsWith("*") && page.startsWith(pattern.substring(0, pattern.length() - 1))) {
                return true;
            }
        }
        return false;
    }

    private boolean isEnabled(IntentSpec spec) {
        IntentConfigDO config = intentConfigMapper.selectByIntentId(spec.getId());
        return config == null || !Boolean.FALSE.equals(config.getEnabled());
    }

    private boolean visibleTo(IntentPrincipal principal, IntentSpec spec) {
        return permissionPolicy.canUse(principal, rolesOf(spec));
    }

    private List<String> rolesOf(IntentSpec spec) {
        IntentConfigDO config = intentConfigMapper.selectByIntentId(spec.getId());
        if (config != null && StringUtils.isNotBlank(config.getRoles())) {
            return normalizeRoles(parseRoles(config.getRoles()));
        }
        return normalizeRoles(spec.getPolicy() == null ? null : spec.getPolicy().getRoles());
    }

    private static List<String> normalizeRoles(List<String> roles) {
        return roles == null || roles.isEmpty() ? List.of("*") : List.copyOf(roles);
    }

    private List<String> parseRoles(String json) {
        if (StringUtils.isBlank(json)) {
            return List.of("*");
        }
        try {
            return JsonUtils.parseArray(json, String.class);
        } catch (Exception e) {
            return List.of("*");
        }
    }

    private static String toJson(List<String> roles) {
        try {
            return JsonUtils.toJsonString(roles);
        } catch (Exception e) {
            return "[\"*\"]";
        }
    }

    private IntentSpec requireSpec(String intentId) {
        return specRegistry.get(intentId).orElseThrow(
                () -> new IntentSpecException(intentId, List.of("意图不存在: " + intentId)));
    }

    private void validateExecutorRef(String executorId) {
        if (!intentRuntime.executorIds().contains(executorId)) {
            throw new ExecutorProfileException(executorId,
                    List.of("执行器未注册: " + executorId + "（已注册: " + intentRuntime.executorIds() + "）"));
        }
    }

    private static IntentResult failure(String intentId, String code, String message) {
        return new IntentResult("trc-" + java.util.UUID.randomUUID(), intentId,
                IntentStatus.FAILED, null, null, new IntentError(code, message),
                List.of(), UsageInfo.ZERO, 0);
    }

    /**
     * 目录增强结果缓存：key = 用户|页面，TTL 到期或用户执行意图后失效。
     */
    private static final class CatalogCache {

        private final Map<String, Entry> entries = new ConcurrentHashMap<>();

        void clear() {
            entries.clear();
        }

        IntentCatalogAssembler.Result get(String key, int ttlSeconds) {
            if (ttlSeconds <= 0) {
                return null;
            }
            Entry entry = entries.get(key);
            if (entry == null) {
                return null;
            }
            if (System.currentTimeMillis() - entry.at() > ttlSeconds * 1000L) {
                entries.remove(key);
                return null;
            }
            return entry.result();
        }

        void put(String key, IntentCatalogAssembler.Result result, int ttlSeconds) {
            if (ttlSeconds > 0) {
                entries.put(key, new Entry(result, System.currentTimeMillis()));
            }
        }

        void evictUser(String userId) {
            entries.keySet().removeIf(key -> key.startsWith(userId + "|"));
        }

        /**
         * 缓存条目
         *
         * @param result 装配结果
         * @param at     写入时刻
         */
        private record Entry(IntentCatalogAssembler.Result result, long at) {
        }
    }

}
