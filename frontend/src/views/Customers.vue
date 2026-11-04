<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Key, Check, Close, Delete } from '@element-plus/icons-vue'
import { api } from '../api'
import { state, refresh, vehicle, mask, labels, dateText, today, overdue } from '../store'
import FormDialog from '../components/FormDialog.vue'
import StatusTag from '../components/StatusTag.vue'
import VehicleIdentity from '../components/VehicleIdentity.vue'
const tab = ref('profiles'), accounts = ref([]), tickets = ref([]), ticketStatus = ref('PENDING')
const query = reactive({ search: '', category: '', restricted: '' }), page = ref(1), open = ref(false), detail = ref(null), edit = ref(null), initial = ref({}), busy = ref(false)
const accountOpen = ref(false), accountCustomer = ref(null), accountInitial = ref({}), reviewOpen = ref(false), selectedTicket = ref(null), reviewForm = reactive({ action: 'APPROVE', verified: false, review_notes: '' }), reviewRef = ref(null)
const account = id => accounts.value.find(user => user.customer_id === id)
const pending = computed(() => tickets.value.filter(ticket => ticket.status === 'PENDING').length)
const visibleTickets = computed(() => tickets.value.filter(ticket => !ticketStatus.value || ticket.status === ticketStatus.value))
const ticketLabels = { PENDING: '待核验', RESOLVED: '已重置', REJECTED: '已驳回' }
async function load() { try { [accounts.value, tickets.value] = await Promise.all([api.get('/customers/accounts'), api.get('/customers/reset-tickets')]) } catch {} }
async function reload() { await Promise.all([refresh(), load()]) }
onMounted(load)
const filtered = computed(() => state.customers.filter(item => {
  const keyword = query.search.trim().toLowerCase()
  return (!keyword || `${item.name} ${item.phone} ${account(item.id)?.username || ''}`.toLowerCase().includes(keyword)) && (!query.category || item.category === query.category) && (query.restricted === '' || query.restricted == null || item.restricted === query.restricted)
}))
watch(query, () => page.value = 1)
const fields = computed(() => [
  { key: 'name', label: '客户姓名' }, { key: 'phone', label: '联系电话', maxlength: 20 },
  { key: 'identity_no', label: '身份证 / 证件号', tip: '演示环境请使用虚构编号' }, { key: 'license_no', label: '驾驶证编号' },
  { key: 'license_expiry', label: '驾驶证有效期', type: 'date' },
  { key: 'category', label: '客户类别', type: 'select', options: [{ label: '普通客户', value: 'NORMAL' }, { label: '会员客户', value: 'VIP' }] },
  ...(!edit.value ? [{ key: 'username', label: '客户登录账号', tip: '3 至 30 位字母、数字或下划线' }, { key: 'password', label: '初始密码', type: 'password', tip: '首次登录须由客户本人设置密保' }] : []),
  { key: 'restricted', label: '限制租赁', type: 'switch', required: false },
  { key: 'notes', label: '备注 / 限制原因', type: 'textarea', wide: true, required: false, maxlength: 500 }
])
const accountFields = computed(() => [
  { key: 'username', label: '客户登录账号', wide: true },
  ...(!account(accountCustomer.value?.id) ? [{ key: 'password', label: '初始密码', type: 'password', wide: true }] : []),
  { key: 'enabled', label: '账号启用', type: 'switch', required: false }
])
function create(customer) { edit.value = customer?.id || null; initial.value = customer ? { ...customer } : { category: 'NORMAL', restricted: false, password: '88888888' }; open.value = true }
async function save(data) {
  busy.value = true
  try {
    const result = await api[edit.value ? 'put' : 'post']('/customers' + (edit.value ? '/' + edit.value : ''), data)
    open.value = false; await reload()
    if (detail.value?.id === result.id) detail.value = result
    ElMessage.success(edit.value ? '客户资料已保存' : '客户档案与登录账号已创建')
  } catch {} finally { busy.value = false }
}
function manageAccount(customer) {
  accountCustomer.value = customer
  accountInitial.value = account(customer.id) ? { ...account(customer.id) } : { username: 'customer' + customer.id, enabled: true, password: '88888888' }
  accountOpen.value = true
}
async function saveAccount(input) {
  const existing = account(accountCustomer.value.id)
  const data = { ...input, role: 'CUSTOMER', account_type: 'CUSTOMER', customer_id: accountCustomer.value.id, name: accountCustomer.value.name }
  busy.value = true
  try {
    await api[existing ? 'put' : 'post']('/customers/accounts' + (existing ? '/' + existing.id : ''), data)
    accountOpen.value = false; await reload(); ElMessage.success('客户账号已保存')
  } catch {} finally { busy.value = false }
}
async function removeAccount() {
  const user = account(accountCustomer.value.id)
  try {
    await ElMessageBox.prompt(`请输入账号 ${user.username} 确认删除登录身份。客户档案与历史订单仍保留。`, '删除客户登录账号', { type: 'warning', inputValidator: value => value === user.username || '请输入完整账号' })
    busy.value = true
    await api.delete('/customers/accounts/' + user.id, { data: { confirmation: 'DELETE' } })
    accountOpen.value = false; await reload(); ElMessage.success('客户登录账号已删除')
  } catch {} finally { busy.value = false }
}
function review(ticket, action) { selectedTicket.value = ticket; Object.assign(reviewForm, { action, verified: false, review_notes: '' }); reviewOpen.value = true; reviewRef.value?.clearValidate() }
async function submitReview() {
  if (!await reviewRef.value.validate().catch(() => false)) return
  busy.value = true
  try {
    await api.post(`/customers/reset-tickets/${selectedTicket.value.id}/review`, reviewForm)
    reviewOpen.value = false; await load()
    ElMessage.success(reviewForm.action === 'APPROVE' ? '密码已重置为 88888888，旧会话已失效' : '工单已驳回')
  } catch {} finally { busy.value = false }
}
const history = computed(() => state.orders.filter(order => order.customer_id === detail.value?.id))
// 信用记录由历史订单汇总，不额外编造评分。
const credit = computed(() => ({ overdue: history.value.filter(order => overdue(order) || (order.actual_end && order.actual_end > order.planned_end)).length, damage: history.value.filter(order => order.damage).length, completed: history.value.filter(order => order.status === 'COMPLETED').length }))
</script>

