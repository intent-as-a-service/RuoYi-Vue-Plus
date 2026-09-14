<template>
  <div class="p-2 app-container intent-center-page">
    <el-card shadow="hover" class="intent-intro">
      <template #header>
        <div class="panel-heading">
          <h3>意图调试台</h3>
          <span class="panel-sub">全量目录 · 点意图先填槽位再执行 · 执行留痕可回放</span>
        </div>
      </template>
      <div class="intent-bar">
        <el-input v-model="page" class="page-input" clearable placeholder="页面标识，如 system/user（留空 = 意图中心，展示全部）" />
        <el-button type="primary" icon="Refresh" @click="reloadPanel">重新装载</el-button>
        <el-button icon="Pointer" @click="toggleFloating">{{ floating ? '移除悬浮球' : '挂载悬浮球' }}</el-button>
        <div class="intent-status">
          <el-tag v-if="status.systemName" type="info" effect="plain">{{ status.systemName }}</el-tag>
          <el-tag type="success" effect="plain">意图 {{ status.intentCount ?? '-' }}</el-tag>
          <el-tag type="warning" effect="plain">宿主工具 {{ status.toolCount ?? '-' }}</el-tag>
          <el-tag effect="plain">执行器 {{ status.executorCount ?? '-' }}</el-tag>
          <el-tag :type="status.gatewayEnabled ? 'success' : 'info'" effect="plain">
            网关 {{ status.gatewayEnabled ? '已装配' : '未装配' }}
          </el-tag>
        </div>
      </div>
      <el-alert v-if="status.gatewayEnabled === false" class="mt-2" type="info" :closable="false" show-icon
        title="意图网关未装配"
        description="本地（LOCAL）意图完全可用；跨系统（REMOTE / COMPOSITE）意图会明确返回 REMOTE_UNAVAILABLE，不会静默失败。" />
    </el-card>

    <el-card shadow="hover" class="mt-3">
      <div ref="panelRef" class="intent-panel-host" />
    </el-card>
  </div>
</template>

<script setup lang="ts" name="IntentCenter">
import { getIntentStatus } from '@/api/intent';
import type { IntentStatusVO } from '@/api/intent/types';
import { currentIntentPage } from '@/views/intent/pageContext';
import { setupIntentSdk } from '@/views/intent/sdk';

/** 面板挂载容器 */
const panelRef = ref<HTMLElement>();

/** 页面标识（空 = 意图中心，展示全量目录） */
const page = ref('');

/** 平台状态 */
const status = ref<IntentStatusVO>({});

/** SDK 返回的面板与悬浮球实例 */
let panel: { destroy: () => void } | null = null;
let floating: { destroy: () => void } | null = null;

/** 装载内嵌面板（debug 模式：点意图先弹槽位表单，再执行） */
const reloadPanel = () => {
  const intentUI = setupIntentSdk();
  panel?.destroy();
  panel = intentUI.mount({
    container: panelRef.value,
    page: page.value.trim() || undefined,
    mode: 'debug'
  });
};

/** 悬浮球：全站唯一入口；这里用于在调试台内直接验证挂载效果 */
const toggleFloating = () => {
  const intentUI = setupIntentSdk();
  if (floating) {
    floating.destroy();
    floating = null;
    return;
  }
  floating = intentUI.mountFloating({
    getPage: () => page.value.trim() || currentIntentPage(),
    getContext: () => ({})
  });
};

const loadStatus = async () => {
  const res = await getIntentStatus();
  status.value = res.data ?? {};
};

onMounted(async () => {
  await loadStatus();
  await nextTick();
  reloadPanel();
});

onBeforeUnmount(() => {
  panel?.destroy();
  floating?.destroy();
  panel = null;
  floating = null;
});
</script>

<style lang="scss" scoped>
.intent-intro {
  .panel-heading {
    display: flex;
    align-items: baseline;
    gap: 10px;

    h3 {
      margin: 0;
      font-size: 15px;
    }

    .panel-sub {
      font-size: 12px;
      color: var(--el-text-color-secondary);
    }
  }
}

.intent-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;

  .page-input {
    max-width: 420px;
  }

  .intent-status {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-left: auto;
  }
}

.intent-panel-host {
  min-height: 420px;
}

.mt-3 {
  margin-top: 12px;
}

.mt-2 {
  margin-top: 8px;
}
</style>
