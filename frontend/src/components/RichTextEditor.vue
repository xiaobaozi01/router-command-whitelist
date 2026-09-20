<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'

const props = defineProps<{ modelValue: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const editor = ref<HTMLDivElement>()
let savedRange: Range | undefined

const normalizedHtml = (html: string) => html
  .replace(/<b(?:\s[^>]*)?>/gi, '<strong>')
  .replace(/<\/b>/gi, '</strong>')
  .replace(/<i(?:\s[^>]*)?>/gi, '<em>')
  .replace(/<\/i>/gi, '</em>')
  .replace(/<strike(?:\s[^>]*)?>/gi, '<s>')
  .replace(/<\/strike>/gi, '</s>')
  .replace(/&nbsp;/gi, ' ')

const syncValue = () => {
  if (!editor.value) return
  emit('update:modelValue', normalizedHtml(editor.value.innerHTML))
}

const saveSelection = () => {
  const selection = window.getSelection()
  if (!editor.value || !selection?.rangeCount) return
  const range = selection.getRangeAt(0)
  if (editor.value.contains(range.commonAncestorContainer)) savedRange = range.cloneRange()
}

const moveCaretAfterFormat = (command: 'bold' | 'italic' | 'strikeThrough') => {
  const selection = window.getSelection()
  if (!editor.value || !selection?.rangeCount) return

  const tagNames = {
    bold: ['B', 'STRONG'],
    italic: ['I', 'EM'],
    strikeThrough: ['S', 'STRIKE'],
  }[command]
  const range = selection.getRangeAt(0)
  let node: Node | null = range.endContainer
  let formattedElement: HTMLElement | undefined

  while (node && node !== editor.value) {
    if (node instanceof HTMLElement && tagNames.includes(node.tagName)) {
      formattedElement = node
    }
    node = node.parentNode
  }

  const caret = document.createRange()
  if (formattedElement?.parentNode) {
    caret.setStartAfter(formattedElement)
  } else {
    caret.setStart(range.endContainer, range.endOffset)
  }
  caret.collapse(true)
  selection.removeAllRanges()
  selection.addRange(caret)
  if (document.queryCommandState(command)) {
    document.execCommand(command, false, '')
  }
  savedRange = selection.rangeCount ? selection.getRangeAt(0).cloneRange() : caret.cloneRange()
}

const format = (command: 'bold' | 'italic' | 'strikeThrough') => {
  if (!editor.value) return
  editor.value.focus()
  if (savedRange) {
    const selection = window.getSelection()
    selection?.removeAllRanges()
    selection?.addRange(savedRange)
  }
  document.execCommand('styleWithCSS', false, 'false')
  document.execCommand(command, false, '')
  moveCaretAfterFormat(command)
  syncValue()
}

const onPaste = (event: ClipboardEvent) => {
  event.preventDefault()
  const text = (event.clipboardData?.getData('text/plain') ?? '').replace(/\s*\r?\n\s*/g, ' ')
  document.execCommand('insertText', false, text)
  syncValue()
}

const normalizeEditor = () => {
  if (!editor.value) return
  const html = normalizedHtml(editor.value.innerHTML)
  if (editor.value.innerHTML !== html) editor.value.innerHTML = html
  emit('update:modelValue', html)
}

onMounted(() => {
  if (editor.value) editor.value.innerHTML = props.modelValue || ''
})

watch(() => props.modelValue, async (value) => {
  await nextTick()
  if (editor.value && document.activeElement !== editor.value && editor.value.innerHTML !== value) {
    editor.value.innerHTML = value || ''
  }
})
</script>

<template>
  <div class="rich-editor">
    <div class="rich-toolbar" @mousedown.prevent>
      <button type="button" title="加粗" @click="format('bold')"><strong>B</strong></button>
      <button type="button" title="斜体" @click="format('italic')"><em>I</em></button>
      <button type="button" title="删除线" @click="format('strikeThrough')"><s>S</s></button>
      <span>选中文字后应用样式</span>
    </div>
    <div
      ref="editor"
      class="rich-content"
      contenteditable="true"
      spellcheck="false"
      data-placeholder="例如：display interface interface-name"
      @input="syncValue"
      @blur="normalizeEditor"
      @paste="onPaste"
      @mouseup="saveSelection"
      @keyup="saveSelection"
      @keydown.enter.prevent
    ></div>
  </div>
</template>

<style scoped>
.rich-editor { width: 100%; border: 1px solid #dcdfe6; border-radius: 7px; overflow: hidden; transition: border-color .2s; }
.rich-editor:focus-within { border-color: #3d67dc; box-shadow: 0 0 0 1px rgba(61,103,220,.08); }
.rich-toolbar { height: 38px; display: flex; align-items: center; gap: 5px; padding: 0 9px; border-bottom: 1px solid #e7ebf1; background: #f8fafc; }
.rich-toolbar button { width: 28px; height: 27px; padding: 0; border: 1px solid transparent; border-radius: 5px; color: #344158; background: transparent; cursor: pointer; }
.rich-toolbar button:hover { color: #2856d6; border-color: #d7e0f4; background: white; }
.rich-toolbar span { margin-left: 5px; color: #929dad; font-size: 11px; }
.rich-content { min-height: 78px; padding: 10px 12px; outline: none; line-height: 1.65; color: #263149; overflow-wrap: anywhere; font-synthesis: style; }
.rich-content:empty::before { content: attr(data-placeholder); color: #a8abb2; pointer-events: none; }
.rich-content :deep(em), .rich-content :deep(i) { font-style: italic !important; }
</style>
