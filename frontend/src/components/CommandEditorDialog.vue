<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Check, Close, CopyDocument, MagicStick, QuestionFilled, Search } from '@element-plus/icons-vue'
import { aiApi, commandApi, commandApprovalApi, fragmentApi, getErrorMessage, sceneApi, viewApi } from '../api'
import { isAdmin } from '../auth'
import type {
  AiFormatCommandResult,
  AiGenerateRegexResult,
  AiStatus,
  CommandApproval,
  CommandPayload,
  CommandRule,
  OptionItem,
  RegexFragment,
  RegexPreview,
} from '../types'
import RegexEditor from './RegexEditor.vue'
import RichTextEditor from './RichTextEditor.vue'

const props = defineProps<{
  modelValue: boolean
  command?: CommandRule
  approval?: CommandApproval
  defaultSceneId?: number
}>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; saved: [] }>()

const formRef = ref<FormInstance>()
const regexEditor = ref<InstanceType<typeof RegexEditor>>()
const saving = ref(false)
const loadingOptions = ref(false)
const scenes = ref<OptionItem[]>([])
const views = ref<OptionItem[]>([])
const fragments = ref<RegexFragment[]>([])
const fragmentKeyword = ref('')
const preview = ref<RegexPreview>({ valid: false, results: [] })
const previewing = ref(false)
const aiStatus = ref<AiStatus>({ enabled: false, available: false, protocol: '', model: '', message: '正在检查 AI 服务…' })
const aiStatusLoading = ref(false)
const aiFormatting = ref(false)
const aiGenerating = ref(false)
const aiSuggestionVisible = ref(false)
const aiSuggestionKind = ref<'format' | 'regex'>('format')
const formatOriginalHtml = ref('')
const formatSuggestion = ref<AiFormatCommandResult>()
const regexSuggestion = ref<AiGenerateRegexResult>()
let previewTimer: number | undefined

const form = reactive<CommandPayload & { testText: string; changeReason: string }>({
  expressionHtml: '', description: '', regexTemplate: '', matchStart: true, matchEnd: true,
  currentViewIds: [], targetViewId: undefined, sceneIds: [], testText: '', changeReason: '',
})

const sameIds = (left: number[], right: number[]) =>
  [...left].sort((a, b) => a - b).join(',') === [...right].sort((a, b) => a - b).join(',')

const criticalChanged = computed(() => {
  const snapshot = props.approval?.proposedSnapshot
  if (snapshot) {
    return form.expressionHtml !== snapshot.expressionHtml
      || form.regexTemplate !== snapshot.regexTemplate
      || form.matchStart !== snapshot.matchStart
      || form.matchEnd !== snapshot.matchEnd
      || (form.targetViewId ?? undefined) !== (snapshot.targetView?.id ?? undefined)
      || !sameIds(form.currentViewIds, snapshot.currentViews.map(item => item.id))
      || !sameIds(form.sceneIds, snapshot.scenes.map(item => item.id))
  }
  const command = props.command
  if (!command) return false
  return form.expressionHtml !== command.expressionHtml
    || form.regexTemplate !== command.regexTemplate
    || form.matchStart !== command.matchStart
    || form.matchEnd !== command.matchEnd
    || (form.targetViewId ?? undefined) !== (command.targetView?.id ?? undefined)
    || !sameIds(form.currentViewIds, command.currentViews.map(item => item.id))
    || !sameIds(form.sceneIds, command.scenes.map(item => item.id))
})

const requiresChangeReason = computed(() => props.approval
  ? props.approval.requestType === 'UPDATE'
  : Boolean(props.command) && (!isAdmin.value || criticalChanged.value))
const dialogTitle = computed(() => props.approval ? '修改申请' : props.command ? '编辑命令' : '新建命令')

const hasManualBoundary = computed(() => {
  const template = form.regexTemplate
  if (template.startsWith('^')) return true
  if (!template.endsWith('$')) return false
  let backslashes = 0
  for (let index = template.length - 2; index >= 0 && template[index] === '\\'; index--) backslashes++
  return backslashes % 2 === 0
})

