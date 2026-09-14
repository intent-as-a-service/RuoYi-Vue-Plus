package org.dromara.intent.tool.system;

import dev.intent.sdk.tool.Schemas;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.intent.tool.BaseHostTool;
import org.dromara.system.domain.bo.SysConfigBo;
import org.dromara.system.domain.vo.SysConfigVo;
import org.dromara.system.service.ISysConfigService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 宿主工具：参数配置查询。
 *
 * <p><b>脱敏是硬要求</b>：参数表里可能存密钥、密码、私有配置，
 * 凡是键名或名称命中敏感词的，值一律以掩码返回，只暴露"是否已配置"。</p>
 *
 * @author RuoYi-Vue-Plus
 */
@Component
@RequiredArgsConstructor
public class SysConfigLookupTool extends BaseHostTool {

    private static final int MAX_ROWS = 60;

    /**
     * 敏感键名关键词（命中即掩码）
     */
    private static final List<String> SENSITIVE = List.of(
            "password", "secret", "token", "key", "accesskey", "private", "salt", "credential", "密码", "密钥");

    private final ISysConfigService configService;

    @Override
    public String name() {
        return "sys_config_lookup";
    }

    @Override
    public String description() {
        return "查询系统参数配置（sys_config）：键名、名称、取值与备注。敏感参数（含 password/secret/token/key 等）"
                + "一律掩码返回，只暴露是否已配置。用于参数审计、排查开关类问题。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("configKey", Schemas.string("参数键名（精确匹配）；留空 = 返回清单"));
        properties.put("keyword", Schemas.string("在键名/名称中模糊匹配的关键字"));
        properties.put("limit", Schemas.number("清单条数上限，默认 50，最大 60"));
        return Schemas.object(properties, List.of());
    }

    @Override
    protected Map<String, Object> run(Map<String, Object> args) throws Exception {
        String configKey = str(args, "configKey");
        if (StringUtils.isNotBlank(configKey)) {
            SysConfigVo config = configService.selectConfigList(newConfigBo(configKey, null)).stream()
                    .findFirst().orElse(null);
            if (config == null) {
                return result(0, List.of(), "参数不存在: " + configKey);
            }
            return result(1, List.of(toRow(config)), null);
        }

        String keyword = str(args, "keyword");
        int limit = Math.min(Math.max(intOr(args, "limit", 50), 1), MAX_ROWS);
        List<SysConfigVo> configs = configService.selectConfigList(newConfigBo(null, keyword));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SysConfigVo config : configs) {
            if (rows.size() >= limit) {
                break;
            }
            rows.add(toRow(config));
        }
        String note = configs.size() > rows.size()
                ? "命中 " + configs.size() + " 条，仅返回前 " + rows.size() + " 条" : null;
        return result(configs.size(), rows, note);
    }

    private static SysConfigBo newConfigBo(String configKey, String keyword) {
        SysConfigBo bo = new SysConfigBo();
        if (StringUtils.isNotBlank(configKey)) {
            bo.setConfigKey(configKey);
        }
        if (StringUtils.isNotBlank(keyword)) {
            bo.setConfigName(keyword);
        }
        return bo;
    }

    private static Map<String, Object> toRow(SysConfigVo config) {
        boolean sensitive = isSensitive(config.getConfigKey()) || isSensitive(config.getConfigName());
        String value = sensitive ? mask(config.getConfigValue()) : config.getConfigValue();
        return row("configId", config.getConfigId(),
                "configKey", config.getConfigKey(),
                "configName", config.getConfigName(),
                "configValue", value,
                "masked", sensitive,
                "remark", config.getRemark());
    }

    private static boolean isSensitive(String text) {
        if (text == null) {
            return false;
        }
        String lower = text.toLowerCase();
        for (String word : SENSITIVE) {
            if (lower.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private static String mask(String value) {
        if (StringUtils.isBlank(value)) {
            return "（未配置）";
        }
        return "******（已配置，长度 " + value.length() + "）";
    }

}
