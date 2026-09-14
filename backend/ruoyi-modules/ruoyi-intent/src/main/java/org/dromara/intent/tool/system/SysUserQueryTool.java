package org.dromara.intent.tool.system;

import dev.intent.sdk.tool.Schemas;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.intent.tool.BaseHostTool;
import org.dromara.system.domain.bo.SysUserBo;
import org.dromara.system.domain.vo.SysUserVo;
import org.dromara.system.service.ISysUserService;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 宿主工具：用户查询。
 *
 * <p>账号盘点、风险排查、找人等意图的公共取数入口；口径与「系统管理 → 用户管理」
 * 列表一致（同一 Service、同一数据权限）。</p>
 *
 * @author RuoYi-Vue-Plus
 */
@Component
@RequiredArgsConstructor
public class SysUserQueryTool extends BaseHostTool {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final int MAX_ROWS = 50;

    private final ISysUserService userService;

    @Override
    public String name() {
        return "sys_user_query";
    }

    @Override
    public String description() {
        return "按用户名/昵称/手机号/状态/部门查询系统用户清单，返回账号、昵称、所属部门、状态、"
                + "最后登录时间与 IP。用于账号盘点、风险排查、查找具体人员。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("userName", Schemas.string("登录账号，支持模糊匹配"));
        properties.put("nickName", Schemas.string("用户昵称，支持模糊匹配"));
        properties.put("phoneNumber", Schemas.string("手机号，支持模糊匹配"));
        properties.put("deptId", Schemas.string("部门编号（精确匹配）"));
        properties.put("status", Schemas.enumeration(List.of("0", "1"), "账号状态：0=正常，1=停用"));
        properties.put("limit", Schemas.number("返回条数上限，默认 20，最大 50"));
        return Schemas.object(properties, List.of());
    }

    @Override
    protected Map<String, Object> run(Map<String, Object> args) throws Exception {
        SysUserBo query = new SysUserBo();
        query.setUserName(str(args, "userName"));
        query.setNickName(str(args, "nickName"));
        query.setPhoneNumber(str(args, "phoneNumber"));
        query.setStatus(str(args, "status"));
        Long deptId = num(args, "deptId");
        if (deptId != null) {
            query.setDeptId(deptId);
        }
        int limit = Math.min(Math.max(intOr(args, "limit", 20), 1), MAX_ROWS);
        PageResult<SysUserVo> page = userService.selectPageUserList(query, new PageQuery(limit, 1));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (SysUserVo user : page.getRows()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", user.getUserId());
            row.put("userName", user.getUserName());
            row.put("nickName", user.getNickName());
            row.put("deptName", user.getDeptName());
            row.put("status", "0".equals(user.getStatus()) ? "正常" : "停用");
            row.put("phoneNumber", user.getPhoneNumber());
            row.put("email", user.getEmail());
            row.put("createTime", user.getCreateTime() == null ? null : user.getCreateTime().format(TIME));
            row.put("loginDate", user.getLoginDate() == null ? null : user.getLoginDate().format(TIME));
            row.put("loginIp", user.getLoginIp());
            rows.add(row);
        }
        String note = page.getTotal() > rows.size()
                ? "命中 " + page.getTotal() + " 条，本次仅返回前 " + rows.size() + " 条（可用更精确的条件缩小范围）"
                : null;
        return result(page.getTotal(), rows, note);
    }

    /**
     * 供其它工具复用的精简视图
     *
     * @param user 用户
     * @return 行数据
     */
    static Map<String, Object> brief(SysUserVo user) {
        return row("userId", user.getUserId(),
                "userName", user.getUserName(),
                "nickName", user.getNickName(),
                "deptName", user.getDeptName(),
                "status", "0".equals(user.getStatus()) ? "正常" : "停用",
                "loginDate", user.getLoginDate() == null ? null : user.getLoginDate().format(TIME));
    }

    /**
     * 判断字符串是否为空（供兄弟工具复用）
     *
     * @param value 值
     * @return 是否为空
     */
    static boolean blank(String value) {
        return StringUtils.isBlank(value);
    }

}
