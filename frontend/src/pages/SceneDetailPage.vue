<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getErrorMessage, sceneApi } from '../api'
import type { Scene } from '../types'
import CommandManager from '../components/CommandManager.vue'

const route = useRoute()
const router = useRouter()
const sceneId = Number(route.params.id)
const scene = ref<Scene>()

onMounted(async () => {
  try { scene.value = (await sceneApi.get(sceneId)).data }
  catch (error) { ElMessage.error(getErrorMessage(error)); router.push('/scenes') }
})
</script>

<template>
  <div class="detail-back"><el-button :icon="ArrowLeft" link @click="router.push('/scenes')">返回场景列表</el-button><span v-if="scene">{{ scene.name }} · {{ scene.commandCount }} 条命令</span></div>
  <CommandManager v-if="scene" :scene-id="sceneId" :title="scene.name" description="当前列表仅显示属于该场景的命令" />
</template>

<style scoped>
.detail-back { display: flex; align-items: center; gap: 14px; margin: -8px 0 14px; color: #7a879a; font-size: 12px; }
</style>
