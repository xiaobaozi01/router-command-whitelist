<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Download, Plus, Search } from '@element-plus/icons-vue'
import { getErrorMessage, sceneApi } from '../api'
import { isAdmin } from '../auth'
import { formatDateTime } from '../dateTime'
import type { Scene } from '../types'
import PageHeader from '../components/PageHeader.vue'

const router = useRouter()
const loading = ref(false)
const records = ref<Scene[]>([])
const total = ref(0)
const query = reactive({ current: 1, size: 10, keyword: '' })
const dialogVisible = ref(false)
const saving = ref(false)
const exporting = ref(false)
const selectedScenes = ref<Scene[]>([])
const editingId = ref<number>()
const form = reactive({ name: '' })
const formRef = ref<FormInstance>()

const load = async () => {
  loading.value = true
  try {
    const { data } = await sceneApi.page(query)
    records.value = data.records
    total.value = data.total
  } catch (error) { ElMessage.error(getErrorMessage(error)) }
  finally { loading.value = false }
}

const search = () => { query.current = 1; load() }
const rowIndex = (index: number) => index + 1
const openCreate = () => { editingId.value = undefined; form.name = ''; dialogVisible.value = true }
const openEdit = (row: Scene) => { editingId.value = row.id; form.name = row.name; dialogVisible.value = true }

const save = async () => {
  await formRef.value?.validate()
  saving.value = true
  try {
    if (editingId.value) await sceneApi.update(editingId.value, form.name)
    else await sceneApi.create(form.name)
    ElMessage.success(editingId.value ? '场景已更新' : '场景已创建')
    dialogVisible.value = false
    load()
  } catch (error) { ElMessage.error(getErrorMessage(error)) }
  finally { saving.value = false }
}

const remove = async (row: Scene) => {
  try {
    await ElMessageBox.confirm(`确定删除场景“${row.name}”吗？`, '删除场景', { type: 'warning' })
    await sceneApi.remove(row.id)
    ElMessage.success('场景已删除')
    load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(getErrorMessage(error))
  }
}

const exportScenes = async () => {
  if (!selectedScenes.value.length) return
  exporting.value = true
  try {
    const response = await sceneApi.export(selectedScenes.value.map(scene => scene.id))
    const disposition = response.headers['content-disposition'] as string | undefined
    const encodedName = disposition?.match(/filename\*=UTF-8''([^;]+)/i)?.[1]
    const fileName = encodedName ? decodeURIComponent(encodedName.replace(/^"|"$/g, '')) : '场景命令导出.zip'
    const url = URL.createObjectURL(response.data)
    const link = document.createElement('a')
    link.href = url
    link.download = fileName
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
    ElMessage.success(`已导出 ${selectedScenes.value.length} 个场景`)
  } catch (error) { ElMessage.error(getErrorMessage(error)) }
  finally { exporting.value = false }
}

onMounted(load)
</script>

<template>
  <section class="page-card">
    <div class="page-toolbar">
      <PageHeader title="场景列表" description="按业务用途组织命令，一条命令可以加入多个场景" />
      <div v-if="isAdmin" class="scene-actions">
        <el-button :icon="Download" :disabled="!selectedScenes.length" :loading="exporting" @click="exportScenes">导出场景</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新建场景</el-button>
      </div>
    </div>
    <div class="page-toolbar">
      <div class="filters">
        <el-input v-model="query.keyword" clearable spellcheck="false" placeholder="搜索场景名称" style="width: 280px" @keyup.enter="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button @click="search">查询</el-button>
      </div>
    </div>
    <div class="table-wrap">
      <el-table v-loading="loading" :data="records" row-key="id" @selection-change="selectedScenes = $event" @row-click="(row: Scene) => router.push(`/scenes/${row.id}`)">
        <el-table-column v-if="isAdmin" type="selection" width="52" reserve-selection />
        <el-table-column type="index" label="序号" width="70" align="center" :index="rowIndex" />
        <el-table-column prop="name" label="场景名称" min-width="240">
          <template #default="{ row }"><el-link type="primary" @click.stop="router.push(`/scenes/${row.id}`)">{{ row.name }}</el-link></template>
        </el-table-column>
        <el-table-column prop="commandCount" label="命令数量" width="150">
          <template #default="{ row }"><el-tag effect="plain">{{ row.commandCount }} 条</el-tag></template>
        </el-table-column>
        <el-table-column prop="createdBy" label="创建人" width="120" show-overflow-tooltip />
        <el-table-column label="创建时间" width="170"><template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template></el-table-column>
        <el-table-column prop="updatedBy" label="修改人" width="120" show-overflow-tooltip />
        <el-table-column label="修改时间" width="170"><template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template></el-table-column>
        <el-table-column v-if="isAdmin" label="操作" width="150" align="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click.stop="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <div class="pagination-row">
      <el-pagination v-model:current-page="query.current" v-model:page-size="query.size" layout="total, sizes, prev, pager, next" :total="total" @change="load" />
    </div>
  </section>

  <el-dialog v-if="isAdmin" v-model="dialogVisible" :title="editingId ? '编辑场景' : '新建场景'" width="460px" destroy-on-close>
    <el-form ref="formRef" :model="form" label-position="top">
      <el-form-item label="场景名称" prop="name" :rules="[{ required: true, whitespace: true, message: '请输入场景名称' }]">
        <el-input v-model="form.name" maxlength="100" show-word-limit spellcheck="false" placeholder="例如：日常巡检" @keyup.enter="save" />
      </el-form-item>
    </el-form>
    <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
  </el-dialog>
</template>

<style scoped>
.scene-actions { display: flex; gap: 10px; }
</style>
