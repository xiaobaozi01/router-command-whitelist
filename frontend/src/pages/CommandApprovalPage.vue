<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MagicStick, Refresh, Search } from '@element-plus/icons-vue'
import { commandApprovalApi, getErrorMessage } from '../api'
import { isAdmin } from '../auth'
import { formatDateTime } from '../dateTime'
import type {
  AiApprovalAnalysis,
  AiApprovalRecommendation,
  AiApprovalRiskLevel,
  CommandApproval,
  CommandApprovalStatus,
  CommandApprovalType,
} from '../types'
import PageHeader from '../components/PageHeader.vue'
import CommandApprovalSnapshotCard from '../components/CommandApprovalSnapshotCard.vue'
import CommandEditorDialog from '../components/CommandEditorDialog.vue'

const loading = ref(false)
const deciding = ref(false)
const aiAnalyzing = ref(false)
const aiAnalysis = ref<AiApprovalAnalysis>()
const aiAnalysisCache = new Map<string, AiApprovalAnalysis>()
const records = ref<CommandApproval[]>([])
const total = ref(0)
const selected = ref<CommandApproval>()
const detailVisible = ref(false)
const editorVisible = ref(false)
const editingApproval = ref<CommandApproval>()
const reviewComment = ref('')
const query = reactive({
  current: 1,
  size: 10,
  status: 'PENDING' as CommandApprovalStatus | '',
  requestType: '' as CommandApprovalType | '',
})

