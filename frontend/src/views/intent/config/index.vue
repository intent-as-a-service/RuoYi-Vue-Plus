<template>
  <div class="p-2 app-container intent-config-page">
    <el-card shadow="hover">
      <template #header>
        <div class="toolbar-shell">
          <div class="table-heading">
            <h3>意图管理</h3>
            <span class="panel-sub">上架开关 · 可见角色 · 执行器 —— 保存即生效，无需发版</span>
          </div>
          <div class="toolbar-actions">
            <el-input v-model="keyword" class="kw" clearable placeholder="按编号 / 名称 / 描述过滤" />
            <el-button icon="Refresh" @click="getList">刷新</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" border :data="filteredList" row-key="intentId">
        <el-table-column label="意图" min-width="260">
          <template #default="{ row }">
            <div class="intent-name">{{ row.name }}</div>
            <div class="intent-id">{{ row.intentId }}</div>
            <div v-if="row.description" class="intent-desc">{{ row.description }}</div>
          </template>
        </el-table-column>
        <el-table-column label="范围" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="scopeTag(row.scope)" effect="plain" size="small">{{ row.scope }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="挂载页面" width="200">
          <template #default="{ row }">
            <span v-if="!row.pages || row.pages.length === 0" class="muted">全局</span>
            <template v-else>
              <el-tag v-for="p in row.pages.slice(0, 3)" :key="p" size="small" effect="plain" class="page-tag">
                {{ p || '意图中心' }}
              </el-tag>
              <span v-if="row.pages.length > 3" class="muted">+{{ row.pages.length - 3 }}</span>
            </template>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.source === 'builtin' ? 'info' : 'success'" effect="plain" size="small">
              {{ row.source === 'builtin' ? '内置' : '后台' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="上架" width="80" align="center">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" @change="markDirty(row)" />
          </template>
        </el-table-column>
        <el-table-column label="可见角色" width="240">
          <template #default="{ row }">
            <el-select v-model="row.roles" multiple collapse-tags collapse-tags-tooltip placeholder="不限制"
              class="w-full" @change="markDirty(row)">
              <el-option v-for="role in roleOptions" :key="role.roleKey" :label="role.roleName"
                :value="role.roleKey" />
            </el-select>
            <div class="muted">留空 = 不限制角色</div>
          </template>
        </el-table-column>
        <el-table-column label="执行器" width="200">
          <template #default="{ row }">
            <el-select v-model="row.executor" class="w-full" @change="markDirty(row)">
              <el-option v-for="opt in executorOptions" :key="opt.executorId" :label="opt.name"
                :value="opt.executorId" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" icon="Document" @click="openYaml(row)">规范</el-button>
            <el-button link type="success" icon="Check" :disabled="!dirty.has(row.intentId)"
              @click="save(row)">保存</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="yamlDialog.visible" :title="`意图规范 · ${yamlDialog.intentId}`" width="820px" append-to-body>
      <el-alert class="mb-2" type="info" :closable="false" show-icon
        title="规范即契约"
        description="五要素齐备（标识 / 输入 / 编排 / 输出 / 治理）才能保存；执行器与宿主工具必须已注册，保存时强校验。" />
      <el-input v-model="yamlDialog.yaml" type="textarea" :rows="24" spellcheck="false" class="yaml-editor" />
      <template #footer>
        <div class="dialog-footer">
          <el-button :disabled="!yamlDialog.editable" @click="validateYaml">校验</el-button>
          <el-button :disabled="!yamlDialog.editable" type="primary" @click="saveYaml">保存规范</el-button>
          <el-button @click="yamlDialog.visible = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="IntentConfig">
import {
  getIntentSpecYaml,
  listExecutorOptions,
  listIntentConfig,
  listRoleOptions,
  updateIntentConfig,
  updateIntentSpec,
  validateIntentSpec
} from '@/api/intent';
import type { IntentConfigVO, IntentExecutorOption } from '@/api/intent/types';
import modal from '@/plugins/modal';

const loading = ref(false);
const list = ref<IntentConfigVO[]>([]);
const keyword = ref('');
const roleOptions = ref<Array<Record<string, any>>>([]);
const executorOptions = ref<IntentExecutorOption[]>([]);

/** 已改动但未保存的意图编号（保存按钮据此高亮） */
const dirty = ref(new Set<string>());

const yamlDialog = reactive({
  visible: false,
  intentId: '',
  yaml: '',
  editable: true
});

const filteredList = computed(() => {
  const kw = keyword.value.trim().toLowerCase();
  if (!kw) {
    return list.value;
  }
  return list.value.filter(
    item =>
      item.intentId.toLowerCase().includes(kw) ||
      (item.name ?? '').toLowerCase().includes(kw) ||
      (item.description ?? '').toLowerCase().includes(kw)
  );
});

const scopeTag = (scope?: string) => {
  if (scope === 'REMOTE') return 'warning';
  if (scope === 'COMPOSITE') return 'danger';
  return 'success';
};

/**
 * 标记为"已改动"。
 *
 * 参数用 Partial：Element Plus 的表格插槽把 row 声明为宽泛的行类型，
 * 用精确类型会报 TS2345（框架既有页面也是同样的处理方式）。
 */
const markDirty = (row: Partial<IntentConfigVO>) => {
  if (row.intentId) {
    dirty.value.add(row.intentId);
  }
};

const getList = async () => {
  loading.value = true;
  try {
    const [configRes, roleRes, executorRes] = await Promise.all([
      listIntentConfig(),
      listRoleOptions().catch(() => ({ data: { rows: [] } } as any)),
      listExecutorOptions().catch(() => ({ data: [] } as any))
    ]);
    list.value = configRes.data ?? [];
    roleOptions.value = roleRes?.data?.rows ?? [];
    executorOptions.value = executorRes?.data ?? [];
    dirty.value.clear();
  } finally {
    loading.value = false;
  }
};

const save = async (row: Partial<IntentConfigVO>) => {
  if (!row.intentId) {
    return;
  }
  await updateIntentConfig({
    intentId: row.intentId,
    enabled: row.enabled,
    roles: row.roles && row.roles.length > 0 ? row.roles : [],
    executor: row.executor,
    remark: undefined
  });
  dirty.value.delete(row.intentId);
  modal.msgSuccess(`「${row.name ?? row.intentId}」已保存并即时生效`);
};

const openYaml = async (row: Partial<IntentConfigVO>) => {
  if (!row.intentId) {
    return;
  }
  const res = await getIntentSpecYaml(row.intentId);
  yamlDialog.intentId = row.intentId;
  yamlDialog.yaml = res.data ?? '';
  yamlDialog.editable = row.source !== 'builtin';
  yamlDialog.visible = true;
};

const validateYaml = async () => {
  const res = await validateIntentSpec(yamlDialog.yaml);
  const errors = res.data ?? [];
  if (errors.length === 0) {
    modal.msgSuccess('校验通过');
    return;
  }
  modal.alertError(errors.join('\n'));
};

const saveYaml = async () => {
  await updateIntentSpec(yamlDialog.intentId, yamlDialog.yaml);
  modal.msgSuccess('意图规范已保存');
  yamlDialog.visible = false;
  await getList();
};

onMounted(() => {
  getList();
});
</script>

<style lang="scss" scoped>
.toolbar-shell {
  display: flex;
  align-items: center;
  gap: 12px;

  .table-heading {
    display: flex;
    align-items: baseline;
    gap: 10px;

    h3 {
      margin: 0;
      font-size: 15px;
    }
  }

  .panel-sub {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  .toolbar-actions {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-left: auto;

    .kw {
      width: 240px;
    }
  }
}

.intent-name {
  font-weight: 600;
}

.intent-id {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.intent-desc {
  font-size: 12px;
  color: var(--el-text-color-regular);
  margin-top: 2px;
}

.page-tag {
  margin-right: 4px;
}

.muted {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.w-full {
  width: 100%;
}

.yaml-editor :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
  line-height: 1.6;
}

.mb-2 {
  margin-bottom: 8px;
}
</style>
