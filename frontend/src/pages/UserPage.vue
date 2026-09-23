<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { getErrorMessage, userApi } from '../api'
import type { ManagedUser } from '../types'
import { formatDateTime } from '../dateTime'
import PageHeader from '../components/PageHeader.vue'

const loading = ref(false)
const records = ref<ManagedUser[]>([])
const total = ref(0)
const query = reactive({ current: 1, size: 10, keyword: '' })
const dialogVisible = ref(false)
const resetVisible = ref(false)
const saving = ref(false)
const editingId = ref<number>()
const resettingUser = ref<ManagedUser>()
const formRef = ref<FormInstance>()
const resetFormRef = ref<FormInstance>()
const form = reactive<{ username: string; displayName: string; role: ManagedUser['role']; password: string }>({ username: '', displayName: '', role: 'USER', password: '' })
const resetForm = reactive({ newPassword: '', confirmPassword: '' })
const roleLabels = { DEVELOPER: '开发人员', USER: '普通用户' }

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }, { pattern: /^[A-Za-z][A-Za-z0-9_.-]*$/, message: '须以字母开头，只能包含字母、数字、点、横线和下划线', trigger: 'blur' }],
  displayName: [{ required: true, whitespace: true, message: '请输入姓名', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
  password: [{ required: true, message: '请输入初始密码', trigger: 'blur' }, { min: 6, max: 100, message: '密码长度应为6至100个字符', trigger: 'blur' }],
}
const resetRules: FormRules = {
  newPassword: [{ required: true, message: '请输入新密码', trigger: 'blur' }, { min: 6, max: 100, message: '密码长度应为6至100个字符', trigger: 'blur' }],
  confirmPassword: [{
    validator: (_rule, value: string, callback) => {
      if (!value) callback(new Error('请再次输入新密码'))
      else if (value !== resetForm.newPassword) callback(new Error('两次输入的密码不一致'))
      else callback()
    },
    trigger: 'blur',
  }],
}

const load = async () => {
  loading.value = true
  try { const { data } = await userApi.page(query); records.value = data.records; total.value = data.total }
  catch (error) { ElMessage.error(getErrorMessage(error)) }
  finally { loading.value = false }
}
const search = () => { query.current = 1; load() }
const rowIndex = (index: number) => index + 1
const openCreate = () => { editingId.value = undefined; Object.assign(form, { username: '', displayName: '', role: 'USER', password: '' }); dialogVisible.value = true }
const openEdit = (row: ManagedUser) => { editingId.value = row.id; Object.assign(form, { username: row.username, displayName: row.displayName, role: row.role, password: '' }); dialogVisible.value = true }
const save = async () => {
  await formRef.value?.validate()
  saving.value = true
  try {
    if (editingId.value) await userApi.update(editingId.value, { displayName: form.displayName, role: form.role })
    else await userApi.create(form)
    ElMessage.success(editingId.value ? '用户已更新' : '用户已创建')
    dialogVisible.value = false
    load()
  } catch (error) { ElMessage.error(getErrorMessage(error)) }
  finally { saving.value = false }
}
const openReset = (row: ManagedUser) => { resettingUser.value = row; Object.assign(resetForm, { newPassword: '', confirmPassword: '' }); resetVisible.value = true }
const saveReset = async () => {
  await resetFormRef.value?.validate()
  if (!resettingUser.value) return
  saving.value = true
  try {
    await userApi.resetPassword(resettingUser.value.id, resetForm.newPassword)
    ElMessage.success('密码已重置')
    resetVisible.value = false
  } catch (error) { ElMessage.error(getErrorMessage(error)) }
  finally { saving.value = false }
}
const remove = async (row: ManagedUser) => {
  try {
    await ElMessageBox.confirm(`确定删除用户“${row.displayName}（${row.username}）”吗？`, '删除用户', { type: 'warning' })
    await userApi.remove(row.id)
    ElMessage.success('用户已删除')
    load()
  } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(getErrorMessage(error)) }
}

onMounted(load)
</script>

<template>
  <section class="page-card">
    <div class="page-toolbar">
      <PageHeader title="人员列表" description="维护开发人员和普通用户；管理员账号由后端配置管理" />
      <el-button type="primary" :icon="Plus" @click="openCreate">新增用户</el-button>
    </div>
    <div class="page-toolbar">
      <div class="filters">
        <el-input v-model="query.keyword" clearable spellcheck="false" placeholder="搜索用户名或姓名" style="width: 280px" @keyup.enter="search"><template #prefix><el-icon><Search /></el-icon></template></el-input>
        <el-button @click="search">查询</el-button>
      </div>
    </div>
    <div class="table-wrap">
      <el-table v-loading="loading" :data="records" row-key="id">
        <el-table-column type="index" label="序号" width="70" align="center" :index="rowIndex" />
        <el-table-column prop="username" label="用户名" min-width="180" />
        <el-table-column prop="displayName" label="姓名" min-width="180" />
        <el-table-column label="角色" width="140"><template #default="{ row }"><el-tag :type="row.role === 'DEVELOPER' ? 'primary' : 'info'" effect="plain">{{ roleLabels[row.role as ManagedUser['role']] }}</el-tag></template></el-table-column>
        <el-table-column label="创建时间" width="170"><template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template></el-table-column>
        <el-table-column label="修改时间" width="170"><template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template></el-table-column>
        <el-table-column label="操作" width="230" align="right"><template #default="{ row }"><el-button link type="primary" @click="openEdit(row)">编辑</el-button><el-button link @click="openReset(row)">重置密码</el-button><el-button link type="danger" @click="remove(row)">删除</el-button></template></el-table-column>
      </el-table>
    </div>
    <div class="pagination-row"><el-pagination v-model:current-page="query.current" v-model:page-size="query.size" layout="total, sizes, prev, pager, next" :total="total" @change="load" /></div>
  </section>

  <el-dialog v-model="dialogVisible" :title="editingId ? '编辑用户' : '新增用户'" width="500px" destroy-on-close>
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item label="用户名" prop="username"><el-input v-model="form.username" :disabled="!!editingId" maxlength="64" spellcheck="false" /></el-form-item>
      <el-form-item label="姓名" prop="displayName"><el-input v-model="form.displayName" maxlength="100" /></el-form-item>
      <el-form-item label="角色" prop="role"><el-select v-model="form.role" style="width: 100%"><el-option label="开发人员" value="DEVELOPER" /><el-option label="普通用户" value="USER" /></el-select></el-form-item>
      <el-form-item v-if="!editingId" label="初始密码" prop="password"><el-input v-model="form.password" type="password" show-password autocomplete="new-password" /></el-form-item>
    </el-form>
    <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
  </el-dialog>

  <el-dialog v-model="resetVisible" :title="`重置 ${resettingUser?.displayName ?? ''} 的密码`" width="440px" destroy-on-close>
    <el-form ref="resetFormRef" :model="resetForm" :rules="resetRules" label-position="top">
      <el-form-item label="新密码" prop="newPassword"><el-input v-model="resetForm.newPassword" type="password" show-password autocomplete="new-password" /></el-form-item>
      <el-form-item label="确认新密码" prop="confirmPassword"><el-input v-model="resetForm.confirmPassword" type="password" show-password autocomplete="new-password" @keyup.enter="saveReset" /></el-form-item>
    </el-form>
    <template #footer><el-button @click="resetVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveReset">确认重置</el-button></template>
  </el-dialog>
</template>
