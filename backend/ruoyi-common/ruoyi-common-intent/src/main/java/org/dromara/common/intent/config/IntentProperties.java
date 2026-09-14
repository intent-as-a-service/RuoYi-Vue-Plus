package org.dromara.common.intent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 意图即服务（Intent as a Service）配置项，前缀 {@code intent.*}。
 *
 * <p>业务模块的接入只有两份声明，本类不含任何业务耦合：</p>
 * <ol>
 *   <li>把宿主能力包装成 {@code dev.intent.sdk.tool.IntentTool} Bean；</li>
 *   <li>在模块 {@code resources/intent/*.yaml} 声明意图规范。</li>
 * </ol>
 *
 * @author RuoYi-Vue-Plus
 */
@Data
@ConfigurationProperties(prefix = "intent")
public class IntentProperties {

    /**
     * 总开关；关闭后不装配任何意图相关 Bean（控制器随之失效，前端浮标显示"未启用"）。
     */
    private boolean enabled = true;

    /**
     * 系统名（目录响应中展示，用于多系统意图中心区分来源）。
     */
    private String systemName = "RuoYi-Vue-Plus";

    /**
     * 时区（IANA，如 Asia/Shanghai）；目录增强求值"还有几天到期"按此计算，不配 = 服务器时区。
     */
    private String timeZone;

    /**
     * 外部事实规则目录（运维热改）：classpath {@code intent-rules/*.yaml} 之外的追加来源，
     * 与内置规则同名同 id 时以外置为准。
     */
    private String rulesDir;

    private final Llm llm = new Llm();

    private final Trace trace = new Trace();

    private final Gateway gateway = new Gateway();

    private final Suggestions suggestions = new Suggestions();

    /**
     * 大模型接入配置。
     */
    @Data
    public static class Llm {

        /**
         * 预置供应商标识：deepseek | openai | anthropic | custom（OpenAI 兼容端点）。
         */
        private String provider = "deepseek";

        /**
         * API Key；为空时回退环境变量（DEEPSEEK_API_KEY / OPENAI_API_KEY / ANTHROPIC_API_KEY）。
         */
        private String apiKey;

        /**
         * custom 供应商时的端点地址（如 http://127.0.0.1:8000/v1）。
         */
        private String baseUrl;

        /**
         * 模型标识；为空时按供应商取默认模型。
         */
        private String modelId;

        /**
         * 上下文窗口（token）。
         */
        private long contextWindow = 131072;

        /**
         * 单次回复最大 token。
         */
        private int maxTokens = 8192;

        /**
         * 单次执行最大推理轮数。
         */
        private int maxTurns = 12;

        /**
         * 输出不合规时的整体重试次数（0 = 不重试）。
         */
        private int outputMaxRetries = 1;
    }

    /**
     * 执行留痕存储配置。
     */
    @Data
    public static class Trace {

        /**
         * JSONL 留痕目录；为空则使用内存存储（重启即失，仅适合演示）。
         */
        private String dir = "./data/intent-traces";
    }

    /**
     * 意图网关（可组装项）：不装配 = 本地闭环（remote/composite 意图自动降级为不可用）。
     */
    @Data
    public static class Gateway {

        private boolean enabled = false;

        /**
         * 网关地址，如 https://intent-gateway.internal/api。
         */
        private String baseUrl;

        private String appKey;

        private String appSecret;

        /**
         * 声明式配置的跨系统意图（网关未下发目录时用于展示"不可用态"）。
         */
        private Map<String, String> remoteIntents = new LinkedHashMap<>();
    }

    /**
     * 目录增强（动态徽标与动态待办）：宿主事实驱动的个性化入口，求值不经过大模型。
     */
    @Data
    public static class Suggestions {

        /**
         * 总开关：关闭后目录只返回静态意图，不做任何事实求值。
         */
        private boolean enabled = true;

        /**
         * 每条业务规则最多返回几条动态建议；各规则配额独立，互不挤占。
         */
        private int max = 5;

        /**
         * 增强结果缓存秒数（0 = 不缓存）；同一用户执行意图后立即失效。
         */
        private int cacheSeconds = 30;
    }

}
