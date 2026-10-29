import axios from 'axios'
import { ElMessage } from 'element-plus'

export const api = axios.create({ baseURL: '/api', timeout: 20000, headers: { 'X-Requested-With': 'XMLHttpRequest' } })
api.interceptors.response.use(response => response.data, error => {
  if (!error.config?.silent) ElMessage.error(error.response?.data?.message || '服务连接失败，请确认后端已启动')
  if (error.response?.status === 401 && !error.config?.silent) window.dispatchEvent(new Event('auth:expired'))
  return Promise.reject(error)
})

export function download(content, name, type = 'text/csv;charset=utf-8') {
  const url = URL.createObjectURL(new Blob([content], { type }))
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = name
  anchor.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

export function exportCsv(rows, columns, name) {
  const escape = value => {
    let text = String(value ?? '')
    if (/^[=+@-]/.test(text)) text = "'" + text
    return '"' + text.replaceAll('"', '""') + '"'
  }
  const content = [columns.map(column => escape(column.label)).join(','), ...rows.map(row => columns.map(column => escape(typeof column.value === 'function' ? column.value(row) : row[column.value])).join(','))].join('\r\n')
  download('\ufeff' + content, name)
}
