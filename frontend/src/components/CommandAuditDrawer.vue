<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { commandApi, getErrorMessage } from '../api'
import { formatDateTime } from '../dateTime'
import type { CommandAuditEvent, CommandAuditSnapshot, CommandRule } from '../types'

const props = defineProps<{ modelValue: boolean; command?: CommandRule }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

const loading = ref(false)
const events = ref<CommandAuditEvent[]>([])
const total = ref(0)
const current = ref(1)
const size = ref(20)

const fieldLabels: Record<string, string> = {
  expression: '命令行表达式',
  regexTemplate: '正则模板',
  matchStart: '开头匹配',
  matchEnd: '结尾匹配',
  expandedRegex: '实际正则',
  currentViews: '命令所在视图',
  targetView: '命令进入视图',
  scenes: '所属场景',
}

const actionLabels: Record<string, string> = {
  CREATE: '创建命令',
  UPDATE: '修改命令',
  DELETE: '删除命令',
  FRAGMENT_IMPACT: '正则片段影响',
}

const valueOf = (snapshot: CommandAuditSnapshot | undefined, field: string) => {
  if (!snapshot) return '—'
  switch (field) {
    case 'expression': return snapshot.expressionText || '（空）'
    case 'regexTemplate': return snapshot.regexTemplate || '（空）'
    case 'matchStart': return snapshot.matchStart ? '是' : '否'
    case 'matchEnd': return snapshot.matchEnd ? '是' : '否'
    case 'expandedRegex': return snapshot.expandedRegex || '（空）'
    case 'currentViews': return snapshot.currentViews.map(item => item.name).join('、') || '无'
    case 'targetView': return snapshot.targetView?.name ?? '不切换'
    case 'scenes': return snapshot.scenes.map(item => item.name).join('、') || '无'
    default: return '—'
  }
}

const load = async () => {
  loading.value = true
  try {
    if (props.command) {
      events.value = (await commandApi.auditEvents(props.command.id)).data
      total.value = events.value.length
    } else {
      const result = (await commandApi.auditEventPage({ current: current.value, size: size.value })).data
      events.value = result.records
      total.value = result.total
    }
  }
  catch (error) { ElMessage.error(getErrorMessage(error)) }
  finally { loading.value = false }
}

watch(() => props.modelValue, open => {
  if (open) { current.value = 1; load() }
  else events.value = []
})
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    size="680px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template #header>
      <div class="drawer-title">
        <span>{{ command ? '变更记录 ·' : '命令审计日志' }}</span>
        <span v-if="command" class="command-rich-title" v-html="command.expressionHtml"></span>
      </div>
    </template>
    <div v-loading="loading" class="audit-list">
      <el-empty v-if="!loading && !events.length" description="暂无变更记录" />
      <article v-for="event in events" :key="event.id" class="audit-card">
        <header>
          <div>
            <el-tag size="small" effect="plain">{{ actionLabels[event.action] ?? event.action }}</el-tag>
            <span
              v-if="!command"
              class="command-name"
              v-html="event.afterSnapshot?.expressionHtml ?? event.beforeSnapshot?.expressionHtml ?? `命令 #${event.commandId}`"
            ></span>
            <strong>{{ event.actorDisplayName }}</strong>
            <span class="username">{{ event.actorUsername }}</span>
          </div>
          <time>{{ formatDateTime(event.occurredAt) }}</time>
        </header>
        <div class="reason"><span>原因</span>{{ event.changeReason }}</div>
        <div class="changes">
          <div v-for="field in event.changedFields" :key="field" class="change-row">
            <strong>{{ fieldLabels[field] ?? field }}</strong>
            <div class="change-value before">
              <span>修改前</span>
              <div
                v-if="field === 'expression' && event.beforeSnapshot"
                class="rich-expression"
                v-html="event.beforeSnapshot.expressionHtml"
              ></div>
              <code v-else>{{ valueOf(event.beforeSnapshot, field) }}</code>
            </div>
            <div class="change-value after">
              <span>修改后</span>
              <div
                v-if="field === 'expression' && event.afterSnapshot"
                class="rich-expression"
                v-html="event.afterSnapshot.expressionHtml"
              ></div>
              <code v-else>{{ valueOf(event.afterSnapshot, field) }}</code>
            </div>
          </div>
        </div>
      </article>
      <el-pagination
        v-if="!command && total > size"
        v-model:current-page="current"
        v-model:page-size="size"
        layout="total, prev, pager, next"
        :total="total"
        @change="load"
      />
    </div>
  </el-drawer>
</template>

<style scoped>
.drawer-title { display: flex; align-items: center; gap: 6px; min-width: 0; color: #303133; font-size: 16px; line-height: 1.5; }
.command-rich-title { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.command-rich-title :deep(p) { display: inline; margin: 0; }
.audit-list { min-height: 180px; }
.audit-card { margin-bottom: 16px; padding: 16px; border: 1px solid #e3e8ef; border-radius: 8px; background: #fff; }
.audit-card header { display: flex; justify-content: space-between; gap: 16px; padding-bottom: 12px; border-bottom: 1px solid #eef1f5; }
.audit-card header > div { display: flex; align-items: center; gap: 8px; min-width: 0; }
.audit-card time, .username { color: #8490a2; font-size: 12px; }
.command-name { max-width: 180px; overflow: hidden; color: #3f4b5c; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.command-name :deep(p) { display: inline; margin: 0; }
.reason { display: grid; grid-template-columns: 48px 1fr; gap: 8px; padding: 12px 0; font-size: 13px; }
.reason span { color: #8490a2; }
.changes { display: grid; gap: 10px; }
.change-row { display: grid; grid-template-columns: 110px 1fr 1fr; gap: 10px; align-items: stretch; }
.change-row > strong { padding-top: 8px; font-size: 13px; }
.change-value { min-width: 0; padding: 7px 9px; border-radius: 6px; background: #f7f8fa; }
.change-value span { display: block; margin-bottom: 4px; color: #8994a5; font-size: 11px; }
.change-value code { display: block; overflow-wrap: anywhere; white-space: pre-wrap; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12px; line-height: 1.45; }
.rich-expression { overflow-wrap: anywhere; font-size: 13px; line-height: 1.45; }
.rich-expression :deep(p) { margin: 0; }
.change-value.before { background: #fff5f4; }
.change-value.after { background: #f1faf6; }
</style>
