package org.dromara.common.intent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 意图前端组件静态资源映射。
 *
 * <p>{@code intent-ui-sdk} 是<b>框架无关的原生 JS</b>（无 Vue/React 依赖），
 * 同一份文件同时是三种交付形态：</p>
 * <ol>
 *   <li>演示页 / 意图调试台：{@code <script src="/intent-ui/js/intent-ui-sdk.js">}；</li>
 *   <li>npm 包（构建式宿主）：{@code import { IntentUI } from 'intent-ui-sdk'}；</li>
 *   <li>老技术栈（JSP / jQuery / 服务端模板）：一个 {@code <div>} + 一段 mount 调用。</li>
 * </ol>
 *
 * <p>资源路径 {@code /intent-ui/**} 已在框架默认放行名单内（{@code /**&#47;*.js}、
 * {@code /**&#47;*.css}、{@code /**&#47;*.html}），无需登录即可访问。</p>
 *
 * @author RuoYi-Vue-Plus
 */
@Slf4j
@AutoConfiguration
@ConditionalOnProperty(prefix = "intent", name = "enabled", havingValue = "true", matchIfMissing = true)
@Configuration(proxyBeanMethods = false)
public class IntentWebConfiguration implements WebMvcConfigurer {

    /**
     * 意图前端组件资源根路径
     */
    public static final String UI_PATH = "/intent-ui";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(UI_PATH + "/**")
                .addResourceLocations("classpath:/intent-ui/");
        log.info("[intent] 意图前端组件资源已挂载: {} -> classpath:/intent-ui/", UI_PATH);
    }

}
