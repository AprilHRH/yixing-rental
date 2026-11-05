<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, ArrowRight, Tickets, Delete } from '@element-plus/icons-vue'
import { api } from '../api'
import { state, refresh, vehicle, rule, branch, money, labels, localTime, dateText, mask, netPaid, payments } from '../store'
import VehicleThumbnail from '../components/VehicleThumbnail.vue'
import VehicleIdentity from '../components/VehicleIdentity.vue'
import StatusTag from '../components/StatusTag.vue'
const route = useRoute(), router = useRouter(), search = ref(''), category = ref(''), orderStatus = ref(''), booking = ref(null), detail = ref(null), busy = ref(false), quote = ref(null), formRef = ref(null), profileRef = ref(null)
// 选车、本人订单和个人资料复用页面，当前路径决定显示哪块内容。
const view = computed(() => route.path.endsWith('/orders') ? 'orders' : route.path.endsWith('/profile') ? 'profile' : 'cars')
const profile = reactive({}), form = reactive({ planned_start: '', planned_end: '', billing_mode: 'DAILY', expected_mileage: 0, return_branch: null })
const closing = ref(false), closeForm = reactive({ password: '', confirmed: false })
const unfinished = computed(() => state.orders.some(order => ['RESERVED', 'ACTIVE', 'RETURNED'].includes(order.status)))
function openClosure() { closeForm.password = ''; closeForm.confirmed = false; closing.value = true }
async function closeAccount() {
  if (!closeForm.password || !closeForm.confirmed) return
  busy.value = true
  try {
    await api.delete('/auth/account', { data: { password: closeForm.password, confirmation: 'CLOSE' } })
    closing.value = false
    state.user = null
    window.dispatchEvent(new Event('auth:expired'))
    ElMessage.success('账号已注销')
  } catch {} finally { busy.value = false }
}
const cars = computed(() => state.vehicles.filter(car => ['AVAILABLE', 'RENTED'].includes(car.status) && (!category.value || category.value === car.category) && (!search.value || `${car.brand}${car.model}${car.plate}`.toLowerCase().includes(search.value.toLowerCase()))))
const categories = computed(() => [...new Set(state.vehicles.map(car => car.category))])
const orders = computed(() => state.orders.filter(order => !orderStatus.value || order.status === orderStatus.value))
const required = label => [{ required: true, message: '请填写' + label, trigger: ['blur', 'change'] }]
watch(() => state.customers[0], customer => { if (customer) Object.assign(profile, customer) }, { immediate: true })
// 表单一变，旧报价就不能继续展示，需按新条件重新试算。
watch(form, () => quote.value = null)
function reserve(car) { booking.value = car; Object.assign(form, { planned_start: localTime(new Date(Date.now() + 3600000)), planned_end: localTime(new Date(Date.now() + 3600000 + 86400000 * 2)), billing_mode: 'DAILY', expected_mileage: 0, return_branch: car.branch_id }); quote.value = null }
function payload() { return { ...form, vehicle_id: booking.value.id } }
async function calculate() { if (!await formRef.value.validate().catch(() => false)) return; busy.value = true; try { quote.value = await api.post('/portal/quote', payload()) } catch { quote.value = null } finally { busy.value = false } }
async function submit() {
  // 提交的是预约，不是在线付款；取还车和线下收退款由门店办理。
  if (!await formRef.value.validate().catch(() => false)) return
  busy.value = true
  try { await api.post('/portal/orders', payload()); booking.value = null; await refresh(); await router.push('/portal/orders'); ElMessage.success('预约已提交，请按约定时间到店办理取车') } catch {} finally { busy.value = false }
}
async function cancel(order) {
  try { await ElMessageBox.confirm('确认取消该预约？取消后该租期将释放。', '取消预约', { type: 'warning', confirmButtonText: '确认取消', cancelButtonText: '保留预约' }); busy.value = true; await api.post(`/portal/orders/${order.id}/cancel`, {}); detail.value = null; await refresh(); ElMessage.success('预约已取消') } catch {} finally { busy.value = false }
}
async function saveProfile() {
  // 保存成功后重新读取服务器数据，账号显示姓名也同步更新。
  if (!await profileRef.value.validate().catch(() => false)) return
  busy.value = true
  try { await api.put('/portal/profile', profile); state.user.name = profile.name; await refresh(); ElMessage.success('个人资料已更新') } catch {} finally { busy.value = false }
}
</script>
<template>
  <template v-if="view === 'cars'">
    <section class="customer-welcome"><div><span class="eyebrow">你好，{{ state.user.name }}</span><h2>下一站，想去哪里？</h2><p>城市通勤，周末出游。找到适合这段旅程的车。</p><el-button @click="router.push('/portal/orders')">我的预约 {{ state.orders.filter(order => order.status === 'RESERVED').length }}<el-icon><ArrowRight/></el-icon></el-button></div><img src="/images/rental-road.webp" alt="汽车出行品牌配图，非库存实拍"/></section>
    <el-alert v-if="profile.restricted" title="当前账户暂时限制租赁，请联系门店核实。" type="warning" :closable="false"/>
    <section class="panel listing-panel"><div class="filter-bar"><el-input v-model="search" class="search-input" :prefix-icon="Search" placeholder="搜索品牌、车型、车牌" clearable/><el-select v-model="category" placeholder="全部车型" clearable><el-option v-for="item in categories" :key="item" :label="item" :value="item"/></el-select><span class="filter-spacer"></span><span class="text-muted">{{ cars.length }} 辆可选 · 以预约校验为准</span></div>
    <div class="vehicle-grid customer-vehicle-grid"><article v-for="car in cars" :key="car.id" class="vehicle-card"><VehicleThumbnail :vehicle="car" size="card"/><div class="customer-car-content"><div class="customer-car-title"><h3>{{ car.brand }} {{ car.model }}</h3><StatusTag :value="car.status"/></div><p>{{ car.plate }} · {{ car.seats }} 座 · {{ car.color }}</p><div class="customer-car-price"><strong>¥{{ money(rule(car.pricing_id).daily_rate) }}<small>/天</small></strong><el-button type="primary" :disabled="profile.restricted" @click="reserve(car)">预约用车</el-button></div><small>取车地点：{{ branch(car.branch_id).name }}</small></div></article></div><el-empty v-if="!cars.length" description="暂无匹配车辆，请调整筛选条件"/></section>
  </template>
  <template v-else-if="view === 'orders'">
    <div class="module-overview"><div><strong>{{ state.orders.length }}</strong><span>我的订单</span></div><div><strong class="text-accent">{{ state.orders.filter(order => order.status === 'RESERVED').length }}</strong><span>待取车</span></div><div><strong class="text-blue">{{ state.orders.filter(order => order.status === 'ACTIVE').length }}</strong><span>租赁中</span></div><el-button type="primary" @click="router.push('/portal')">继续选车</el-button></div>
    <section class="panel listing-panel"><div class="filter-bar"><el-select v-model="orderStatus" placeholder="全部订单状态" clearable><el-option v-for="status in ['RESERVED','ACTIVE','RETURNED','COMPLETED','CANCELLED']" :key="status" :label="labels[status]" :value="status"/></el-select><span class="filter-spacer"></span><span class="text-muted">收款、取还车及结算由门店办理</span></div><el-table :data="orders"><el-table-column prop="order_no" label="订单编号" min-width="215"/><el-table-column label="车辆" min-width="250"><template #default="{ row }"><VehicleIdentity :vehicle="vehicle(row.vehicle_id)"/></template></el-table-column><el-table-column label="租期" min-width="170"><template #default="{ row }">{{ dateText(row.planned_start) }}<small class="cell-sub">至 {{ dateText(row.planned_end) }}</small></template></el-table-column><el-table-column label="租金" min-width="115"><template #default="{ row }">¥{{ money(row.actual_rent ?? row.estimated_rent) }}<small class="cell-sub">{{ row.actual_rent == null ? '预计租金' : '结算租金' }}</small></template></el-table-column><el-table-column label="状态" width="100"><template #default="{ row }"><StatusTag :value="row.status"/></template></el-table-column><el-table-column label="操作" width="170"><template #default="{ row }"><el-button link type="primary" @click="detail = row">详情</el-button><el-button v-if="row.status === 'RESERVED' && netPaid(row.id) === 0" link type="danger" :disabled="busy" @click="cancel(row)">取消预约</el-button></template></el-table-column></el-table></section>
  </template>
  <template v-else>
    <section class="panel customer-profile"><div class="panel-heading"><div><h3>个人资料</h3><p>证件号：{{ mask(profile.identity_no) }} · 修改身份证信息请联系管理员</p></div><span class="soft-label">{{ labels[profile.category] }}</span></div><el-form ref="profileRef" :model="profile" class="form-grid" label-position="top"><el-form-item label="姓名" prop="name" :rules="required('姓名')"><el-input v-model="profile.name" maxlength="60"/></el-form-item><el-form-item label="手机号" prop="phone" :rules="required('手机号')"><el-input v-model="profile.phone" maxlength="20"/></el-form-item><el-form-item label="驾驶证号" prop="license_no" :rules="required('驾驶证号')"><el-input v-model="profile.license_no" maxlength="80"/></el-form-item><el-form-item label="驾驶证有效期至" prop="license_expiry" :rules="required('驾驶证有效期')"><el-date-picker v-model="profile.license_expiry" type="date" value-format="YYYY-MM-DD"/></el-form-item><div class="full-width"><el-button type="primary" :loading="busy" @click="saveProfile">保存个人资料</el-button></div></el-form></section>
    <section class="account-closure-section">
      <div><h3>注销账号</h3><p>注销后无法再登录。个人资料保留 7 天后清除，已完成订单保留去身份化记录。</p><p v-if="unfinished" class="text-danger">请先取消待取车预约，或联系门店完成还车和结算。</p></div>
      <el-button type="danger" plain :icon="Delete" :disabled="unfinished || busy" @click="openClosure">注销账号</el-button>
    </section>
  </template>
  <el-dialog v-model="closing" title="确认注销账号" width="460px" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy">
    <p>账号注销后不能恢复登录。个人资料将在 7 天后清除，已完成订单仅保留去身份化记录。</p>
    <el-form label-position="top" @submit.prevent="closeAccount">
      <el-form-item label="当前登录密码"><el-input v-model="closeForm.password" type="password" show-password autocomplete="current-password" maxlength="64"/></el-form-item>
      <el-checkbox v-model="closeForm.confirmed">我已了解并确认注销此账号</el-checkbox>
    </el-form>
    <template #footer><el-button :disabled="busy" @click="closing = false">保留账号</el-button><el-button type="danger" :loading="busy" :disabled="!closeForm.password || !closeForm.confirmed" @click="closeAccount">确认注销</el-button></template>
  </el-dialog>
  <el-dialog :model-value="!!booking" @close="booking = null" title="预约车辆" width="680px" :close-on-click-modal="false" destroy-on-close><template v-if="booking"><VehicleIdentity :vehicle="booking"/><el-form ref="formRef" :model="form" class="form-grid order-form" label-position="top"><el-form-item label="计划取车时间" prop="planned_start" :rules="required('取车时间')"><el-date-picker v-model="form.planned_start" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss"/></el-form-item><el-form-item label="计划还车时间" prop="planned_end" :rules="required('还车时间')"><el-date-picker v-model="form.planned_end" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss"/></el-form-item><el-form-item label="计费方式"><el-select v-model="form.billing_mode"><el-option v-for="mode in ['DAILY','MILEAGE','MONTHLY']" :key="mode" :value="mode" :label="labels[mode]"/></el-select></el-form-item><el-form-item label="还车门店" prop="return_branch" :rules="required('还车门店')"><el-select v-model="form.return_branch"><el-option v-for="item in state.branches" :key="item.id" :label="item.name" :value="item.id"/></el-select></el-form-item><el-form-item v-if="form.billing_mode === 'MILEAGE'" label="预计里程（km）"><el-input-number v-model="form.expected_mileage" :min="0" :max="1000000"/></el-form-item></el-form><div v-if="quote" class="customer-quote"><strong>预计租金 ¥{{ money(quote.rent) }}</strong><span>押金 ¥{{ money(quote.deposit) }} · {{ quote.formula }}</span></div><p class="field-tip">{{ form.billing_mode === 'MONTHLY' ? '包月以 30 天为一个计费月，不足部分按天计。' : '预计租金不含附加费用。' }}提交时会重新校验驾驶资格、车辆状态及租期冲突，不直接收款。</p></template><template #footer><el-button :loading="busy" @click="calculate">试算租金</el-button><el-button type="primary" :loading="busy" @click="submit">确认预约</el-button></template></el-dialog>
  <el-drawer :model-value="!!detail" @close="detail = null" title="我的订单详情" size="620px"><template v-if="detail"><VehicleIdentity :vehicle="vehicle(detail.vehicle_id)"/><div class="order-detail-heading"><h3>{{ detail.order_no }}</h3><StatusTag :value="detail.status"/></div><el-descriptions :column="1" border><el-descriptions-item label="计划取车">{{ dateText(detail.planned_start) }} · {{ branch(detail.pickup_branch).name }}</el-descriptions-item><el-descriptions-item label="计划还车">{{ dateText(detail.planned_end) }} · {{ branch(detail.return_branch).name }}</el-descriptions-item><el-descriptions-item label="预计租金">¥{{ money(detail.estimated_rent) }}</el-descriptions-item><el-descriptions-item label="实际租金">{{ detail.actual_rent == null ? '待结算' : '¥' + money(detail.actual_rent) }}</el-descriptions-item><el-descriptions-item label="押金">¥{{ money(detail.deposit) }}</el-descriptions-item><el-descriptions-item label="已登记净收款">¥{{ money(netPaid(detail.id)) }}</el-descriptions-item></el-descriptions><h3 class="section-title">收退款记录</h3><el-table :data="payments(detail.id)"><el-table-column label="类型"><template #default="{ row }">{{ labels[row.kind] }}</template></el-table-column><el-table-column label="金额"><template #default="{ row }">{{ row.direction === 'OUT' ? '−' : '+' }}¥{{ money(row.amount) }}</template></el-table-column><el-table-column label="时间" min-width="160"><template #default="{ row }">{{ dateText(row.created_at) }}</template></el-table-column></el-table><p class="field-tip">如需更改租期、退款或处理异常，请联系门店：{{ branch(detail.pickup_branch).phone }}</p></template></el-drawer>
</template>
<style scoped>
.account-closure-section { max-width: 800px; display: flex; align-items: center; justify-content: space-between; gap: 24px; padding: 24px 0; border-top: 1px solid var(--border, #e5e7eb); margin-top: 24px; }
.account-closure-section h3 { margin: 0 0 8px; font-size: 16px; }
.account-closure-section p { margin: 6px 0; line-height: 1.7; color: #606266; font-size: 14px; }
.account-closure-section p.text-danger { color: #c63737; }
@media (max-width: 600px) {
  .account-closure-section { align-items: flex-start; flex-direction: column; gap: 12px; }
}
</style>
