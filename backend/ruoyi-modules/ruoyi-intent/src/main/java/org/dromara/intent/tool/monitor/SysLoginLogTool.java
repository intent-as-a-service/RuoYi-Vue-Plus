package org.dromara.intent.tool.monitor;

import dev.intent.sdk.tool.Schemas;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.intent.tool.BaseHostTool;
import org.dromara.system.domain.bo.SysLoginInfoBo;
import org.dromara.system.domain.vo.SysLoginInfoVo;
import org.dromara.system.service.ISysLoginInfoService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 宿主工具：登录日志分析取数。
 *
 * <p>口径与「监控 → 登录日志」一致；取最新 N 条后在内存里按时间窗过滤，
 * 这样既受控（不会全表扫描），又能回答"近 24 小时有哪些失败登录"。</p>
 *
 * @author RuoYi-Vue-Plus
 */
@Component
@RequiredArgsConstructor
public class SysLoginLogTool extends BaseHostTool {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final int SCAN = 300;

    private final ISysLoginInfoService loginInfoService;

    @Override
    public String name() {
        return "sys_login_log_query";
    }

    @Override
    public String description() {
        return "查询登录日志并做聚合：成功/失败条数、失败账号 TOP、来源 IP TOP、异地与非常用设备线索。"
                + "用于登录异常分析、暴力破解识别、账号活跃度核查。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("userName", Schemas.string("按账号模糊过滤"));
        properties.put("ipaddr", Schemas.string("按来源 IP 模糊过滤"));
        properties.put("status", Schemas.enumeration(List.of("0", "1"), "0=成功，1=失败；留空 = 全部"));
        properties.put("sinceHours", Schemas.number("只看最近多少小时，默认 24"));
        properties.put("limit", Schemas.number("明细返回条数上限，默认 30，最大 100"));
        return Schemas.object(properties, List.of());
    }

    @Override
    protected Map<String, Object> run(Map<String, Object> args) throws Exception {
        String userName = str(args, "userName");
        String ipaddr = str(args, "ipaddr");
        String status = str(args, "status");
        int sinceHours = Math.max(intOr(args, "sinceHours", 24), 1);
        int limit = Math.min(Math.max(intOr(args, "limit", 30), 1), 100);

        SysLoginInfoBo query = new SysLoginInfoBo();
        if (StringUtils.isNotBlank(userName)) {
            query.setUserName(userName);
        }
        if (StringUtils.isNotBlank(ipaddr)) {
            query.setIpaddr(ipaddr);
        }
        if (StringUtils.isNotBlank(status)) {
            query.setStatus(status);
        }
        PageResult<SysLoginInfoVo> page = loginInfoService.selectPageLoginInfoList(query, new PageQuery(SCAN, 1));
        LocalDateTime since = LocalDateTime.now().minusHours(sinceHours);

        List<Map<String, Object>> rows = new ArrayList<>();
        Set<String> failedAccounts = new LinkedHashSet<>();
        Set<String> failedIps = new LinkedHashSet<>();
        Set<String> locations = new LinkedHashSet<>();
        Map<String, Integer> failByAccount = new LinkedHashMap<>();
        Map<String, Integer> failByIp = new LinkedHashMap<>();
        int success = 0;
        int fail = 0;
        int inWindow = 0;
        for (SysLoginInfoVo log : page.getRows()) {
            if (log.getLoginTime() == null || log.getLoginTime().isBefore(since)) {
                continue;
            }
            inWindow++;
            boolean ok = "0".equals(log.getStatus());
            if (ok) {
                success++;
            } else {
                fail++;
                failedAccounts.add(log.getUserName());
                failedIps.add(log.getIpaddr());
                failByAccount.merge(StringUtils.blankToDefault(log.getUserName(), "(未知)"), 1, Integer::sum);
                failByIp.merge(StringUtils.blankToDefault(log.getIpaddr(), "(未知)"), 1, Integer::sum);
            }
            if (StringUtils.isNotBlank(log.getLoginLocation())) {
                locations.add(log.getLoginLocation());
            }
            if (rows.size() < limit) {
                rows.add(row("time", log.getLoginTime().format(TIME),
                        "userName", log.getUserName(),
                        "status", ok ? "成功" : "失败",
                        "ipaddr", log.getIpaddr(),
                        "loginLocation", log.getLoginLocation(),
                        "deviceType", log.getDeviceType(),
                        "clientKey", log.getClientKey(),
                        "browser", log.getBrowser(),
                        "os", log.getOs(),
                        "msg", log.getMsg()));
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("windowHours", sinceHours);
        data.put("scanned", page.getRows().size());
        data.put("inWindow", inWindow);
        data.put("successCount", success);
        data.put("failCount", fail);
        data.put("distinctFailedAccounts", failedAccounts.size());
        data.put("distinctFailedIps", failedIps.size());
        data.put("topFailedAccounts", topN(failByAccount, 5));
        data.put("topFailedIps", topN(failByIp, 5));
        data.put("locations", new ArrayList<>(locations));
        data.put("rows", rows);
        data.put("note", "最多回溯最新 " + SCAN + " 条日志进行统计；inWindow 为落在时间窗内的条数。"
                + "若 scanned 已达上限，说明日志量很大，建议缩小账号或 IP 范围后再分析。");
        return data;
    }

    private static List<Map<String, Object>> topN(Map<String, Integer> counter, int n) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counter.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < Math.min(n, entries.size()); i++) {
            rows.add(row("key", entries.get(i).getKey(), "count", entries.get(i).getValue()));
        }
        return rows;
    }

}
