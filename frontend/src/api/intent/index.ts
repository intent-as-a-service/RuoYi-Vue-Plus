import type {
  IntentCatalogResponse,
  IntentConfigVO,
  IntentExecutorOption,
  IntentExecutorVO,
  IntentResult,
  IntentSpecVO,
  IntentStatusVO,
  IntentToolOption,
  IntentTraceRecord
} from '@/api/intent/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';

// =====================================================================
// 用户端（面板自身走 SDK 内置 fetch，不经过这里；这些接口供宿主页面按需调用）
// =====================================================================

/** 获得意图目录（按当前用户角色与页面过滤） */
export const getIntentCatalog = (page?: string): AxiosPromise<IntentCatalogResponse> => {
  return request({
    url: '/intent/catalog',
    method: 'get',
    params: { page }
  });
};

/** 执行意图 */
export const executeIntent = (data: {
  intentId: string;
  params?: Record<string, any>;
  context?: Record<string, any>;
}): AxiosPromise<IntentResult> => {
  return request({
    url: '/intent/execute',
    method: 'post',
    data,
    headers: { repeatSubmit: false }
  });
};

/** 当前用户的执行历史 */
export const listIntentHistory = (intentId?: string, limit = 20): AxiosPromise<IntentTraceRecord[]> => {
  return request({
    url: '/intent/history',
    method: 'get',
    params: { intentId, limit }
  });
};

/** 执行留痕详情 */
export const getIntentTrace = (traceId: string): AxiosPromise<IntentTraceRecord> => {
  return request({
    url: '/intent/trace/' + traceId,
    method: 'get'
  });
};

/** 结果评价反馈 */
export const feedbackIntent = (data: {
  traceId: string;
  intentId: string;
  rating: string;
  comment?: string;
}): AxiosPromise<boolean> => {
  return request({
    url: '/intent/feedback',
    method: 'post',
    data
  });
};

/** 平台状态 */
export const getIntentStatus = (): AxiosPromise<IntentStatusVO> => {
  return request({
    url: '/intent/status',
    method: 'get'
  });
};

// =====================================================================
// 管理端：意图运营配置
// =====================================================================

/**
 * 角色候选（意图可见性配置用）。
 *
 * 刻意走宿主既有的角色接口而不是新建接口：角色是宿主的领域概念，
 * 意图平台只是消费它，不复制一份。
 */
export const listRoleOptions = (): AxiosPromise<{ rows: Array<Record<string, any>> }> => {
  return request({
    url: '/system/role/list',
    method: 'get',
    params: { pageNum: 1, pageSize: 200 }
  });
};

/** 意图配置列表（上架 / 角色 / 执行器） */
export const listIntentConfig = (): AxiosPromise<IntentConfigVO[]> => {
  return request({
    url: '/intent/config/list',
    method: 'get'
  });
};

/** 执行器下拉选项 */
export const listExecutorOptions = (): AxiosPromise<IntentExecutorOption[]> => {
  return request({
    url: '/intent/config/executor-options',
    method: 'get'
  });
};

/** 更新意图配置 */
export const updateIntentConfig = (data: {
  intentId: string;
  enabled?: boolean;
  roles?: string[];
  executor?: string;
  remark?: string;
}) => {
  return request({
    url: '/intent/config/update',
    method: 'put',
    data
  });
};

// =====================================================================
// 管理端：意图规范
// =====================================================================

/** 意图规范列表 */
export const listIntentSpec = (): AxiosPromise<IntentSpecVO[]> => {
  return request({
    url: '/intent/spec/list',
    method: 'get'
  });
};

/** 意图规范原文 */
export const getIntentSpecYaml = (intentId: string): AxiosPromise<string> => {
  return request({
    url: '/intent/spec/yaml/' + intentId,
    method: 'get'
  });
};

/** 校验意图规范（不落库） */
export const validateIntentSpec = (yaml: string): AxiosPromise<string[]> => {
  return request({
    url: '/intent/spec/validate',
    method: 'post',
    data: { yaml },
    headers: { repeatSubmit: false }
  });
};

/** 新增意图规范 */
export const addIntentSpec = (yaml: string) => {
  return request({
    url: '/intent/spec',
    method: 'post',
    data: { yaml },
    headers: { repeatSubmit: false }
  });
};

/** 修改意图规范 */
export const updateIntentSpec = (intentId: string, yaml: string) => {
  return request({
    url: '/intent/spec/' + intentId,
    method: 'put',
    data: { yaml },
    headers: { repeatSubmit: false }
  });
};

/** 删除意图规范 */
export const delIntentSpec = (intentId: string) => {
  return request({
    url: '/intent/spec/' + intentId,
    method: 'delete'
  });
};

// =====================================================================
// 管理端：执行器档案
// =====================================================================

/** 执行器档案列表 */
export const listIntentExecutors = (): AxiosPromise<IntentExecutorVO[]> => {
  return request({
    url: '/intent/executor/list',
    method: 'get'
  });
};

/** 宿主工具清单 */
export const listIntentTools = (): AxiosPromise<IntentToolOption[]> => {
  return request({
    url: '/intent/executor/tools',
    method: 'get'
  });
};

/** 执行器档案原文 */
export const getExecutorYaml = (executorId: string): AxiosPromise<string> => {
  return request({
    url: '/intent/executor/yaml/' + executorId,
    method: 'get'
  });
};

/** 校验执行器档案（不落库） */
export const validateExecutorYaml = (yaml: string): AxiosPromise<string[]> => {
  return request({
    url: '/intent/executor/validate',
    method: 'post',
    data: { yaml },
    headers: { repeatSubmit: false }
  });
};

/** 新增执行器档案（保存即热更新） */
export const addExecutor = (yaml: string) => {
  return request({
    url: '/intent/executor',
    method: 'post',
    data: { yaml },
    headers: { repeatSubmit: false }
  });
};

/** 修改执行器档案（保存即热更新） */
export const updateExecutor = (executorId: string, yaml: string) => {
  return request({
    url: '/intent/executor/' + executorId,
    method: 'put',
    data: { yaml },
    headers: { repeatSubmit: false }
  });
};

/** 删除执行器档案（保存即热更新） */
export const delExecutor = (executorId: string) => {
  return request({
    url: '/intent/executor/' + executorId,
    method: 'delete'
  });
};
