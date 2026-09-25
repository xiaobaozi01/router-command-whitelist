import axios from 'axios'
import type {
  AiApprovalAnalysis,
  AiFormatCommandResult,
  AiGenerateRegexResult,
  AiStatus,
  CommandAuditUsers,
  CommandAuditEvent,
  CommandApproval,
  CommandPayload,
  CommandRule,
  CurrentUser,
  DataMigrationSummary,
  GitSyncResult,
  GitSyncStatus,
  ManagedUser,
  OptionItem,
  PageResponse,
  RegexFragment,
  RegexPreview,
  Scene,
  ViewDefinition,
} from './types'

export const http = axios.create({ baseURL: '/api', timeout: 10000 })

interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

http.interceptors.response.use(
  (response) => {
    const body = response.data as Partial<ApiResponse<unknown>> | undefined
    if (body && typeof body === 'object' && typeof body.code === 'number' && 'data' in body) {
      response.data = body.data
    }
    return response
  },
  async (error) => {
    if (axios.isAxiosError(error) && error.response?.data instanceof Blob && error.response.data.type.includes('json')) {
      try { error.response.data = JSON.parse(await error.response.data.text()) }
      catch { /* 保留原始响应，由统一错误处理展示网络错误 */ }
    }
    if (axios.isAxiosError(error)
      && error.response?.status === 401
      && error.config?.url !== '/auth/me'
      && window.location.pathname !== '/login') {
      window.location.assign('/login')
    }
    return Promise.reject(error)
  },
)

export const authApi = {
  login: (username: string, password: string) => http.post<CurrentUser>('/auth/login', { username, password }),
  me: () => http.get<CurrentUser>('/auth/me'),
  logout: () => http.post('/auth/logout'),
  changePassword: (currentPassword: string, newPassword: string) =>
    http.put('/auth/password', { currentPassword, newPassword }),
}

export const userApi = {
  page: (params: Record<string, unknown>) => http.get<PageResponse<ManagedUser>>('/users', { params }),
  create: (payload: { username: string; displayName: string; role: ManagedUser['role']; password: string }) =>
    http.post<ManagedUser>('/users', payload),
  update: (id: number, payload: { displayName: string; role: ManagedUser['role'] }) =>
    http.put<ManagedUser>(`/users/${id}`, payload),
  resetPassword: (id: number, newPassword: string) => http.put(`/users/${id}/password`, { newPassword }),
  remove: (id: number) => http.delete(`/users/${id}`),
}

export const getErrorMessage = (error: unknown) => {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { message?: string; fields?: Record<string, string> } | undefined
    if (data?.fields) return Object.values(data.fields)[0] ?? data.message ?? '请求失败'
    return data?.message ?? error.message
  }
  return error instanceof Error ? error.message : '请求失败'
}

export const sceneApi = {
  page: (params: Record<string, unknown>) => http.get<PageResponse<Scene>>('/scenes', { params }),
  get: (id: number) => http.get<Scene>(`/scenes/${id}`),
  options: () => http.get<OptionItem[]>('/scenes/options'),
  create: (name: string) => http.post<Scene>('/scenes', { name }),
  update: (id: number, name: string) => http.put<Scene>(`/scenes/${id}`, { name }),
  remove: (id: number) => http.delete(`/scenes/${id}`),
  export: (sceneIds: number[]) => http.post<Blob>('/scenes/export', { sceneIds }, { responseType: 'blob', timeout: 60000 }),
}

export const viewApi = {
  page: (params: Record<string, unknown>) => http.get<PageResponse<ViewDefinition>>('/views', { params }),
  options: () => http.get<OptionItem[]>('/views/options'),
  create: (payload: { name: string; displayOrder: number }) => http.post<ViewDefinition>('/views', payload),
  update: (id: number, payload: { name: string; displayOrder: number }) =>
    http.put<ViewDefinition>(`/views/${id}`, payload),
  remove: (id: number) => http.delete(`/views/${id}`),
}