const typeLabels: Record<CommandApprovalType, string> = { CREATE: '新增', UPDATE: '修改', DELETE: '删除' }
const statusLabels: Record<CommandApprovalStatus, string> = {
  PENDING: '待审批', APPROVED: '已通过', REJECTED: '已驳回', CANCELLED: '已撤销',
}
const statusTag: Record<CommandApprovalStatus, 'warning' | 'success' | 'danger' | 'info'> = {
  PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger', CANCELLED: 'info',
}
const riskLabels: Record<AiApprovalRiskLevel, string> = { LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险' }
const riskTags: Record<AiApprovalRiskLevel, 'success' | 'warning' | 'danger'> = { LOW: 'success', MEDIUM: 'warning', HIGH: 'danger' }
const recommendationLabels: Record<AiApprovalRecommendation, string> = {
  APPROVE: '建议通过', REVIEW: '建议人工复核', REJECT: '建议驳回',
}
const recommendationTags: Record<AiApprovalRecommendation, 'success' | 'warning' | 'danger'> = {
  APPROVE: 'success', REVIEW: 'warning', REJECT: 'danger',
}
const pageDescription = computed(() => isAdmin.value
  ? '审批开发人员提交的命令新增、修改和删除申请'
  : '查看自己提交的命令申请及审批结果')

const load = async () => {
  loading.value = true
  try {
    const params = Object.fromEntries(Object.entries(query).filter(([, value]) => value !== ''))
    const { data } = await commandApprovalApi.page(params)
    records.value = data.records
    total.value = data.total
  } catch (error) { ElMessage.error(getErrorMessage(error)) }
  finally { loading.value = false }
}

const search = () => { query.current = 1; load() }
const reset = () => { Object.assign(query, { current: 1, status: 'PENDING', requestType: '' }); load() }
const openDetail = (row: CommandApproval) => {
  selected.value = row
  reviewComment.value = row.reviewComment ?? ''
  aiAnalysis.value = aiAnalysisCache.get(`${row.id}:${row.version}`)
  aiAnalyzing.value = false
  detailVisible.value = true
}
const analyzeWithAi = async () => {
  const approval = selected.value
  if (!approval) return
  aiAnalyzing.value = true
  try {
    const { data } = await commandApprovalApi.analyzeWithAi(approval.id)
    aiAnalysisCache.set(`${approval.id}:${approval.version}`, data)
    if (selected.value?.id === approval.id) aiAnalysis.value = data
  } catch (error) {
    ElMessage.error(getErrorMessage(error))
  } finally {
    if (selected.value?.id === approval.id) aiAnalyzing.value = false
  }
}
const decide = async (approved: boolean) => {
  if (!selected.value) return
  if (!approved && !reviewComment.value.trim()) {
    ElMessage.warning('驳回申请时必须填写原因')
    return
  }
  deciding.value = true
  try {
    if (approved) await commandApprovalApi.approve(selected.value.id, reviewComment.value.trim())
    else await commandApprovalApi.reject(selected.value.id, reviewComment.value.trim())
    ElMessage.success(approved ? '申请已通过' : '申请已驳回')
    detailVisible.value = false
    await load()
  } catch (error) { ElMessage.error(getErrorMessage(error)) }
  finally { deciding.value = false }
}

const editRequest = async (row: CommandApproval) => {
  if (row.requestType !== 'DELETE') {
    editingApproval.value = row
    detailVisible.value = false
    editorVisible.value = true
    return
  }
  try {
    const { value } = await ElMessageBox.prompt('修改该删除申请的原因。', '修改申请', {
      confirmButtonText: '保存修改',
      cancelButtonText: '取消',
      inputValue: row.changeReason,
      inputType: 'textarea',
      inputPlaceholder: '请输入删除原因',
      inputValidator: value => Boolean(value.trim()) || '必须填写删除原因',
      inputErrorMessage: '必须填写删除原因',
    })
    await commandApprovalApi.updateReason(row.id, value.trim())
    ElMessage.success('申请已修改')
    detailVisible.value = false
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(getErrorMessage(error))
  }
}

const cancelRequest = async (row: CommandApproval) => {
  try {
    await ElMessageBox.confirm('撤销后管理员将无法审批该申请，确定继续吗？', '撤销申请', {
      type: 'warning',
      confirmButtonText: '确认撤销',
      cancelButtonText: '取消',
    })
    await commandApprovalApi.cancel(row.id)
    ElMessage.success('申请已撤销')
    detailVisible.value = false
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(getErrorMessage(error))
  }
}

const requestEdited = async () => {
  editingApproval.value = undefined
  await load()
}

const snapshotTitle = (row: CommandApproval, side: 'before' | 'after') => {
  if (side === 'before') return row.requestType === 'DELETE' ? '待删除命令' : '修改前'
  return row.requestType === 'CREATE' ? '待新增命令' : '修改后'
}

onMounted(load)
</script>

<template>
  <section class="page-card">
    <div class="page-toolbar">
      <PageHeader :title="isAdmin ? '命令审批' : '我的申请'" :description="pageDescription" />
    </div>
    <div class="page-toolbar filter-toolbar">
      <div class="filters">
        <el-select v-model="query.status" clearable placeholder="审批状态" style="width: 150px">
          <el-option label="待审批" value="PENDING" />
          <el-option label="已通过" value="APPROVED" />
          <el-option label="已驳回" value="REJECTED" />
          <el-option label="已撤销" value="CANCELLED" />
        </el-select>
        <el-select v-model="query.requestType" clearable placeholder="申请类型" style="width: 150px">
          <el-option label="新增" value="CREATE" />
          <el-option label="修改" value="UPDATE" />
          <el-option label="删除" value="DELETE" />
        </el-select>
        <el-button type="primary" plain :icon="Search" @click="search">查询</el-button>
        <el-button :icon="Refresh" @click="reset">重置</el-button>
      </div>
    </div>
    <div class="table-wrap">
      <el-table v-loading="loading" :data="records" row-key="id">
        <el-table-column prop="id" label="审批单号" width="100" />
        <el-table-column label="类型" width="90"><template #default="{ row }"><el-tag effect="plain">{{ typeLabels[row.requestType as CommandApprovalType] }}</el-tag></template></el-table-column>
        <el-table-column label="命令表达式" min-width="240"><template #default="{ row }"><div class="command-rich" v-html="row.proposedSnapshot.expressionHtml"></div></template></el-table-column>
        <el-table-column v-if="isAdmin" label="申请人" width="150"><template #default="{ row }">{{ row.submitterDisplayName }}<small class="subtext">{{ row.submitterUsername }}</small></template></el-table-column>
        <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="statusTag[row.status as CommandApprovalStatus]" effect="light">{{ statusLabels[row.status as CommandApprovalStatus] }}</el-tag></template></el-table-column>
        <el-table-column label="申请时间" width="170"><template #default="{ row }">{{ formatDateTime(row.submittedAt) }}</template></el-table-column>
        <el-table-column label="审批人" width="130"><template #default="{ row }">{{ row.reviewerDisplayName || '—' }}</template></el-table-column>
        <el-table-column label="操作" :width="isAdmin ? 90 : 220" fixed="right" align="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">{{ isAdmin && row.status === 'PENDING' ? '审批' : '查看' }}</el-button>
            <template v-if="!isAdmin && row.status === 'PENDING'">
              <el-button link type="primary" @click="editRequest(row)">修改</el-button>
              <el-button link type="danger" @click="cancelRequest(row)">撤销</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <div class="pagination-row"><el-pagination v-model:current-page="query.current" v-model:page-size="query.size" layout="total, sizes, prev, pager, next" :total="total" @change="load" /></div>
  </section>

  <el-dialog v-model="detailVisible" :title="`命令审批单 #${selected?.id ?? ''}`" width="980px" top="5vh" destroy-on-close>
    <template v-if="selected">
      <el-descriptions :column="3" border class="approval-meta">
        <el-descriptions-item label="申请类型">{{ typeLabels[selected.requestType] }}</el-descriptions-item>
        <el-descriptions-item label="申请人">{{ selected.submitterDisplayName }}（{{ selected.submitterUsername }}）</el-descriptions-item>
        <el-descriptions-item label="申请时间">{{ formatDateTime(selected.submittedAt) }}</el-descriptions-item>
        <el-descriptions-item label="申请原因" :span="3">{{ selected.changeReason }}</el-descriptions-item>
        <el-descriptions-item v-if="selected.reviewedAt" label="审批结果" :span="3">
          {{ statusLabels[selected.status] }} · {{ selected.reviewerDisplayName }} · {{ formatDateTime(selected.reviewedAt) }}
          <span v-if="selected.reviewComment">：{{ selected.reviewComment }}</span>
        </el-descriptions-item>
      </el-descriptions>

      <div class="snapshot-grid" :class="{ single: selected.requestType !== 'UPDATE' }">
        <CommandApprovalSnapshotCard
          v-if="selected.requestType !== 'CREATE'"
          :title="snapshotTitle(selected, 'before')"
          :snapshot="selected.beforeSnapshot!"
        />
        <CommandApprovalSnapshotCard
          v-if="selected.requestType !== 'DELETE'"
          :title="snapshotTitle(selected, 'after')"
          :snapshot="selected.proposedSnapshot"
        />
      </div>

      <section v-if="isAdmin && selected.status === 'PENDING'" class="ai-review-panel">
        <header class="ai-review-header">
          <div>
            <h3>AI 审批助手</h3>
            <p>基于当前审批快照分析变更风险，不会自动执行审批。</p>
          </div>
          <el-button type="primary" plain :icon="MagicStick" :loading="aiAnalyzing" @click="analyzeWithAi">
            {{ aiAnalysis ? '重新分析' : '分析风险' }}
          </el-button>
        </header>

        <div v-if="aiAnalysis" class="ai-review-result">
          <div class="ai-review-tags">
            <el-tag :type="riskTags[aiAnalysis.riskLevel]" effect="dark">
              {{ riskLabels[aiAnalysis.riskLevel] }}
            </el-tag>
            <el-tag :type="recommendationTags[aiAnalysis.recommendation]" effect="plain">
              {{ recommendationLabels[aiAnalysis.recommendation] }}
            </el-tag>
          </div>
          <p class="ai-summary">{{ aiAnalysis.summary }}</p>
          <div class="ai-reason"><strong>建议理由</strong><span>{{ aiAnalysis.recommendationReason }}</span></div>
          <div v-if="aiAnalysis.riskPoints.length" class="ai-list risk-list">
            <strong>风险点</strong>
            <ul><li v-for="item in aiAnalysis.riskPoints" :key="item">{{ item }}</li></ul>
          </div>
          <div v-if="aiAnalysis.checklist.length" class="ai-list">
            <strong>审批前核对</strong>
            <ul><li v-for="item in aiAnalysis.checklist" :key="item">{{ item }}</li></ul>
          </div>
          <el-alert
            v-if="aiAnalysis.warnings.length"
            type="warning"
            :closable="false"
            show-icon
            :title="aiAnalysis.warnings.join('；')"
          />
          <p class="ai-disclaimer">AI 结论仅供参考，最终审批仍由管理员根据实际业务和设备规范决定。</p>
        </div>
        <el-empty v-else :image-size="52" description="点击“分析风险”获取结构化审批建议" />
      </section>

      <el-form v-if="isAdmin && selected.status === 'PENDING'" label-position="top" class="decision-form">
        <el-form-item label="审批意见（驳回时必填）">
          <el-input v-model="reviewComment" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="请输入审批意见" />
        </el-form-item>
      </el-form>
    </template>
    <template #footer>
      <el-button @click="detailVisible = false">关闭</el-button>
      <template v-if="isAdmin && selected?.status === 'PENDING'">
        <el-button type="danger" plain :loading="deciding" @click="decide(false)">驳回</el-button>
        <el-button type="primary" :loading="deciding" @click="decide(true)">通过</el-button>
      </template>
      <template v-else-if="!isAdmin && selected?.status === 'PENDING'">
        <el-button type="primary" plain @click="editRequest(selected)">修改申请</el-button>
        <el-button type="danger" plain @click="cancelRequest(selected)">撤销申请</el-button>
      </template>
    </template>
  </el-dialog>

  <CommandEditorDialog
    v-if="editingApproval"
    v-model="editorVisible"
    :approval="editingApproval"
    @saved="requestEdited"
  />
</template>

<style scoped>
.filter-toolbar { padding-top: 14px; padding-bottom: 14px; }
.subtext { display: block; margin-top: 2px; color: #9099a8; }
.approval-meta { margin-bottom: 20px; }
.snapshot-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.snapshot-grid.single { grid-template-columns: 1fr; }
.ai-review-panel { margin-top: 20px; padding: 18px; border: 1px solid #dce5f5; border-radius: 10px; background: #f8faff; }
.ai-review-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.ai-review-header h3 { margin: 0 0 5px; color: #303a4d; font-size: 15px; }
.ai-review-header p { margin: 0; color: #7a869a; font-size: 12px; }
.ai-review-panel :deep(.el-empty) { padding: 15px 0 0; }
.ai-review-result { display: grid; gap: 13px; margin-top: 16px; padding-top: 16px; border-top: 1px solid #e1e7f2; }
.ai-review-tags { display: flex; gap: 8px; }
.ai-summary { margin: 0; color: #35415a; font-size: 14px; line-height: 1.7; }
.ai-reason { display: grid; grid-template-columns: 74px 1fr; gap: 10px; color: #536078; font-size: 13px; line-height: 1.65; }
.ai-reason strong, .ai-list > strong { color: #35415a; }
.ai-list { display: grid; grid-template-columns: 74px 1fr; gap: 10px; color: #536078; font-size: 13px; line-height: 1.65; }
.ai-list ul { margin: 0; padding-left: 18px; }
.risk-list li::marker { color: #d96055; }
.ai-disclaimer { margin: 0; color: #8a94a6; font-size: 11px; }
.decision-form { margin-top: 20px; }
</style>
