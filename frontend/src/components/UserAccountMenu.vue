<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ArrowDown, Lock, SwitchButton } from '@element-plus/icons-vue'
import { authApi } from '../api'
import { currentUser, setCurrentUser } from '../auth'

const router = useRouter()
const roleLabels = { ADMIN: '管理员', DEVELOPER: '开发人员', USER: '普通用户' }
defineProps<{ dark?: boolean }>()

const handleCommand = async (command: string) => {
  if (command === 'password') {
    router.push('/password')
    return
  }
  if (command === 'logout') {
    try { await authApi.logout() } finally {
      setCurrentUser()
      router.replace('/login')
    }
  }
}

</script>

<template>
  <el-dropdown trigger="click" @command="handleCommand">
    <button class="account-trigger" :class="{ 'is-dark': dark }" type="button">
      <span class="account-avatar">{{ currentUser?.displayName.slice(0, 1) }}</span>
      <span class="account-copy"><strong>{{ currentUser?.displayName }}</strong><small>{{ currentUser ? roleLabels[currentUser.role] : '' }}</small></span>
      <el-icon><ArrowDown /></el-icon>
    </button>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item v-if="currentUser?.passwordChangeable" command="password" :icon="Lock">修改密码</el-dropdown-item>
        <el-dropdown-item v-else disabled :icon="Lock">密码由后端配置管理</el-dropdown-item>
        <el-dropdown-item command="logout" :icon="SwitchButton" divided>退出登录</el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>

</template>

<style scoped>
.account-trigger { display: flex; align-items: center; gap: 9px; padding: 5px 8px; border: 0; border-radius: 9px; color: #35415a; background: transparent; cursor: pointer; }
.account-trigger:hover { background: #f3f6fa; }
.account-avatar { width: 32px; height: 32px; display: grid; place-items: center; border-radius: 9px; color: #fff; background: #2856d6; font-size: 13px; font-weight: 700; }
.account-copy { min-width: 70px; text-align: left; }
.account-copy strong, .account-copy small { display: block; }
.account-copy strong { font-size: 13px; }
.account-copy small { margin-top: 2px; color: #8995a8; font-size: 10px; }
.account-trigger.is-dark { width: 100%; color: #e7ebf4; }
.account-trigger.is-dark:hover { background: rgba(255,255,255,.07); }
.account-trigger.is-dark .account-copy small { color: #8290aa; }
</style>
