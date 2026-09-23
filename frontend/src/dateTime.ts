export const formatDateTime = (value?: string) => value ? value.replace('T', ' ').slice(0, 19) : '—'
