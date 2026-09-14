package org.dromara.common.intent.mapper;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.intent.domain.IntentExecutorDO;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 执行器档案 Mapper
 *
 * @author RuoYi-Vue-Plus
 */
public interface IntentExecutorMapper extends BaseMapperPlus<IntentExecutorDO, IntentExecutorDO> {

    /**
     * 按执行器标识查询（逻辑删除行自动过滤）
     *
     * @param executorId 执行器标识
     * @return 执行器档案
     */
    default IntentExecutorDO selectByExecutorId(String executorId) {
        return selectOne(Wrappers.<IntentExecutorDO>lambdaQuery()
                .eq(IntentExecutorDO::getExecutorId, executorId));
    }

    /**
     * 统计执行器标识是否存在（<b>含删除态</b>，口径同意图规范）
     *
     * @param executorId 执行器标识
     * @return 记录数
     */
    @Select("select count(1) from intent_executor where executor_id = #{executorId}")
    long countByExecutorIdIncludeDeleted(@Param("executorId") String executorId);

}