const rules: FormRules = {
  expressionHtml: [{ required: true, message: '请输入命令行表达式', trigger: 'change' }],
  regexTemplate: [{ required: true, message: '请输入正则表达式', trigger: 'blur' }],
  currentViewIds: [{ type: 'array', required: true, min: 1, message: '至少选择一个所在视图', trigger: 'change' }],
  sceneIds: [{ type: 'array', required: true, min: 1, message: '至少选择一个所属场景', trigger: 'change' }],
  changeReason: [{
    validator: (_rule, value, callback) => {
      if (requiresChangeReason.value && !String(value ?? '').trim()) callback(new Error('请填写修改原因'))
      else callback()
    },
    trigger: 'blur',
  }],
}

const commonFragments = computed(() => fragments.value.filter(item => item.common))
const aiAvailable = computed(() => aiStatus.value.available && !aiStatusLoading.value)
const aiButtonTitle = computed(() => aiAvailable.value
  ? `${aiStatus.value.protocol}${aiStatus.value.model ? ` · ${aiStatus.value.model}` : ''}`
  : aiStatus.value.message)
const aiSuggestionTitle = computed(() => aiSuggestionKind.value === 'format' ? 'AI 格式化建议' : 'AI 正则生成建议')
const filteredFragments = computed(() => {
  const keyword = fragmentKeyword.value.trim().toUpperCase()
  if (!keyword) return fragments.value
  return fragments.value.filter(item => item.name.includes(keyword) || item.description.toUpperCase().includes(keyword))
})

const resetForm = () => {
  const command = props.command
  const snapshot = props.approval?.proposedSnapshot
  Object.assign(form, {
    expressionHtml: snapshot?.expressionHtml ?? command?.expressionHtml ?? '',
    description: snapshot?.description ?? command?.description ?? '',
    regexTemplate: snapshot?.regexTemplate ?? command?.regexTemplate ?? '',
    matchStart: snapshot?.matchStart ?? command?.matchStart ?? true,
    matchEnd: snapshot?.matchEnd ?? command?.matchEnd ?? true,
    currentViewIds: snapshot?.currentViews.map(item => item.id) ?? command?.currentViews.map(item => item.id) ?? [],
    targetViewId: snapshot?.targetView?.id ?? command?.targetView?.id,
    sceneIds: snapshot?.scenes.map(item => item.id) ?? command?.scenes.map(item => item.id)
      ?? (props.defaultSceneId ? [props.defaultSceneId] : []),
    version: props.approval?.targetCommandVersion ?? command?.version,
    changeReason: props.approval?.requestType === 'UPDATE' ? props.approval.changeReason : '',
    testText: '',
  })
  preview.value = { valid: false, results: [] }
  formatSuggestion.value = undefined
  regexSuggestion.value = undefined
  aiSuggestionVisible.value = false
  fragmentKeyword.value = ''
  nextTick(() => formRef.value?.clearValidate())
}

const loadAiStatus = async () => {
  aiStatusLoading.value = true
  try { aiStatus.value = (await aiApi.status()).data }
  catch (error) {
    aiStatus.value = { enabled: false, available: false, protocol: '', model: '', message: getErrorMessage(error) }
  }
  finally { aiStatusLoading.value = false }
}

const formatWithAi = async () => {
  if (!form.expressionHtml.trim()) {
    ElMessage.warning('请先输入命令行表达式')
    return
  }
  aiFormatting.value = true
  try {
    formatOriginalHtml.value = form.expressionHtml
    formatSuggestion.value = (await aiApi.formatCommand(
      form.expressionHtml,
      form.description,
      form.currentViewIds,
      form.targetViewId,
    )).data
    regexSuggestion.value = undefined
    aiSuggestionKind.value = 'format'
    aiSuggestionVisible.value = true
  } catch (error) { ElMessage.error(getErrorMessage(error)) }
  finally { aiFormatting.value = false }
}

const generateRegexWithAi = async () => {
  if (!form.expressionHtml.trim()) {
    ElMessage.warning('请先输入命令行表达式')
    return
  }
  aiGenerating.value = true
  try {
    regexSuggestion.value = (await aiApi.generateRegex({
      expressionHtml: form.expressionHtml,
      description: form.description,
      matchStart: form.matchStart,
      matchEnd: form.matchEnd,
      currentViewIds: form.currentViewIds,
      targetViewId: form.targetViewId,
    })).data
    formatSuggestion.value = undefined
    aiSuggestionKind.value = 'regex'
    aiSuggestionVisible.value = true
  } catch (error) { ElMessage.error(getErrorMessage(error)) }
  finally { aiGenerating.value = false }
}

