package org.dromara.intent.tool;

import dev.intent.sdk.tool.IntentTool;
import dev.intent.sdk.tool.IntentToolContext;
import dev.intent.sdk.tool.IntentToolResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 宿主工具基类：把"包装宿主能力"这件事收敛到三个方法。
 *
 * <p>业务侧写一个工具只需要：</p>
 * <ol>
 *   <li>{@link #name()} / {@link #description()} / {@link #parameters()} —— 契约（进模型提示词）；</li>
 *   <li>{@link #run(Map)} —— 进程内直调宿主 Service，
 *       <b>权限与事务天然沿用宿主调用栈</b>（在同一个登录用户、同一个事务里执行）。</li>
 * </ol>
 *
 * <p>异常一律转成"错误工具结果"回传模型，让它自行纠正后重试 ——
 * 而不是把栈信息抛给用户。真正的失败语义由模型在最终结果里说明。</p>
 *
 * @author RuoYi-Vue-Plus
 */
public abstract class BaseHostTool implements IntentTool {

    @Override
    public IntentToolResult execute(String toolCallId, Map<String, Object> args, IntentToolContext context) {
        try {
            return IntentToolResult.data(run(args == null ? Map.of() : args));
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return IntentToolResult.error("工具 " + name() + " 执行失败：" + message
                    + "（请调整参数或换一种取数方式后重试）");
        }
    }

    /**
     * 工具主体：返回结构化数据（随执行轨迹回传前端，模型看到的是它的 JSON 文本）。
     *
     * @param args 已按 {@link #parameters()} 校验过的入参
     * @return 结构化结果
     * @throws Exception 执行失败
     */
    protected abstract Map<String, Object> run(Map<String, Object> args) throws Exception;

    // ------------------------------------------------------------ 入参读取

    protected static String str(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value == null ? null : String.valueOf(value).trim();
    }

    protected static String strOr(Map<String, Object> args, String key, String fallback) {
        String value = str(args, key);
        return value == null || value.isEmpty() ? fallback : value;
    }

    protected static Long num(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    protected static int intOr(Map<String, Object> args, String key, int fallback) {
        Long value = num(args, key);
        return value == null ? fallback : value.intValue();
    }

    protected static boolean boolOr(Map<String, Object> args, String key, boolean fallback) {
        Object value = args.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(value).trim());
    }

    @SuppressWarnings("unchecked")
    protected static List<String> strList(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
            return result;
        }
        return List.of(String.valueOf(value));
    }

    // ------------------------------------------------------------ 结果构造

    /**
     * 一行数据（保持插入顺序，模型与前端看到的字段顺序一致）
     *
     * @param kv 交替的 key / value
     * @return 行
     */
    protected static Map<String, Object> row(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }

    /**
     * 结果信封：总数 + 明细 + 口径说明
     *
     * @param total 命中总数
     * @param rows  明细
     * @param note  取数口径 / 截断说明
     * @return 结果
     */
    protected static Map<String, Object> result(long total, List<Map<String, Object>> rows, String note) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total);
        data.put("returned", rows.size());
        data.put("rows", rows);
        if (note != null && !note.isBlank()) {
            data.put("note", note);
        }
        return data;
    }

    /**
     * 截断到上限并在结果中显式说明（避免模型把"只看到 20 条"当成"一共 20 条"）
     *
     * @param rows  全量明细
     * @param limit 上限
     * @return 截断后的明细
     */
    protected static List<Map<String, Object>> cap(List<Map<String, Object>> rows, int limit) {
        if (limit <= 0 || rows.size() <= limit) {
            return rows;
        }
        return new ArrayList<>(rows.subList(0, limit));
    }

}
