import axios from 'axios'
import { ElMessage } from 'element-plus'

// 全站请求共用地址、超时和后端要求的请求头；成功时直接返回业务数据。
export const api = axios.create({ baseURL: '/api', timeout: 20000, headers: { 'X-Requested-With': 'XMLHttpRequest' } })
api.interceptors.response.use(response => response.data, error => {
  if (!error.config?.silent) ElMessage.error(error.response?.data?.message || '服务连接失败，请确认后端已启动')
  // 登录过期统一通知路由回到登录页；silent 用于启动时安静地检查登录状态。
  if (error.response?.status === 401 && !error.config?.silent) window.dispatchEvent(new Event('auth:expired'))
  return Promise.reject(error)
})

export function reportUnexpectedError(error) {
  // 接口错误已由拦截器提示；请求发出前的脚本错误也必须让用户看得见。
  if (axios.isAxiosError(error)) return
  console.error('业务操作失败', error)
  ElMessage.error(error instanceof Error ? error.message : '操作未完成，请刷新页面后重试')
}

export function download(content, name, type = 'text/csv;charset=utf-8') {
  const url = URL.createObjectURL(new Blob([content], { type }))
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = name
  anchor.click()
  // 留出浏览器开始下载的时间，再释放临时地址，避免长时间占用内存。
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

export function exportCsv(rows, columns, name) {
  const escape = value => {
    let text = String(value ?? '')
    // 阻止表格软件把用户输入识别成公式；双引号转义用于保留逗号、换行等内容。
    if (/^[=+@-]/.test(text)) text = "'" + text
    return '"' + text.replaceAll('"', '""') + '"'
  }
  const content = [columns.map(column => escape(column.label)).join(','), ...rows.map(row => columns.map(column => escape(typeof column.value === 'function' ? column.value(row) : row[column.value])).join(','))].join('\r\n')
  // 加 UTF-8 标记，便于 Excel 正确识别中文。
  download('\ufeff' + content, name)
}
