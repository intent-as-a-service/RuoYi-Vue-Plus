package org.dromara.common.intent.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 意图运营配置对象 intent_config
 *
 * <p>与 {@code intent_spec} 分开是因为二者<b>变更频率与责任人不同</b>：
 * spec 是开发写的契约（随代码走），config 是运营在后台点的开关（上架 / 可见角色 / 执行器）。</p>
 *
 * @author RuoYi-Vue-Plus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("intent_config")
public class IntentConfigDO extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 意图编号
     */
    private String intentId;

    /**
     * 是否上架（false = 目录与执行都拒绝）
     */
    private Boolean enabled;

    /**
     * 可见角色编码（JSON 数组文本；["*"] 或空 = 不限制）
     */
    private String roles;

    /**
     * 指定执行器标识（空 = 用 spec 声明或内置执行器）
     */
    private String executor;

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
