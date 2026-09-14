package org.dromara.common.intent.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 意图资源扫描工具：统一按 {@code classpath*:} 收集各业务模块自带的声明文件。
 *
 * <p>约定（宿主侧约定，SDK 不感知）：</p>
 * <ul>
 *   <li>{@code classpath*:intent/*.yaml} —— 意图规范种子；</li>
 *   <li>{@code classpath*:intent-executor/*.yaml} —— 执行器档案种子；</li>
 *   <li>{@code classpath*:intent-rules/*.yaml} —— 声明式事实规则。</li>
 * </ul>
 *
 * <p>扫描结果按文件名排序，保证多模块合并时的行为可预测、可复现。</p>
 *
 * @author RuoYi-Vue-Plus
 */
@Slf4j
public final class IntentResources {

    private IntentResources() {
    }

    /**
     * 扫描 classpath 资源（扫描失败或命中为空均返回空列表，不阻断启动）。
     *
     * @param pattern 资源模式，如 {@code classpath*:intent/*.yaml}
     * @return 排序后的资源列表
     */
    public static List<Resource> scan(String pattern) {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(pattern);
            List<Resource> sorted = new ArrayList<>(List.of(resources));
            sorted.sort(Comparator.comparing(resource ->
                    resource.getFilename() == null ? "" : resource.getFilename()));
            return sorted;
        } catch (Exception e) {
            log.warn("[intent] classpath 扫描失败（可忽略）: {} - {}", pattern, e.getMessage());
            return List.of();
        }
    }

    /**
     * 读取资源文本（UTF-8）。
     *
     * @param resource 资源
     * @return 文本内容
     * @throws IOException 读取失败
     */
    public static String read(Resource resource) throws IOException {
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

}
