package org.dromara.common.intent.bridge;

import dev.intent.sdk.host.IntentPrincipal;
import dev.intent.sdk.host.IntentPrincipalProvider;
import org.dromara.common.core.constant.SystemConstants;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.system.api.model.LoginUser;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Sa-Token 身份提供者：把宿主登录态翻译成 SDK 需要的"你是谁"。
 *
 * <p>SDK 只保留 userId / userName / roles 四项，多一项都会加深耦合；
 * 这里刻意把<b>角色编码与权限串合并</b>放进 roles —— 意图规范的
 * {@code policy.roles} 因此既能写角色（{@code superadmin}），
 * 也能写权限串（{@code system:user:list}），两种口径都命中。</p>
 *
 * <p>无登录态必须返回 {@link IntentPrincipal#anonymous()} 而不是 null（SDK 契约）。</p>
 *
 * @author RuoYi-Vue-Plus
 */
public class SaTokenIntentPrincipalProvider implements IntentPrincipalProvider {

    @Override
    public IntentPrincipal current() {
        LoginUser loginUser = LoginHelper.getLoginUser();
        if (loginUser == null || loginUser.getUserId() == null) {
            // 退化路径：token session 尚未就绪时至少拿到 token extra 里的 userId
            Long userId = LoginHelper.getUserId();
            if (userId == null) {
                return IntentPrincipal.anonymous();
            }
            return IntentPrincipal.of(String.valueOf(userId), LoginHelper.getUsername(), null,
                    Set.of(SystemConstants.SUPER_ADMIN_ROLE_KEY.equals(LoginHelper.getUsername())
                            ? SystemConstants.SUPER_ADMIN_ROLE_KEY : ""));
        }
        return IntentPrincipal.of(String.valueOf(loginUser.getUserId()),
                loginUser.getNickname() == null ? loginUser.getUsername() : loginUser.getNickname(),
                null, rolesOf(loginUser));
    }

    /**
     * 角色编码 ∪ 权限串：让 {@code policy.roles} 支持角色与权限两种表达。
     *
     * @param loginUser 登录用户
     * @return 角色与权限串集合
     */
    public static Set<String> rolesOf(LoginUser loginUser) {
        Set<String> roles = new LinkedHashSet<>();
        if (loginUser.getRolePermission() != null) {
            roles.addAll(loginUser.getRolePermission());
        }
        if (loginUser.getMenuPermission() != null) {
            roles.addAll(loginUser.getMenuPermission());
        }
        roles.remove(null);
        roles.remove("");
        return roles;
    }

}
