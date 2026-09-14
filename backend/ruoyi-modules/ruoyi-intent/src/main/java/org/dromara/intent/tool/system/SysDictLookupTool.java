package org.dromara.intent.tool.system;

import dev.intent.sdk.tool.Schemas;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.intent.tool.BaseHostTool;
import org.dromara.system.domain.vo.SysDictDataVo;
import org.dromara.system.domain.vo.SysDictTypeVo;
import org.dromara.system.service.ISysDictTypeService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 宿主工具：数据字典查询。
 *
 * <p>回答"这个下拉框的取值有哪些 / 这个码值是什么意思"，
 * 并顺带给出体检所需的事实（空字典、重复项、无备注）。</p>
 *
 * @author RuoYi-Vue-Plus
 */
@Component
@RequiredArgsConstructor
public class SysDictLookupTool extends BaseHostTool {

    private static final int MAX_TYPES = 60;

    private final ISysDictTypeService dictTypeService;

    @Override
    public String name() {
        return "sys_dict_lookup";
    }

    @Override
    public String description() {
        return "查询数据字典：字典类型清单与指定类型的字典项（标签/值/排序/是否默认）。"
                + "用于解释码值含义、检查字典规范（空项、缺备注、命名不合规）。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("dictType", Schemas.string("字典类型编码（如 sys_normal_disable）；留空 = 只返回类型清单"));
        properties.put("keyword", Schemas.string("在类型名称/编码中模糊匹配的关键字"));
        properties.put("limit", Schemas.number("类型清单条数上限，默认 40，最大 60"));
        return Schemas.object(properties, List.of());
    }

    @Override
    protected Map<String, Object> run(Map<String, Object> args) throws Exception {
        String dictType = str(args, "dictType");
        String keyword = str(args, "keyword");
        int limit = Math.min(Math.max(intOr(args, "limit", 40), 1), MAX_TYPES);

        if (StringUtils.isNotBlank(dictType)) {
            SysDictTypeVo type = dictTypeService.selectDictTypeByType(dictType);
            if (type == null) {
                return result(0, List.of(), "字典类型不存在: " + dictType);
            }
            List<SysDictDataVo> items = dictTypeService.selectDictDataByType(dictType);
            List<Map<String, Object>> rows = new ArrayList<>();
            for (SysDictDataVo item : items) {
                rows.add(row("label", item.getDictLabel(),
                        "value", item.getDictValue(),
                        "sort", item.getDictSort(),
                        "isDefault", "Y".equals(item.getIsDefault()),
                        "listClass", item.getListClass(),
                        "remark", item.getRemark()));
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("dictType", type.getDictType());
            data.put("dictName", type.getDictName());
            data.put("itemCount", rows.size());
            data.put("items", rows);
            data.put("remark", type.getRemark());
            return data;
        }

        List<SysDictTypeVo> types = dictTypeService.selectDictTypeAll();
        List<Map<String, Object>> rows = new ArrayList<>();
        long total = 0;
        for (SysDictTypeVo type : types) {
            if (StringUtils.isNotBlank(keyword)
                    && !contains(type.getDictType(), keyword) && !contains(type.getDictName(), keyword)) {
                continue;
            }
            total++;
            if (rows.size() >= limit) {
                continue;
            }
            int itemCount = dictTypeService.selectDictDataByType(type.getDictType()).size();
            rows.add(row("dictType", type.getDictType(),
                    "dictName", type.getDictName(),
                    "itemCount", itemCount,
                    "empty", itemCount == 0,
                    "noRemark", StringUtils.isBlank(type.getRemark())));
        }
        String note = total > rows.size() ? "命中 " + total + " 个类型，仅返回前 " + rows.size() + " 个" : null;
        return result(total, rows, note);
    }

    private static boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword.toLowerCase());
    }

}