<template>
  <div class="module-overview">
    <div><strong>{{ state.customers.length }}</strong><span>客户档案</span></div>
    <div><strong class="text-accent">{{ accounts.length }}</strong><span>客户登录账号</span></div>
    <div><strong>{{ pending }}</strong><span>待核验重置工单</span></div>
    <div><strong>{{ state.customers.filter(item => item.restricted).length }}</strong><span>限制租赁</span></div>
    <el-button type="primary" :icon="Plus" @click="create()">新增客户</el-button>
  </div>
  <section class="panel listing-panel">
    <el-tabs v-model="tab">
      <el-tab-pane label="客户档案与账号" name="profiles">
        <div class="filter-bar">
          <el-input v-model="query.search" :prefix-icon="Search" placeholder="搜索姓名、联系方式、账号" clearable class="search-input"/>
          <el-select v-model="query.category" placeholder="全部类别" clearable><el-option label="普通客户" value="NORMAL"/><el-option label="会员客户" value="VIP"/></el-select>
          <el-select v-model="query.restricted" placeholder="全部租赁资格" clearable><el-option label="正常" :value="false"/><el-option label="限制租赁" :value="true"/></el-select>
          <el-button :disabled="busy" @click="reload">刷新</el-button>
        </div>
        <el-table :data="filtered.slice((page - 1) * 10, page * 10)" empty-text="暂无客户">
          <el-table-column label="客户" min-width="130"><template #default="{ row }"><strong>{{ row.name }}</strong><small class="cell-sub">C{{ String(row.id).padStart(4, '0') }}</small></template></el-table-column>
          <el-table-column label="登录账号" min-width="155"><template #default="{ row }"><span>{{ account(row.id)?.username || '未开通 / 已注销' }}</span><small v-if="account(row.id)" :class="['cell-sub', account(row.id).enabled ? 'text-green' : 'text-danger']">{{ account(row.id).enabled ? '已启用' : '已禁用' }} · {{ account(row.id).security_configured ? '已设密保' : '待设密保' }}</small></template></el-table-column>
          <el-table-column label="联系方式" min-width="130"><template #default="{ row }">{{ mask(row.phone) }}</template></el-table-column>
          <el-table-column label="驾驶证有效期" min-width="130"><template #default="{ row }"><span :class="{ 'text-danger': row.license_expiry < today() }">{{ row.license_expiry }}</span></template></el-table-column>
          <el-table-column label="类别" width="100"><template #default="{ row }"><span :class="['category-tag', { vip: row.category === 'VIP' }]">{{ labels[row.category] }}</span></template></el-table-column>
          <el-table-column label="租赁资格" width="100"><template #default="{ row }"><span :class="row.restricted ? 'text-danger' : 'text-green'">{{ row.restricted ? '限制租赁' : '正常' }}</span></template></el-table-column>
          <el-table-column label="操作" width="185" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="detail = row">详情</el-button><el-button link type="primary" @click="create(row)">编辑</el-button><el-button link type="primary" :icon="Key" @click="manageAccount(row)">账号</el-button></template></el-table-column>
        </el-table>
        <div class="pagination"><span>共 {{ filtered.length }} 位客户</span><el-pagination v-model:current-page="page" :page-size="10" :total="filtered.length" layout="prev, pager, next"/></div>
      </el-tab-pane>
      <el-tab-pane :label="`密码重置工单（${pending}）`" name="tickets">
        <div class="filter-bar"><el-select v-model="ticketStatus" placeholder="全部状态" clearable><el-option v-for="(label, value) in ticketLabels" :key="value" :value="value" :label="label"/></el-select><span class="filter-spacer"></span><el-button @click="load">刷新工单</el-button></div>
        <el-table :data="visibleTickets" empty-text="暂无密码重置工单">
          <el-table-column label="提交时间" min-width="165"><template #default="{ row }">{{ dateText(row.created_at) }}</template></el-table-column>
          <el-table-column prop="username" label="客户账号" min-width="135"/>
          <el-table-column label="档案 / 预留电话" min-width="160"><template #default="{ row }"><el-button link type="primary" @click="detail = state.customers.find(item => item.id === row.customer_id)">{{ state.customers.find(item => item.id === row.customer_id)?.name || '历史客户' }}</el-button><small class="cell-sub">{{ mask(row.contact) }}</small></template></el-table-column>
          <el-table-column prop="reason" label="申请原因" min-width="200" show-overflow-tooltip/>
          <el-table-column label="状态" width="100"><template #default="{ row }">{{ ticketLabels[row.status] }}</template></el-table-column>
          <el-table-column prop="review_notes" label="处理说明" min-width="180" show-overflow-tooltip/>
          <el-table-column label="操作" width="175" fixed="right"><template #default="{ row }"><template v-if="row.status === 'PENDING'"><el-button link type="primary" :icon="Check" @click="review(row, 'APPROVE')">核验重置</el-button><el-button link type="danger" :icon="Close" @click="review(row, 'REJECT')">驳回</el-button></template><span v-else>{{ row.reviewer }}</span></template></el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </section>
  <FormDialog v-model="open" :title="edit ? '编辑客户资料' : '登记新客户并开通账号'" :fields="fields" :initial="initial" :busy="busy" @submit="save"/>
  <FormDialog v-model="accountOpen" :title="`客户账号 · ${accountCustomer?.name || ''}`" :fields="accountFields" :initial="accountInitial" :busy="busy" @submit="saveAccount"/>
  <el-dialog v-model="reviewOpen" :title="reviewForm.action === 'APPROVE' ? '核验身份并重置密码' : '驳回重置申请'" width="540px" :close-on-click-modal="false">
    <template v-if="selectedTicket">
      <p>客户账号：<strong>{{ selectedTicket.username }}</strong> · 预留电话：{{ selectedTicket.contact }}</p>
      <p>申请原因：{{ selectedTicket.reason }}</p>
      <el-form ref="reviewRef" :model="reviewForm" label-position="top">
        <el-form-item label="核验 / 驳回说明" prop="review_notes" :rules="[{ required: true, message: '请填写实际核验方式或驳回原因', trigger: 'blur' }]"><el-input v-model="reviewForm.review_notes" type="textarea" :rows="3" maxlength="500" show-word-limit/></el-form-item>
        <el-checkbox v-if="reviewForm.action === 'APPROVE'" v-model="reviewForm.verified">已通过档案预留电话或到店证件核实本人身份</el-checkbox>
      </el-form>
    </template>
    <template #footer><el-button :disabled="busy" @click="reviewOpen = false">取消</el-button><el-button :type="reviewForm.action === 'APPROVE' ? 'primary' : 'danger'" :loading="busy" :disabled="reviewForm.action === 'APPROVE' && !reviewForm.verified" @click="submitReview">{{ reviewForm.action === 'APPROVE' ? '重置为 88888888' : '确认驳回' }}</el-button></template>
  </el-dialog>
  <el-drawer :model-value="!!detail" @close="detail = null" title="客户档案" size="580px">
    <template v-if="detail">
      <div class="profile-heading"><span class="avatar large-avatar">{{ detail.name.slice(-2) }}</span><h2>{{ detail.name }}</h2><span class="category-tag">{{ labels[detail.category] }}</span></div>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="登录账号">{{ account(detail.id)?.username || '未开通 / 已注销' }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ detail.phone }}</el-descriptions-item>
        <el-descriptions-item label="证件号">{{ detail.identity_no }}</el-descriptions-item>
        <el-descriptions-item label="驾驶证号">{{ mask(detail.license_no) }}</el-descriptions-item>
        <el-descriptions-item label="驾驶证有效期">{{ detail.license_expiry }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detail.notes || '无' }}</el-descriptions-item>
      </el-descriptions>
      <div class="customer-account-actions"><el-button :icon="Key" @click="manageAccount(detail)">管理登录账号</el-button><el-button v-if="account(detail.id)" type="danger" plain :icon="Delete" :disabled="busy" @click="accountCustomer = detail; removeAccount()">删除登录账号</el-button></div>
      <div class="credit-grid"><div><strong>{{ credit.completed }}</strong><span>正常完成</span></div><div><strong>{{ credit.overdue }}</strong><span>逾期记录</span></div><div><strong>{{ credit.damage }}</strong><span>损坏记录</span></div></div>
      <h3 class="section-title">历史租赁订单</h3>
      <el-table :data="history"><el-table-column prop="order_no" label="订单编号" min-width="160"/><el-table-column label="车辆" min-width="235"><template #default="{ row }"><VehicleIdentity :vehicle="vehicle(row.vehicle_id)"/></template></el-table-column><el-table-column label="租赁日期" min-width="160"><template #default="{ row }">{{ dateText(row.planned_start) }}</template></el-table-column><el-table-column label="状态" width="100"><template #default="{ row }"><StatusTag :value="row.status"/></template></el-table-column></el-table>
    </template>
  </el-drawer>
</template>
<style scoped>
.customer-account-actions { display: flex; flex-wrap: wrap; gap: 12px; margin-top: 20px; }
.filter-bar { flex-wrap: wrap; }
@media (max-width: 600px) {
  .filter-bar .search-input { width: 100%; flex-basis: 100%; }
  .filter-bar :deep(.el-select) { flex: 1; min-width: 130px; }
}
</style>
