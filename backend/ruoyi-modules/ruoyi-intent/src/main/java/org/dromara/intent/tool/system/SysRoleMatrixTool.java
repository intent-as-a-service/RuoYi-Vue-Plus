package org.dromara.intent.tool.system;

import dev.intent.sdk.tool.Schemas;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.constant.SystemConstants;
import org.dromara.intent.tool.BaseHostTool;
import org.dromara.system.domain.vo.SysRoleVo;
import org.dromara.system.service.ISysMenuService;
import org.dromara.system.service.ISysRoleService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 宿主工具：角色权限矩阵。
 *
 * <p>把"角色 → 权限串集合 → 成员数"一次性取出来，是权限体检类意图的事实底座。
 * 口径与「系统管理 → 角色管理 → 分配菜单」一致。</p>
 *
 * @author RuoYi-Vue-Plus
 */
@Component
@RequiredArgsConstructor
public class SysRoleMatrixTool extends BaseHostTool {

    private final ISysRoleService roleService;

    private final ISysMenuService menuService;

    @Override
    public String name() {
        return "sys_role_matrix";
    }

    @Override
    public String description() {
        return "查询角色权限矩阵：每个角色的编码、名称、数据范围、状态、成员数量与权限串集合。"
                + "用于权限体检、越权面排查、僵尸角色识别。可用 roleKey 只看某个角色。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("roleKey", Schemas.string("角色编码（精确匹配，如 admin；留空 = 全部角色）"));
        properties.put("onlyEmpty", Schemas.bool("是否只看没有任何成员的角色，默认 false"));
        properties.put("withPerms", Schemas.bool("是否返回完整权限串集合，默认 false（只返回数量与前 30 条）"));
        return Schemas.object(properties, List.of());
    }

    @Override
    protected Map<String, Object> run(Map<String, Object> args) throws Exception {
        String roleKey = str(args, "roleKey");
        boolean onlyEmpty = boolOr(args, "onlyEmpty", false);
        boolean withPerms = boolOr(args, "withPerms", false);

        List<Map<String, Object>> rows = new ArrayList<>();
        List<SysRoleVo> roles = roleService.selectRoleAll();
        roles.sort(Comparator.comparing(SysRoleVo::getRoleSort, Comparator.nullsLast(Integer::compareTo)));
        long total = 0;
        for (SysRoleVo role : roles) {
            if (roleKey != null && !roleKey.isEmpty() && !roleKey.equals(role.getRoleKey())) {
                continue;
            }
            total++;
            long members = roleService.countUserRoleByRoleId(role.getRoleId());
            if (onlyEmpty && members > 0) {
                continue;
            }
            Set<String> perms = menuService.selectMenuPermsByRoleId(role.getRoleId());
            List<String> permList = new ArrayList<>(perms == null ? Set.<String>of() : perms);
            permList.sort(Comparator.naturalOrder());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("roleId", role.getRoleId());
            row.put("roleKey", role.getRoleKey());
            row.put("roleName", role.getRoleName());
            row.put("dataScope", dataScopeText(role.getDataScope()));
            row.put("status", "0".equals(role.getStatus()) ? "正常" : "停用");
            row.put("memberCount", members);
            row.put("permCount", permList.size());
            if (withPerms) {
                row.put("perms", permList);
            } else {
                row.put("permSample", permList.size() > 30 ? permList.subList(0, 30) : permList);
            }
            row.put("isSuperAdmin", SystemConstants.SUPER_ADMIN_ROLE_KEY.equals(role.getRoleKey()));
            rows.add(row);
        }
        String note = withPerms ? null : "默认只返回权限串样例（前 30 条），需要完整清单请把 withPerms 设为 true";
        return result(total, rows, note);
    }

    /**
     * 数据范围文案
     *
     * @param dataScope 编码
     * @return 文案
     */
    private static String dataScopeText(String dataScope) {
        if (dataScope == null) {
            return "未设置";
        }
        return switch (dataScope) {
            case "1" -> "全部数据";
            case "2" -> "自定义数据";
            case "3" -> "本部门数据";
            case "4" -> "本部门及以下数据";
            case "5" -> "仅本人数据";
            case "6" -> "部门及以下或本人数据";
            default -> "未知(" + dataScope + ")";
        };
    }

}
