<script setup lang="ts">
import type { CommandApprovalSnapshot } from '../types'

defineProps<{ title: string; snapshot: CommandApprovalSnapshot }>()
</script>

<template>
  <section class="snapshot-card">
    <h3>{{ title }}</h3>
    <div class="snapshot-expression command-rich" v-html="snapshot.expressionHtml"></div>
    <div class="snapshot-row"><span>命令描述</span><div>{{ snapshot.description || '—' }}</div></div>
    <div class="snapshot-row"><span>匹配正则</span><code>{{ snapshot.expandedRegex }}</code></div>
    <div class="snapshot-row"><span>所在视图</span><div class="approval-tags"><el-tag v-for="item in snapshot.currentViews" :key="item.id" size="small" effect="plain">{{ item.name }}</el-tag></div></div>
    <div class="snapshot-row"><span>进入视图</span><div>{{ snapshot.targetView?.name ?? '不切换' }}</div></div>
    <div class="snapshot-row"><span>所属场景</span><div class="approval-tags"><el-tag v-for="item in snapshot.scenes" :key="item.id" size="small" effect="plain">{{ item.name }}</el-tag></div></div>
  </section>
</template>

<style scoped>
.snapshot-card { min-width: 0; padding: 16px; border: 1px solid #e2e8f1; border-radius: 9px; background: #fafbfd; }
.snapshot-card h3 { margin: 0 0 14px; font-size: 15px; }
.snapshot-expression { min-height: 24px; margin-bottom: 12px; padding: 10px; border-radius: 6px; background: #fff; }
.snapshot-row { display: grid; grid-template-columns: 84px minmax(0, 1fr); gap: 10px; padding: 8px 0; border-top: 1px solid #e7ebf2; }
.snapshot-row > span { color: #7b8799; font-size: 12px; }
.snapshot-row > div, .snapshot-row > code { min-width: 0; color: #344054; word-break: break-all; }
.snapshot-row > code { font: 12px/1.6 "SFMono-Regular", Consolas, monospace; }
.approval-tags { display: flex; flex-wrap: wrap; gap: 5px; }
</style>
