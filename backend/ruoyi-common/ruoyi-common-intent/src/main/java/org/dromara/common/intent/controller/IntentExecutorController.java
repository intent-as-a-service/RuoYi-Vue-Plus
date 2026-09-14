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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 执行器档案管理（管理员端）。
 *
 * <p>把"用哪个模型、能用哪些工具、几步做完"从代码变成数据：改完即时热更新到运行时，
 * 已经上线的意图立刻换执行策略，不用发版、不用重启。</p>
 *
 * @author RuoYi-Vue-Plus
 */
@Tag(name = "执行器档案")
@RestController
@RequiredArgsConstructor
@RequestMapping("/intent/executor")
@ConditionalOnProperty(prefix = "intent", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IntentExecutorController extends BaseController {

    private final IntentService intentService;

    /**
     * 执行器档案列表
     *
     * @return 档案列表
     */
    @Operation(summary = "执行器档案列表")
    @SaCheckPermission("intent:executor:query")
    @GetMapping("/list")
    public R<List<Map<String, Object>>> list() {
        return R.ok(intentService.getExecutorList());
    }

    /**
     * 宿主工具清单（勾选工具白名单）
     *
     * @return 工具清单
     */
    @Operation(summary = "宿主工具清单")
    @SaCheckPermission("intent:executor:query")
    @GetMapping("/tools")
    public R<List<Map<String, Object>>> tools() {
        return R.ok(intentService.getToolOptions());
    }

    /**
     * 执行器档案原文（编辑器回显）
     *
     * @param executorId 执行器标识
     * @return YAML 文本
     */
    @Operation(summary = "执行器档案原文（YAML）")
    @SaCheckPermission("intent:executor:query")
    @GetMapping("/yaml/{executorId}")
    public R<String> yaml(@PathVariable("executorId") String executorId) {
        return R.ok(intentService.getExecutorYaml(executorId));
    }

    /**
     * 校验执行器档案（不落库）
     *
     * @param reqVO 校验请求
     * @return 错误列表（空 = 通过）
     */
    @Operation(summary = "校验执行器档案（不落库）")
    @SaCheckPermission("intent:executor:query")
    @PostMapping("/validate")
    public R<List<String>> validate(@Valid @RequestBody YamlReqVO reqVO) {
        return R.ok(intentService.validateExecutorYaml(reqVO.getYaml()));
    }

    /**
     * 新增执行器档案（保存即热更新）
     *
     * @param reqVO 新增请求
     * @return 执行器标识
     */
    @Operation(summary = "新增执行器档案（保存即热更新）")
    @SaCheckPermission("intent:executor:add")
    @Log(title = "执行器档案", businessType = BusinessType.INSERT)
    @PostMapping
    public R<String> create(@Valid @RequestBody YamlReqVO reqVO) {
        return R.ok(intentService.createExecutor(reqVO.getYaml()));
    }

    /**
     * 修改执行器档案（保存即热更新）
     *
     * @param executorId 执行器标识
     * @param reqVO      修改请求
     * @return 执行器标识
     */
    @Operation(summary = "修改执行器档案（保存即热更新）")
    @SaCheckPermission("intent:executor:edit")
    @Log(title = "执行器档案", businessType = BusinessType.UPDATE)
    @PutMapping("/{executorId}")
    public R<String> update(@PathVariable("executorId") String executorId,
            @Valid @RequestBody YamlReqVO reqVO) {
        return R.ok(intentService.updateExecutor(executorId, reqVO.getYaml()));
    }

    /**
     * 删除执行器档案（保存即热更新）
     *
     * @param executorId 执行器标识
     * @return 是否成功
     */
    @Operation(summary = "删除执行器档案（保存即热更新）")
    @SaCheckPermission("intent:executor:remove")
    @Log(title = "执行器档案", businessType = BusinessType.DELETE)
    @DeleteMapping("/{executorId}")
    public R<Boolean> delete(@PathVariable("executorId") String executorId) {
        intentService.deleteExecutor(executorId);
        return R.ok(true);
    }

    /**
     * YAML 请求 VO
     */
    @Data
    public static class YamlReqVO {

        /**
         * 执行器档案 YAML 文本
         */
        @NotBlank(message = "执行器档案内容不能为空")
        private String yaml;
    }

}
