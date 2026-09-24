export interface PageResponse<T> {
  records: T[]
  total: number
  current: number
  size: number
  pages: number
}

export type UserRole = 'ADMIN' | 'DEVELOPER' | 'USER'

export interface CurrentUser {
  id?: number
  username: string
  displayName: string
  role: UserRole
  passwordChangeable: boolean
}

export interface ManagedUser {
  id: number
  username: string
  displayName: string
  role: Exclude<UserRole, 'ADMIN'>
  createdAt: string
  updatedAt: string
}

export interface OptionItem {
  id: number
  name: string
}

export interface Scene extends OptionItem {
  commandCount: number
  createdBy: string
  updatedBy: string
  createdAt: string
  updatedAt: string
}

export interface ViewDefinition extends OptionItem {
  commandCount: number
  createdBy: string
  updatedBy: string
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
  createdBy: string
  updatedBy: string
  createdAt: string
  updatedAt: string
}

export interface CommandRule {
  id: number
  expressionHtml: string
  expressionText: string
  description: string
  regexTemplate: string
  matchStart: boolean
  matchEnd: boolean
  expandedRegex: string
  currentViews: OptionItem[]
  targetView?: OptionItem
  scenes: OptionItem[]
  createdBy: string
  updatedBy: string
  createdAt: string
  updatedAt: string
  version: number
}

export interface CommandAuditUsers {
  creators: string[]
  updaters: string[]
}

export interface CommandPayload {
  expressionHtml: string
  description: string
  regexTemplate: string
  matchStart: boolean
  matchEnd: boolean
  currentViewIds: number[]
  targetViewId?: number
  sceneIds: number[]
  version?: number
  changeReason?: string
}

export type CommandAuditAction = 'CREATE' | 'UPDATE' | 'DELETE' | 'FRAGMENT_IMPACT'

export interface CommandAuditSnapshot {
  expressionHtml: string
  expressionText: string
  regexTemplate: string
  matchStart: boolean
  matchEnd: boolean
  expandedRegex: string
  currentViews: OptionItem[]
  targetView?: OptionItem
  scenes: OptionItem[]
}

export interface CommandAuditEvent {
  id: number
  commandId: number
  action: CommandAuditAction
  actorUserId?: number
  actorUsername: string
  actorDisplayName: string
  occurredAt: string
  changeReason: string
  changedFields: string[]
  beforeSnapshot?: CommandAuditSnapshot
  afterSnapshot?: CommandAuditSnapshot
  source: 'WEB' | 'SYSTEM'
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

export interface DataMigrationSummary {
  regexFragments: number
  scenes: number
  views: number
  commands: number
  commandSceneRelations: number
  commandViewRelations: number
}

export interface GitSyncStatus {
  enabled: boolean
  repositoryUrl?: string
  branch: string
}

export interface GitSyncResult {
  changed: boolean
  commitId: string
  changedFiles: number
  message: string
  data: DataMigrationSummary
}