const copyAiSuggestion = async (content: string, label: string) => {
  if (!content) return
  try {
    if (!navigator.clipboard?.writeText) throw new Error('Clipboard API unavailable')
    await navigator.clipboard.writeText(content)
    ElMessage.success(`${label}已复制`)
  } catch {
    const textarea = document.createElement('textarea')
    textarea.value = content
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    const copied = document.execCommand('copy')
    textarea.remove()
    if (copied) ElMessage.success(`${label}已复制`)
    else ElMessage.error('复制失败，请手动选择复制')
  }
}

const applyAiSuggestion = () => {
  if (aiSuggestionKind.value === 'format' && formatSuggestion.value) {
    form.expressionHtml = formatSuggestion.value.formattedHtml
    void nextTick(() => formRef.value?.validateField('expressionHtml').catch(() => undefined))
    ElMessage.success('已应用 AI 格式化建议')
  }
  if (aiSuggestionKind.value === 'regex' && regexSuggestion.value) {
    form.regexTemplate = regexSuggestion.value.regexTemplate
    form.testText = [...regexSuggestion.value.positiveCases, ...regexSuggestion.value.negativeCases].join('\n')
    preview.value = regexSuggestion.value.preview
    void nextTick(() => formRef.value?.validateField('regexTemplate').catch(() => undefined))
    ElMessage.success('已应用 AI 正则和测试数据')
  }
  aiSuggestionVisible.value = false
}

const loadOptions = async () => {
  loadingOptions.value = true
  try {
    const [sceneResult, viewResult, fragmentResult] = await Promise.all([
      sceneApi.options(), viewApi.options(), fragmentApi.options(),
    ])
    scenes.value = sceneResult.data
    views.value = viewResult.data
    fragments.value = fragmentResult.data
  } catch (error) { ElMessage.error(getErrorMessage(error)) }
  finally { loadingOptions.value = false }
}

const runPreview = async () => {
  if (!form.regexTemplate.trim()) {
    preview.value = { valid: false, results: [] }
    return
  }
  previewing.value = true
  try { preview.value = (await commandApi.preview(form.regexTemplate, form.matchStart, form.matchEnd, form.testText)).data }
  catch (error) { preview.value = { valid: false, error: getErrorMessage(error), results: [] } }
  finally { previewing.value = false }
}

const schedulePreview = () => {
  window.clearTimeout(previewTimer)
  previewTimer = window.setTimeout(runPreview, 350)
}

const validateRegex = () => {
  void formRef.value?.validateField('regexTemplate').catch(() => undefined)
}

const insertFragment = (fragment: RegexFragment) => {
  const reference = `\${${fragment.name}}`
  if (regexEditor.value?.replaceSelection(reference)) return

  form.regexTemplate += reference
  schedulePreview()
}

const save = async () => {
  await formRef.value?.validate()
  await runPreview()
  if (!preview.value.valid) {
    ElMessage.error(preview.value.error || '正则表达式校验失败')
    return
  }
  saving.value = true
  try {
    const payload: CommandPayload = {
      expressionHtml: form.expressionHtml,
      description: form.description,
      regexTemplate: form.regexTemplate,
      matchStart: form.matchStart,
      matchEnd: form.matchEnd,
      currentViewIds: form.currentViewIds,
      targetViewId: form.targetViewId,
      sceneIds: form.sceneIds,
      version: props.approval?.targetCommandVersion ?? props.command?.version,
      changeReason: requiresChangeReason.value ? form.changeReason.trim() : undefined,
    }
    if (props.approval) {
      await commandApprovalApi.updateRequest(props.approval.id, payload)
      ElMessage.success('申请已修改')
    } else if (isAdmin.value) {
      if (props.command) await commandApi.update(props.command.id, payload)
      else await commandApi.create(payload)
      ElMessage.success(props.command ? '命令已更新' : '命令已创建')
    } else {
      if (props.command) await commandApprovalApi.submitUpdate(props.command.id, payload)
      else await commandApprovalApi.submitCreate(payload)
      ElMessage.success('已提交管理员审批')
    }
    emit('update:modelValue', false)
    emit('saved')
  } catch (error) { ElMessage.error(getErrorMessage(error)) }
  finally { saving.value = false }
}

