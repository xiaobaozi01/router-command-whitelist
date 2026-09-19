import axios from 'axios'
import type {
  CommandPayload,
  CommandRule,
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

http.interceptors.response.use((response) => {
  const body = response.data as Partial<ApiResponse<unknown>> | undefined
  if (body && typeof body === 'object' && typeof body.code === 'number' && 'data' in body) {
    response.data = body.data
  }
  return response
})

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
  create: (payload: Omit<RegexFragment, 'id' | 'referenceCount' | 'createdAt' | 'updatedAt'>) =>
    http.post<RegexFragment>('/fragments', payload),
  update: (id: number, payload: Omit<RegexFragment, 'id' | 'referenceCount' | 'createdAt' | 'updatedAt'>) =>
    http.put<RegexFragment>(`/fragments/${id}`, payload),
  remove: (id: number) => http.delete(`/fragments/${id}`),
}

export const commandApi = {
  page: (params: Record<string, unknown>) => http.get<PageResponse<CommandRule>>('/commands', { params }),
  get: (id: number) => http.get<CommandRule>(`/commands/${id}`),
  create: (payload: CommandPayload) => http.post<CommandRule>('/commands', payload),
  update: (id: number, payload: CommandPayload) => http.put<CommandRule>(`/commands/${id}`, payload),
  remove: (id: number) => http.delete(`/commands/${id}`),
  preview: (regexTemplate: string, testText: string) =>
    http.post<RegexPreview>('/commands/regex-preview', { regexTemplate, testText }),
}
