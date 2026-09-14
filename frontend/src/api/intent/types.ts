/**
 * 意图即服务 · 协议类型（与后端 dev.intent.protocol 一一对应）
 *
 * 这些类型是宿主与意图面板之间的<b>唯一契约</b>：面板只认这个结构，
 * 因此本地执行与（未来的）跨系统网关返回同构，前端不需要为两者写两套渲染。
 */

/** 执行范围：本地闭环 / 远程系统 / 混合编排 */
export type IntentScope = 'LOCAL' | 'REMOTE' | 'COMPOSITE';

/** 执行状态：成功 / 待补参 / 失败 */
export type IntentStatus = 'SUCCESS' | 'NEED_INPUT' | 'FAILED';

/** 网关装配状态 */
export type GatewayStatus = 'ENABLED' | 'UNAVAILABLE';

/** 上下文声明：面板据此从页面采集实体 */
export interface IntentContextField {
  key: string;
  title: string;
  required?: boolean;
}

/** 事实徽标（挂在意图按钮上的动态提示） */
export interface IntentBadge {
  text: string;
  level?: 'info' | 'warning' | 'danger';
  count?: number;
}

/** 目录项：一个可点击的意图 */
export interface IntentCatalogEntry {
  id: string;
  name: string;
  description?: string;
  scope?: IntentScope;
  targetSystem?: string;
  cardType?: string;
  paramsSchema?: Record<string, any>;
  context?: IntentContextField[];
  pages?: string[];
  aliases?: string[];
  badge?: IntentBadge;
}

/** 动态建议（待办 / 推荐）：params 已齐备，点击即执行 */
export interface IntentSuggestion {
  id: string;
  intentId: string;
  title: string;
  subtitle?: string;
  kind?: 'item' | 'aggregate';
  count?: number;
  params?: Record<string, any>;
  reason?: string;
  dedupKey?: string;
}

/** 目录响应 */
export interface IntentCatalogResponse {
  systemName?: string;
  gatewayStatus?: GatewayStatus;
  entries: IntentCatalogEntry[];
  suggestions?: IntentSuggestion[];
}

/** 标准结果信封的渲染块（UI 只认这五种） */
export interface IntentResultBlock {
  kind: 'text' | 'kv' | 'table' | 'list' | 'badges';
  title?: string;
  text?: string;
  items?: Array<{ label: string; value: string }>;
  columns?: string[];
  rows?: string[][];
  level?: 'info' | 'warning' | 'danger';
}

/** 标准结果信封 */
export interface IntentOutput {
  title: string;
  summary: string;
  blocks: IntentResultBlock[];
  followups?: string[];
  nextIntents?: Array<{ intentId: string; title: string; reason?: string; params?: Record<string, any> }>;
}

/** 执行步骤（工具调用 / 模型轮次） */
export interface IntentStepTrace {
  kind: string;
  name: string;
  argsSummary?: string;
  resultSummary?: string;
  ok?: boolean;
  error?: string;
  durationMs?: number;
}

/** token 用量 */
export interface IntentUsage {
  inputTokens?: number;
  outputTokens?: number;
  totalTokens?: number;
}

/** 错误 */
export interface IntentError {
  code: string;
  message: string;
}

/** 执行结果 */
export interface IntentResult {
  traceId: string;
  intentId: string;
  status: IntentStatus;
  output?: IntentOutput;
  missingParams?: string[];
  error?: IntentError;
  steps?: IntentStepTrace[];
  usage?: IntentUsage;
  durationMs?: number;
}

/** 执行留痕记录 */
export interface IntentTraceRecord {
  traceId: string;
  intentId: string;
  intentName?: string;
  userId?: string;
  userName?: string;
  startedAt?: number;
  durationMs?: number;
  status?: IntentStatus;
  params?: Record<string, any>;
  output?: IntentOutput;
  error?: IntentError;
  steps?: IntentStepTrace[];
}

/** 管理端：意图运营配置 */
export interface IntentConfigVO {
  intentId: string;
  name: string;
  description?: string;
  scope?: IntentScope;
  version?: number;
  pages?: string[];
  source?: 'builtin' | 'custom';
  executor?: string;
  enabled: boolean;
  roles: string[];
  configured?: boolean;
}

/** 管理端：意图规范摘要 */
export interface IntentSpecVO {
  intentId: string;
  name: string;
  description?: string;
  brief?: string;
  scope?: IntentScope;
  version?: number;
  source?: 'builtin' | 'custom';
  executor?: string;
  toolCount?: number;
  pages?: string[];
  aliases?: string[];
}

/** 管理端：执行器档案 */
export interface IntentExecutorVO {
  executorId: string;
  name?: string;
  type?: string;
  description?: string;
  model?: string | null;
  tools?: string[];
  stepCount?: number;
  nodeCount?: number;
  maxTurns?: number | null;
  source?: 'builtin' | 'custom';
}

/** 管理端：执行器下拉项 */
export interface IntentExecutorOption {
  executorId: string;
  name: string;
  type?: string;
}

/** 管理端：宿主工具项 */
export interface IntentToolOption {
  name: string;
  description: string;
}

/** 平台状态 */
export interface IntentStatusVO {
  systemName?: string;
  gatewayEnabled?: boolean;
  gatewayStatus?: GatewayStatus;
  intentCount?: number;
  toolCount?: number;
  executorCount?: number;
  llmProvider?: string;
  llmModel?: string;
}
