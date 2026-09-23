import axios from 'axios'
import type {
  CommandPayload,
  CommandRule,
  CurrentUser,
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
  create: (name: string) => http.post<ViewDefinition>('/views', { name }),
  update: (id: number, name: string) => http.put<ViewDefinition>(`/views/${id}`, { name }),
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
  get: (id: number) => http.get<CommandRule>(`/commands/${id}`),
  create: (payload: CommandPayload) => http.post<CommandRule>('/commands', payload),
  update: (id: number, payload: CommandPayload) => http.put<CommandRule>(`/commands/${id}`, payload),
  remove: (id: number) => http.delete(`/commands/${id}`),
  preview: (regexTemplate: string, matchStart: boolean, matchEnd: boolean, testText: string) =>
    http.post<RegexPreview>('/commands/regex-preview', { regexTemplate, matchStart, matchEnd, testText }),
}