watch(() => props.modelValue, (open) => {
  if (open) { resetForm(); loadOptions(); loadAiStatus(); nextTick(schedulePreview) }
}, { immediate: true })
watch(() => props.command, () => { if (props.modelValue) resetForm() })
watch(() => props.approval, () => { if (props.modelValue) resetForm() })
</script>

<template>
  <el-dialog
    class="command-dialog"
    :model-value="modelValue"
    :title="dialogTitle"
    width="1120px"
    top="4vh"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-loading="loadingOptions" class="command-editor-layout">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="command-form">
        <div class="form-grid">
          <el-form-item prop="expressionHtml" class="wide-field">
            <template #label>
              <span class="field-label-row">
                <span>命令行表达式</span>
                <el-button
                  type="primary"
                  link
                  :icon="MagicStick"
                  :loading="aiFormatting"
                  :disabled="!aiAvailable"
                  :title="aiButtonTitle"
                  @click="formatWithAi"
                >AI 格式化</el-button>
              </span>
            </template>
            <RichTextEditor v-model="form.expressionHtml" />
          </el-form-item>
          <el-form-item label="命令行描述" class="wide-field">
            <el-input
              v-model="form.description"
              type="textarea"
              :rows="3"
              maxlength="1000"
              show-word-limit
              spellcheck="false"
              resize="vertical"
              placeholder="请输入命令行描述"
            />
          </el-form-item>
          <el-form-item label="命令所在视图" prop="currentViewIds">
            <el-select v-model="form.currentViewIds" multiple filterable placeholder="可多选" style="width: 100%">
              <el-option v-for="item in views" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="命令进入视图">
            <el-select v-model="form.targetViewId" clearable filterable placeholder="不切换视图可留空" style="width: 100%">
              <el-option v-for="item in views" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="所属场景" prop="sceneIds" class="wide-field">
            <el-select v-model="form.sceneIds" multiple filterable placeholder="可多选" style="width: 100%">
              <el-option v-for="item in scenes" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="requiresChangeReason" label="修改原因" prop="changeReason" class="wide-field">
            <el-input
              v-model="form.changeReason"
              type="textarea"
              :rows="2"
              maxlength="500"
              show-word-limit
              :placeholder="isAdmin ? '请说明本次修改的原因' : '请说明本次修改的原因，将随申请提交审批'"
            />
          </el-form-item>
        </div>

        <div class="regex-section">
          <div class="section-heading">
            <strong>匹配正则</strong>
            <span>Java 正则 · 按边界设置匹配 · 光标移到括号旁可查看配对</span>
            <div class="ai-heading-actions">
              <el-button
                type="primary"
                link
                :icon="MagicStick"
                :loading="aiGenerating"
                :disabled="!aiAvailable"
                :title="aiButtonTitle"
                @click="generateRegexWithAi"
              >AI 生成</el-button>
              <el-popover
                placement="bottom-end"
                trigger="click"
                :width="390"
                popper-class="ai-quality-popover"
              >
                <template #reference>
                  <el-button link :icon="QuestionFilled">了解更多</el-button>
                </template>
                <div class="ai-quality-content">
                  <strong>如何提升生成准确率</strong>
                  <p>AI 会根据命令格式和视图上下文理解命令。信息越准确、越完整，生成的正则表达式和测试数据就越可靠。</p>
                  <ul>
                    <li>命令行格式标记越准确，AI 越容易识别固定关键字、参数、可选项和不支持内容。</li>
                    <li>当前视图选择越准确，AI 越容易判断命令适用的上下文；多个视图只选择真正支持该命令的视图。</li>
                    <li>命令执行后会切换视图时，准确选择下一级视图可以帮助 AI 理解命令效果。</li>
                    <li>错误的视图信息可能降低生成质量；不确定的下一级视图可以暂时不选。</li>
                  </ul>
                  <p class="ai-quality-note">AI 结果仅作为建议，应用前请检查正则和测试数据。</p>
                </div>
              </el-popover>
            </div>
          </div>
          <div class="boundary-options">
            <span>匹配边界</span>
            <el-checkbox v-model="form.matchStart" @change="schedulePreview">开头匹配 <code>^</code></el-checkbox>
            <el-checkbox v-model="form.matchEnd" @change="schedulePreview">结尾匹配 <code>$</code></el-checkbox>
            <small>默认首尾都匹配</small>
          </div>
          <div v-if="commonFragments.length" class="quick-fragments">
            <span>常用片段</span>
            <button v-for="item in commonFragments" :key="item.id" type="button" @click="insertFragment(item)">{{ '${' + item.name + '}' }}</button>
          </div>
          <el-form-item prop="regexTemplate" class="regex-form-item">
            <RegexEditor
              ref="regexEditor"
              v-model="form.regexTemplate"
              placeholder="例如：display ip routing-table ${IPV4}"
              @change="schedulePreview"
              @blur="validateRegex"
            />
          </el-form-item>
          <div v-if="hasManualBoundary" class="boundary-warning">正则模板中不需要手动输入 ^ 或 $，请使用上方的匹配边界选项。</div>

          <div class="preview-block" :class="{ invalid: preview.error }">
            <div class="preview-label">
              <span>展开后的实际正则</span>
              <el-icon v-if="preview.valid" color="#20966f"><Check /></el-icon>
              <el-icon v-else-if="preview.error" color="#d64c4c"><Close /></el-icon>
              <small v-if="previewing">校验中…</small>
            </div>
            <code v-if="preview.valid">{{ preview.expandedRegex }}</code>
            <span v-else class="preview-error">{{ preview.error || '输入正则后自动展开并校验' }}</span>
          </div>

          <div class="test-panel">
            <div class="section-heading"><strong>多行匹配测试</strong><span>每行作为一条独立命令</span></div>
            <el-input v-model="form.testText" type="textarea" :rows="4" resize="vertical" spellcheck="false" placeholder="每行输入一条待测试命令" @input="schedulePreview" />
            <div v-if="preview.results.length" class="test-results">
              <div v-for="item in preview.results" :key="item.lineNumber" class="test-row">
                <span class="line-number">{{ item.lineNumber }}</span>
                <el-tag :type="item.matched ? 'success' : 'danger'" size="small" effect="light">{{ item.matched ? '匹配' : '不匹配' }}</el-tag>
                <code>{{ item.command || '（空行）' }}</code>
              </div>
            </div>
          </div>
        </div>
      </el-form>

      <aside class="fragment-library">
        <div class="library-title"><strong>正则片段库</strong><span>{{ fragments.length }} 个片段</span></div>
        <el-input v-model="fragmentKeyword" clearable spellcheck="false" placeholder="搜索片段" :prefix-icon="Search" />
        <div class="fragment-scroll">
          <button v-for="item in filteredFragments" :key="item.id" type="button" class="fragment-card" @click="insertFragment(item)">
            <span><code>{{ '${' + item.name + '}' }}</code><el-tag v-if="item.common" size="small" type="success" effect="plain">常用</el-tag></span>
            <small>{{ item.description }}</small>
          </button>
          <el-empty v-if="!filteredFragments.length" :image-size="54" description="没有匹配的片段" />
        </div>
      </aside>
    </div>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">{{ approval ? '保存修改' : isAdmin ? '保存命令' : '提交审批' }}</el-button>
    </template>
  </el-dialog>

  <el-dialog
    v-model="aiSuggestionVisible"
    :title="aiSuggestionTitle"
    width="720px"
    append-to-body
    destroy-on-close
  >
    <template v-if="aiSuggestionKind === 'format' && formatSuggestion">
      <div class="ai-compare-grid">
        <section>
          <div class="ai-preview-title">格式化前</div>
          <div class="ai-command-preview command-rich" v-html="formatOriginalHtml"></div>
        </section>
        <section>
          <div class="ai-preview-title">格式化后</div>
          <div class="ai-command-preview command-rich" v-html="formatSuggestion.formattedHtml"></div>
        </section>
      </div>
      <p v-if="formatSuggestion.explanation" class="ai-explanation">{{ formatSuggestion.explanation }}</p>
      <el-alert
        v-for="warning in formatSuggestion.warnings"
        :key="warning"
        class="ai-warning"
        type="warning"
        :closable="false"
        :title="warning"
      />
    </template>

    <template v-if="aiSuggestionKind === 'regex' && regexSuggestion">
      <div class="ai-supported-expression">
        <div class="ai-preview-title">删除线处理后的命令手册表达式</div>
        <div class="ai-supported-expression-html" v-html="regexSuggestion.supportedExpressionHtml"></div>
        <small>已按删除线语义移除本系统不支持的内容，请先确认该结构是否符合预期。</small>
      </div>
      <div class="ai-regex-result">
        <div class="ai-preview-title ai-copy-title">
          <span>正则模板</span>
          <el-button
            link
            type="primary"
            size="small"
            :icon="CopyDocument"
            @click="copyAiSuggestion(regexSuggestion.regexTemplate, '正则模板')"
          >复制</el-button>
        </div>
        <code>{{ regexSuggestion.regexTemplate }}</code>
      </div>
      <div class="ai-test-grid">
        <section>
          <div class="ai-preview-title ai-copy-title">
            <span>应当匹配</span>
            <el-button
              link
              type="primary"
              size="small"
              :icon="CopyDocument"
              @click="copyAiSuggestion(regexSuggestion.positiveCases.join('\n'), '应当匹配用例')"
            >复制</el-button>
          </div>
          <code v-for="item in regexSuggestion.positiveCases" :key="`positive-${item}`">{{ item }}</code>
        </section>
        <section>
          <div class="ai-preview-title ai-copy-title">
            <span>不应匹配</span>
            <el-button
              link
              type="primary"
              size="small"
              :icon="CopyDocument"
              @click="copyAiSuggestion(regexSuggestion.negativeCases.join('\n'), '不应匹配用例')"
            >复制</el-button>
          </div>
          <code v-for="item in regexSuggestion.negativeCases" :key="`negative-${item}`">{{ item }}</code>
        </section>
      </div>
      <p v-if="regexSuggestion.explanation" class="ai-explanation">{{ regexSuggestion.explanation }}</p>
      <el-alert
        v-for="warning in regexSuggestion.warnings"
        :key="warning"
        class="ai-warning"
        type="warning"
        :closable="false"
        :title="warning"
      />
    </template>

    <template #footer>
      <el-button @click="aiSuggestionVisible = false">取消</el-button>
      <el-button type="primary" @click="applyAiSuggestion">应用建议</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.command-editor-layout { display: grid; grid-template-columns: minmax(0, 1fr) 260px; gap: 22px; height: 100%; min-height: 0; }
