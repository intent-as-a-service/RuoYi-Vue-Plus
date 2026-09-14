package org.dromara.intent.tool.monitor;

import dev.intent.sdk.tool.Schemas;
import org.dromara.common.core.constant.CacheNames;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.intent.tool.BaseHostTool;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 宿主工具：缓存概览。
 *
 * <p>只做<b>计数与规模</b>统计，不返回任何缓存值 —— 缓存里可能有会话、令牌与业务数据，
 * 让模型读到等于把敏感数据搬进了提示词。</p>
 *
 * @author RuoYi-Vue-Plus
 */
@Component
public class SysCacheOverviewTool extends BaseHostTool {

    /**
     * 框架内置缓存名 → 用途说明（口径与 CacheNames 一致）
     */
    private static final Map<String, String> KNOWN = new LinkedHashMap<>();

    static {
        KNOWN.put(CacheNames.SYS_CONFIG, "系统参数");
        KNOWN.put(CacheNames.SYS_DICT, "字典数据");
        KNOWN.put(CacheNames.SYS_DICT_TYPE, "字典类型");
        KNOWN.put(CacheNames.SYS_CLIENT, "客户端配置");
        KNOWN.put(CacheNames.SYS_USER_NAME, "用户名 → 用户信息");
        KNOWN.put(CacheNames.SYS_NICKNAME, "昵称");
        KNOWN.put(CacheNames.SYS_DEPT, "部门");
        KNOWN.put(CacheNames.SYS_ROLE_CUSTOM, "角色自定义数据范围");
        KNOWN.put(CacheNames.SYS_DEPT_AND_CHILD, "部门及子部门");
        KNOWN.put(CacheNames.ONLINE_TOKEN_KEY, "在线会话令牌");
        KNOWN.put(CacheNames.PWD_ERR_CNT_KEY, "密码错误计数");
    }

    @Override
    public String name() {
        return "sys_cache_overview";
    }

    @Override
    public String description() {
        return "统计 Redis 中各业务缓存的键数量（参数、字典、部门、角色、在线会话、密码错误计数等）。"
                + "只返回数量与规模，不返回任何缓存值。用于缓存膨胀排查、容量评估。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("includeUnknown", Schemas.bool("是否统计未归类前缀的键，默认 false"));
        return Schemas.object(properties, List.of());
    }

    @Override
    protected Map<String, Object> run(Map<String, Object> args) {
        boolean includeUnknown = boolOr(args, "includeUnknown", false);
        List<Map<String, Object>> rows = new ArrayList<>();
        long total = 0;
        for (Map.Entry<String, String> entry : KNOWN.entrySet()) {
            String prefix = stripTtl(entry.getKey());
            long count = RedisUtils.keys(prefix + "*").size();
            total += count;
            rows.add(row("cacheName", prefix, "purpose", entry.getValue(), "keyCount", count));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("knownKeyTotal", total);
        data.put("caches", rows);
        if (includeUnknown) {
            Collection<String> all = RedisUtils.keys("*");
            data.put("allKeyCount", all.size());
            data.put("unclassifiedKeyCount", Math.max(all.size() - total, 0));
            data.put("note", "allKeyCount 为全库键数（scan 统计，大库下耗时较长）；不含任何键名明细。");
        } else {
            data.put("note", "仅统计已知业务前缀；需要全库规模请把 includeUnknown 设为 true。");
        }
        return data;
    }

    /**
     * 去掉 {@code cacheNames#ttl#maxIdleTime#maxSize#local} 里的配置后缀
     *
     * @param cacheName 缓存名
     * @return 键前缀
     */
    private static String stripTtl(String cacheName) {
        if (StringUtils.isBlank(cacheName)) {
            return cacheName;
        }
        int index = cacheName.indexOf('#');
        return index < 0 ? cacheName : cacheName.substring(0, index);
    }

}
