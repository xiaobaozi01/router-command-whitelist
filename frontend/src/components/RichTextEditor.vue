<script setup lang="ts">
import { ref, watch } from 'vue'
import { EditorContent, useEditor } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'

const props = defineProps<{ modelValue: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const empty = ref(true)

const editor = useEditor({
  content: props.modelValue || '',
  extensions: [
    StarterKit.configure({
      blockquote: false,
      bulletList: false,
      code: false,
      codeBlock: false,
      hardBreak: false,
      heading: false,
      horizontalRule: false,
      link: false,
      listItem: false,
      listKeymap: false,
      orderedList: false,
      trailingNode: false,
      underline: false,
    }),
  ],
  editorProps: {
    attributes: {
      spellcheck: 'false',
      'aria-label': '命令行表达式',
    },
    handleKeyDown: (_view, event) => event.key === 'Enter',
    handlePaste: (view, event) => {
      const text = event.clipboardData?.getData('text/plain')
      if (text === undefined) return false
      view.dispatch(view.state.tr.insertText(text.replace(/\s*\r?\n\s*/g, ' ')))
      return true
    },
  },
  onCreate: ({ editor: instance }) => {
    empty.value = instance.isEmpty
  },
  onUpdate: ({ editor: instance }) => {
    empty.value = instance.isEmpty
    emit('update:modelValue', instance.isEmpty ? '' : instance.getHTML())
  },
})

const toggleFormat = (format: 'bold' | 'italic' | 'strike') => {
  const instance = editor.value
  if (!instance) return

  if (format === 'bold') instance.chain().focus().toggleBold().run()
  if (format === 'italic') instance.chain().focus().toggleItalic().run()
  if (format === 'strike') instance.chain().focus().toggleStrike().run()
}

watch(() => props.modelValue, (value) => {
  const instance = editor.value
  if (!instance) return
  const currentHtml = instance.isEmpty ? '' : instance.getHTML()
  if (currentHtml === value) return
  instance.commands.setContent(value || '', { emitUpdate: false })
  empty.value = instance.isEmpty
})
</script>

<template>
  <div class="rich-editor">
    <div class="rich-toolbar" @mousedown.prevent>
      <button type="button" title="加粗" :class="{ active: editor?.isActive('bold') }" @click="toggleFormat('bold')"><strong>B</strong></button>
      <button type="button" title="斜体" :class="{ active: editor?.isActive('italic') }" @click="toggleFormat('italic')"><em>I</em></button>
      <button type="button" title="删除线" :class="{ active: editor?.isActive('strike') }" @click="toggleFormat('strike')"><s>S</s></button>
      <span>选中文字后应用样式</span>
    </div>
    <div class="rich-content">
      <EditorContent :editor="editor" />
      <span v-if="empty" class="rich-placeholder">例如：display interface interface-name</span>
    </div>
  </div>
</template>

<style scoped>
.rich-editor { width: 100%; border: 1px solid #dcdfe6; border-radius: 7px; overflow: hidden; transition: border-color .2s; }
.rich-editor:focus-within { border-color: #3d67dc; box-shadow: 0 0 0 1px rgba(61,103,220,.08); }
.rich-toolbar { height: 38px; display: flex; align-items: center; gap: 5px; padding: 0 9px; border-bottom: 1px solid #e7ebf1; background: #f8fafc; }
.rich-toolbar button { width: 28px; height: 27px; padding: 0; border: 1px solid transparent; border-radius: 5px; color: #344158; background: transparent; cursor: pointer; }
.rich-toolbar button:hover, .rich-toolbar button.active { color: #2856d6; border-color: #b8c8ef; background: #edf2ff; }
.rich-toolbar span { margin-left: 5px; color: #929dad; font-size: 11px; }
.rich-content { position: relative; }
.rich-content :deep(.tiptap) { min-height: 78px; padding: 10px 12px; outline: none; line-height: 1.65; color: #263149; overflow-wrap: anywhere; font-synthesis: style; }
.rich-content :deep(.tiptap p) { margin: 0; }
.rich-content :deep(.tiptap em), .rich-content :deep(.tiptap i) { font-style: italic !important; }
.rich-placeholder { position: absolute; top: 10px; left: 12px; color: #a8abb2; pointer-events: none; }
</style>
