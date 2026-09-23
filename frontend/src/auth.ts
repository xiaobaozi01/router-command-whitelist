import { computed, ref } from 'vue'
import { authApi } from './api'
import type { CurrentUser } from './types'

export const currentUser = ref<CurrentUser>()
let initialized = false

export const isAdmin = computed(() => currentUser.value?.role === 'ADMIN')
export const canEditCommands = computed(() => ['ADMIN', 'DEVELOPER'].includes(currentUser.value?.role ?? ''))

export const loadCurrentUser = async () => {
  if (initialized) return currentUser.value
  try {
    currentUser.value = (await authApi.me()).data
  } catch {
    currentUser.value = undefined
  } finally {
    initialized = true
  }
  return currentUser.value
}

export const setCurrentUser = (user?: CurrentUser) => {
  currentUser.value = user
  initialized = true
}
