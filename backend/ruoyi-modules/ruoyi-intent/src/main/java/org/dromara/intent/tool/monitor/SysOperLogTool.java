package org.dromara.intent.tool.monitor;

import dev.intent.sdk.tool.Schemas;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.intent.tool.BaseHostTool;
import org.dromara.system.domain.bo.SysOperLogBo;
import org.dromara.system.domain.vo.SysOperLogVo;
import org.dromara.system.service.ISysOperLogService;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 宿主工具：操作日志查询与归因取数。
 *
 * @author RuoYi-Vue-Plus
 */
@Component
@RequiredArgsConstructor
public class SysOperLogTool extends BaseHostTool {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final int SCAN = 300;

    private final ISysOperLogService operLogService;

    @Override
    public String name() {
        return "sys_oper_log_query";
    }

    @Override
    public String description() {
        return "查询操作日志：谁在什么时候做了什么、耗时多久、成功还是失败、失败原因是什么。"
                + "用于操作审计、失败归因、慢操作排查。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("operName", Schemas.string("操作人账号，模糊匹配"));
        properties.put("title", Schemas.string("操作模块标题，如「用户管理」，模糊匹配"));
        properties.put("status", Schemas.enumeration(List.of("0", "1"), "0=成功，1=失败；留空 = 全部"));
        properties.put("onlySlow", Schemas.bool("是否只看耗时超过 1000ms 的操作，默认 false"));
        properties.put("limit", Schemas.number("返回条数上限，默认 30，最大 100"));
        return Schemas.object(properties, List.of());
    }

    @Override
    protected Map<String, Object> run(Map<String, Object> args) throws Exception {
        SysOperLogBo query = new SysOperLogBo();
        String operName = str(args, "operName");
        if (StringUtils.isNotBlank(operName)) {
            query.setOperName(operName);
        }
        String title = str(args, "title");
        if (StringUtils.isNotBlank(title)) {
            query.setTitle(title);
        }
        String status = str(args, "status");
        if (StringUtils.isNotBlank(status)) {
            query.setStatus(Integer.valueOf(status));
        }
        boolean onlySlow = boolOr(args, "onlySlow", false);
        int limit = Math.min(Math.max(intOr(args, "limit", 30), 1), 100);

        PageResult<SysOperLogVo> page = operLogService.selectPageOperLogList(query, new PageQuery(SCAN, 1));
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Integer> failByTitle = new LinkedHashMap<>();
        int success = 0;
        int fail = 0;
        long slowest = 0;
        for (SysOperLogVo log : page.getRows()) {
            boolean ok = log.getStatus() != null && log.getStatus() == 0;
            if (ok) {
                success++;
            } else {
                fail++;
                failByTitle.merge(StringUtils.blankToDefault(log.getTitle(), "(未标注模块)"), 1, Integer::sum);
            }
            if (log.getCostTime() != null) {
                slowest = Math.max(slowest, log.getCostTime());
            }
            if (onlySlow && (log.getCostTime() == null || log.getCostTime() <= 1000)) {
                continue;
            }
            if (rows.size() >= limit) {
                continue;
            }
            rows.add(row("time", log.getOperTime() == null ? null : log.getOperTime().format(TIME),
                    "title", log.getTitle(),
                    "operName", log.getOperName(),
                    "deptName", log.getDeptName(),
                    "requestMethod", log.getRequestMethod(),
                    "operUrl", log.getOperUrl(),
                    "operIp", log.getOperIp(),
                    "status", ok ? "成功" : "失败",
                    "costTime", log.getCostTime(),
                    "errorMsg", log.getErrorMsg()));
        }
        List<Map<String, Object>> topFail = new ArrayList<>();
        failByTitle.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(5)
                .forEach(entry -> topFail.add(row("title", entry.getKey(), "failCount", entry.getValue())));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("scanned", page.getRows().size());
        data.put("successCount", success);
        data.put("failCount", fail);
        data.put("slowestCostMs", slowest);
        data.put("topFailedModules", topFail);
        data.put("rows", rows);
        return data;
    }

}
