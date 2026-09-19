<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Collection, Connection, DataAnalysis, Tickets } from '@element-plus/icons-vue'

const route = useRoute()
const activeMenu = computed(() => route.path.startsWith('/scenes/') ? '/scenes' : route.path)
</script>

<template>
  <el-container class="app-shell">
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
      </el-menu>
      <div class="sidebar-foot">
        <span class="status-dot"></span>
        单厂商模式 · 华为
      </div>
    </el-aside>
    <el-container>
      <el-header class="topbar">
        <div>
          <h1>{{ route.meta.title }}</h1>
          <p>管理路由器命令行正则白名单</p>
        </div>
        <div class="topbar-badge">JDK 21 · H2</div>
      </el-header>
      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>
