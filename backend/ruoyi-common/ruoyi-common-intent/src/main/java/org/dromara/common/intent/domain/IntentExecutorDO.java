package org.dromara.common.intent.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 执行器档案对象 intent_executor
 *
 * <p>声明式执行器（agent / skill / flow）：把"用哪个模型、能用哪些工具、几步做完"
 * 变成后台可维护的数据，改完即时热更新到运行时，不用发版。</p>
 *
 * @author RuoYi-Vue-Plus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("intent_executor")
public class IntentExecutorDO extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 执行器标识
     */
    private String executorId;

    /**
     * 类型：agent / skill
     */
    private String type;

    /**
     * 名称
     */
    private String name;

    /**
     * 来源：builtin（classpath 种子）/ custom（后台创建）
     */
    private String source;

    /**
     * 档案原文（YAML）
     */
    private String profileYaml;

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
