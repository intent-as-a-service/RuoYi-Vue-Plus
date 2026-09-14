package org.dromara.intent.tool.system;

import dev.intent.sdk.tool.Schemas;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.intent.tool.BaseHostTool;
import org.dromara.system.domain.vo.SysMenuVo;
import org.dromara.system.service.ISysMenuService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 宿主工具：菜单与权限检索。
 *
 * <p>回答两类问题："这个权限串是什么意思"（按 {@code system:user:*} 找菜单）
 * 和"某个功能在哪个菜单下"（按中文名找）。</p>
 *
 * @author RuoYi-Vue-Plus
 */
@Component
@RequiredArgsConstructor
public class SysMenuSearchTool extends BaseHostTool {

    private static final int MAX_ROWS = 80;

    private final ISysMenuService menuService;

    @Override
    public String name() {
        return "sys_menu_search";
    }

    @Override
    public String description() {
        return "按关键字检索菜单与权限：支持权限串（如 system:user:add）、菜单名称（如「用户管理」）、"
                + "路由地址（如 system/user）三种口径，返回菜单类型、层级、权限串与路由。"
                + "用于解释某个权限能做什么、某个按钮归哪个菜单管。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("keyword", Schemas.string("关键字：权限串片段、菜单名称或路由片段"));
        properties.put("menuType", Schemas.enumeration(List.of("M", "C", "F"),
                "菜单类型：M=目录，C=菜单，F=按钮权限；留空 = 全部"));
        properties.put("limit", Schemas.number("返回条数上限，默认 40，最大 80"));
        return Schemas.object(properties, List.of("keyword"));
    }

    @Override
    protected Map<String, Object> run(Map<String, Object> args) throws Exception {
        String keyword = strOr(args, "keyword", "");
        String menuType = str(args, "menuType");
        int limit = Math.min(Math.max(intOr(args, "limit", 40), 1), MAX_ROWS);

        List<SysMenuVo> all = menuService.selectMenuList(LoginHelper.getUserId());
        Map<Long, String> nameOf = new LinkedHashMap<>();
        for (SysMenuVo menu : all) {
            nameOf.put(menu.getMenuId(), menu.getMenuName());
        }
        List<Map<String, Object>> matched = new ArrayList<>();
        long total = 0;
        for (SysMenuVo menu : all) {
            if (menuType != null && !menuType.isEmpty() && !menuType.equals(menu.getMenuType())) {
                continue;
            }
            if (!hit(menu, keyword)) {
                continue;
            }
            total++;
            if (matched.size() >= limit) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("menuId", menu.getMenuId());
            row.put("menuName", menu.getMenuName());
            row.put("parentName", nameOf.get(menu.getParentId()));
            row.put("menuType", menuTypeText(menu.getMenuType()));
            row.put("perms", menu.getPerms());
            row.put("path", menu.getPath());
            row.put("component", menu.getComponent());
            row.put("visible", "0".equals(menu.getVisible()) ? "显示" : "隐藏");
            row.put("status", "0".equals(menu.getStatus()) ? "正常" : "停用");
            matched.add(row);
        }
        String note = total > matched.size()
                ? "命中 " + total + " 条，本次仅返回前 " + matched.size() + " 条" : null;
        return result(total, matched, note);
    }

    private static boolean hit(SysMenuVo menu, String keyword) {
        if (StringUtils.isBlank(keyword)) {
            return true;
        }
        String lower = keyword.toLowerCase();
        return contains(menu.getPerms(), lower)
                || contains(menu.getMenuName(), lower)
                || contains(menu.getPath(), lower)
                || contains(menu.getComponent(), lower);
    }

    private static boolean contains(String value, String lowerKeyword) {
        return value != null && value.toLowerCase().contains(lowerKeyword);
    }

    private static String menuTypeText(String menuType) {
        if (menuType == null) {
            return "未知";
        }
        return switch (menuType) {
            case "M" -> "目录";
            case "C" -> "菜单";
            case "F" -> "按钮权限";
            default -> menuType;
        };
    }

}
