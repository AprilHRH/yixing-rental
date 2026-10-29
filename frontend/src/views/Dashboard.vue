<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Van, Upload, Download, Wallet, Warning, Tools, Refresh, Plus } from '@element-plus/icons-vue'
import { api } from '../api'
import { state, isAdmin, vehicle, customer, labels, money, dateText } from '../store'
import { buildOperations, categorySummary, dayKey, fleetSummary, matchesOrderScope, recentOrders, revenueSeries, weekStart } from '../dashboard'
import ChartPanel from '../components/ChartPanel.vue'
import StatusTag from '../components/StatusTag.vue'
import VehicleThumbnail from '../components/VehicleThumbnail.vue'
import VehicleIdentity from '../components/VehicleIdentity.vue'

const router = useRouter(), taskPanel = ref(null)
const report = ref(null), reportError = ref(false), reportLoading = ref(false)
const weekReport = ref(null), weekError = ref(false)
const taskTab = ref('all'), range = ref('week'), clock = ref(new Date())
const greeting = computed(() => clock.value.getHours() < 12 ? '上午好' : clock.value.getHours() < 18 ? '下午好' : '晚上好')
let timer, requestVersion = 0
async function load() {
  const version = ++requestVersion
  reportLoading.value = true
  reportError.value = false
  weekError.value = false
  const results = await Promise.allSettled([
    api.get('/dashboard', { silent: true }),
    isAdmin.value ? api.get('/reports', { params: { from: weekStart(clock.value), to: dayKey(clock.value) }, silent: true }) : Promise.resolve(null),
  ])
  if (version !== requestVersion) return
  report.value = results[0].status === 'fulfilled' ? results[0].value : null
  weekReport.value = results[1].status === 'fulfilled' ? results[1].value : null
  reportError.value = results[0].status === 'rejected'
  weekError.value = results[1].status === 'rejected'
  reportLoading.value = false
}
onMounted(() => { load(); timer = setInterval(() => { clock.value = new Date() }, 30000) })
onBeforeUnmount(() => { clearInterval(timer); requestVersion++ })
watch(() => state.orders, load)
watch(() => dayKey(clock.value), load)
const pickups = computed(() => state.orders.filter(order => matchesOrderScope(order, 'pickup-today', clock.value)))
const returns = computed(() => state.orders.filter(order => matchesOrderScope(order, 'return-today', clock.value)))
const lateReturns = computed(() => state.orders.filter(order => matchesOrderScope(order, 'overdue', clock.value)))
const rented = computed(() => state.vehicles.filter(car => car.status === 'RENTED').length)
const settlements = computed(() => state.orders.filter(order => order.status === 'RETURNED').length)
const categories = computed(() => categorySummary(state.vehicles))
const fleet = computed(() => fleetSummary(state.vehicles))
const available = computed(() => fleet.value.find(item => item.status === 'AVAILABLE').count)
const operations = computed(() => buildOperations(state.orders, state.maintenance, clock.value))
const todayTasks = task => ['pickup', 'return'].includes(task.type) && task.due?.slice(0, 10) === dayKey(clock.value)
const tabs = computed(() => [
  { key: 'all', label: '全部待办', count: operations.value.length },
  { key: 'today', label: '今日交还', count: operations.value.filter(todayTasks).length },
  { key: 'late', label: '逾期未还', count: lateReturns.value.length },
  { key: 'maintenance', label: '维修保养', count: operations.value.filter(task => task.type === 'maintenance').length },
])
const visibleTasks = computed(() => operations.value.filter(task => taskTab.value === 'all' || (taskTab.value === 'today' && todayTasks(task)) || (taskTab.value === 'late' && task.type === 'return' && task.level === 'danger') || (taskTab.value === 'maintenance' && task.type === 'maintenance')))
const recent = computed(() => recentOrders(state.orders))
const futureCreated = computed(() => state.orders.filter(order => new Date(order.created_at) > clock.value).length)
const chartReport = computed(() => range.value === 'week' ? weekReport.value : report.value)
const chartError = computed(() => range.value === 'week' ? weekError.value : reportError.value)
const trend = computed(() => revenueSeries(chartReport.value?.trend || [], range.value))
const rangeTotal = computed(() => trend.value.reduce((total, point) => total + Number(point.revenue), 0))
const zeroDays = computed(() => trend.value.filter(point => !Number(point.revenue)).length)
const stats = computed(() => [
  { label: '今日待取车', value: pickups.value.length, unit: '笔', tone: 'pickup', icon: Upload, note: '按计划取车日期统计', path: '/orders?scope=pickup-today' },
  { label: '今日待还车', value: returns.value.length, unit: '笔', tone: 'return', icon: Download, note: lateReturns.value.length ? `另有 ${lateReturns.value.length} 笔逾期未还，优先跟进` : '暂无逾期未还订单', alert: lateReturns.value.length > 0, path: '/orders?scope=return-today' },
  { label: '当前出租车辆', value: rented.value, unit: '辆', tone: 'rented', icon: Van, note: `车队共 ${state.vehicles.length} 辆 · ${available.value} 辆可用`, path: '/vehicles?status=RENTED' },
  isAdmin.value
    ? { label: '本月租赁收入', value: reportError.value ? '加载失败' : report.value ? money(report.value.revenue) : '—', unit: report.value ? '元' : '', icon: Wallet, note: '已结算租金，不含押金', primary: true, path: '/reports' }
    : { label: '还车待结算', value: settlements.value, unit: '笔', icon: Wallet, note: '核对费用与收退款后结算', primary: true, path: '/orders?status=RETURNED' },
])
const fleetOption = computed(() => ({
  animation: false,
  tooltip: { trigger: 'item', confine: true, formatter: point => `${point.name}：${point.value} 辆（${point.percent}%）` },
  series: [{ type: 'pie', radius: ['65%', '82%'], center: ['50%', '50%'], label: { show: false },
    emphasis: { scale: false }, itemStyle: { borderColor: '#fff', borderWidth: state.vehicles.length > 1 ? 3 : 0, borderRadius: 6 },
    data: fleet.value.map(item => ({ name: labels[item.status], value: item.count, itemStyle: { color: item.color } })) }],
}))
const revenueOption = computed(() => ({
  animation: false,
  color: ['#f97316'],
  textStyle: { fontFamily: 'Microsoft YaHei, sans-serif' },
  tooltip: { trigger: 'axis', confine: true, valueFormatter: value => '¥' + money(value) },
  grid: { left: 48, right: 10, top: 14, bottom: 28, containLabel: false },
  xAxis: { type: 'category', data: trend.value.map(point => point.date.slice(5).replace('-', '/')), boundaryGap: true, axisLine: { lineStyle: { color: '#dfe4e1' } }, axisTick: { show: false }, axisLabel: { color: '#4d607a', fontSize: 11, hideOverlap: true, interval: range.value === 'week' ? 0 : Math.max(0, Math.floor(trend.value.length / 5) - 1) } },
  yAxis: { type: 'value', minInterval: 1, splitNumber: 3, splitLine: { lineStyle: { color: '#edf0ee', type: 'dashed' } }, axisLabel: { color: '#4d607a', fontSize: 11, formatter: value => value >= 10000 ? (value / 10000) + '万' : value.toLocaleString() } },
  series: [{ name: '已结算租金（元）', type: 'bar', barMaxWidth: 16, itemStyle: { borderRadius: [2, 2, 0, 0] }, data: trend.value.map(point => Number(point.revenue)) }],
}))
</script>

