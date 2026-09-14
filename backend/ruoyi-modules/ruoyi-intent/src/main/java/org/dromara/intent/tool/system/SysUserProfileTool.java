package org.dromara.intent.tool.system;

import dev.intent.sdk.tool.Schemas;
import lombok.RequiredArgsConstructor;
import org.dromara.intent.tool.BaseHostTool;
import org.dromara.system.domain.vo.SysRoleVo;
import org.dromara.system.domain.vo.SysUserVo;
import org.dromara.system.service.ISysUserService;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 宿主工具：用户全景画像（单个账号的完整事实）。
 *
 * @author RuoYi-Vue-Plus
 */
@Component
@RequiredArgsConstructor
public class SysUserProfileTool extends BaseHostTool {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ISysUserService userService;

    @Override
    public String name() {
        return "sys_user_profile";
    }

    @Override
    public String description() {
        return "查询单个账号的完整画像：基础信息、所属部门、角色与岗位、状态、最后登录时间与 IP。"
                + "用于「这个人是谁 / 有什么权限 / 最近有没有登录」。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("userId", Schemas.string("用户编号"));
        properties.put("userName", Schemas.string("登录账号（与 userId 二选一）"));
        return Schemas.object(properties, List.of());
    }

    @Override
    protected Map<String, Object> run(Map<String, Object> args) throws Exception {
        Long userId = num(args, "userId");
        String userName = str(args, "userName");
        SysUserVo user = userId != null
                ? userService.selectUserById(userId)
                : (userName == null ? null : userService.selectUserByUserName(userName));
        if (user == null) {
            return result(0, List.of(), "未找到匹配的账号，请确认编号或账号名是否正确");
        }
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("userId", user.getUserId());
        profile.put("userName", user.getUserName());
        profile.put("nickName", user.getNickName());
        profile.put("deptName", user.getDeptName());
        profile.put("status", "0".equals(user.getStatus()) ? "正常" : "停用");
        profile.put("phoneNumber", user.getPhoneNumber());
        profile.put("email", user.getEmail());
        profile.put("userType", user.getUserType());
        profile.put("createTime", user.getCreateTime() == null ? null : user.getCreateTime().format(TIME));
        profile.put("loginDate", user.getLoginDate() == null ? null : user.getLoginDate().format(TIME));
        profile.put("loginIp", user.getLoginIp());
        profile.put("roleGroup", userService.selectUserRoleGroup(user.getUserId()));
        profile.put("postGroup", userService.selectUserPostGroup(user.getUserId()));
        List<String> roleKeys = new ArrayList<>();
        if (user.getRoles() != null) {
            for (SysRoleVo role : user.getRoles()) {
                roleKeys.add(role.getRoleKey() + "(" + role.getRoleName() + ")");
            }
        }
        profile.put("roles", roleKeys);
        profile.put("remark", user.getRemark());
        return result(1, List.of(profile), null);
    }

}
