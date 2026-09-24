<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { defaultKeymap, history, historyKeymap } from '@codemirror/commands'
import { EditorState, StateField } from '@codemirror/state'
import {
  Decoration,
  EditorView,
  keymap,
  placeholder as editorPlaceholder,
  type DecorationSet,
} from '@codemirror/view'
import { findRegexBracketMatch } from '../bracketMatching'

const props = withDefaults(defineProps<{
  modelValue: string
  placeholder?: string
}>(), {
  placeholder: '',
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  change: []
  blur: []
}>()

const editorHost = ref<HTMLDivElement>()
let editorView: EditorView | undefined

const buildBracketDecorations = (state: EditorState): DecorationSet => {
  const selection = state.selection.main
  const match = findRegexBracketMatch(state.doc.toString(), selection.from, selection.to)
  if (!match) return Decoration.none

  const bracketMark = Decoration.mark({ class: 'cm-regex-bracket-match' })
  return Decoration.set(
    [match.active, match.matching]
      .sort((left, right) => left - right)
      .map(position => bracketMark.range(position, position + 1)),
  )
}

const bracketMatchingField = StateField.define<DecorationSet>({
  create: buildBracketDecorations,
  update: (_decorations, transaction) => buildBracketDecorations(transaction.state),
  provide: field => EditorView.decorations.from(field),
})

onMounted(() => {
  if (!editorHost.value) return

  editorView = new EditorView({
    parent: editorHost.value,
    state: EditorState.create({
      doc: props.modelValue,
      extensions: [
        history(),
        keymap.of([...defaultKeymap, ...historyKeymap]),
        EditorView.lineWrapping,
        EditorView.contentAttributes.of({
          'aria-label': '正则表达式',
          spellcheck: 'false',
          autocapitalize: 'off',
          autocomplete: 'off',
        }),
        editorPlaceholder(props.placeholder),
        bracketMatchingField,
        EditorView.domEventHandlers({
          blur: () => {
            emit('blur')
            return false
          },
        }),
        EditorView.updateListener.of(update => {
          if (!update.docChanged) return
          emit('update:modelValue', update.state.doc.toString())
          emit('change')
        }),
      ],
    }),
  })
})

watch(() => props.modelValue, value => {
  if (!editorView || value === editorView.state.doc.toString()) return

  const cursor = Math.min(editorView.state.selection.main.head, value.length)
  editorView.dispatch({
    changes: { from: 0, to: editorView.state.doc.length, insert: value },
    selection: { anchor: cursor },
  })
})

onBeforeUnmount(() => editorView?.destroy())

const focus = () => editorView?.focus()

const replaceSelection = (text: string) => {
  if (!editorView) return false

  const selection = editorView.state.selection.main
  const cursor = selection.from + text.length
  editorView.dispatch({
    changes: { from: selection.from, to: selection.to, insert: text },
    selection: { anchor: cursor },
    scrollIntoView: true,
  })
  editorView.focus()
  return true
}

defineExpose({ focus, replaceSelection })
</script>

<template>
  <div ref="editorHost" class="regex-editor"></div>
</template>

<style scoped>
.regex-editor {
  box-sizing: border-box;
  width: 100%;
  height: 108px;
  min-height: 108px;
  overflow: hidden;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  background: #fff;
  resize: vertical;
}

.regex-editor:focus-within {
  border-color: #2856d6;
}

.regex-editor :deep(.cm-editor) {
  height: 100%;
  color: #263149;
  background: transparent;
  font: 12px/1.7 "SFMono-Regular", Consolas, monospace;
}

.regex-editor :deep(.cm-editor.cm-focused) {
  outline: none;
}

.regex-editor :deep(.cm-scroller) {
  overflow: auto;
  font-family: inherit;
  line-height: inherit;
}

.regex-editor :deep(.cm-content) {
  min-height: 86px;
  padding: 10px 12px;
  caret-color: #263149;
}

.regex-editor :deep(.cm-line) {
  padding: 0;
}

.regex-editor :deep(.cm-gutters) {
  display: none;
}

.regex-editor :deep(.cm-placeholder) {
  color: #a8abb2;
  font-style: normal;
}

.regex-editor :deep(.cm-selectionBackground),
.regex-editor :deep(.cm-content ::selection) {
  background: #cfe0ff !important;
}

.regex-editor :deep(.cm-regex-bracket-match) {
  border-radius: 3px;
  color: #163f9c;
  background: #ffe58f;
  box-shadow: 0 0 0 1px #e7bd37;
}
</style>
