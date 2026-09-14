package org.dromara.common.intent.bridge;

import dev.intent.sdk.host.IntentContextBridge;
import dev.intent.sdk.tool.IntentTool;
import dev.intent.sdk.tool.IntentToolContext;
import dev.intent.sdk.tool.IntentToolResult;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Sa-Token / Spring 上下文桥接器。
 *
 * <p><b>为什么需要它：</b>pi-agent 的推理循环通过 {@code CompletableFuture} 在独立线程执行工具调用，
 * 而宿主的登录态解析（Sa-Token）、数据权限、请求级信息都挂在当前线程的
 * {@link RequestContextHolder} 上。若不显式搬运，工具里的 {@code LoginHelper.getUserId()}
 * 会拿不到人——表现为"越权查到别人的数据"或"查不到任何数据"。</p>
 *
 * <p><b>实现要点：</b>在意图执行入口（HTTP 线程）捕获 {@link RequestAttributes}，
 * 在每个工具调用线程中恢复，用完还原。Sa-Token 的 Spring 上下文正是通过
 * {@code RequestContextHolder} 解析请求与 token 的，因此恢复请求属性即可让
 * {@code StpUtil} / {@code LoginHelper} 在全链路正常工作，token 会话仍走 Redis（{@code PlusSaTokenDao}），
 * 天然支持集群。</p>
 *
 * @author RuoYi-Vue-Plus
 */
public class SaTokenIntentContextBridge implements IntentContextBridge {

    private final RequestAttributes requestAttributes;

    public SaTokenIntentContextBridge() {
        this(RequestContextHolder.getRequestAttributes());
    }

    private SaTokenIntentContextBridge(RequestAttributes requestAttributes) {
        this.requestAttributes = requestAttributes;
    }

    @Override
    public UnaryOperator<IntentTool> toolDecorator() {
        if (requestAttributes == null) {
            // 非 Web 线程（定时任务 / 消息消费）触发意图：无请求上下文可搬，直接透传
            return UnaryOperator.identity();
        }
        RequestAttributes captured = requestAttributes;
        return delegate -> new IntentTool() {

            @Override
            public String name() {
                return delegate.name();
            }

            @Override
            public String description() {
                return delegate.description();
            }

            @Override
            public Map<String, Object> parameters() {
                return delegate.parameters();
            }

            @Override
            public IntentToolResult execute(String toolCallId, Map<String, Object> args,
                    IntentToolContext context) throws Exception {
                RequestAttributes previous = RequestContextHolder.getRequestAttributes();
                try {
                    RequestContextHolder.setRequestAttributes(captured);
                    return delegate.execute(toolCallId, args, context);
                } finally {
                    if (previous == null) {
                        RequestContextHolder.resetRequestAttributes();
                    } else {
                        RequestContextHolder.setRequestAttributes(previous);
                    }
                }
            }
        };
    }

}
