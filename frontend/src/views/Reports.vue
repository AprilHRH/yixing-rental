<script setup>
import { computed, onMounted, ref } from 'vue'
import { Download, Search } from '@element-plus/icons-vue'
import { api, exportCsv } from '../api'
import { state, vehicle, money, today } from '../store'
import ChartPanel from '../components/ChartPanel.vue'
import VehicleIdentity from '../components/VehicleIdentity.vue'
const dates = ref([today().slice(0, 8) + '01', today()]), result = ref(null), busy = ref(false), tab = ref('trend'), metric = ref('hours')
async function load() { if (!dates.value?.length) return; busy.value = true; try { result.value = await api.get('/reports', { params: { from: dates.value[0], to: dates.value[1] } }) } catch {} finally { busy.value = false } }
onMounted(load)
const lineOption = computed(() => ({ color: ['#f97316', '#2563eb'], tooltip: { trigger: 'axis' }, legend: { bottom: 0, data: ['租赁收入', '新增订单'] }, grid: { top: 38, left: 65, right: 55, bottom: 60 }, xAxis: { type: 'category', name: '日期', nameLocation: 'middle', nameGap: 26, data: (result.value?.trend || []).map(item => item.date.slice(5)), axisLine: { lineStyle: { color: '#dfe5e1' } }, axisLabel: { color: '#4d607a' }, axisTick: { show: false } }, yAxis: [{ type: 'value', name: '收入（元）', splitLine: { lineStyle: { color: '#edf0ee' } } }, { type: 'value', name: '订单（笔）', minInterval: 1, splitLine: { show: false } }], series: [{ name: '租赁收入', type: 'bar', barMaxWidth: 18, itemStyle: { borderRadius: [3, 3, 0, 0] }, data: (result.value?.trend || []).map(item => Number(item.revenue)) }, { name: '新增订单', type: 'line', yAxisIndex: 1, smooth: true, symbolSize: 5, data: (result.value?.trend || []).map(item => item.orders) }] }))
const metricNames = { hours: '出租时长', count: '出租次数', revenue: '租金收入' }
const ranked = computed(() => [...(result.value?.usage || [])].sort((first, second) => Number(second[metric.value]) - Number(first[metric.value]) || Number(first.vehicle_id) - Number(second.vehicle_id)))
const maximum = computed(() => Math.max(0, ...ranked.value.map(row => Number(row[metric.value]))))
const dailyRows = computed(() => [...(result.value?.trend || [])].sort((first, second) => second.date.localeCompare(first.date)))
const metricValue = row => metric.value === 'hours' ? Number(row.hours).toFixed(1) + ' 小时' : metric.value === 'count' ? row.count + ' 次' : '¥' + money(row.revenue)
function exportData() {
  if (!result.value) return
  if (tab.value === 'usage') exportCsv(ranked.value, [{ label: '车牌', value: 'plate' }, { label: '车型', value: 'model' }, { label: '出租次数', value: 'count' }, { label: '出租小时', value: row => Number(row.hours).toFixed(2) }, { label: '已结算租金（元）', value: 'revenue' }], '车辆使用统计.csv')
  else exportCsv(dailyRows.value, [{ label: '日期', value: 'date' }, { label: '租赁收入（元）', value: 'revenue' }, { label: '新增订单（笔）', value: 'orders' }], '经营统计.csv')
}
</script>
<template>
  <section class="panel report-filter"><div><h3>经营数据</h3><p>按结算日期统计收入，不含押金和未完成订单</p></div><el-date-picker v-model="dates" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" range-separator="至" :clearable="false"/><el-button type="primary" :icon="Search" :loading="busy" @click="load">查询统计</el-button><el-button :icon="Download" @click="exportData">导出数据</el-button></section>
  <div v-loading="busy"><template v-if="result"><div class="stats-grid report-stats"><article class="stat-card"><div class="stat-top">租赁收入<span class="stat-unit">元</span></div><div class="stat-value">{{ money(result.revenue) }}</div><p>按结算日期 · 不含押金</p></article><article class="stat-card"><div class="stat-top">新增订单</div><div class="stat-value">{{ result.created }}<small>笔</small></div><p>按订单创建日期</p></article><article class="stat-card"><div class="stat-top">完成 / 取消</div><div class="stat-value">{{ result.completed }}<small>/ {{ result.cancelled }} 笔</small></div><p>分别按完成及取消日期</p></article><article class="stat-card"><div class="stat-top">维修保养费用</div><div class="stat-value">{{ money(result.maintenance_cost) }}</div><p>按工单完成日期</p></article></div>
  <section class="panel"><div class="panel-heading"><div><h3>{{ tab === 'trend' ? '收入与订单趋势' : '车辆' + metricNames[metric] + '排行' }}</h3><p>{{ result.from }} 至 {{ result.to }} · 来源：本系统业务记录</p></div><el-radio-group v-model="tab"><el-radio-button value="trend">经营趋势</el-radio-button><el-radio-button value="usage">车辆使用</el-radio-button></el-radio-group></div>
    <ChartPanel v-if="tab === 'trend'" :option="lineOption" :height="340"/>
    <template v-else><div class="usage-toolbar"><el-radio-group v-model="metric" aria-label="车辆排行指标"><el-radio-button value="hours">出租时长</el-radio-button><el-radio-button value="count">出租次数</el-radio-button><el-radio-button value="revenue">租金收入</el-radio-button></el-radio-group><span>{{ ranked.length }} 辆 · 按{{ metricNames[metric] }}降序</span></div><p class="usage-explanation">次数按取车日期统计；时长仅计入查询区间内的实际出租时间；收入按结算日期统计已结算租金，不含押金。</p>
    <div class="usage-ranking" role="list" :aria-label="'车辆' + metricNames[metric] + '排行'"><div v-for="(row, index) in ranked" :key="row.vehicle_id" class="usage-ranking-row" role="listitem"><span class="rank-number">{{ String(index + 1).padStart(2, '0') }}</span><VehicleIdentity :vehicle="vehicle(row.vehicle_id)"/><div class="usage-track" aria-hidden="true"><span :style="{ width: (maximum ? Number(row[metric]) / maximum * 100 : 0) + '%' }"></span></div><strong class="usage-value">{{ metricValue(row) }}</strong></div><el-empty v-if="!ranked.length" description="所选区间暂无车辆使用记录"/></div></template>
  </section>
  <section v-if="tab === 'trend'" class="panel"><div class="panel-heading"><div><h3>每日经营明细</h3><p>最新日期在前。收入与新增订单采用不同时间口径，不直接对应同一批订单。</p></div></div><el-table :data="dailyRows" max-height="420" show-summary :summary-method="({ data }) => ['合计', '¥' + money(data.reduce((sum, row) => sum + Number(row.revenue), 0)), data.reduce((sum, row) => sum + Number(row.orders), 0)]"><el-table-column prop="date" label="日期"/><el-table-column label="租赁收入（元）"><template #default="{ row }">¥{{ money(row.revenue) }}</template></el-table-column><el-table-column prop="orders" label="新增订单（笔）"/></el-table></section></template><el-result v-else-if="!busy" icon="warning" title="统计数据暂不可用"><template #extra><el-button @click="load">重新加载</el-button></template></el-result></div>
</template>
