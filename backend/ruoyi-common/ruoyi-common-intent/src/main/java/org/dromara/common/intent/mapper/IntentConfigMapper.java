package org.dromara.common.intent.mapper;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.dromara.common.intent.domain.IntentConfigDO;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 意图运营配置 Mapper
 *
 * @author RuoYi-Vue-Plus
 */
public interface IntentConfigMapper extends BaseMapperPlus<IntentConfigDO, IntentConfigDO> {

    /**
     * 按意图编号查询运营配置（无配置返回 null = 使用 spec 声明）
     *
     * @param intentId 意图编号
     * @return 运营配置
     */
    default IntentConfigDO selectByIntentId(String intentId) {
        return selectOne(Wrappers.<IntentConfigDO>lambdaQuery()
                .eq(IntentConfigDO::getIntentId, intentId));
    }

    /**
     * 物理删除某意图的运营配置（删除意图时同步清理，避免残留）
     *
     * @param intentId 意图编号
     * @return 影响行数
     */
    default int deleteByIntentId(String intentId) {
        return delete(Wrappers.<IntentConfigDO>lambdaQuery()
                .eq(IntentConfigDO::getIntentId, intentId));
    }

}
