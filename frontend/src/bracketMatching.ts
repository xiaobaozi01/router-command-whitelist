import { Decoration, Extension } from '@tiptap/core'
import type { Node as ProseMirrorNode } from '@tiptap/pm/model'

const bracketPairs = new Map([
  ['(', ')'],
  ['[', ']'],
  ['{', '}'],
  ['<', '>'],
  ['（', '）'],
  ['【', '】'],
  ['｛', '｝'],
])

const openingBrackets = new Set(bracketPairs.keys())
const closingBrackets = new Set(bracketPairs.values())
const matchingOpeningBracket = new Map(Array.from(bracketPairs, ([opening, closing]) => [closing, opening]))

export interface TextBracketMatch {
  active: number
  matching: number
}

interface BracketCharacter {
  character: string
  position: number
}

const isEscaped = (text: string, index: number) => {
  let backslashes = 0
  for (let cursor = index - 1; cursor >= 0 && text[cursor] === '\\'; cursor--) backslashes++
  return backslashes % 2 === 1
}

const collectRegexBrackets = (text: string) => {
  const brackets: BracketCharacter[] = []
  let inCharacterClass = false
  for (let position = 0; position < text.length; position++) {
    const character = text[position]
    if (isEscaped(text, position)) continue

    if (inCharacterClass) {
      if (character === ']') {
        brackets.push({ character, position })
        inCharacterClass = false
      }
      continue
    }

    if (character === '[') {
      inCharacterClass = true
      brackets.push({ character, position })
    } else if (openingBrackets.has(character) || closingBrackets.has(character)) {
      brackets.push({ character, position })
    }
  }

  return brackets
}

const collectBrackets = (doc: ProseMirrorNode) => {
  const brackets: BracketCharacter[] = []

  doc.descendants((node, position) => {
    if (!node.isText || !node.text) return

    for (let offset = 0; offset < node.text.length; offset++) {
      const character = node.text[offset]
      if (openingBrackets.has(character) || closingBrackets.has(character)) {
        brackets.push({ character, position: position + offset })
      }
    }
  })

  return brackets
}

const findActiveBracketIndex = (brackets: BracketCharacter[], from: number, to: number) => {
  if (from !== to) {
    return brackets.findIndex(bracket => bracket.position === from && bracket.position + 1 === to)
  }

  const immediatelyBeforeCursor = brackets.findIndex(bracket => bracket.position + 1 === from)
  if (immediatelyBeforeCursor !== -1) return immediatelyBeforeCursor
  return brackets.findIndex(bracket => bracket.position === from)
}

const findMatchingBracketIndex = (brackets: BracketCharacter[], activeIndex: number) => {
  const active = brackets[activeIndex]
  if (!active) return -1

  if (openingBrackets.has(active.character)) {
    const expectedClosings = [bracketPairs.get(active.character)!]

    for (let index = activeIndex + 1; index < brackets.length; index++) {
      const character = brackets[index].character
      if (openingBrackets.has(character)) {
        expectedClosings.push(bracketPairs.get(character)!)
      } else if (character === expectedClosings.at(-1)) {
        expectedClosings.pop()
        if (!expectedClosings.length) return index
      } else {
        return -1
      }
    }
  } else {
    const expectedOpenings = [matchingOpeningBracket.get(active.character)!]

    for (let index = activeIndex - 1; index >= 0; index--) {
      const character = brackets[index].character
      if (closingBrackets.has(character)) {
        expectedOpenings.push(matchingOpeningBracket.get(character)!)
      } else if (character === expectedOpenings.at(-1)) {
        expectedOpenings.pop()
        if (!expectedOpenings.length) return index
      } else {
        return -1
      }
    }
  }

  return -1
}

export const findRegexBracketMatch = (
  text: string,
  selectionStart: number,
  selectionEnd: number,
): TextBracketMatch | undefined => {
  const brackets = collectRegexBrackets(text)
  const activeIndex = findActiveBracketIndex(brackets, selectionStart, selectionEnd)
  const matchingIndex = findMatchingBracketIndex(brackets, activeIndex)
  if (activeIndex === -1 || matchingIndex === -1) return undefined

  return {
    active: brackets[activeIndex].position,
    matching: brackets[matchingIndex].position,
  }
}

export const BracketMatching = Extension.create({
  name: 'bracketMatching',

  addDecorations() {
    return {
      create: ({ state }) => {
        const brackets = collectBrackets(state.doc)
        const activeIndex = findActiveBracketIndex(brackets, state.selection.from, state.selection.to)
        const matchingIndex = findMatchingBracketIndex(brackets, activeIndex)
        if (activeIndex === -1 || matchingIndex === -1) return []

        return [activeIndex, matchingIndex].map(index => {
          const { position } = brackets[index]
          return Decoration.Inline(position, position + 1, { class: 'bracket-match' })
        })
      },
      shouldUpdate: ({ tr }) => tr.docChanged || tr.selectionSet,
    }
  },
})
