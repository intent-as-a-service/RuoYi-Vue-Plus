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
 * 意图规范管理（管理员端）：YAML 就地编辑、校验、增删改。
 *
 * <p>规范即契约：保存前强校验（五要素齐备、执行器与宿主工具都必须已注册），
 * 不合规的意图进不了库，也上不了线。</p>
 *
 * @author RuoYi-Vue-Plus
 */
@Tag(name = "意图规范管理")
@RestController
@RequiredArgsConstructor
@RequestMapping("/intent/spec")
@ConditionalOnProperty(prefix = "intent", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IntentSpecController extends BaseController {

    private final IntentService intentService;

    /**
     * 意图规范列表
     *
     * @return 规范列表
     */
    @Operation(summary = "意图规范列表")
    @SaCheckPermission("intent:spec:query")
    @GetMapping("/list")
    public R<List<Map<String, Object>>> list() {
        return R.ok(intentService.getSpecList());
    }

    /**
     * 意图规范原文（编辑器回显）
     *
     * @param intentId 意图编号
     * @return YAML 文本
     */
    @Operation(summary = "意图规范原文（YAML）")
    @SaCheckPermission("intent:spec:query")
    @GetMapping("/yaml/{intentId}")
    public R<String> yaml(@PathVariable("intentId") String intentId) {
        return R.ok(intentService.getSpecYaml(intentId));
    }

    /**
     * 校验意图规范（不落库）
     *
     * @param reqVO 校验请求
     * @return 错误列表（空 = 通过）
     */
    @Operation(summary = "校验意图规范（不落库）")
    @SaCheckPermission("intent:spec:query")
    @PostMapping("/validate")
    public R<List<String>> validate(@Valid @RequestBody YamlReqVO reqVO) {
        return R.ok(intentService.validateSpecYaml(reqVO.getYaml()));
    }

    /**
     * 新增意图规范
     *
     * @param reqVO 新增请求
     * @return 意图编号
     */
    @Operation(summary = "新增意图规范")
    @SaCheckPermission("intent:spec:add")
    @Log(title = "意图规范", businessType = BusinessType.INSERT)
    @PostMapping
    public R<String> create(@Valid @RequestBody YamlReqVO reqVO) {
        return R.ok(intentService.createSpec(reqVO.getYaml()));
    }

    /**
     * 修改意图规范
     *
     * @param intentId 意图编号
     * @param reqVO    修改请求
     * @return 意图编号
     */
    @Operation(summary = "修改意图规范")
    @SaCheckPermission("intent:spec:edit")
    @Log(title = "意图规范", businessType = BusinessType.UPDATE)
    @PutMapping("/{intentId}")
    public R<String> update(@PathVariable("intentId") String intentId,
            @Valid @RequestBody YamlReqVO reqVO) {
        return R.ok(intentService.updateSpec(intentId, reqVO.getYaml()));
    }

    /**
     * 删除意图规范
     *
     * @param intentId 意图编号
     * @return 是否成功
     */
    @Operation(summary = "删除意图规范")
    @SaCheckPermission("intent:spec:remove")
    @Log(title = "意图规范", businessType = BusinessType.DELETE)
    @DeleteMapping("/{intentId}")
    public R<Boolean> delete(@PathVariable("intentId") String intentId) {
        intentService.deleteSpec(intentId);
        return R.ok(true);
    }

    /**
     * YAML 请求 VO
     */
    @Data
    public static class YamlReqVO {

        /**
         * 意图规范 YAML 文本
         */
        @NotBlank(message = "意图规范内容不能为空")
        private String yaml;
    }

}
