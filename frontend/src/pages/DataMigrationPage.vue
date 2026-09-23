<script setup lang="ts">
import { ref } from 'vue'
import { Download, UploadFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { dataMigrationApi, getErrorMessage } from '../api'
import type { DataMigrationSummary } from '../types'
import PageHeader from '../components/PageHeader.vue'

const fileInput = ref<HTMLInputElement>()
const selectedFile = ref<File>()
const summary = ref<DataMigrationSummary>()
const exporting = ref(false)
const validating = ref(false)
const importing = ref(false)

const countItems: Array<{ key: keyof DataMigrationSummary; label: string }> = [
  { key: 'commands', label: '命令行' },
  { key: 'scenes', label: '场景' },
  { key: 'views', label: '视图' },
  { key: 'regexFragments', label: '正则片段' },
  { key: 'commandSceneRelations', label: '命令-场景关联' },
  { key: 'commandViewRelations', label: '命令-视图关联' },
]

const formatBytes = (size: number) => {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

const exportData = async () => {
  exporting.value = true
  try {
    const { data } = await dataMigrationApi.exportData()
    const url = URL.createObjectURL(data)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = 'asset-data.zip'
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    URL.revokeObjectURL(url)
    ElMessage.success('数据包已导出')
  } catch (error) {
    ElMessage.error(getErrorMessage(error))
  } finally {
    exporting.value = false
  }
}

const chooseFile = () => {
  if (fileInput.value) fileInput.value.value = ''
  fileInput.value?.click()
}

const selectFile = async (event: Event) => {
  const file = (event.target as HTMLInputElement).files?.[0]
  selectedFile.value = undefined
  summary.value = undefined
  if (!file) return
  if (!file.name.toLowerCase().endsWith('.zip')) {
    ElMessage.error('请选择 ZIP 数据包')
    return
  }
  if (file.size > 100 * 1024 * 1024) {
    ElMessage.error('数据包不能超过 100 MB')
    return
  }
  selectedFile.value = file
  validating.value = true
  try {
    const { data } = await dataMigrationApi.validate(file)
    summary.value = data
    ElMessage.success('数据包校验通过')
  } catch (error) {
    selectedFile.value = undefined
    ElMessage.error(getErrorMessage(error))
  } finally {
    validating.value = false
  }
}

const importData = async () => {
  if (!selectedFile.value || !summary.value) return
  try {
    await ElMessageBox.confirm(
      `将以数据包中的 ${summary.value.commands} 条命令及相关配置覆盖当前全部业务数据，人员数据不受影响。是否继续？`,
      '确认全量导入',
      { type: 'warning', confirmButtonText: '覆盖并导入', cancelButtonText: '取消' },
    )
    importing.value = true
    const { data } = await dataMigrationApi.importData(selectedFile.value)
    summary.value = data
    ElMessage.success('数据导入完成')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(getErrorMessage(error))
  } finally {
    importing.value = false
  }
}
</script>

<template>
  <section class="page-card">
    <div class="page-toolbar">
      <PageHeader title="数据迁移" description="导出和导入全部命令白名单配置，人员数据不会迁移" />
    </div>

    <div class="migration-grid">
      <article class="migration-panel">
        <div class="panel-icon export-icon"><el-icon><Download /></el-icon></div>
        <div>
          <h3>导出数据包</h3>
          <p>生成 <code>asset-data.zip</code>，内含按 ID 固定分片的 JSONL 文件。</p>
          <el-button type="primary" :icon="Download" :loading="exporting" @click="exportData">导出全部数据</el-button>
        </div>
      </article>

      <article class="migration-panel">
        <div class="panel-icon import-icon"><el-icon><UploadFilled /></el-icon></div>
        <div class="panel-content">
          <h3>导入数据包</h3>
          <p>选择由本系统导出的 ZIP，系统会在覆盖数据前先完整校验。</p>
          <input ref="fileInput" class="hidden-input" type="file" accept=".zip,application/zip" @change="selectFile">
          <div class="import-actions">
            <el-button :icon="UploadFilled" :loading="validating" @click="chooseFile">选择数据包</el-button>
            <el-button type="danger" :disabled="!summary" :loading="importing" @click="importData">全量覆盖导入</el-button>
          </div>
          <div v-if="selectedFile" class="selected-file">
            <strong>{{ selectedFile.name }}</strong>
            <span>{{ formatBytes(selectedFile.size) }}</span>
          </div>
        </div>
      </article>
    </div>

    <div v-if="summary" class="summary-block">
      <h3>数据包内容</h3>
      <div class="summary-grid">
        <div v-for="item in countItems" :key="item.key" class="summary-item">
          <strong>{{ summary[item.key] }}</strong>
          <span>{{ item.label }}</span>
        </div>
      </div>
    </div>

    <div class="git-guide">
      <h3>在 GitHub 中查看数据变更</h3>
      <ol>
        <li>将 <code>asset-data.zip</code> 解压到 Git 仓库。</li>
        <li>覆盖仓库中原有的 <code>asset-data/</code> 目录。</li>
        <li>提交解压后的 JSONL 文件，GitHub 会按行显示每条资产的变更。</li>
      </ol>
      <p class="warning-text">导入会覆盖命令行、场景、视图、正则片段及关联关系，建议先导出当前数据作为备份。</p>
    </div>
  </section>
</template>

<style scoped>
.migration-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; padding: 24px 22px; }
.migration-panel { display: flex; gap: 18px; min-height: 190px; padding: 24px; border: 1px solid #e4eaf2; border-radius: 12px; background: #fafbfd; }
.migration-panel h3, .summary-block h3, .git-guide h3 { margin: 0 0 9px; font-size: 16px; }
.migration-panel p { min-height: 44px; margin: 0 0 20px; color: #6c788e; font-size: 13px; line-height: 1.7; }
.panel-icon { flex: none; width: 44px; height: 44px; display: grid; place-items: center; border-radius: 11px; font-size: 21px; }
.export-icon { color: #2856d6; background: #eaf0ff; }
.import-icon { color: #26815e; background: #e8f7f0; }
.panel-content { min-width: 0; flex: 1; }
.hidden-input { display: none; }
.import-actions { display: flex; gap: 10px; }
.selected-file { display: flex; justify-content: space-between; gap: 12px; margin-top: 15px; padding: 10px 12px; border-radius: 7px; color: #536078; background: #fff; font-size: 12px; }
.selected-file strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.selected-file span { flex: none; color: #8b96a8; }
.summary-block { padding: 22px; border-top: 1px solid #edf0f5; }
.summary-grid { display: grid; grid-template-columns: repeat(6, 1fr); gap: 10px; }
.summary-item { padding: 15px; border: 1px solid #e7ebf2; border-radius: 9px; background: #fff; }
.summary-item strong, .summary-item span { display: block; }
.summary-item strong { margin-bottom: 5px; color: #2856d6; font-size: 21px; }
.summary-item span { color: #778398; font-size: 12px; }
.git-guide { padding: 22px; border-top: 1px solid #edf0f5; background: #fbfcfe; }
.git-guide ol { margin: 12px 0 0; padding-left: 22px; color: #536078; font-size: 13px; line-height: 2; }
.git-guide code, .migration-panel code { padding: 2px 5px; border-radius: 4px; color: #35415a; background: #eef1f6; font-family: "SFMono-Regular", Consolas, monospace; }
.warning-text { margin: 14px 0 0; color: #b46717; font-size: 12px; }
@media (max-width: 1320px) {
  .summary-grid { grid-template-columns: repeat(3, 1fr); }
}
</style>
