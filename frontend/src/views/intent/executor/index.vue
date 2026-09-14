<template>
  <div class="p-2 app-container intent-executor-page">
    <el-card shadow="hover">
      <template #header>
        <div class="toolbar-shell">
          <div class="table-heading">
            <h3>执行器档案</h3>
            <span class="panel-sub">用哪个模型 · 能用哪些工具 · 几步做完 —— 保存即热更新到运行时</span>
          </div>
          <div class="toolbar-actions">
            <el-button type="primary" icon="Plus" @click="openEditor()">新增执行器</el-button>
            <el-button icon="Refresh" @click="getList">刷新</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" border :data="list" row-key="executorId">
        <el-table-column label="执行器" min-width="240">
          <template #default="{ row }">
            <div class="ex-name">{{ row.name || row.executorId }}</div>
            <div class="ex-id">{{ row.executorId }}</div>
            <div v-if="row.description" class="ex-desc">{{ row.description }}</div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.type === 'skill' ? 'warning' : 'success'" effect="plain" size="small">
              {{ row.type }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="模型" width="180">
          <template #default="{ row }">
            <span v-if="row.model">{{ row.model }}</span>
            <span v-else class="muted">（纯工具技能）</span>
          </template>
        </el-table-column>
        <el-table-column label="工具白名单" min-width="200">
          <template #default="{ row }">
            <span v-if="!row.tools || row.tools.length === 0" class="muted">全部宿主工具</span>
            <template v-else>
              <el-tag v-for="tool in row.tools.slice(0, 4)" :key="tool" size="small" effect="plain" class="tool-tag">
                {{ tool }}
              </el-tag>
              <span v-if="row.tools.length > 4" class="muted">+{{ row.tools.length - 4 }}</span>
            </template>
          </template>
        </el-table-column>
        <el-table-column label="流程" width="110" align="center">
          <template #default="{ row }">
            <span v-if="row.stepCount">步骤 {{ row.stepCount }}</span>
            <span v-else-if="row.nodeCount">节点 {{ row.nodeCount }}</span>
            <span v-else class="muted">推理循环</span>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.source === 'builtin' ? 'info' : 'success'" effect="plain" size="small">
              {{ row.source === 'builtin' ? '内置' : '后台' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" icon="Edit" @click="openEditor(row.executorId)">编辑</el-button>
            <el-button link type="danger" icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="hover" class="mt-3">
      <template #header>
        <div class="table-heading">
          <h3>宿主工具清单</h3>
          <span class="panel-sub">业务模块声明的 IntentTool Bean —— 这就是模型能调用的全部能力边界</span>
        </div>
      </template>
      <el-table :data="tools" border size="small" max-height="320">
        <el-table-column label="工具名" width="240" prop="name" />
        <el-table-column label="说明" prop="description" />
      </el-table>
    </el-card>

    <el-dialog v-model="dialog.visible" :title="dialog.title" width="860px" append-to-body>
      <el-alert class="mb-2" type="info" :closable="false" show-icon title="执行器档案（YAML）"
        description="type=agent 走推理循环；type=skill 走确定性步骤；声明 flow 走流程编排。工具名必须来自下方清单，写错保存时会被拦住。" />
      <el-input v-model="dialog.yaml" type="textarea" :rows="22" spellcheck="false" class="yaml-editor" />
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="handleValidate">校验</el-button>
          <el-button type="primary" :loading="dialog.saving" @click="handleSubmit">保存并热更新</el-button>
          <el-button @click="dialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="IntentExecutor">
import {
  addExecutor,
  delExecutor,
  getExecutorYaml,
  listIntentExecutors,
  listIntentTools,
  updateExecutor,
  validateExecutorYaml
} from '@/api/intent';
import type { IntentExecutorVO, IntentToolOption } from '@/api/intent/types';
import modal from '@/plugins/modal';

const loading = ref(false);
const list = ref<IntentExecutorVO[]>([]);
const tools = ref<IntentToolOption[]>([]);

const dialog = reactive({
  visible: false,
  title: '',
  executorId: '',
  yaml: '',
  saving: false
});

/** 新建时的骨架：把写档案这件事变得"填空即可" */
const TEMPLATE = `id: my-analyst
type: agent
name: 我的分析执行器
description: 只做账号分析的轻量执行器
model:
  provider: deepseek
  modelId: deepseek-chat
tools:
  - sys_user_query
limits:
  maxTurns: 8
  outputMaxRetries: 1
`;

const getList = async () => {
  loading.value = true;
  try {
    const [executorRes, toolRes] = await Promise.all([
      listIntentExecutors(),
      listIntentTools().catch(() => ({ data: [] } as any))
    ]);
    list.value = executorRes.data ?? [];
    tools.value = toolRes?.data ?? [];
  } finally {
    loading.value = false;
  }
};

const openEditor = async (executorId?: string) => {
  if (!executorId) {
    dialog.executorId = '';
    dialog.title = '新增执行器';
    dialog.yaml = TEMPLATE;
    dialog.visible = true;
    return;
  }
  const res = await getExecutorYaml(executorId);
  dialog.executorId = executorId;
  dialog.title = `编辑执行器 · ${executorId}`;
  dialog.yaml = res.data ?? '';
  dialog.visible = true;
};

const handleValidate = async () => {
  const res = await validateExecutorYaml(dialog.yaml);
  const errors = res.data ?? [];
  if (errors.length === 0) {
    modal.msgSuccess('校验通过');
    return;
  }
  modal.alertError(errors.join('\n'));
};

const handleSubmit = async () => {
  dialog.saving = true;
  try {
    if (dialog.executorId) {
      await updateExecutor(dialog.executorId, dialog.yaml);
    } else {
      await addExecutor(dialog.yaml);
    }
    modal.msgSuccess('已保存并热更新到运行时');
    dialog.visible = false;
    await getList();
  } finally {
    dialog.saving = false;
  }
};

const handleDelete = async (row: Partial<IntentExecutorVO>) => {
  if (!row.executorId) {
    return;
  }
  await modal.confirm(`是否确认删除执行器「${row.executorId}」？已引用它的意图会明确报"执行器未注册"，不会静默降级。`);
  await delExecutor(row.executorId);
  modal.msgSuccess('删除成功');
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
}

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
}

.ex-name {
  font-weight: 600;
}

.ex-id {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.ex-desc {
  font-size: 12px;
  color: var(--el-text-color-regular);
  margin-top: 2px;
}

.tool-tag {
  margin-right: 4px;
}

.muted {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.yaml-editor :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
  line-height: 1.6;
}

.mb-2 {
  margin-bottom: 8px;
}

.mt-3 {
  margin-top: 12px;
}
</style>
