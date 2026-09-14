package org.dromara.intent.tool.system;

import dev.intent.sdk.tool.Schemas;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.intent.tool.BaseHostTool;
import org.dromara.system.api.DeptService;
import org.dromara.system.api.domain.DeptDTO;
import org.dromara.system.domain.bo.SysUserBo;
import org.dromara.system.service.ISysUserService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 宿主工具：组织架构与人员规模。
 *
 * <p>把"部门层级 + 每部门人数 + 空岗部门"一次性取出，
 * 是组织分析类意图的事实底座。</p>
 *
 * @author RuoYi-Vue-Plus
 */
@Component
@RequiredArgsConstructor
public class SysDeptTreeTool extends BaseHostTool {

    private final DeptService deptService;

    private final ISysUserService userService;

    @Override
    public String name() {
        return "sys_dept_tree";
    }

    @Override
    public String description() {
        return "查询组织架构：部门层级关系与每个部门的在册人数（可选返回停用账号数）。"
                + "用于组织结构分析、空岗部门识别、人员分布统计。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("withDisabled", Schemas.bool("是否统计每个部门的停用账号数，默认 false"));
        properties.put("flat", Schemas.bool("是否返回平铺列表（默认 false = 返回树）"));
        return Schemas.object(properties, List.of());
    }

    @Override
    protected Map<String, Object> run(Map<String, Object> args) throws Exception {
        boolean withDisabled = boolOr(args, "withDisabled", false);
        boolean flat = boolOr(args, "flat", false);

        List<DeptDTO> depts = deptService.selectDeptsByList();
        Map<Long, List<DeptDTO>> childrenOf = new LinkedHashMap<>();
        for (DeptDTO dept : depts) {
            childrenOf.computeIfAbsent(dept.getParentId(), key -> new ArrayList<>()).add(dept);
        }
        childrenOf.values().forEach(list ->
                list.sort(Comparator.comparing(DeptDTO::getDeptName, Comparator.nullsLast(String::compareTo))));

        if (flat) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (DeptDTO dept : depts) {
                rows.add(node(dept, withDisabled, 0));
            }
            return result(depts.size(), rows, "平铺视图，parentId 可用于自行还原层级");
        }
        List<Map<String, Object>> tree = new ArrayList<>();
        for (DeptDTO root : childrenOf.getOrDefault(0L, List.of())) {
            tree.add(build(root, childrenOf, withDisabled));
        }
        return result(depts.size(), tree, "树形视图，children 为下级部门；memberCount 为在册人数");
    }

    private Map<String, Object> build(DeptDTO dept, Map<Long, List<DeptDTO>> childrenOf, boolean withDisabled) {
        Map<String, Object> node = node(dept, withDisabled, 1);
        List<Map<String, Object>> children = new ArrayList<>();
        for (DeptDTO child : childrenOf.getOrDefault(dept.getDeptId(), List.of())) {
            children.add(build(child, childrenOf, withDisabled));
        }
        node.put("children", children);
        return node;
    }

    private Map<String, Object> node(DeptDTO dept, boolean withDisabled, int level) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("deptId", dept.getDeptId());
        node.put("parentId", dept.getParentId());
        node.put("deptName", dept.getDeptName());
        node.put("level", level);
        node.put("memberCount", countUsers(dept.getDeptId(), null));
        if (withDisabled) {
            node.put("disabledCount", countUsers(dept.getDeptId(), "1"));
        }
        return node;
    }

    private long countUsers(Long deptId, String status) {
        SysUserBo query = new SysUserBo();
        query.setDeptId(deptId);
        if (status != null) {
            query.setStatus(status);
        }
        PageResult<?> page = userService.selectPageUserList(query, new PageQuery(1, 1));
        return page.getTotal();
    }

}
