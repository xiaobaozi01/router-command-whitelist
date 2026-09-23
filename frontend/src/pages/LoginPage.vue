<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { authApi, getErrorMessage } from '../api'
import { setCurrentUser } from '../auth'

const route = useRoute()
const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ username: '', password: '' })
const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

const login = async () => {
  await formRef.value?.validate()
  loading.value = true
  try {
    const { data } = await authApi.login(form.username, form.password)
    setCurrentUser(data)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/commands'
    router.replace(redirect)
  } catch (error) { ElMessage.error(getErrorMessage(error)) }
  finally { loading.value = false }
}
</script>

<template>
  <main class="login-page">
    <section class="login-card">
      <div class="login-brand"><span>CLI</span><div><strong>命令白名单</strong><small>Huawei Router</small></div></div>
      <div class="login-heading"><h1>用户登录</h1><p>登录后进入命令行正则白名单管理系统</p></div>
      <el-form ref="formRef" :model="form" :rules="rules" size="large" @submit.prevent="login">
        <el-form-item prop="username"><el-input v-model="form.username" :prefix-icon="User" placeholder="用户名" autocomplete="username" /></el-form-item>
        <el-form-item prop="password"><el-input v-model="form.password" :prefix-icon="Lock" type="password" show-password placeholder="密码" autocomplete="current-password" @keyup.enter="login" /></el-form-item>
        <el-button type="primary" :loading="loading" class="login-button" @click="login">登录</el-button>
      </el-form>
    </section>
  </main>
</template>

<style scoped>
.login-page { min-height: 100vh; display: grid; place-items: center; padding: 24px; background: radial-gradient(circle at 70% 20%, #e9efff 0, transparent 34%), #f3f6fa; }
.login-card { width: 420px; padding: 38px 42px 42px; border: 1px solid #e2e8f1; border-radius: 18px; background: rgba(255,255,255,.96); box-shadow: 0 22px 60px rgba(28,45,85,.12); }
.login-brand { display: flex; align-items: center; gap: 11px; }
.login-brand > span { width: 42px; height: 42px; display: grid; place-items: center; border-radius: 11px; color: #fff; background: #2856d6; font-size: 13px; font-weight: 800; }
.login-brand strong, .login-brand small { display: block; }
.login-brand strong { color: #182239; font-size: 16px; }
.login-brand small { margin-top: 3px; color: #919caf; font-size: 10px; letter-spacing: 1px; text-transform: uppercase; }
.login-heading { margin: 36px 0 25px; }
.login-heading h1 { margin: 0; font-size: 23px; }
.login-heading p { margin: 8px 0 0; color: #7a879b; font-size: 12px; }
.login-button { width: 100%; margin-top: 7px; }
</style>
