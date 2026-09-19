export interface PageResponse<T> {
  records: T[]
  total: number
  current: number
  size: number
  pages: number
}

export interface OptionItem {
  id: number
  name: string
}

export interface Scene extends OptionItem {
  commandCount: number
  createdAt: string
  updatedAt: string
}

export interface ViewDefinition extends OptionItem {
  commandCount: number
  createdAt: string
  updatedAt: string
}

export interface RegexFragment {
  id: number
  name: string
  description: string
  pattern: string
  common: boolean
  referenceCount: number
  createdAt: string
  updatedAt: string
}

export interface CommandRule {
  id: number
  expressionHtml: string
  expressionText: string
  description: string
  regexTemplate: string
  expandedRegex: string
  currentViews: OptionItem[]
  targetView?: OptionItem
  scenes: OptionItem[]
  createdAt: string
  updatedAt: string
}

export interface CommandPayload {
  expressionHtml: string
  description: string
  regexTemplate: string
  currentViewIds: number[]
  targetViewId?: number
  sceneIds: number[]
}

export interface TestLineResult {
  lineNumber: number
  command: string
  matched: boolean
}

export interface RegexPreview {
  valid: boolean
  expandedRegex?: string
  error?: string
  results: TestLineResult[]
}
