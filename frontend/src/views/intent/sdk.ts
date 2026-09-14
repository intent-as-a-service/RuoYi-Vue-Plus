/**
 * 意图面板 SDK 的宿主注入点（幂等）。
 *
 * 分层原则——**SDK 管交互，宿主管运营**：
 * - L1~L2 交互层（悬浮球 / 抽屉 / 意图菜单 / 槽位表单 / 待办分组 / 结果卡片 / 轨迹 / 历史 / 反馈）
 *   全部来自 `intent-ui-sdk.js`，一个文件所有宿主共用，改交互只改这一份；
 * - 宿主适配只有下面这几十行"注入 + 挂载"，没有任何渲染逻辑。
 *
 * 鉴权每次请求现取（token 刷新后无需重新挂载）；apiPrefix 带上宿主的 API 前缀
 * （SDK 走自己的 fetch，不经过 axios 实例，因此必须自带 /dev-api 或 /prod-api）。
 */
import { IntentUI } from 'intent-ui-sdk';
import 'intent-ui-sdk/css/intent-ui.css';
import { getLanguage } from '@/lang';
import { getToken } from '@/utils/auth';
import request from '@/utils/request';

let configured = false;

/** 实体下拉加载器：宿主数据 → SDK 需要的 [{ label, value }] */
type EntityLoader = () => Promise<Array<{ label: string; value: any }>>;

/**
 * 安全地调用宿主接口：无权限/失败时返回空数组，不弹错、不影响面板打开。
 */
async function safeOptions(
  loader: () => Promise<any>,
  mapper: (row: any) => { label: string; value: any }
): Promise<Array<{ label: string; value: any }>> {
  try {
    const res = await loader();
    const rows = res?.data?.rows ?? res?.data ?? [];
    return Array.isArray(rows) ? rows.map(mapper).filter(item => item.value !== undefined) : [];
  } catch {
    return [];
  }
}

/** 槽位实体下拉（面板里的 ⚙ 按钮）：字段名 → 候选来源 */
const entityOptions: Record<string, EntityLoader> = {
  // 用户
  // 注意：后端意图规范的入参 Schema 里主键声明为 string（JSON Schema 校验是强校验），
  //      所以候选值一律 String(...) 转换，避免"选完点执行却报参数未通过校验"。
  userId: () =>
    safeOptions(
      () => request({ url: '/system/user/list', method: 'get', params: { pageNum: 1, pageSize: 50 } }),
      row => ({ label: `${row.nickName || row.userName}（${row.userName}）`, value: String(row.userId) })
    ),
  userName: () =>
    safeOptions(
      () => request({ url: '/system/user/list', method: 'get', params: { pageNum: 1, pageSize: 50 } }),
      row => ({ label: `${row.nickName || row.userName}（${row.userName}）`, value: String(row.userName) })
    ),
  // 部门
  deptId: () =>
    safeOptions(
      () => request({ url: '/system/dept/list', method: 'get' }),
      row => ({ label: row.deptName, value: String(row.deptId) })
    ),
  // 角色
  roleKey: () =>
    safeOptions(
      () => request({ url: '/system/role/list', method: 'get', params: { pageNum: 1, pageSize: 50 } }),
      row => ({ label: `${row.roleName}（${row.roleKey}）`, value: String(row.roleKey) })
    ),
  // 字典类型
  dictType: () =>
    safeOptions(
      () => request({ url: '/system/dict/type/list', method: 'get', params: { pageNum: 1, pageSize: 50 } }),
      row => ({ label: `${row.dictName}（${row.dictType}）`, value: String(row.dictType) })
    ),
  // 客户端
  clientId: () =>
    safeOptions(
      () => request({ url: '/system/client/list', method: 'get', params: { pageNum: 1, pageSize: 50 } }),
      row => ({ label: `${row.clientKey}（${row.deviceType}）`, value: String(row.clientId) })
    )
};

/**
 * 注入宿主上下文（幂等，重复调用只生效一次）。
 *
 * @returns 配置好的 IntentUI 单例
 */
export function setupIntentSdk() {
  if (configured) {
    return IntentUI;
  }
  configured = true;
  IntentUI.configure({
    // SDK 自带 fetch，不走宿主 axios，因此前缀要写全
    apiPrefix: `${import.meta.env.VITE_APP_BASE_API}/intent`,
    authHeaders: () => ({
      Authorization: 'Bearer ' + getToken(),
      // 宿主安全策略要求 clientid 与令牌匹配（见 SecurityConfig 的客户端校验）
      clientid: import.meta.env.VITE_APP_CLIENT_ID
    }),
    locale: getLanguage() === 'en_US' ? 'en-US' : 'zh-CN',
    // 低于 Element Plus 的弹层（2000+），避免遮挡宿主的对话框
    zIndex: 900,
    entityOptions
  });
  return IntentUI;
}

export { IntentUI };
