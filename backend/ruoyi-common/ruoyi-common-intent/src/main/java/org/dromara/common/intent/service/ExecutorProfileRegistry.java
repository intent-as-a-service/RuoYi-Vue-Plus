package org.dromara.common.intent.service;

import dev.intent.protocol.IntentSpec;
import dev.intent.sdk.executor.ExecutorProfile;
import dev.intent.sdk.executor.ExecutorProfileException;
import dev.intent.sdk.executor.ExecutorProfileLoader;
import dev.intent.sdk.executor.IntentExecutor;
import dev.intent.sdk.pi.ExecutorProfiles;
import dev.intent.sdk.pi.IntentRuntime;
import dev.intent.sdk.pi.SkillExecutor;
import dev.intent.sdk.tool.HostToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.intent.domain.IntentExecutorDO;
import org.dromara.common.intent.mapper.IntentExecutorMapper;
import org.dromara.common.intent.util.IntentResources;
import org.springframework.core.io.Resource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 执行器档案注册中心：执行器配置的<b>单一事实来源</b>（DB 化，与意图规范同模式）。
 *
 * <p>"用哪个模型、能用哪些工具、几步做完"从代码变成数据：后台改完调用
 * {@link #applyTo(IntentRuntime)} 热更新到运行时，已经上线的意图立刻换执行策略，
 * 不用重启、不用发版。</p>
 *
 * <p><b>保留 id</b>：内置 {@code builtin-agent} 与宿主以 Bean 形式声明的执行器
 * （工作流 / 规则引擎等代码级 SPI）不可被档案占用，热更新也不会误注销它们。</p>
 *
 * @author RuoYi-Vue-Plus
 */
@Slf4j
public class ExecutorProfileRegistry {

    /**
     * classpath 种子目录
     */
    public static final String SEED_PATTERN = "classpath*:intent-executor/*.yaml";

    /**
     * 缓存条目：解析后的档案 + 来源（builtin = classpath 种子 / custom = 后台创建）。
     *
     * @param profile 执行器档案
     * @param source  来源
     */
    public record Entry(ExecutorProfile profile, String source) {
    }

    private final IntentExecutorMapper mapper;

    private final HostToolRegistry tools;

    private final Supplier<List<IntentSpec>> specCatalog;

    /**
     * 不可被档案占用的执行器 id：内置 builtin-agent + 宿主 Bean 执行器
     */
    private final Set<String> reservedIds;

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    private final List<IntentExecutor> built = new ArrayList<>();

    public ExecutorProfileRegistry(IntentExecutorMapper mapper, HostToolRegistry tools,
            Supplier<List<IntentSpec>> specCatalog, Set<String> reservedIds) {
        this.mapper = mapper;
        this.tools = tools;
        this.specCatalog = specCatalog;
        this.reservedIds = Set.copyOf(reservedIds);
    }

    // ------------------------------------------------------------ 播种与加载

    /**
     * 扫描并播种全部 classpath 执行器档案。
     *
     * @return 本次新播种的条数
     */
    public int seedFromClasspath() {
        int seeded = 0;
        for (Resource resource : IntentResources.scan(SEED_PATTERN)) {
            try {
                if (seedFromYaml(IntentResources.read(resource), resource.getFilename())) {
                    seeded++;
                }
            } catch (Exception e) {
                throw new IllegalStateException("执行器档案种子加载失败: " + resource.getFilename()
                        + " - " + e.getMessage(), e);
            }
        }
        return seeded;
    }

    /**
     * 从 YAML 文本播种内置执行器：物理不存在才写入（含删除态判定）。
     *
     * @param yaml       YAML 文本
     * @param sourceName 来源文件名
     * @return 本次是否写入
     */
    public boolean seedFromYaml(String yaml, String sourceName) {
        ExecutorProfile profile = ExecutorProfileLoader.parse(yaml, true);
        if (mapper.countByExecutorIdIncludeDeleted(profile.id()) > 0) {
            return false;
        }
        IntentExecutorDO row = new IntentExecutorDO();
        row.setExecutorId(profile.id());
        row.setType(profile.type());
        row.setName(profile.name());
        row.setSource("builtin");
        row.setProfileYaml(yaml);
        row.setRemark("classpath 种子: " + sourceName);
        mapper.insert(row);
        log.info("[intent] 播种内置执行器: {}", profile.id());
        return true;
    }

    /**
     * 重新加载缓存并重建执行器实例（DB 全量；坏档案跳过，不阻断启动）。
     */
    public synchronized void reload() {
        Map<String, Entry> loaded = new LinkedHashMap<>();
        Set<String> reserved = new LinkedHashSet<>(reservedIds);
        List<IntentExecutor> rebuilt = new ArrayList<>();
        for (IntentExecutorDO row : mapper.selectList()) {
            String yaml = row.getProfileYaml();
            if (yaml == null || yaml.isBlank()) {
                log.error("[intent] 执行器档案为空，已跳过: {}", row.getExecutorId());
                continue;
            }
            try {
                ExecutorProfile profile = ExecutorProfileLoader.parse(yaml, true);
                if (reserved.contains(profile.id())) {
                    log.error("[intent] 执行器档案与宿主 Bean 执行器冲突，已跳过: {}", profile.id());
                    continue;
                }
                rebuilt.add(ExecutorProfiles.build(List.of(profile), tools, specCatalog).get(0));
                loaded.put(profile.id(), new Entry(profile, row.getSource()));
            } catch (Exception e) {
                log.error("[intent] 执行器档案加载失败，已跳过: {} - {}", row.getExecutorId(), e.getMessage());
            }
        }
        entries.clear();
        entries.putAll(loaded);
        built.clear();
        built.addAll(rebuilt);
        log.info("[intent] 执行器档案缓存刷新: {} 个执行器", built.size());
    }

    // ------------------------------------------------------------ 读取

    /**
     * 全部档案条目（缓存，含来源）。
     *
     * @return 档案条目
     */
    public List<Entry> entries() {
        return List.copyOf(entries.values());
    }

    /**
     * 按标识查询档案。
     *
     * @param executorId 执行器标识
     * @return 档案条目
     */
    public Optional<Entry> get(String executorId) {
        return Optional.ofNullable(entries.get(executorId));
    }

    /**
     * 已构建的执行器实例（供运行时装配）。
     *
     * @return 执行器实例
     */
    public List<IntentExecutor> executors() {
        return List.copyOf(built);
    }

    /**
     * 不可被档案占用的执行器 id（内置 + 宿主 Bean）。
     *
     * @return 保留 id
     */
    public Set<String> reservedIds() {
        return reservedIds;
    }

    /**
     * 档案原文（后台编辑器回显）。
     *
     * @param executorId 执行器标识
     * @return YAML 文本
     */
    public String getYaml(String executorId) {
        IntentExecutorDO row = mapper.selectByExecutorId(executorId);
        if (row == null) {
            throw new ExecutorProfileException(executorId, List.of("执行器不存在: " + executorId));
        }
        return row.getProfileYaml();
    }

    // ------------------------------------------------------------ 写操作（CRUD 后由调用方 applyTo 热更新）

    /**
     * 新增（YAML 文本）。标识已存在（含删除态）或与保留 id 冲突时报错。
     *
     * @param yaml 档案 YAML
     * @return 新档案
     */
    public synchronized ExecutorProfile create(String yaml) {
        ExecutorProfile profile = ExecutorProfileLoader.parse(yaml, true);
        if (reservedIds.contains(profile.id())) {
            throw new ExecutorProfileException(profile.id(),
                    List.of("执行器标识被内置执行器或宿主 Bean 占用，请更换标识"));
        }
        if (mapper.countByExecutorIdIncludeDeleted(profile.id()) > 0) {
            throw new ExecutorProfileException(profile.id(),
                    List.of("执行器已存在（可能已被删除），请更换标识或使用修改功能"));
        }
        insertRow(profile, yaml, "custom");
        reload();
        return profile;
    }

    /**
     * 修改（YAML 文本，标识不可变更）。
     *
     * @param executorId 目标执行器标识
     * @param yaml       档案 YAML
     * @return 修改后的档案
     */
    public synchronized ExecutorProfile update(String executorId, String yaml) {
        ExecutorProfile profile = ExecutorProfileLoader.parse(yaml, true);
        if (!profile.id().equals(executorId)) {
            throw new ExecutorProfileException(executorId,
                    List.of("YAML 中的 id(" + profile.id() + ") 与目标不一致"));
        }
        IntentExecutorDO row = mapper.selectByExecutorId(executorId);
        if (row == null) {
            throw new ExecutorProfileException(executorId, List.of("执行器不存在，无法修改"));
        }
        row.setType(profile.type());
        row.setName(profile.name());
        row.setProfileYaml(yaml);
        mapper.updateById(row);
        reload();
        return profile;
    }

    /**
     * 删除（逻辑删除）。
     *
     * @param executorId 执行器标识
     */
    public synchronized void delete(String executorId) {
        IntentExecutorDO row = mapper.selectByExecutorId(executorId);
        if (row == null) {
            throw new ExecutorProfileException(executorId, List.of("执行器不存在，无法删除"));
        }
        mapper.deleteById(row.getId());
        reload();
    }

    /**
     * 与运行时对齐：注销已删除的档案执行器、注册/替换新执行器（内置与宿主 Bean 执行器不受影响）。
     *
     * @param runtime 意图运行时
     */
    public synchronized void applyTo(IntentRuntime runtime) {
        Set<String> target = built.stream().map(IntentExecutor::id).collect(Collectors.toSet());
        for (String current : runtime.executorIds()) {
            if (!target.contains(current) && !reservedIds.contains(current)) {
                runtime.unregisterExecutor(current);
                log.info("[intent] 注销执行器: {}", current);
            }
        }
        for (IntentExecutor executor : built) {
            runtime.registerExecutor(executor);
            log.info("[intent] 注册执行器: {} ({})", executor.id(),
                    executor instanceof SkillExecutor ? "skill" : "agent");
        }
    }

    private void insertRow(ExecutorProfile profile, String yaml, String source) {
        IntentExecutorDO row = new IntentExecutorDO();
        row.setExecutorId(profile.id());
        row.setType(profile.type());
        row.setName(profile.name());
        row.setSource(source);
        row.setProfileYaml(yaml);
        mapper.insert(row);
    }

}