export const fragmentApi = {
  page: (params: Record<string, unknown>) => http.get<PageResponse<RegexFragment>>('/fragments', { params }),
  options: () => http.get<RegexFragment[]>('/fragments/options'),
  create: (payload: Omit<RegexFragment, 'id' | 'referenceCount' | 'createdBy' | 'updatedBy' | 'createdAt' | 'updatedAt'>) =>
    http.post<RegexFragment>('/fragments', payload),
  update: (id: number, payload: Omit<RegexFragment, 'id' | 'referenceCount' | 'createdBy' | 'updatedBy' | 'createdAt' | 'updatedAt'>) =>
    http.put<RegexFragment>(`/fragments/${id}`, payload),
  remove: (id: number) => http.delete(`/fragments/${id}`),
}

export const commandApi = {
  page: (params: Record<string, unknown>) => http.get<PageResponse<CommandRule>>('/commands', { params }),
  auditUsers: () => http.get<CommandAuditUsers>('/commands/audit-users'),
  get: (id: number) => http.get<CommandRule>(`/commands/${id}`),
  auditEvents: (id: number) => http.get<CommandAuditEvent[]>(`/commands/${id}/audit-events`),
  auditEventPage: (params: { current: number; size: number }) =>
    http.get<PageResponse<CommandAuditEvent>>('/commands/audit-events', { params }),
  create: (payload: CommandPayload) => http.post<CommandRule>('/commands', payload),
  update: (id: number, payload: CommandPayload) => http.put<CommandRule>(`/commands/${id}`, payload),
  remove: (id: number, version: number, reason: string) =>
    http.delete(`/commands/${id}`, { params: { version, reason } }),
  preview: (regexTemplate: string, matchStart: boolean, matchEnd: boolean, testText: string) =>
    http.post<RegexPreview>('/commands/regex-preview', { regexTemplate, matchStart, matchEnd, testText }),
}

export const aiApi = {
  status: () => http.get<AiStatus>('/ai/status'),
  formatCommand: (expressionHtml: string, description: string, currentViewIds: number[], targetViewId?: number) =>
    http.post<AiFormatCommandResult>('/ai/format-command', {
      expressionHtml, description, currentViewIds, targetViewId,
    }, { timeout: 35000 }),
  generateRegex: (payload: {
    expressionHtml: string
    description: string
    currentRegexTemplate: string
    matchStart: boolean
    matchEnd: boolean
    currentViewIds: number[]
    targetViewId?: number
  }) => http.post<AiGenerateRegexResult>('/ai/generate-regex', payload, { timeout: 35000 }),
}

export const commandApprovalApi = {
  page: (params: Record<string, unknown>) =>
    http.get<PageResponse<CommandApproval>>('/command-approvals', { params }),
  submitCreate: (payload: CommandPayload) =>
    http.post<CommandApproval>('/command-approvals/commands', payload),
  submitUpdate: (id: number, payload: CommandPayload) =>
    http.put<CommandApproval>(`/command-approvals/commands/${id}`, payload),
  submitDelete: (id: number, version: number, reason: string) =>
    http.delete<CommandApproval>(`/command-approvals/commands/${id}`, { params: { version, reason } }),
  approve: (id: number, comment: string) =>
    http.post<CommandApproval>(`/command-approvals/${id}/approve`, { comment }),
  reject: (id: number, comment: string) =>
    http.post<CommandApproval>(`/command-approvals/${id}/reject`, { comment }),
  analyzeWithAi: (id: number) =>
    http.post<AiApprovalAnalysis>(`/command-approvals/${id}/ai-analysis`, undefined, { timeout: 35000 }),
}

const migrationForm = (file: File) => {
  const form = new FormData()
  form.append('file', file)
  return form
}

export const dataMigrationApi = {
  exportData: () => http.post<Blob>('/data-migration/export', undefined, { responseType: 'blob', timeout: 120000 }),
  validate: (file: File) => http.post<DataMigrationSummary>(
    '/data-migration/validate', migrationForm(file), { timeout: 120000 },
  ),
  importData: (file: File) => http.post<DataMigrationSummary>(
    '/data-migration/import', migrationForm(file), { timeout: 120000 },
  ),
  gitStatus: () => http.get<GitSyncStatus>('/data-migration/git/status'),
  syncToGit: () => http.post<GitSyncResult>('/data-migration/git/sync', undefined, { timeout: 180000 }),
}
