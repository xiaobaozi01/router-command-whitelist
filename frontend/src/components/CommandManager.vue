<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Clock, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { commandApi, commandApprovalApi, getErrorMessage, sceneApi, viewApi } from '../api'
import { canEditCommands, isAdmin } from '../auth'
import type { CommandRule, OptionItem } from '../types'
import CommandEditorDialog from './CommandEditorDialog.vue'
import CommandAuditDrawer from './CommandAuditDrawer.vue'
import PageHeader from './PageHeader.vue'

const props = withDefaults(defineProps<{ sceneId?: number; title?: string; description?: string }>(), {
  title: '命令行列表',
  description: '维护可执行命令、适用视图、目标视图和整行匹配正则',
})

const loading = ref(false)
const records = ref<CommandRule[]>([])
const total = ref(0)
const scenes = ref<OptionItem[]>([])
const views = ref<OptionItem[]>([])
const dialogVisible = ref(false)
const editing = ref<CommandRule>()
const auditing = ref<CommandRule>()
const auditVisible = ref(false)
const query = reactive({
  current: 1,
  size: 10,
  keyword: '',
  regexKeyword: '',
  currentViewId: undefined as number | undefined,
  targetViewId: undefined as number | undefined,
  sceneId: props.sceneId,
})

const loadOptions = async () => {
  try {
    const [sceneResult, viewResult] = await Promise.all([
      sceneApi.options(), viewApi.options(),
    ])
    scenes.value = sceneResult.data
    views.value = viewResult.data
  }
  catch (error) { ElMessage.error(getErrorMessage(error)) }
}
const load = async () => {
  loading.value = true
  try {
    const params = Object.fromEntries(Object.entries(query).filter(([, value]) => value !== '' && value !== undefined))
    const { data } = await commandApi.page(params)
    records.value = data.records; total.value = data.total
  } catch (error) { ElMessage.error(getErrorMessage(error)) } finally { loading.value = false }
}
const refresh = () => { load(); loadOptions() }
const search = () => { query.current = 1; load() }
const reset = () => { Object.assign(query, { current: 1, keyword: '', regexKeyword: '', currentViewId: undefined, targetViewId: undefined, sceneId: props.sceneId }); load() }
const rowIndex = (index: number) => index + 1
const openCreate = () => { editing.value = undefined; dialogVisible.value = true }
const openEdit = (row: CommandRule) => { editing.value = row; dialogVisible.value = true }
const openAudit = (row: CommandRule) => { auditing.value = row; auditVisible.value = true }
const openAllAudits = () => { auditing.value = undefined; auditVisible.value = true }
const remove = async (row: CommandRule) => {
  try {
    const { value } = await ElMessageBox.prompt(
      isAdmin.value
        ? `确定删除命令“${row.expressionText}”吗？删除后命令不可恢复，请填写删除原因。`
        : `确定提交命令“${row.expressionText}”的删除申请吗？管理员审批通过后才会删除。`,
      isAdmin.value ? '删除命令' : '申请删除命令',
      {
        type: 'warning',
        confirmButtonText: isAdmin.value ? '确认删除' : '提交申请',
        inputPlaceholder: '删除原因',
        inputValidator: value => Boolean(value.trim()) || '必须填写删除原因',
        inputErrorMessage: '必须填写删除原因',
      },
    )
    if (isAdmin.value) {
      await commandApi.remove(row.id, row.version, value.trim())
      ElMessage.success('命令已删除')
      refresh()
    } else {
      await commandApprovalApi.submitDelete(row.id, row.version, value.trim())
      ElMessage.success('删除申请已提交管理员审批')
    }
  } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(getErrorMessage(error)) }
}

watch(() => props.sceneId, (value) => { query.sceneId = value; query.current = 1; load() })
onMounted(() => { loadOptions(); load() })
</script>

