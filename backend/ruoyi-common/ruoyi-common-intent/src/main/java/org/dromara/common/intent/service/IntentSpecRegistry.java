package org.dromara.common.intent.service;

import dev.intent.protocol.IntentSpec;
import dev.intent.sdk.spec.IntentSpecException;
import dev.intent.sdk.spec.IntentSpecLoader;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.intent.domain.IntentSpecDO;
import org.dromara.common.intent.mapper.IntentSpecMapper;
import org.dromara.common.intent.util.IntentResources;
import org.springframework.core.io.Resource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 意图规范注册中心：意图定义的<b>单一事实来源</b>（DB 化）。
 *
 * <p>生命周期：</p>
 * <ol>
 *   <li><b>播种</b>：启动时把各业务模块 {@code classpath*:intent/*.yaml} 写入 {@code intent_spec} 表。
 *       已存在（<b>含逻辑删除态</b>）则跳过 —— 运营在后台删掉的意图不会因为重启被"复活"；</li>
 *   <li><b>加载</b>：{@link #reload()} 把 DB 全量读入内存缓存，目录与执行引擎只读缓存；</li>
 *   <li><b>运营</b>：后台新增/修改/删除后即时 {@link #reload()}，无需发版、无需重启。</li>
 * </ol>
 *
 * <p>校验发生在写入之前（{@link IntentSpecLoader#parse} 内含
 * {@code IntentSpecValidator}），不合规范的意图进不了库，也上不了线。</p>
 *
 * @author RuoYi-Vue-Plus
 */
@Slf4j
public class IntentSpecRegistry {

    /**
     * classpath 种子目录（各业务模块自带）
     */
    public static final String SEED_PATTERN = "classpath*:intent/*.yaml";

    private final IntentSpecMapper mapper;

    private final List<IntentSpec> cache = new CopyOnWriteArrayList<>();

    public IntentSpecRegistry(IntentSpecMapper mapper) {
        this.mapper = mapper;
    }

    // ------------------------------------------------------------ 播种与加载

    /**
     * 扫描并播种全部 classpath 意图规范。
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
                // 种子非法必须显式暴露：静默丢一个意图，线上就是"按钮凭空消失"
                throw new IllegalStateException("意图规范种子加载失败: " + resource.getFilename()
                        + " - " + e.getMessage(), e);
            }
        }
        return seeded;
    }

    /**
     * 从 YAML 文本播种内置意图：物理不存在才写入（含删除态判定）。
     *
     * @param yaml       YAML 文本
     * @param sourceName 来源文件名（日志用）
     * @return 本次是否写入
     */
    public boolean seedFromYaml(String yaml, String sourceName) {
        IntentSpec spec = IntentSpecLoader.parse(yaml, true);
        if (mapper.countByIntentIdIncludeDeleted(spec.getId()) > 0) {
            return false;
        }
        IntentSpecDO row = new IntentSpecDO();
        row.setIntentId(spec.getId());
        row.setName(spec.getName());
        row.setDescription(spec.getDescription());
        row.setScope(spec.getScope() == null ? "LOCAL" : spec.getScope().name());
        row.setVersion(spec.getVersion());
        row.setExecutor(spec.getExecutor());
        row.setSource("builtin");
        row.setSpecYaml(yaml);
        row.setRemark("classpath 种子: " + sourceName);
        mapper.insert(row);
        log.info("[intent] 播种内置意图: {}", spec.getId());
        return true;
    }

    /**
     * 重新加载缓存（DB 全量，逻辑删除行自动过滤）。
     */
    public synchronized void reload() {
        List<IntentSpec> loaded = new ArrayList<>();
        for (IntentSpecDO row : mapper.selectList()) {
            String yaml = row.getSpecYaml();
            if (yaml == null || yaml.isBlank()) {
                log.error("[intent] 意图规范为空，已跳过: {}", row.getIntentId());
                continue;
            }
            try {
                loaded.add(IntentSpecLoader.parse(yaml, true));
            } catch (Exception e) {
                log.error("[intent] 意图规范解析失败，已跳过: {} - {}", row.getIntentId(), e.getMessage());
            }
        }
        loaded.sort(Comparator.comparing(IntentSpec::getId));
        cache.clear();
        cache.addAll(loaded);
        log.info("[intent] 意图规范缓存刷新: {} 个意图", cache.size());
    }

    // ------------------------------------------------------------ 读取

    /**
     * 全量意图（缓存）。
     *
     * @return 意图列表
     */
    public List<IntentSpec> getAll() {
        if (cache.isEmpty()) {
            reload();
        }
        return List.copyOf(cache);
    }

    /**
     * 按编号查询。
     *
     * @param intentId 意图编号
     * @return 意图
     */
    public Optional<IntentSpec> get(String intentId) {
        if (intentId == null || intentId.isBlank()) {
            return Optional.empty();
        }
        return getAll().stream().filter(spec -> spec.getId().equals(intentId)).findFirst();
    }

    /**
     * 取意图规范原文（后台编辑器回显）。
     *
     * @param intentId 意图编号
     * @return YAML 文本
     */
    public String getYaml(String intentId) {
        IntentSpecDO row = mapper.selectByIntentId(intentId);
        if (row == null) {
            throw new IntentSpecException(intentId, List.of("意图不存在: " + intentId));
        }
        return row.getSpecYaml();
    }

    /**
     * 来源（builtin = classpath 种子，不可删除；custom = 后台创建）。
     *
     * @param intentId 意图编号
     * @return 来源标识
     */
    public String sourceOf(String intentId) {
        IntentSpecDO row = mapper.selectByIntentId(intentId);
        return row == null ? "custom" : (row.getSource() == null ? "custom" : row.getSource());
    }

    // ------------------------------------------------------------ 写操作

    /**
     * 新增（YAML 文本）。编号已存在（含删除态）时报错。
     *
     * @param yaml 意图规范 YAML
     * @return 新意图
     */
    public synchronized IntentSpec create(String yaml) {
        IntentSpec spec = IntentSpecLoader.parse(yaml, true);
        if (mapper.countByIntentIdIncludeDeleted(spec.getId()) > 0) {
            throw new IntentSpecException(spec.getId(),
                    List.of("意图已存在（可能已被删除），请更换编号或使用修改功能"));
        }
        insertRow(spec, yaml, "custom");
        reload();
        return spec;
    }

    /**
     * 修改（YAML 文本，编号不可变更）。
     *
     * @param intentId 目标意图编号
     * @param yaml     意图规范 YAML
     * @return 修改后的意图
     */
    public synchronized IntentSpec update(String intentId, String yaml) {
        IntentSpec spec = IntentSpecLoader.parse(yaml, true);
        if (!spec.getId().equals(intentId)) {
            throw new IntentSpecException(intentId,
                    List.of("YAML 中的 id(" + spec.getId() + ") 与目标不一致"));
        }
        IntentSpecDO row = mapper.selectByIntentId(intentId);
        if (row == null) {
            throw new IntentSpecException(intentId, List.of("意图不存在，无法修改"));
        }
        row.setName(spec.getName());
        row.setDescription(spec.getDescription());
        row.setScope(spec.getScope() == null ? "LOCAL" : spec.getScope().name());
        row.setVersion(spec.getVersion());
        row.setExecutor(spec.getExecutor());
        row.setSpecYaml(yaml);
        mapper.updateById(row);
        reload();
        return spec;
    }

    /**
     * 删除（逻辑删除）。
     *
     * @param intentId 意图编号
     */
    public synchronized void delete(String intentId) {
        IntentSpecDO row = mapper.selectByIntentId(intentId);
        if (row == null) {
            throw new IntentSpecException(intentId, List.of("意图不存在，无法删除"));
        }
        mapper.deleteById(row.getId());
        reload();
    }

    private void insertRow(IntentSpec spec, String yaml, String source) {
        IntentSpecDO row = new IntentSpecDO();
        row.setIntentId(spec.getId());
        row.setName(spec.getName());
        row.setDescription(spec.getDescription());
        row.setScope(spec.getScope() == null ? "LOCAL" : spec.getScope().name());
        row.setVersion(spec.getVersion());
        row.setExecutor(spec.getExecutor());
        row.setSource(source);
        row.setSpecYaml(yaml);
        mapper.insert(row);
    }

}
