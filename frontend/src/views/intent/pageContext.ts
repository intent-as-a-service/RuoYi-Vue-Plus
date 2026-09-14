/**
 * 页面上下文注册中心。
 *
 * 为什么需要它：意图按钮要"点了就能跑"，前提是面板知道当前页面正在看哪个对象
 * （哪个用户、哪个部门…）。让宿主页面自己声明上下文来源，比让面板去猜路由参数可靠得多——
 * **面板与页面看到的必须是同一份数据**，否则会出现"张冠李戴"（面板分析了另一个对象）。
 *
 * 用法（业务页面 4 行）：
 * ```ts
 * import { registerIntentPageContext } from '@/views/intent/pageContext';
 * onMounted(() => {
 *   registerIntentPageContext('system/user', () => ({
 *     userId: currentRow.value?.userId,
 *     userName: currentRow.value?.userName
 *   }));
 * });
 * ```
 *
 * 返回一个注销函数（组件卸载时调用），避免离开页面后上下文残留。
 */

/** 上下文提供者：返回当前页面的实体键值 */
export type IntentContextProvider = () => Record<string, any>;

const providers = new Map<string, Set<IntentContextProvider>>();

/**
 * 注册某个页面的上下文来源。
 *
 * @param page     页面标识（与后端 IntentSpec.pages 同一口径，如 system/user）
 * @param provider 上下文提供者（每次打开面板时调用，取值时须来自页面可见数据）
 * @returns 注销函数
 */
export function registerIntentPageContext(page: string, provider: IntentContextProvider): () => void {
  if (!page || typeof provider !== 'function') {
    return () => {};
  }
  const set = providers.get(page) ?? new Set<IntentContextProvider>();
  set.add(provider);
  providers.set(page, set);
  return () => {
    const current = providers.get(page);
    if (!current) {
      return;
    }
    current.delete(provider);
    if (current.size === 0) {
      providers.delete(page);
    }
  };
}

/**
 * 求值某个页面的上下文（多个提供者合并，后者覆盖前者的同名字段）。
 *
 * @param page 页面标识
 * @returns 上下文字典（空对象表示该页面没有声明上下文）
 */
export function resolveIntentPageContext(page: string): Record<string, any> {
  const set = providers.get(page);
  if (!set || set.size === 0) {
    return {};
  }
  const merged: Record<string, any> = {};
  set.forEach(provider => {
    try {
      const value = provider();
      if (value && typeof value === 'object') {
        Object.entries(value).forEach(([key, item]) => {
          if (item !== undefined && item !== null && item !== '') {
            merged[key] = item;
          }
        });
      }
    } catch (e) {
      console.warn('[intent] 页面上下文求值失败，已跳过该提供者', e);
    }
  });
  return merged;
}

/**
 * 当前页面标识：与后端 IntentSpec.pages 的匹配口径一致（路由路径去掉前导斜杠）。
 *
 * @param path 路由路径（默认取当前路由）
 * @returns 页面标识，如 system/user
 */
export function currentIntentPage(path?: string): string {
  const raw = path ?? (typeof window === 'undefined' ? '' : window.location.pathname);
  return raw.replace(/^\/+/, '').replace(/\/+$/, '');
}
