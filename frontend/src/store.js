import { reactive, computed } from 'vue'
import { api } from './api'

export const state = reactive({ user: null, loading: false, updatedAt: null, vehicles: [], customers: [], orders: [], payments: [], branches: [], pricing: [], dispatch: [], maintenance: [], reminders: [], settings: {} })
export const isAdmin = computed(() => state.user?.role === 'ADMIN')
export async function refresh() {
  state.loading = true
  try { Object.assign(state, await api.get(state.user?.role === 'CUSTOMER' ? '/portal/bootstrap' : '/bootstrap')); state.updatedAt = Date.now() } finally { state.loading = false }
}
export const vehicle = id => state.vehicles.find(item => Number(item.id) === Number(id)) || {}
export const customer = id => state.customers.find(item => Number(item.id) === Number(id)) || {}
export const branch = id => state.branches.find(item => Number(item.id) === Number(id)) || {}
export const rule = id => state.pricing.find(item => Number(item.id) === Number(id)) || {}
export const payments = id => state.payments.filter(item => Number(item.order_id) === Number(id))
export const netPaid = id => payments(id).reduce((total, payment) => total + (payment.direction === 'IN' ? 1 : -1) * Number(payment.amount), 0)
export const money = value => Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
export const dateText = value => value ? String(value).replace('T', ' ').slice(0, 16) : '—'
export const localTime = (value = new Date()) => {
  const date = new Date(value)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}T${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}:${String(date.getSeconds()).padStart(2, '0')}`
}
export const today = () => localTime().slice(0, 10)
export const mask = value => String(value || '').length > 7 ? String(value).slice(0, 3) + '****' + String(value).slice(-4) : value
export const labels = { AVAILABLE: '可用', RENTED: '出租中', MAINTENANCE: '维修中', DISPATCHING: '调度中', DISABLED: '已停用', RESERVED: '待取车', ACTIVE: '租赁中', RETURNED: '待结算', COMPLETED: '已完成', CANCELLED: '已取消', PENDING: '待处理', IN_PROGRESS: '进行中', DAILY: '按天计费', MILEAGE: '按里程计费', MONTHLY: '包月计费', NORMAL: '普通客户', VIP: '会员客户', REPAIR: '维修', SERVICE: '保养', DEPOSIT: '押金', RENT: '预收租金', SETTLEMENT: '结算', CANCEL_REFUND: '取消退款', ADMIN: '管理员', STAFF: '业务人员', CUSTOMER: '客户' }
export const statusClass = status => ({ AVAILABLE: 'green', COMPLETED: 'green', RENTED: 'blue', ACTIVE: 'blue', MAINTENANCE: 'orange', RETURNED: 'orange', DISPATCHING: 'purple', RESERVED: 'purple', PENDING: 'orange', IN_PROGRESS: 'blue', CANCELLED: 'gray', DISABLED: 'gray' }[status] || 'gray')
export const overdue = order => order.status === 'ACTIVE' && new Date(order.planned_end) < new Date()
