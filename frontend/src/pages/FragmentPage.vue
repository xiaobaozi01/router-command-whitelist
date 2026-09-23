<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { fragmentApi, getErrorMessage } from '../api'
import { isAdmin } from '../auth'
import type { RegexFragment } from '../types'
import PageHeader from '../components/PageHeader.vue'

const loading = ref(false)
const records = ref<RegexFragment[]>([])
const total = ref(0)
const query = reactive({ current: 1, size: 10, keyword: '' })
const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref<number>()
const editingReferenceCount = ref(0)
const formRef = ref<FormInstance>()
const form = reactive({ name: '', description: '', pattern: '', common: false })

const load = async () => {
  loading.value = true
  try { const { data } = await fragmentApi.page(query); records.value = data.records; total.value = data.total }
  catch (error) { ElMessage.error(getErrorMessage(error)) } finally { loading.value = false }
}
const search = () => { query.current = 1; load() }
const rowIndex = (index: number) => index + 1
const openCreate = () => { editingId.value = undefined; editingReferenceCount.value = 0; Object.assign(form, { name: '', description: '', pattern: '', common: false }); dialogVisible.value = true }
const openEdit = (row: RegexFragment) => { editingId.value = row.id; editingReferenceCount.value = row.referenceCount; Object.assign(form, { name: row.name, description: row.description, pattern: row.pattern, common: row.common }); dialogVisible.value = true }
const save = async () => {
  await formRef.value?.validate(); saving.value = true
  try {
    const payload = { ...form }
    if (editingId.value) await fragmentApi.update(editingId.value, payload); else await fragmentApi.create(payload)
    ElMessage.success(editingId.value ? '正则片段已更新，引用命令将立即使用新内容' : '正则片段已创建')
    dialogVisible.value = false; load()
  } catch (error) { ElMessage.error(getErrorMessage(error)) } finally { saving.value = false }
}
const remove = async (row: RegexFragment) => {
  try {
    await ElMessageBox.confirm(`确定删除片段 \${${row.name}} 吗？`, '删除正则片段', { type: 'warning' })
    await fragmentApi.remove(row.id); ElMessage.success('正则片段已删除'); load()
  } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(getErrorMessage(error)) }
}
onMounted(load)
</script>

<template>
  <section class="page-card">
    <div class="page-toolbar">
      <PageHeader title="正则片段库" description="维护可复用的单层正则片段，片段之间不允许相互引用" />
      <el-button v-if="isAdmin" type="primary" :icon="Plus" @click="openCreate">新建片段</el-button>
    </div>
    <div class="page-toolbar">
      <div class="filters">
        <el-input v-model="query.keyword" clearable spellcheck="false" placeholder="搜索片段名称或描述" style="width: 320px" @keyup.enter="search"><template #prefix><el-icon><Search /></el-icon></template></el-input>
        <el-button @click="search">查询</el-button>
      </div>
    </div>
    <div class="table-wrap">
      <el-table v-loading="loading" :data="records">
        <el-table-column type="index" label="序号" width="70" align="center" :index="rowIndex" />
        <el-table-column label="片段" width="180"><template #default="{ row }"><span class="fragment-name">{{ '${' + row.name + '}' }}</span><el-tag v-if="row.common" size="small" effect="plain" type="success" class="common-tag">常用</el-tag></template></el-table-column>
        <el-table-column prop="description" label="描述" min-width="220" />
        <el-table-column label="正则内容" min-width="360" show-overflow-tooltip><template #default="{ row }"><span class="code-text">{{ row.pattern }}</span></template></el-table-column>
        <el-table-column prop="referenceCount" label="引用" width="90"><template #default="{ row }">{{ row.referenceCount }} 条</template></el-table-column>
        <el-table-column v-if="isAdmin" label="操作" width="150" align="right"><template #default="{ row }"><el-button link type="primary" @click="openEdit(row)">编辑</el-button><el-button link type="danger" @click="remove(row)">删除</el-button></template></el-table-column>
      </el-table>
    </div>
    <div class="pagination-row"><el-pagination v-model:current-page="query.current" v-model:page-size="query.size" layout="total, sizes, prev, pager, next" :total="total" @change="load" /></div>
  </section>

  <el-dialog v-if="isAdmin" v-model="dialogVisible" :title="editingId ? '编辑正则片段' : '新建正则片段'" width="650px" destroy-on-close>
    <p v-if="editingId" class="dialog-tip">修改内容会立即影响 {{ editingReferenceCount }} 条引用命令；被引用时不能修改名称。</p>
    <el-form ref="formRef" :model="form" label-position="top">
      <el-form-item label="片段名称" prop="name" :rules="[{ required: true, message: '请输入片段名称' }, { pattern: /^[A-Z][A-Z0-9_]*$/, message: '只能使用大写字母、数字和下划线，且以字母开头' }]">
        <el-input v-model="form.name" maxlength="64" spellcheck="false" placeholder="例如：INTERFACE_NAME"><template #prepend>${</template><template #append>}</template></el-input>
      </el-form-item>
      <el-form-item label="片段描述" prop="description" :rules="[{ required: true, whitespace: true, message: '请输入片段描述' }]"><el-input v-model="form.description" maxlength="500" show-word-limit spellcheck="false" placeholder="说明该片段可以匹配的内容" /></el-form-item>
      <el-form-item label="正则内容" prop="pattern" :rules="[{ required: true, message: '请输入正则内容' }]"><el-input v-model="form.pattern" type="textarea" :rows="5" resize="vertical" spellcheck="false" placeholder="Java 正则语法；不允许包含其他 ${片段}" class="mono-input" /></el-form-item>
      <el-form-item><el-checkbox v-model="form.common">显示在命令编辑器的常用片段区</el-checkbox></el-form-item>
    </el-form>
    <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
  </el-dialog>
</template>

<style scoped>
.fragment-name { display: inline-block; margin-right: 8px; padding: 4px 7px; border-radius: 5px; color: #2856d6; background: #edf2ff; font: 600 12px "SFMono-Regular", Consolas, monospace; }
.common-tag { vertical-align: middle; }
.mono-input :deep(textarea) { font-family: "SFMono-Regular", Consolas, monospace; font-size: 12px; line-height: 1.7; }
</style>
