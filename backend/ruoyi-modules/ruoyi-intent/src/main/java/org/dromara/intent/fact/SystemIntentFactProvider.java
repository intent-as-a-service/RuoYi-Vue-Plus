package org.dromara.intent.fact;

import dev.intent.sdk.catalog.IntentCatalogContext;
import dev.intent.sdk.host.rule.DatasetSpec;
import dev.intent.sdk.host.rule.IntentFactProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.system.domain.bo.SysLoginInfoBo;
import org.dromara.system.domain.bo.SysUserBo;
import org.dromara.system.domain.vo.SysLoginInfoVo;
import org.dromara.system.domain.vo.SysRoleVo;
import org.dromara.system.domain.vo.SysUserVo;
import org.dromara.system.service.ISysLoginInfoService;
import org.dromara.system.service.ISysRoleService;
import org.dromara.system.service.ISysUserService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统域事实供给：为声明式事实规则提供数据集（<b>只取数，不做判断</b>）。
 *
 * <p>职责边界很清楚：这里只负责"把事实捞出来"，</p>
 * <ul>
 *   <li><b>条件</b>（几天算久未登录、几个算多）写在 {@code resources/intent-rules/*.yaml}；</li>
 *   <li><b>文案</b>（"账号「张三」已 91 天未登录"）也写在规则 YAML 里。</li>
 * </ul>
 * <p>于是运营改阈值、改文案、改挂载页面只动 YAML，不用改 Java、不用发版。</p>
 *
 * <p>取数口径与「系统管理 → 用户管理」一致（同一个 Service、同一套数据权限）；
 * 全部为<b>轻量有界查询</b>（最多扫一定条数），异常被 SDK 隔离，不会拖垮目录接口。</p>
 *
 * @author RuoYi-Vue-Plus
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemIntentFactProvider implements IntentFactProvider {

    /**
     * 数据集：久未登录的在册账号
     */
    public static final String DS_INACTIVE_USERS = "system.inactive-users";

    /**
     * 数据集：无成员的角色
     */
    public static final String DS_EMPTY_ROLES = "system.empty-roles";

    /**
     * 数据集：近 24 小时登录失败账号聚合
     */
    public static final String DS_LOGIN_FAILURES = "system.login-failures";

    /**
     * 账号扫描上限（口径有界，避免大库拖慢目录接口）
     */
    private static final int USER_SCAN = 500;

    /**
     * 登录日志扫描上限（只回溯最新若干条）
     */
    private static final int LOGIN_LOG_SCAN = 200;

    /**
     * 登录失败统计时间窗（小时）
     */
    private static final int FAILURE_WINDOW_HOURS = 24;

    private final ISysUserService userService;

    private final ISysRoleService roleService;

    private final ISysLoginInfoService loginInfoService;

    @Override
    public List<DatasetSpec> datasets() {
        List<DatasetSpec> specs = new ArrayList<>();
        Map<String, String> userFields = new LinkedHashMap<>();
        userFields.put("id", "用户编号");
        userFields.put("userName", "登录账号");
        userFields.put("nickName", "昵称");
        userFields.put("deptName", "所属部门");
        userFields.put("status", "状态（正常/停用）");
        userFields.put("idleDays", "距上次登录天数（从未登录为 -1）");
        userFields.put("lastLogin", "上次登录时间");
        specs.add(DatasetSpec.of(DS_INACTIVE_USERS,
                "在册账号的登录活跃事实（按上次登录时间倒排），用于识别久未登录、疑似离职未回收的账号",
                userFields));

        Map<String, String> roleFields = new LinkedHashMap<>();
        roleFields.put("id", "角色编号");
        roleFields.put("roleKey", "角色编码");
        roleFields.put("roleName", "角色名称");
        roleFields.put("status", "状态（正常/停用）");
        roleFields.put("memberCount", "成员数量");
        specs.add(DatasetSpec.of(DS_EMPTY_ROLES,
                "角色及其成员数量，用于识别没有任何成员的僵尸角色",
                roleFields));

        Map<String, String> loginFields = new LinkedHashMap<>();
        loginFields.put("id", "账号（聚合主键）");
        loginFields.put("userName", "登录账号");
        loginFields.put("failCount", "近 24 小时失败次数");
        loginFields.put("lastIp", "最近失败来源 IP");
        loginFields.put("lastTime", "最近一次失败时间");
        specs.add(DatasetSpec.of(DS_LOGIN_FAILURES,
                "近 24 小时登录失败按账号聚合，失败次数从多到少排序，用于识别暴力破解与口令遗忘",
                loginFields));
        return specs;
    }

    @Override
    public boolean supports(String datasetId) {
        return DS_INACTIVE_USERS.equals(datasetId)
                || DS_EMPTY_ROLES.equals(datasetId)
                || DS_LOGIN_FAILURES.equals(datasetId);
    }

    @Override
    public List<Map<String, Object>> rows(String datasetId, IntentCatalogContext context, int limit) {
        int cap = limit <= 0 ? 20 : limit;
        return switch (datasetId) {
            case DS_INACTIVE_USERS -> inactiveUsers(context, cap);
            case DS_EMPTY_ROLES -> emptyRoles(cap);
            case DS_LOGIN_FAILURES -> loginFailures(cap);
            default -> List.of();
        };
    }

    @Override
    public int count(String datasetId, IntentCatalogContext context) {
        return switch (datasetId) {
            case DS_INACTIVE_USERS -> (int) countInactive(context);
            case DS_EMPTY_ROLES -> countEmptyRoles();
            case DS_LOGIN_FAILURES -> countLoginFailures();
            default -> 0;
        };
    }

    // ------------------------------------------------------------ 数据集实现

    /**
     * 在册账号的登录活跃事实：按上次登录时间从早到晚排序。
     */
    private List<Map<String, Object>> inactiveUsers(IntentCatalogContext context, int limit) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SysUserVo user : activeUsers(context)) {
            if (!"0".equals(user.getStatus())) {
                // 已停用的账号不用再提醒"该回收了"
                continue;
            }
            long idleDays = idleDays(user, context);
            rows.add(rowOf(user, idleDays));
        }
        rows.sort(Comparator.comparingLong(row -> (Long) row.get("sortIdleDays")));
        List<Map<String, Object>> ordered = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (ordered.size() >= limit) {
                break;
            }
            row.remove("sortIdleDays");
            ordered.add(row);
        }
        return ordered;
    }

    private long countInactive(IntentCatalogContext context) {
        long count = 0;
        for (SysUserVo user : activeUsers(context)) {
            if (!"0".equals(user.getStatus())) {
                continue;
            }
            if (idleDays(user, context) >= 30) {
                count++;
            }
        }
        return count;
    }

    /**
     * 无成员的角色
     */
    private List<Map<String, Object>> emptyRoles(int limit) {
        List<Map<String, Object>> rows = new ArrayList<>();
        List<SysRoleVo> roles = roleService.selectRoleAll();
        roles.sort(Comparator.comparing(SysRoleVo::getRoleSort, Comparator.nullsLast(Integer::compareTo)));
        for (SysRoleVo role : roles) {
            if (roleService.countUserRoleByRoleId(role.getRoleId()) > 0) {
                continue;
            }
            if (rows.size() >= limit) {
                break;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", role.getRoleId());
            row.put("roleKey", role.getRoleKey());
            row.put("roleName", role.getRoleName());
            row.put("status", "0".equals(role.getStatus()) ? "正常" : "停用");
            row.put("memberCount", 0);
            rows.add(row);
        }
        return rows;
    }

    private int countEmptyRoles() {
        int count = 0;
        for (SysRoleVo role : roleService.selectRoleAll()) {
            if (roleService.countUserRoleByRoleId(role.getRoleId()) == 0) {
                count++;
            }
        }
        return count;
    }

    /**
     * 近 24 小时登录失败按账号聚合：失败次数从多到少排序。
     *
     * <p>只回溯最新 {@link #LOGIN_LOG_SCAN} 条日志，是一个<b>有界样本</b>——
     * 这是刻意的：事实求值发生在目录接口的同步链路上，不能做全表统计。</p>
     */
    private List<Map<String, Object>> loginFailures(int limit) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, FailureStat> entry : failureStats().entrySet()) {
            FailureStat stat = entry.getValue();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", entry.getKey());
            row.put("userName", entry.getKey());
            row.put("failCount", stat.count);
            row.put("lastIp", stat.lastIp);
            row.put("lastTime", stat.lastTime == null ? null : stat.lastTime.toString());
            rows.add(row);
        }
        rows.sort((a, b) -> Integer.compare((Integer) b.get("failCount"), (Integer) a.get("failCount")));
        return rows.size() > limit ? new ArrayList<>(rows.subList(0, limit)) : rows;
    }

    /**
     * 近 24 小时登录失败总次数（徽标口径）
     */
    private int countLoginFailures() {
        int total = 0;
        for (FailureStat stat : failureStats().values()) {
            total += stat.count;
        }
        return total;
    }

    private Map<String, FailureStat> failureStats() {
        SysLoginInfoBo query = new SysLoginInfoBo();
        query.setStatus("1");
        PageResult<SysLoginInfoVo> page =
                loginInfoService.selectPageLoginInfoList(query, new PageQuery(LOGIN_LOG_SCAN, 1));
        LocalDateTime since = LocalDateTime.now().minusHours(FAILURE_WINDOW_HOURS);
        Map<String, FailureStat> stats = new LinkedHashMap<>();
        for (SysLoginInfoVo log : page.getRows()) {
            if (log.getLoginTime() == null || log.getLoginTime().isBefore(since)) {
                continue;
            }
            String account = StringUtils.blankToDefault(log.getUserName(), "(未知账号)");
            FailureStat stat = stats.computeIfAbsent(account, key -> new FailureStat());
            stat.count++;
            stat.lastIp = log.getIpaddr();
            if (stat.lastTime == null || log.getLoginTime().isAfter(stat.lastTime)) {
                stat.lastTime = log.getLoginTime();
            }
        }
        return stats;
    }

    /**
     * 单账号的失败聚合
     */
    private static final class FailureStat {

        private int count;

        private String lastIp;

        private LocalDateTime lastTime;
    }

    // ------------------------------------------------------------ helpers

    private Collection<SysUserVo> activeUsers(IntentCatalogContext context) {
        SysUserBo query = new SysUserBo();
        PageResult<SysUserVo> page = userService.selectPageUserList(query, new PageQuery(USER_SCAN, 1));
        return page.getRows();
    }

    private static Map<String, Object> rowOf(SysUserVo user, long idleDays) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", user.getUserId());
        row.put("userName", StringUtils.blankToDefault(user.getUserName(), "(未知)"));
        row.put("nickName", StringUtils.blankToDefault(user.getNickName(), user.getUserName()));
        row.put("deptName", StringUtils.blankToDefault(user.getDeptName(), "(未分配)"));
        row.put("status", "0".equals(user.getStatus()) ? "正常" : "停用");
        row.put("idleDays", idleDays);
        row.put("lastLogin", user.getLoginDate() == null ? "从未登录" : user.getLoginDate().toString());
        row.put("sortIdleDays", idleDays < 0 ? Long.MAX_VALUE : idleDays);
        return row;
    }

    /**
     * 距上次登录的天数；从未登录返回 -1（由规则决定要不要提示）。
     */
    private static long idleDays(SysUserVo user, IntentCatalogContext context) {
        LocalDateTime last = user.getLoginDate();
        if (last == null) {
            return -1;
        }
        LocalDateTime now = context == null || context.localNow() == null
                ? LocalDateTime.now() : context.localNow();
        return Math.max(ChronoUnit.DAYS.between(last, now), 0);
    }

}
