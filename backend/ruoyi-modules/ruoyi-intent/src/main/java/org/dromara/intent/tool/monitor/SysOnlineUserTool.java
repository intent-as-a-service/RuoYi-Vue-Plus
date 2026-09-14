package org.dromara.intent.tool.monitor;

import cn.dev33.satoken.stp.StpUtil;
import dev.intent.sdk.tool.Schemas;
import org.dromara.common.core.constant.CacheNames;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.intent.tool.BaseHostTool;
import org.dromara.system.api.domain.UserOnlineDTO;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 宿主工具：在线会话（在线用户）查询。
 *
 * <p>数据源与会话一致：Sa-Token 的 token 会话落在 Redis（{@code online_tokens:}），
 * 因此结论与「监控 → 在线用户」页面完全相同。</p>
 *
 * @author RuoYi-Vue-Plus
 */
@Component
public class SysOnlineUserTool extends BaseHostTool {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    @Override
    public String name() {
        return "sys_online_user_query";
    }

    @Override
    public String description() {
        return "查询当前在线会话：账号、部门、客户端、设备、登录 IP 与地点、登录时间。"
                + "用于观察在线规模、检测异常会话（同账号多端、异地登录）。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("userName", Schemas.string("按账号过滤"));
        properties.put("ipaddr", Schemas.string("按登录 IP 过滤"));
        properties.put("limit", Schemas.number("返回条数上限，默认 50，最大 200"));
        return Schemas.object(properties, List.of());
    }

    @Override
    protected Map<String, Object> run(Map<String, Object> args) {
        String userName = str(args, "userName");
        String ipaddr = str(args, "ipaddr");
        int limit = Math.min(Math.max(intOr(args, "limit", 50), 1), 200);

        Collection<String> keys = RedisUtils.keys(CacheNames.ONLINE_TOKEN_KEY + "*");
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Integer> byUser = new LinkedHashMap<>();
        Map<String, Integer> byClient = new LinkedHashMap<>();
        int active = 0;
        for (String key : keys) {
            String token = StringUtils.substringAfterLast(key, StringUtils.COLON);
            if (StpUtil.stpLogic.getTokenActiveTimeoutByToken(token) < -1) {
                continue;
            }
            UserOnlineDTO online = RedisUtils.getCacheObject(key);
            if (online == null) {
                continue;
            }
            active++;
            if (StringUtils.isNotBlank(userName) && !StringUtils.contains(online.getUserName(), userName)) {
                continue;
            }
            if (StringUtils.isNotBlank(ipaddr) && !StringUtils.contains(online.getIpaddr(), ipaddr)) {
                continue;
            }
            byUser.merge(StringUtils.blankToDefault(online.getUserName(), "(未知)"), 1, Integer::sum);
            byClient.merge(StringUtils.blankToDefault(online.getClientKey(), "(未知)"), 1, Integer::sum);
            if (rows.size() >= limit) {
                continue;
            }
            rows.add(row("userName", online.getUserName(),
                    "deptName", online.getDeptName(),
                    "clientKey", online.getClientKey(),
                    "deviceType", online.getDeviceType(),
                    "ipaddr", online.getIpaddr(),
                    "loginLocation", online.getLoginLocation(),
                    "browser", online.getBrowser(),
                    "os", online.getOs(),
                    "loginTime", online.getLoginTime() == null ? null
                            : TIME.format(Instant.ofEpochMilli(online.getLoginTime())),
                    "tokenSuffix", token == null || token.length() < 6 ? token
                            : "…" + token.substring(token.length() - 6)));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("activeSessions", active);
        data.put("matched", rows.size());
        data.put("distinctUsers", byUser.size());
        data.put("sessionsByClient", toRows(byClient, "clientKey"));
        data.put("topUsersBySessions", toRows(byUser, "userName"));
        data.put("rows", rows);
        data.put("note", "tokenSuffix 仅用于人工核对，完整的 token 不返回给模型。");
        return data;
    }

    private static List<Map<String, Object>> toRows(Map<String, Integer> counter, String keyName) {
        List<Map<String, Object>> rows = new ArrayList<>();
        counter.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(10)
                .forEach(entry -> rows.add(row(keyName, entry.getKey(), "sessions", entry.getValue())));
        return rows;
    }

}