.command-form { min-width: 0; padding-right: 8px; overflow-y: auto; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0 18px; }
.wide-field { grid-column: 1 / -1; }
.field-label-row { display: inline-flex; align-items: center; justify-content: space-between; width: calc(100% - 12px); vertical-align: top; }
.wide-field :deep(.el-form-item__label) { width: 100%; }
.regex-section { margin-top: 2px; padding-top: 18px; border-top: 1px solid #e8edf3; }
.section-heading { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.section-heading strong { font-size: 14px; }
.section-heading > span { color: #8a96a8; font-size: 11px; }
.ai-heading-actions { display: flex; align-items: center; gap: 2px; margin-left: auto; white-space: nowrap; }
:global(.ai-quality-popover .ai-quality-content > strong) { display: block; color: #344054; font-size: 14px; }
:global(.ai-quality-popover .ai-quality-content p) { margin: 9px 0; color: #667085; font-size: 12px; line-height: 1.65; }
:global(.ai-quality-popover .ai-quality-content ul) { margin: 8px 0; padding-left: 19px; color: #475467; font-size: 12px; line-height: 1.65; }
:global(.ai-quality-popover .ai-quality-content li + li) { margin-top: 5px; }
:global(.ai-quality-popover .ai-quality-content .ai-quality-note) { margin-bottom: 0; color: #8a5a12; }
.boundary-options { display: flex; align-items: center; gap: 16px; min-height: 38px; margin-bottom: 10px; padding: 5px 10px; border: 1px solid #e2e8f1; border-radius: 7px; background: #fafbfd; }
.boundary-options > span { color: #657289; font-size: 12px; font-weight: 600; }
.boundary-options code { margin-left: 3px; color: #2856d6; font: 600 12px "SFMono-Regular", Consolas, monospace; }
.boundary-options small { margin-left: auto; color: #929dad; }
.boundary-warning { margin: -2px 0 10px; color: #c47a18; font-size: 11px; }
.quick-fragments { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; margin-bottom: 9px; }
.quick-fragments > span { margin-right: 4px; color: #78849a; font-size: 11px; }
.quick-fragments button { padding: 4px 8px; border: 1px solid #dce4f2; border-radius: 5px; color: #3157ba; background: #f5f8ff; font: 11px "SFMono-Regular", Consolas, monospace; cursor: pointer; }
.quick-fragments button:hover { border-color: #91a9ec; background: #edf2ff; }
.regex-form-item { margin-bottom: 10px; }
.preview-block { padding: 10px 12px; border: 1px solid #dce8e4; border-radius: 7px; background: #f7fbfa; }
.preview-block.invalid { border-color: #f0d7d7; background: #fff9f9; }
.preview-label { display: flex; align-items: center; gap: 6px; margin-bottom: 6px; color: #6d7b8f; font-size: 11px; }
.preview-label small { margin-left: auto; }
.preview-block code { display: block; max-height: 75px; overflow: auto; color: #21644f; font: 11px/1.6 "SFMono-Regular", Consolas, monospace; word-break: break-all; }
.preview-error { color: #c14a4a; font-size: 12px; }
.test-panel { margin-top: 17px; }
.test-results { max-height: 150px; margin-top: 9px; overflow: auto; border: 1px solid #e5eaf1; border-radius: 7px; }
.test-row { display: grid; grid-template-columns: 30px 58px 1fr; align-items: center; gap: 7px; min-height: 34px; padding: 4px 9px; border-bottom: 1px solid #edf0f5; }
.test-row:last-child { border-bottom: 0; }
.line-number { color: #9aa5b5; font: 11px "SFMono-Regular", Consolas, monospace; text-align: right; }
.test-row code { overflow: hidden; color: #3b465a; font: 11px "SFMono-Regular", Consolas, monospace; text-overflow: ellipsis; white-space: nowrap; }
.fragment-library { display: flex; flex-direction: column; min-width: 0; min-height: 0; padding-left: 20px; border-left: 1px solid #e5eaf1; }
.library-title { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.library-title strong { font-size: 14px; }
.library-title span { color: #8a96a8; font-size: 11px; }
.fragment-scroll { flex: 1; min-height: 0; margin-top: 10px; padding-right: 3px; overflow-y: auto; }
.fragment-card { display: block; width: 100%; padding: 10px; margin-bottom: 7px; border: 1px solid #e2e8f1; border-radius: 8px; text-align: left; background: #fff; cursor: pointer; }
.fragment-card:hover { border-color: #9db1e8; background: #f8faff; }
.fragment-card > span { display: flex; align-items: center; justify-content: space-between; gap: 6px; }
.fragment-card code { color: #2856d6; font: 600 11px "SFMono-Regular", Consolas, monospace; }
.fragment-card small { display: block; margin-top: 6px; color: #7d899b; line-height: 1.4; }
.ai-compare-grid, .ai-test-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
.ai-preview-title { margin-bottom: 7px; color: #6d7b8f; font-size: 12px; font-weight: 600; }
.ai-copy-title { display: flex; align-items: center; justify-content: space-between; min-height: 24px; }
.ai-command-preview { min-height: 70px; padding: 12px; border: 1px solid #e2e8f1; border-radius: 7px; background: #fafbfd; overflow-wrap: anywhere; }
.ai-supported-expression { margin-bottom: 15px; padding: 12px; border: 1px solid #dce4f2; border-radius: 7px; background: #f8faff; }
.ai-supported-expression-html { color: #35415a; font: 12px/1.65 "SFMono-Regular", Consolas, monospace; overflow-wrap: anywhere; }
.ai-supported-expression-html :deep(p) { margin: 0; }
.ai-supported-expression small { display: block; margin-top: 7px; color: #7c8799; font-size: 11px; }
.ai-regex-result { padding: 12px; border: 1px solid #dce8e4; border-radius: 7px; background: #f7fbfa; }
.ai-regex-result > code { display: block; color: #21644f; font: 12px/1.6 "SFMono-Regular", Consolas, monospace; word-break: break-all; }
.ai-test-grid { margin-top: 15px; }
.ai-test-grid section { min-width: 0; padding: 10px; border: 1px solid #e5eaf1; border-radius: 7px; }
.ai-test-grid code { display: block; padding: 5px 0; color: #3b465a; font: 11px/1.5 "SFMono-Regular", Consolas, monospace; overflow-wrap: anywhere; }
.ai-explanation { margin: 14px 0 0; color: #56647a; line-height: 1.65; }
.ai-warning { margin-top: 10px; }
</style>
