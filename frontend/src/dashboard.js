const fleetPalette = {
  AVAILABLE: '#2dd4a8', RENTED: '#4c8dff', MAINTENANCE: '#ffc247',
  DISPATCHING: '#a78bfa', DISABLED: '#b8c6d9',
}

export function dayKey(value = new Date()) {
  const date = new Date(value)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

export function weekStart(now = new Date()) {
  const start = new Date(now)
  start.setDate(start.getDate() - 6)
  return dayKey(start)
}

export function matchesOrderScope(order, scope, now = new Date()) {
  const today = dayKey(now)
  if (scope === 'pickup-today') return order.status === 'RESERVED' && order.planned_start?.slice(0, 10) === today
  if (scope === 'return-today') return order.status === 'ACTIVE' && order.planned_end?.slice(0, 10) === today
  if (scope === 'overdue') return order.status === 'ACTIVE' && new Date(order.planned_end) < now
  return true
}

export function fleetSummary(vehicles) {
  return Object.entries(fleetPalette).map(([status, color]) => ({
    status, color, count: vehicles.filter(vehicle => vehicle.status === status).length,
  }))
}

export function categorySummary(vehicles) {
  const groups = new Map()
  for (const vehicle of vehicles) {
    const category = vehicle.category || '未分类'
    if (!groups.has(category)) groups.set(category, { category, total: 0, available: 0 })
    const group = groups.get(category)
    group.total++
    if (vehicle.status === 'AVAILABLE') group.available++
  }
  return [...groups.values()].sort((first, second) => second.total - first.total || first.category.localeCompare(second.category, 'zh-CN'))
}

export function buildOperations(orders, maintenance, now = new Date()) {
  const tasks = []
  for (const order of orders) {
    const common = { id: `order-${order.id}`, vehicleId: order.vehicle_id, reference: order.order_no, path: `/orders?detail=${order.id}` }
    if (order.status === 'RESERVED') {
      const late = new Date(order.planned_start) < now
      tasks.push({ ...common, type: 'pickup', label: late ? '取车待跟进' : '待取车', level: late ? 'warning' : 'normal', due: order.planned_start, timing: '计划取车', action: '办理取车', priority: late ? 2 : 4 })
    }
    if (order.status === 'ACTIVE' && order.planned_end?.slice(0, 10) <= dayKey(now)) {
      const late = new Date(order.planned_end) < now
      tasks.push({ ...common, type: 'return', label: late ? '逾期未还' : '今日待还', level: late ? 'danger' : 'warning', due: order.planned_end, timing: '计划归还', action: '办理还车', priority: late ? 0 : 1 })
    }
    if (order.status === 'RETURNED') tasks.push({ ...common, type: 'settlement', label: '待结算', level: 'warning', due: order.actual_end, timing: '实际还车', action: '查看结算', priority: 3 })
  }
  for (const task of maintenance) {
    if (!['PENDING', 'IN_PROGRESS'].includes(task.status)) continue
    tasks.push({ id: `maintenance-${task.id}`, type: 'maintenance', vehicleId: task.vehicle_id, reference: task.project, label: task.status === 'PENDING' ? '待维修保养' : '维修进行中', level: task.status === 'PENDING' ? 'warning' : 'normal', due: task.started_at, timing: '工单登记', action: '查看工单', path: `/maintenance?detail=${task.id}`, priority: 5 })
  }
  return tasks.sort((first, second) => first.priority - second.priority || String(first.due || '').localeCompare(String(second.due || '')))
}

export function recentOrders(orders) {
  return [...orders].sort((first, second) => String(second.created_at || '').localeCompare(String(first.created_at || '')) || Number(second.id) - Number(first.id)).slice(0, 5)
}

export function revenueSeries(trend, range) {
  return range === 'week' ? trend.slice(-7) : trend
}