<template>
  <section class="page-card">
    <div class="page-toolbar">
      <PageHeader :title="title" :description="description" />
      <div>
        <el-button v-if="isAdmin" :icon="Clock" @click="openAllAudits">审计日志</el-button>
        <el-button v-if="canEditCommands" type="primary" :icon="Plus" @click="openCreate">新建命令</el-button>
      </div>
    </div>
    <div class="page-toolbar filter-toolbar">
      <div class="filters">
        <el-input v-model="query.keyword" clearable spellcheck="false" placeholder="搜索命令表达式或描述" style="width: 250px" @keyup.enter="search"><template #prefix><el-icon><Search /></el-icon></template></el-input>
        <el-input v-model="query.regexKeyword" clearable spellcheck="false" placeholder="搜索展开后的匹配正则" style="width: 230px" @keyup.enter="search"><template #prefix><el-icon><Search /></el-icon></template></el-input>
        <el-select v-model="query.currentViewId" clearable filterable placeholder="所在视图" style="width: 150px"><el-option v-for="item in views" :key="item.id" :label="item.name" :value="item.id" /></el-select>
        <el-select v-model="query.targetViewId" clearable filterable placeholder="进入视图" style="width: 150px"><el-option v-for="item in views" :key="item.id" :label="item.name" :value="item.id" /></el-select>
        <el-select v-if="!sceneId" v-model="query.sceneId" clearable filterable placeholder="所属场景" style="width: 160px"><el-option v-for="item in scenes" :key="item.id" :label="item.name" :value="item.id" /></el-select>
        <el-button type="primary" plain @click="search">查询</el-button>
        <el-button :icon="Refresh" @click="reset">重置</el-button>
      </div>
    </div>
    <div class="table-wrap">
      <el-table v-loading="loading" :data="records" row-key="id">
        <el-table-column type="index" label="序号" width="70" align="center" :index="rowIndex" />
        <el-table-column label="命令表达式" min-width="220"><template #default="{ row }"><div class="command-rich" v-html="row.expressionHtml"></div></template></el-table-column>
        <el-table-column label="匹配正则" min-width="260" show-overflow-tooltip><template #default="{ row }"><div class="code-text">{{ row.expandedRegex }}</div></template></el-table-column>
        <el-table-column label="所在视图" min-width="155"><template #default="{ row }"><div class="tag-list"><el-tag v-for="item in row.currentViews" :key="item.id" size="small" effect="plain">{{ item.name }}</el-tag></div></template></el-table-column>
        <el-table-column label="进入视图" min-width="120"><template #default="{ row }"><el-tag v-if="row.targetView" size="small" type="success" effect="plain">{{ row.targetView.name }}</el-tag><span v-else class="empty-hint">不切换</span></template></el-table-column>
        <el-table-column label="所属场景" min-width="155"><template #default="{ row }"><div class="tag-list"><el-tag v-for="item in row.scenes" :key="item.id" class="scene-tag" size="small" effect="light">{{ item.name }}</el-tag></div></template></el-table-column>
        <el-table-column v-if="canEditCommands" label="操作" width="200" fixed="right" align="right"><template #default="{ row }"><el-button link :icon="Clock" @click="openAudit(row)">记录</el-button><el-button link type="primary" @click="openEdit(row)">编辑</el-button><el-button link type="danger" @click="remove(row)">删除</el-button></template></el-table-column>
      </el-table>
    </div>
    <div class="pagination-row"><el-pagination v-model:current-page="query.current" v-model:page-size="query.size" layout="total, sizes, prev, pager, next" :total="total" @change="load" /></div>
  </section>

  <CommandEditorDialog v-if="canEditCommands" v-model="dialogVisible" :command="editing" :default-scene-id="sceneId" @saved="refresh" />
  <CommandAuditDrawer v-if="canEditCommands" v-model="auditVisible" :command="auditing" />
</template>

<style scoped>
.filter-toolbar { padding-top: 14px; padding-bottom: 14px; }
.scene-tag { --el-tag-bg-color: #f3efff; --el-tag-border-color: #ddd3fa; --el-tag-text-color: #6748ad; }
</style>
