package org.dromara.intent.tool.system;

import dev.intent.sdk.tool.Schemas;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.intent.tool.BaseHostTool;
import org.dromara.system.domain.bo.SysClientBo;
import org.dromara.system.domain.vo.SysClientVo;
import org.dromara.system.service.ISysClientService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 宿主工具：客户端与会话策略查询。
 *
 * <p>token 的有效期策略在这个表里（{@code active_timeout} / {@code timeout}），
 * 不在配置文件里 —— 这正是"排查登录有效期问题"最容易找错地方的一环。</p>
 *
 * @author RuoYi-Vue-Plus
 */
@Component
@RequiredArgsConstructor
public class SysClientLookupTool extends BaseHostTool {

    /**
     * 客户端密钥字段一律掩码
     */
    private static final String MASK = "******";

    private final ISysClientService clientService;

    @Override
    public String name() {
        return "sys_client_lookup";
    }

    @Override
    public String description() {
        return "查询系统客户端配置：客户端标识、设备类型、授权类型、访问路径与 IP 白名单、"
                + "会话活跃超时（activeTimeout，秒）与令牌过期（timeout，秒）、状态。"
                + "用于排查登录有效期、终端接入范围与白名单问题。clientSecret 一律掩码。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("clientId", Schemas.string("客户端标识（精确匹配）；留空 = 全部"));
        properties.put("deviceType", Schemas.string("设备类型，如 pc / android / ios"));
        return Schemas.object(properties, List.of());
    }

    @Override
    protected Map<String, Object> run(Map<String, Object> args) {
        String clientId = str(args, "clientId");
        String deviceType = str(args, "deviceType");
        if (StringUtils.isNotBlank(clientId)) {
            SysClientVo client = clientService.queryByClientId(clientId);
            return result(client == null ? 0 : 1,
                    client == null ? List.of() : List.of(toRow(client)),
                    client == null ? "客户端不存在: " + clientId : null);
        }
        SysClientBo query = new SysClientBo();
        if (StringUtils.isNotBlank(deviceType)) {
            query.setDeviceType(deviceType);
        }
        List<SysClientVo> clients = clientService.queryList(query);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SysClientVo client : clients) {
            rows.add(toRow(client));
        }
        return result(clients.size(), rows, "activeTimeout = 无操作多久后需要重新认证（秒）；timeout = 令牌绝对有效期（秒）");
    }

    private static Map<String, Object> toRow(SysClientVo client) {
        return row("id", client.getId(),
                "clientId", client.getClientId(),
                "clientKey", client.getClientKey(),
                "clientSecret", MASK,
                "deviceType", client.getDeviceType(),
                "grantType", client.getGrantType(),
                "accessPath", client.getAccessPath(),
                "ipWhitelist", StringUtils.isBlank(client.getIpWhitelist()) ? "（未限制）" : client.getIpWhitelist(),
                "activeTimeoutSeconds", client.getActiveTimeout(),
                "timeoutSeconds", client.getTimeout(),
                "status", "0".equals(client.getStatus()) ? "正常" : "停用");
    }

}
