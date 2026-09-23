<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { authApi, getErrorMessage } from '../api'
import PageHeader from '../components/PageHeader.vue'

const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })
const rules: FormRules = {
  currentPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 100, message: '密码长度应为6至100个字符', trigger: 'blur' },
  ],
  confirmPassword: [{
    validator: (_rule, value: string, callback) => {
      if (!value) callback(new Error('请再次输入新密码'))
      else if (value !== form.newPassword) callback(new Error('两次输入的新密码不一致'))
      else callback()
    },
    trigger: 'blur',
  }],
}

const save = async () => {
  await formRef.value?.validate()
  saving.value = true
  try {
    await authApi.changePassword(form.currentPassword, form.newPassword)
    ElMessage.success('密码已修改')
    Object.assign(form, { currentPassword: '', newPassword: '', confirmPassword: '' })
    formRef.value?.clearValidate()
  } catch (error) { ElMessage.error(getErrorMessage(error)) }
  finally { saving.value = false }
}
</script>

<template>
  <section class="page-card password-page">
    <div class="page-toolbar"><PageHeader title="修改密码" description="修改当前登录账号的密码" /></div>
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="password-form">
      <el-form-item label="原密码" prop="currentPassword"><el-input v-model="form.currentPassword" type="password" show-password autocomplete="current-password" /></el-form-item>
      <el-form-item label="新密码" prop="newPassword"><el-input v-model="form.newPassword" type="password" show-password autocomplete="new-password" /></el-form-item>
      <el-form-item label="确认新密码" prop="confirmPassword"><el-input v-model="form.confirmPassword" type="password" show-password autocomplete="new-password" @keyup.enter="save" /></el-form-item>
      <el-button type="primary" :loading="saving" @click="save">确认修改</el-button>
    </el-form>
  </section>
</template>

<style scoped>
.password-page { max-width: 680px; }
.password-form { max-width: 430px; padding: 26px 22px 32px; }
</style>
