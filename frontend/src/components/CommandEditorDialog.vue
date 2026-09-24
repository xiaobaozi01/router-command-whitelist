<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Check, Close, Search } from '@element-plus/icons-vue'
import { commandApi, fragmentApi, getErrorMessage, sceneApi, viewApi } from '../api'
import type { CommandPayload, CommandRule, OptionItem, RegexFragment, RegexPreview } from '../types'
import RichTextEditor from './RichTextEditor.vue'

const props = defineProps<{ modelValue: boolean; command?: CommandRule; defaultSceneId?: number }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; saved: [] }>()

const formRef = ref<FormInstance>()
const regexTextarea = ref<HTMLTextAreaElement>()
const saving = ref(false)
const loadingOptions = ref(false)
const scenes = ref<OptionItem[]>([])
const views = ref<OptionItem[]>([])
const fragments = ref<RegexFragment[]>([])
const fragmentKeyword = ref('')
const preview = ref<RegexPreview>({ valid: false, results: [] })
const previewing = ref(false)
let previewTimer: number | undefined

const form = reactive<CommandPayload & { testText: string; changeReason: string }>({
  expressionHtml: '', description: '', regexTemplate: '', matchStart: true, matchEnd: true,
  currentViewIds: [], targetViewId: undefined, sceneIds: [], testText: '', changeReason: '',
})

const sameIds = (left: number[], right: number[]) =>
  [...left].sort((a, b) => a - b).join(',') === [...right].sort((a, b) => a - b).join(',')

const criticalChanged = computed(() => {
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
      if (criticalChanged.value && !String(value ?? '').trim()) callback(new Error('修改关键字段时必须填写修改原因'))
      else callback()
    },
    trigger: 'blur',
  }],
}

const commonFragments = computed(() => fragments.value.filter(item => item.common))
const filteredFragments = computed(() => {
  const keyword = fragmentKeyword.value.trim().toUpperCase()
  if (!keyword) return fragments.value
  return fragments.value.filter(item => item.name.includes(keyword) || item.description.toUpperCase().includes(keyword))
})

const resetForm = () => {
  const command = props.command
  Object.assign(form, {
    expressionHtml: command?.expressionHtml ?? '',
    description: command?.description ?? '',
    regexTemplate: command?.regexTemplate ?? '',
    matchStart: command?.matchStart ?? true,
    matchEnd: command?.matchEnd ?? true,
    currentViewIds: command?.currentViews.map(item => item.id) ?? [],
    targetViewId: command?.targetView?.id,
    sceneIds: command?.scenes.map(item => item.id) ?? (props.defaultSceneId ? [props.defaultSceneId] : []),
    version: command?.version,
    changeReason: '',
    testText: '',
  })
  preview.value = { valid: false, results: [] }
  fragmentKeyword.value = ''
  nextTick(() => formRef.value?.clearValidate())
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

const insertFragment = async (fragment: RegexFragment) => {
  const textarea = regexTextarea.value
  const reference = `\${${fragment.name}}`
  const start = textarea?.selectionStart ?? form.regexTemplate.length
  const end = textarea?.selectionEnd ?? start
  form.regexTemplate = form.regexTemplate.slice(0, start) + reference + form.regexTemplate.slice(end)
  await nextTick()
  textarea?.focus()
  textarea?.setSelectionRange(start + reference.length, start + reference.length)
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
      version: props.command?.version,
      changeReason: criticalChanged.value ? form.changeReason.trim() : undefined,
    }
    if (props.command) await commandApi.update(props.command.id, payload)
    else await commandApi.create(payload)
    ElMessage.success(props.command ? '命令已更新' : '命令已创建')
    emit('update:modelValue', false)
    emit('saved')
  } catch (error) { ElMessage.error(getErrorMessage(error)) }
  finally { saving.value = false }
}

watch(() => props.modelValue, (open) => {
  if (open) { resetForm(); loadOptions(); nextTick(schedulePreview) }
})
watch(() => props.command, () => { if (props.modelValue) resetForm() })
</script>

<template>
  <el-dialog
    class="command-dialog"
    :model-value="modelValue"
    :title="command ? '编辑命令' : '新建命令'"
    width="1120px"
    top="4vh"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-loading="loadingOptions" class="command-editor-layout">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="command-form">
        <div class="form-grid">
          <el-form-item label="命令行表达式" prop="expressionHtml" class="wide-field">
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
          <el-form-item v-if="command && criticalChanged" label="关键字段修改原因" prop="changeReason" class="wide-field">
            <el-input
              v-model="form.changeReason"
              type="textarea"
              :rows="2"
              maxlength="500"
              show-word-limit
              placeholder="请说明本次修改的原因，保存后将写入审计记录"
            />
          </el-form-item>
        </div>

        <div class="regex-section">
          <div class="section-heading"><strong>匹配正则</strong><span>Java 正则 · 按边界设置匹配</span></div>
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
            <textarea ref="regexTextarea" v-model="form.regexTemplate" class="regex-textarea" spellcheck="false" placeholder="例如：display ip routing-table ${IPV4}" @input="schedulePreview"></textarea>
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
      <el-button type="primary" :loading="saving" @click="save">保存命令</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.command-editor-layout { display: grid; grid-template-columns: minmax(0, 1fr) 260px; gap: 22px; height: 100%; min-height: 0; }
.command-form { min-width: 0; padding-right: 8px; overflow-y: auto; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0 18px; }
.wide-field { grid-column: 1 / -1; }
.regex-section { margin-top: 2px; padding-top: 18px; border-top: 1px solid #e8edf3; }
.section-heading { display: flex; align-items: baseline; gap: 10px; margin-bottom: 10px; }
.section-heading strong { font-size: 14px; }
.section-heading span { color: #8a96a8; font-size: 11px; }
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
.regex-textarea { display: block; width: 100%; min-height: 108px; padding: 10px 12px; border: 1px solid #dcdfe6; border-radius: 6px; outline: none; resize: vertical; color: #263149; font: 12px/1.7 "SFMono-Regular", Consolas, monospace; }
.regex-textarea:focus { border-color: #2856d6; }
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
</style>
