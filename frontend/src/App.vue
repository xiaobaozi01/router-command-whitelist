<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Collection, Connection, DataAnalysis, FolderOpened, Tickets, UserFilled } from '@element-plus/icons-vue'
import { isAdmin } from './auth'
import UserAccountMenu from './components/UserAccountMenu.vue'

const route = useRoute()
const activeMenu = computed(() => route.path.startsWith('/scenes/') ? '/scenes' : route.path)
</script>

<template>
  <router-view v-if="route.meta.public" />
  <el-container v-else class="app-shell">
    <el-aside width="232px" class="sidebar">
      <div class="brand">
        <div class="brand-mark">CLI</div>
        <div>
          <strong>命令白名单</strong>
          <span>Huawei Router</span>
        </div>
      </div>
      <el-menu :default-active="activeMenu" router class="nav-menu">
        <el-menu-item index="/commands">
          <el-icon><Tickets /></el-icon>
          <span>命令行管理</span>
        </el-menu-item>
        <el-menu-item index="/scenes">
          <el-icon><Collection /></el-icon>
          <span>场景管理</span>
        </el-menu-item>
        <el-menu-item index="/views">
          <el-icon><Connection /></el-icon>
          <span>视图管理</span>
        </el-menu-item>
        <el-menu-item index="/fragments">
          <el-icon><DataAnalysis /></el-icon>
          <span>正则片段</span>
        </el-menu-item>
        <el-menu-item v-if="isAdmin" index="/users">
          <el-icon><UserFilled /></el-icon>
          <span>人员管理</span>
        </el-menu-item>
        <el-menu-item v-if="isAdmin" index="/data-migration">
          <el-icon><FolderOpened /></el-icon>
          <span>数据迁移</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="topbar">
        <div>
          <h1>{{ route.meta.title }}</h1>
          <p>管理路由器命令行正则白名单</p>
        </div>
        <UserAccountMenu />
      </el-header>
      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>
