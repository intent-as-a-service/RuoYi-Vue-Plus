package org.dromara.common.intent.controller;

import dev.intent.protocol.CatalogResponse;
import dev.intent.protocol.ExecutionTraceRecord;
import dev.intent.protocol.IntentFeedback;
import dev.intent.protocol.IntentRequest;
import dev.intent.protocol.IntentResult;
import dev.intent.protocol.UserInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.intent.service.IntentService;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.web.core.BaseController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 意图 API（用户端统一入口）。
 *
 * <p>认证与数据权限<b>完全沿用宿主</b>：前端组件与业务页面同源同鉴权，
 * 不建独立账号体系、不跨域、不额外授权改造。</p>
 *
 * <p>端点前缀 {@code /intent}，前端经 {@code /dev-api} 或 {@code /prod-api} 代理访问。</p>
 *
 * @author RuoYi-Vue-Plus
 */
@Tag(name = "意图中心")
@RestController
@RequiredArgsConstructor
@RequestMapping("/intent")
@ConditionalOnProperty(prefix = "intent", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IntentController extends BaseController {

    private final IntentService intentService;

    /**
     * 获得意图目录
     *
     * @param page 页面标识（如 system/user）；空 = 意图中心（全量）
     * @return 目录（按角色与页面过滤，含徽标与待办建议）
     */
    @Operation(summary = "获得意图目录（按角色与页面过滤，含网关装配状态）")
    @GetMapping("/catalog")
    public R<CatalogResponse> getCatalog(@RequestParam(value = "page", required = false) String page) {
        return R.ok(intentService.getCatalog(page));
    }

    /**
     * 执行意图
     *
     * @param reqVO 执行请求
     * @return 执行结果（缺参返回 NEED_INPUT 与缺失参数名，前端渲染补全表单）
     */
    @Operation(summary = "执行意图（缺参返回 NEED_INPUT 与参数 Schema，前端渲染补全表单）")
    @Log(title = "意图执行", businessType = BusinessType.OTHER, isSaveResponseData = false)
    @PostMapping("/execute")
    public R<IntentResult> execute(@Valid @RequestBody ExecuteReqVO reqVO) {
        return R.ok(intentService.execute(reqVO.toIntentRequest()));
    }

    /**
     * 当前用户的意图执行历史
     *
     * @param intentId 意图编号（可空 = 全部）
     * @param limit    条数
     * @return 执行记录
     */
    @Operation(summary = "当前用户的意图执行历史")
    @GetMapping("/history")
    public R<List<ExecutionTraceRecord>> getHistory(
            @RequestParam(value = "intentId", required = false) String intentId,
            @RequestParam(value = "limit", defaultValue = "20") Integer limit) {
        return R.ok(intentService.getHistory(intentId, limit == null ? 20 : limit));
    }

    /**
     * 执行留痕详情（步骤级转录，可回放）
     *
     * @param traceId 留痕编号
     * @return 执行记录
     */
    @Operation(summary = "执行留痕详情（步骤级转录，可回放）")
    @GetMapping("/trace/{traceId}")
    public R<ExecutionTraceRecord> getTrace(@PathVariable("traceId") String traceId) {
        return R.ok(intentService.getTrace(traceId));
    }

    /**
     * 结果评价反馈（回流评测库）
     *
     * @param feedback 反馈
     * @return 是否成功
     */
    @Operation(summary = "结果评价反馈（回流评测库）")
    @PostMapping("/feedback")
    public R<Boolean> feedback(@Valid @RequestBody IntentFeedback feedback) {
        intentService.saveFeedback(feedback);
        return R.ok(true);
    }

    /**
     * 平台状态
     *
     * @return 系统名 / 网关装配状态 / 意图与工具规模
     */
    @Operation(summary = "平台状态（系统名 / 网关装配状态 / 规模）")
    @GetMapping("/status")
    public R<Map<String, Object>> getStatus() {
        return R.ok(intentService.getStatus());
    }

    /**
     * 执行请求 VO
     *
     * <p>刻意只暴露 intentId / params / context 三项：用户身份由服务端从登录态取，
     * 不接受前端传入（否则等于把越权入口开在请求体里）。</p>
     */
    @Data
    public static class ExecuteReqVO {

        /**
         * 意图编号
         */
        @NotBlank(message = "意图编号不能为空")
        private String intentId;

        /**
         * 入参
         */
        private Map<String, Object> params;

        /**
         * 页面上下文（页面可见数据，用于自动补全必填参数）
         */
        private Map<String, Object> context;

        /**
         * 转换为 SDK 请求（身份取自 Sa-Token 登录态）
         *
         * @return 意图执行请求
         */
        public IntentRequest toIntentRequest() {
            Long userId = LoginHelper.getUserId();
            UserInfo user = new UserInfo(userId == null ? null : String.valueOf(userId),
                    LoginHelper.getUsername(), LoginHelper.getDeptName(), Map.of());
            return new IntentRequest(intentId, params, context, user, null);
        }
    }

}
