<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { getErrorMessage, viewApi } from '../api'
import { isAdmin } from '../auth'
import { formatDateTime } from '../dateTime'
import type { ViewDefinition } from '../types'
import PageHeader from '../components/PageHeader.vue'

const loading = ref(false)
const records = ref<ViewDefinition[]>([])
const total = ref(0)
const query = reactive({ current: 1, size: 10, keyword: '', sortField: '', sortOrder: '' })
const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref<number>()
const form = reactive({ name: '' })
const formRef = ref<FormInstance>()

const load = async () => {
  loading.value = true
  try { const { data } = await viewApi.page(query); records.value = data.records; total.value = data.total }
  catch (error) { ElMessage.error(getErrorMessage(error)) }
  finally { loading.value = false }
}
const search = () => { query.current = 1; load() }
const sort = ({ prop, order }: { prop: string; order: string | null }) => {
  query.sortField = order ? prop : ''
  query.sortOrder = order === 'ascending' ? 'asc' : order === 'descending' ? 'desc' : ''
  query.current = 1
  load()
}
const rowIndex = (index: number) => index + 1
const openCreate = () => { editingId.value = undefined; form.name = ''; dialogVisible.value = true }
const openEdit = (row: ViewDefinition) => { editingId.value = row.id; form.name = row.name; dialogVisible.value = true }
const save = async () => {
  await formRef.value?.validate(); saving.value = true
  try {
    if (editingId.value) await viewApi.update(editingId.value, form.name); else await viewApi.create(form.name)
    ElMessage.success(editingId.value ? '视图已更新' : '视图已创建'); dialogVisible.value = false; load()
  } catch (error) { ElMessage.error(getErrorMessage(error)) } finally { saving.value = false }
}
const remove = async (row: ViewDefinition) => {
  try {
    await ElMessageBox.confirm(`确定删除视图“${row.name}”吗？`, '删除视图', { type: 'warning' })
    await viewApi.remove(row.id); ElMessage.success('视图已删除'); load()
  } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(getErrorMessage(error)) }
}
onMounted(load)
</script>

<template>
  <section class="page-card">
    <div class="page-toolbar">
      <PageHeader title="视图列表" description="维护华为设备命令执行前后的 CLI 视图" />
      <el-button v-if="isAdmin" type="primary" :icon="Plus" @click="openCreate">新建视图</el-button>
    </div>
    <div class="page-toolbar">
      <div class="filters">
        <el-input v-model="query.keyword" clearable spellcheck="false" placeholder="搜索视图名称" style="width: 280px" @keyup.enter="search"><template #prefix><el-icon><Search /></el-icon></template></el-input>
        <el-button @click="search">查询</el-button>
      </div>
    </div>
    <div class="table-wrap">
      <el-table v-loading="loading" :data="records" @sort-change="sort">
        <el-table-column type="index" label="序号" width="70" align="center" :index="rowIndex" />
        <el-table-column prop="name" label="视图名称" min-width="280" />
        <el-table-column prop="commandCount" label="被命令引用" width="150"><template #default="{ row }"><el-tag :type="row.commandCount ? 'warning' : 'info'" effect="plain">{{ row.commandCount }} 条</el-tag></template></el-table-column>
        <el-table-column prop="createdBy" label="创建人" width="120" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="创建时间" width="170" sortable="custom"><template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template></el-table-column>
        <el-table-column prop="updatedBy" label="修改人" width="120" show-overflow-tooltip />
        <el-table-column prop="updatedAt" label="修改时间" width="170" sortable="custom"><template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template></el-table-column>
        <el-table-column v-if="isAdmin" label="操作" width="150" align="right"><template #default="{ row }"><el-button link type="primary" @click="openEdit(row)">编辑</el-button><el-button link type="danger" @click="remove(row)">删除</el-button></template></el-table-column>
      </el-table>
    </div>
    <div class="pagination-row"><el-pagination v-model:current-page="query.current" v-model:page-size="query.size" layout="total, sizes, prev, pager, next" :total="total" @change="load" /></div>
  </section>
  <el-dialog v-if="isAdmin" v-model="dialogVisible" :title="editingId ? '编辑视图' : '新建视图'" width="460px" destroy-on-close>
    <el-form ref="formRef" :model="form" label-position="top"><el-form-item label="视图名称" prop="name" :rules="[{ required: true, whitespace: true, message: '请输入视图名称' }]"><el-input v-model="form.name" maxlength="100" show-word-limit spellcheck="false" placeholder="例如：系统视图" @keyup.enter="save" /></el-form-item></el-form>
    <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
  </el-dialog>
</template>
