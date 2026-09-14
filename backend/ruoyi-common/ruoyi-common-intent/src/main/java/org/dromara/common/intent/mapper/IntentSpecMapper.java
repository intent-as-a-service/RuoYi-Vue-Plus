package org.dromara.common.intent.mapper;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.intent.domain.IntentSpecDO;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 意图规范 Mapper
 *
 * @author RuoYi-Vue-Plus
 */
public interface IntentSpecMapper extends BaseMapperPlus<IntentSpecDO, IntentSpecDO> {

    /**
     * 按意图编号查询（逻辑删除行自动过滤）
     *
     * @param intentId 意图编号
     * @return 意图规范
     */
    default IntentSpecDO selectByIntentId(String intentId) {
        return selectOne(Wrappers.<IntentSpecDO>lambdaQuery()
                .eq(IntentSpecDO::getIntentId, intentId));
    }

    /**
     * 统计意图编号是否存在（<b>含删除态</b>）
     *
     * <p>播种与新增都要用它：已删除的意图不能因为重启被种子"复活"，
     * 也不能被重新创建成同编号（否则历史留痕会对不上号）。</p>
     *
     * @param intentId 意图编号
     * @return 记录数
     */
    @Select("select count(1) from intent_spec where intent_id = #{intentId}")
    long countByIntentIdIncludeDeleted(@Param("intentId") String intentId);

}