<template>
  <div class="operations-dashboard">
    <div class="dashboard-layout">
      <div class="dashboard-flow">
        <section class="shift-brief" aria-label="今日运营简报">
          <div class="shift-copy">
            <span class="shift-brand">YIXING RENTAL</span>
            <span class="shift-date">{{ clock.toLocaleDateString('zh-CN', { month: 'long', day: 'numeric', weekday: 'long' }) }}</span>
            <h2>{{ greeting }}，<span>{{ state.user?.name || '同事' }}</span></h2>
            <p>{{ state.branches.length }} 家门店 · {{ state.vehicles.length }} 辆车 · 今日有 {{ pickups.length + returns.length }} 笔交还车待办理</p>
            <div class="shift-actions"><el-button class="shift-create" :icon="Plus" @click="router.push('/orders?create=1')">新建租赁订单</el-button><button class="shift-action" @click="taskTab = 'today'; taskPanel?.scrollIntoView({ behavior: 'auto', block: 'nearest' })">查看今日安排 <el-icon><ArrowRight /></el-icon></button></div>
          </div>
          <figure class="shift-photograph"><img src="/images/rental-road.webp" alt="公路上的汽车品牌影像，非库存车辆实拍" width="1500" height="1000" fetchpriority="high"><figcaption>品牌影像 · 非库存实拍</figcaption></figure>
        </section>
        <div class="kpi-grid" aria-label="今日运营指标">
            <button v-for="stat in stats.slice(0, 3)" :key="stat.label" class="kpi-card" @click="router.push(stat.path)">
              <span class="kpi-label">{{ stat.label }}<el-icon><component :is="stat.icon" /></el-icon></span>
              <span :class="['kpi-number', stat.tone]">{{ stat.value }}<small>{{ stat.unit }}</small></span>
              <span :class="['kpi-note', { 'is-alert': stat.alert }]">{{ stat.note }}<el-icon><ArrowRight /></el-icon></span>
            </button>
          </div>
        <section ref="taskPanel" class="panel task-panel">
        <div class="panel-heading"><div><h2>业务待办 <span class="inline-count">{{ operations.length }}</span></h2><p>先处理异常，再安排交还车与养护。</p></div><el-icon class="heading-icon"><Warning /></el-icon></div>
        <div class="task-tabs" role="group" aria-label="待办筛选"><button v-for="tab in tabs" :key="tab.key" :class="{ selected: taskTab === tab.key }" :aria-pressed="taskTab === tab.key" @click="taskTab = tab.key">{{ tab.label }}<span>{{ tab.count }}</span></button></div>
        <div v-if="visibleTasks.length" class="task-list" tabindex="0" aria-label="待办列表，可滚动查看">
          <article v-for="task in visibleTasks" :key="task.id" class="operation-row">
            <div class="operation-vehicle"><VehicleThumbnail :vehicle="vehicle(task.vehicleId)" /><div class="operation-main"><div><span :class="['task-status', task.level]">{{ task.label }}</span><strong>{{ vehicle(task.vehicleId).plate || '未关联车辆' }}</strong><span class="task-model">{{ vehicle(task.vehicleId).model }}</span></div><p>{{ task.reference }}</p></div></div>
            <div class="operation-time"><span>{{ task.timing }}</span><time :datetime="task.due">{{ dateText(task.due) }}</time></div>
            <el-button link type="primary" @click="router.push(task.path)">{{ task.action }}<el-icon><ArrowRight /></el-icon></el-button>
          </article>
        </div>
        <div v-else class="business-empty"><el-icon><Van /></el-icon><strong>{{ taskTab === 'late' ? '暂无逾期未还订单' : '当前筛选下没有待办' }}</strong><span>新业务登记后会显示在这里。</span></div>
        <div class="panel-footnote"><span>按紧急程度与计划时间排序</span><router-link to="/orders">全部订单 <el-icon><ArrowRight /></el-icon></router-link></div>
      </section>
        <section class="panel recent-panel">
        <div class="panel-heading"><div><h2>最近订单</h2><p>按创建时间排序，点击操作查看完整业务记录</p></div><router-link to="/orders" class="text-link">全部订单 <el-icon><ArrowRight /></el-icon></router-link></div>
        <el-table class="recent-order-table" :data="recent" empty-text="暂无订单，请先新建租赁订单">
          <el-table-column label="订单 / 客户" min-width="200"><template #default="{ row }"><router-link class="order-reference" :to="`/orders?detail=${row.id}`">{{ row.order_no }}</router-link><small class="cell-sub">{{ customer(row.customer_id).name || '—' }}</small></template></el-table-column>
          <el-table-column label="车辆" min-width="245"><template #default="{ row }"><VehicleIdentity :vehicle="vehicle(row.vehicle_id)"/></template></el-table-column>
          <el-table-column label="租金（元）" min-width="112" align="right"><template #default="{ row }"><span class="table-money">{{ money(row.status === 'COMPLETED' || row.status === 'RETURNED' ? row.actual_rent : row.estimated_rent) }}</span><small class="cell-sub">{{ ['COMPLETED', 'RETURNED'].includes(row.status) ? '实际租金' : '预计租金' }}</small></template></el-table-column>
          <el-table-column label="状态" width="108"><template #default="{ row }"><StatusTag :value="row.status" /></template></el-table-column>
          <el-table-column label="操作" width="76" fixed="right"><template #default="{ row }"><el-button link type="primary" :aria-label="`查看订单 ${row.order_no}`" @click="router.push(`/orders?detail=${row.id}`)">查看</el-button></template></el-table-column>
        </el-table>
        <div class="recent-order-mobile"><router-link v-for="order in recent" :key="order.id" :to="`/orders?detail=${order.id}`"><div><strong>{{ order.order_no }}</strong><StatusTag :value="order.status" /></div><div class="activity-vehicle"><VehicleThumbnail :vehicle="vehicle(order.vehicle_id)"/><p>{{ customer(order.customer_id).name }} · {{ vehicle(order.vehicle_id).plate }}</p></div><span>{{ ['COMPLETED', 'RETURNED'].includes(order.status) ? '实际租金' : '预计租金' }} <b>¥{{ money(['COMPLETED', 'RETURNED'].includes(order.status) ? order.actual_rent : order.estimated_rent) }}</b></span></router-link><p v-if="!recent.length" class="mobile-order-empty">暂无订单，请先新建租赁订单。</p></div>
        <p v-if="futureCreated" class="data-quality-note"><el-icon><Warning /></el-icon>{{ futureCreated }} 笔订单创建时间晚于当前时间，列表保留原始记录，请在订单详情中核查。</p>
      </section>
      </div>
      <aside class="dashboard-rail" aria-label="经营与运力">
        <div class="finance-stack">
          <button class="kpi-card kpi-primary" @click="router.push(stats[3].path)">
            <span class="kpi-label">{{ stats[3].label }}<el-icon><component :is="stats[3].icon" /></el-icon></span>
            <span class="kpi-number">{{ stats[3].value }}<small>{{ stats[3].unit }}</small></span>
            <span class="income-period">{{ clock.getFullYear() }} 年 {{ clock.getMonth() + 1 }} 月 · <span>{{ isAdmin ? '按完成日期统计' : '归还后待结算' }}</span></span>
            <span class="kpi-note">{{ stats[3].note }}<el-icon><ArrowRight /></el-icon></span>
          </button>
          <section v-if="isAdmin" class="panel revenue-panel" :aria-busy="reportLoading">
        <div class="panel-heading"><div><h2>收入趋势</h2><p>按订单结算日期统计 · 单位：元</p></div><div class="period-switch" role="group" aria-label="收入时间范围"><button :class="{ selected: range === 'week' }" :aria-pressed="range === 'week'" @click="range = 'week'">近7天</button><button :class="{ selected: range === 'month' }" :aria-pressed="range === 'month'" @click="range = 'month'">本月</button></div></div>
        <div v-if="chartError" class="business-empty" role="alert"><strong>收入数据加载失败</strong><span>业务待办仍可正常使用</span><el-button :icon="Refresh" @click="load">重新加载</el-button></div>
        <template v-else-if="chartReport"><div class="revenue-total"><strong>¥{{ money(rangeTotal) }}</strong><span>{{ trend[0]?.date }} 至 {{ trend.at(-1)?.date }}</span></div><ChartPanel :option="revenueOption" :height="188" label="按结算日期统计的租赁收入柱状图；每日金额可通过下方数据明细读取" /><p class="revenue-caption">{{ zeroDays ? `${zeroDays} 天无已结算收入，按 0 展示。` : '所选日期均有已结算收入。' }}不含押金及未结算订单。</p><details class="chart-data"><summary>查看每日金额</summary><ul><li v-for="point in trend" :key="point.date"><time>{{ point.date }}</time><span>¥{{ money(point.revenue) }}</span></li></ul></details></template>
        <el-skeleton v-else :rows="5" animated class="revenue-skeleton" />
      </section>
      <section v-else class="panel staff-guide"><div class="panel-heading"><div><h2>办理前核对</h2><p>减少交还车时的遗漏</p></div></div><div><strong>交车</strong><p>核验驾驶证有效期、押金与车辆外观，登记起始里程。</p></div><div><strong>还车</strong><p>登记归还里程与车况，核对租金、附加费及收退款记录。</p></div><router-link class="text-link" to="/pricing">打开租金试算 <el-icon><ArrowRight /></el-icon></router-link></section>
        </div>
        <section class="panel fleet-summary">
        <div class="panel-heading"><div><h2>车辆状态</h2><p>与车辆档案实时同步</p></div><router-link to="/vehicles" class="text-link">车辆列表 <el-icon><ArrowRight /></el-icon></router-link></div>
        <div class="fleet-overview"><div class="fleet-ring"><ChartPanel :option="fleetOption" :height="166" label="车辆状态占比环形图；各状态数量详见下方列表" /><div class="fleet-ring-label"><strong>{{ state.vehicles.length }}</strong><span>车辆总数</span></div></div>
        <div class="fleet-state-list"><router-link v-for="item in fleet" :key="item.status" :to="`/vehicles?status=${item.status}`"><span><i :style="{ background: item.color }"></i>{{ labels[item.status] }}</span><strong>{{ item.count }}<small>辆</small><el-icon><ArrowRight /></el-icon></strong></router-link></div></div>
        <div v-if="categories.length" class="category-capacity"><div class="category-capacity-heading"><span>分类运力</span><span>可用 / 总数</span></div><div v-for="group in categories" :key="group.category" class="category-capacity-row" :aria-label="`${group.category}，可用 ${group.available} 辆，共 ${group.total} 辆`"><span>{{ group.category }}</span><div class="category-capacity-track" aria-hidden="true"><i :style="{ width: `${group.available / group.total * 100}%` }"></i></div><strong :data-total="group.total" :data-available="group.available">{{ group.available }}<small> / {{ group.total }}</small></strong></div></div>
        <router-link v-if="state.reminders.length" class="service-notice" to="/maintenance"><el-icon><Tools /></el-icon><span>{{ state.reminders.length ? `${state.reminders.length} 辆车已触发保养提醒` : '暂无车辆触发保养提醒' }}</span><el-icon><ArrowRight /></el-icon></router-link>
      </section>
        <div class="desk-shortcuts"><h2>常用工具</h2><router-link to="/pricing"><span>租金试算<small>按租期、里程核对报价</small></span><el-icon><ArrowRight /></el-icon></router-link><router-link to="/dispatch"><span>门店调拨<small>查看门店运力与在途任务</small></span><el-icon><ArrowRight /></el-icon></router-link></div>
      </aside>
    </div>

  </div>
</template>
