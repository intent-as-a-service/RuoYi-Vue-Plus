package org.dromara.common.intent.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
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
import org.dromara.common.web.core.BaseController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 意图管理（管理员端）：上架开关 + 可见角色 + 执行器引用。
 *
 * <p>运营在这里点一下，前端浮标立即生效 —— 不需要发版、不需要重启。</p>
 *
 * @author RuoYi-Vue-Plus
 */
@Tag(name = "意图管理")
@RestController
@RequiredArgsConstructor
@RequestMapping("/intent/config")
@ConditionalOnProperty(prefix = "intent", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IntentConfigController extends BaseController {

    private final IntentService intentService;

    /**
     * 意图配置列表（全部意图 + 运营配置合并视图）
     *
     * @return 配置列表
     */
    @Operation(summary = "意图配置列表（上架 / 角色 / 执行器）")
    @SaCheckPermission("intent:config:query")
    @GetMapping("/list")
    public R<List<Map<String, Object>>> list() {
        return R.ok(intentService.getConfigList());
    }

    /**
     * 执行器下拉选项（内置推理循环 + 档案执行器）
     *
     * @return 选项列表
     */
    @Operation(summary = "执行器下拉选项")
    @SaCheckPermission("intent:config:query")
    @GetMapping("/executor-options")
    public R<List<Map<String, Object>>> executorOptions() {
        return R.ok(intentService.getExecutorOptions());
    }

    /**
     * 更新意图配置
     *
     * @param reqVO 配置请求
     * @return 是否成功
     */
    @Operation(summary = "更新意图配置（上架 / 角色 / 执行器）")
    @SaCheckPermission("intent:config:update")
    @Log(title = "意图管理", businessType = BusinessType.UPDATE)
    @PutMapping("/update")
    public R<Boolean> update(@Valid @RequestBody ConfigReqVO reqVO) {
        intentService.updateConfig(reqVO.getIntentId(), reqVO.getEnabled(),
                reqVO.getRoles(), reqVO.getExecutor(), reqVO.getRemark());
        return R.ok(true);
    }

    /**
     * 更新请求 VO
     */
    @Data
    public static class ConfigReqVO {

        /**
         * 意图编号
         */
        @NotBlank(message = "意图编号不能为空")
        private String intentId;

        /**
         * 是否上架
         */
        private Boolean enabled;

        /**
         * 可见角色编码（空 = 不限制）
         */
        private List<String> roles;

        /**
         * 执行器标识（空 / builtin-agent = 回到缺省内置推理循环）
         */
        private String executor;

        /**
         * 备注
         */
        private String remark;
    }

}
