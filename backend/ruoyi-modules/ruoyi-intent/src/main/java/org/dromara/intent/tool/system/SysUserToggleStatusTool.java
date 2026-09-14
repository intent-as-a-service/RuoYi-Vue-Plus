package org.dromara.intent.tool.system;

import cn.dev33.satoken.stp.StpUtil;
import dev.intent.sdk.tool.Schemas;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.constant.SystemConstants;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.intent.tool.BaseHostTool;
import org.dromara.system.domain.vo.SysUserVo;
import org.dromara.system.service.ISysUserService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 宿主工具：账号启停（<b>唯一的写操作工具</b>，也是"AI 出建议 + 人工确认 + 宿主落库"的示范）。
 *
 * <p>三重护栏，缺一不可：</p>
 * <ol>
 *   <li><b>显式确认</b>：{@code confirm} 必须为 true，否则只返回影响预览、不落库；</li>
 *   <li><b>二次鉴权</b>：工具内直接 {@code StpUtil.checkPermission("system:user:edit")} ——
 *       即使模型被提示词注入诱导，也越不过宿主权限（进程内直调 = 权限随调用栈）；</li>
 *   <li><b>宿主既有校验</b>：复用 {@code checkUserAllowed}（禁止操作超管）与
 *       {@code checkUserDataScope}（数据权限），与后台按钮走同一条路。</li>
 * </ol>
 *
 * @author RuoYi-Vue-Plus
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SysUserToggleStatusTool extends BaseHostTool {

    /**
     * 工具要求的宿主权限串（与后台「用户停用/启用」按钮一致）
     */
    public static final String REQUIRED_PERMISSION = "system:user:edit";

    private final ISysUserService userService;

    @Override
    public String name() {
        return "sys_user_toggle_status";
    }

    @Override
    public String description() {
        return "启用或停用某个账号（写操作）。必须先传 confirm=true 才会真正落库，否则只返回影响预览。"
                + "调用者需具备 system:user:edit 权限，且不能操作超级管理员。"
                + "只应在用户明确要求「停用/启用这个账号」时调用，不要在分析类任务里自行调用。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("userId", Schemas.string("目标用户编号"));
        properties.put("status", Schemas.enumeration(List.of("0", "1"), "目标状态：0=正常（启用），1=停用"));
        properties.put("reason", Schemas.string("变更原因（会写入结果，便于事后审计）"));
        properties.put("confirm", Schemas.bool("是否确认执行；false 或缺省 = 只预览不落库"));
        return Schemas.object(properties, List.of("userId", "status"));
    }

    @Override
    protected Map<String, Object> run(Map<String, Object> args) throws Exception {
        Long userId = num(args, "userId");
        String status = str(args, "status");
        String reason = strOr(args, "reason", "（未填写原因）");
        boolean confirm = boolOr(args, "confirm", false);
        if (userId == null) {
            throw new ServiceException("缺少 userId，无法确定要操作哪个账号");
        }
        if (!"0".equals(status) && !"1".equals(status)) {
            throw new ServiceException("status 只能是 0（启用）或 1（停用）");
        }

        // ② 二次鉴权：模型无法绕过宿主权限
        StpUtil.checkPermission(REQUIRED_PERMISSION);
        // ③ 宿主既有护栏：禁止操作超管 + 数据权限校验
        userService.checkUserAllowed(userId);
        userService.checkUserDataScope(userId);

        SysUserVo user = userService.selectUserById(userId);
        if (user == null) {
            throw new ServiceException("用户不存在: " + userId);
        }
        String current = user.getStatus();
        boolean noChange = status.equals(current);
        String target = "0".equals(status) ? "正常（启用）" : "停用";

        if (!confirm) {
            Map<String, Object> preview = new LinkedHashMap<>();
            preview.put("dryRun", true);
            preview.put("userId", userId);
            preview.put("userName", user.getUserName());
            preview.put("nickName", user.getNickName());
            preview.put("currentStatus", "0".equals(current) ? "正常（启用）" : "停用");
            preview.put("targetStatus", target);
            preview.put("willChange", !noChange);
            preview.put("reason", reason);
            preview.put("nextStep", "请向用户复述以上变更并取得确认，用户同意后再以 confirm=true 重新调用本工具。");
            return preview;
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dryRun", false);
        data.put("userId", userId);
        data.put("userName", user.getUserName());
        data.put("nickName", user.getNickName());
        data.put("previousStatus", "0".equals(current) ? "正常（启用）" : "停用");
        data.put("targetStatus", target);
        data.put("reason", reason);
        if (noChange) {
            data.put("changed", false);
            data.put("message", "账号当前已是目标状态，未做任何修改");
            return data;
        }
        int rows = userService.updateUserStatus(userId, status);
        data.put("changed", rows > 0);
        data.put("operator", StpUtil.getLoginIdAsString());
        data.put("superAdminProtected", SystemConstants.SUPER_ADMIN_USER_ID.equals(userId));
        log.info("[intent] 账号状态变更: userId={} {} -> {} by {} 原因={}",
                userId, current, status, StpUtil.getLoginIdAsString(), reason);
        return data;
    }

}
