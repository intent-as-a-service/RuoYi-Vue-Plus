package org.dromara.common.intent.bridge;

import dev.intent.sdk.host.IntentPermissionPolicy;
import dev.intent.sdk.host.IntentPrincipal;
import org.dromara.common.core.constant.SystemConstants;

import java.util.List;

/**
 * Sa-Token 意图可见性策略。
 *
 * <p>判定口径（与宿主后台"意图管理"页的角色配置同源）：</p>
 * <ol>
 *   <li>未声明角色或含 {@code *} → 不限制，放行；</li>
 *   <li>匿名（未登录）→ 拒绝；</li>
 *   <li>超级管理员 → 放行（与框架其他权限点一致，超管绕过校验）；</li>
 *   <li>其余按角色/权限串交集判定。</li>
 * </ol>
 *
 * @author RuoYi-Vue-Plus
 */
public class SaTokenIntentPermissionPolicy implements IntentPermissionPolicy {

    @Override
    public boolean canUse(IntentPrincipal principal, List<String> requiredRoles) {
        if (requiredRoles == null || requiredRoles.isEmpty() || requiredRoles.contains("*")) {
            return true;
        }
        if (principal == null || principal.isAnonymous()) {
            return false;
        }
        if (principal.hasRole(SystemConstants.SUPER_ADMIN_ROLE_KEY)) {
            return true;
        }
        return principal.hasAnyRole(requiredRoles);
    }

}
