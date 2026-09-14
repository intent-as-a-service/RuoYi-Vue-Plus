package org.dromara.common.intent.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 意图规范对象 intent_spec
 *
 * <p>意图定义的<b>单一事实来源</b>：启动时由各业务模块 {@code classpath*:intent/*.yaml}
 * 播种（source=builtin），之后以本表为准 —— 后台增删改即时生效，无需发版。</p>
 *
 * <p>本表刻意保留原始 YAML 文本（{@code spec_yaml}）而不是序列化后的 JSON：
 * 运营在后台看到的、编辑的就是开发写的那份规范，不存在两套表达的漂移。</p>
 *
 * @author RuoYi-Vue-Plus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("intent_spec")
public class IntentSpecDO extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 意图编号（系统.域.动作，如 system.user.risk-scan）
     */
    private String intentId;

    /**
     * 意图名称
     */
    private String name;

    /**
     * 意图描述
     */
    private String description;

    /**
     * 执行范围：LOCAL / REMOTE / COMPOSITE
     */
    private String scope;

    /**
     * 契约版本
     */
    private Integer version;

    /**
     * 执行器标识（空 = 内置 builtin-agent 推理循环）
     */
    private String executor;

    /**
     * 来源：builtin（classpath 种子）/ custom（后台创建）
     */
    private String source;

    /**
     * 意图规范原文（YAML）
     */
    private String specYaml;

    /**
     * 备注
     */
    private String remark;

    /**
     * 删除标志（0代表存在 1代表删除）
     */
    @TableLogic
    private Long delFlag;

}
