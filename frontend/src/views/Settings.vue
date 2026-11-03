<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Download, Upload, Search, Delete, Edit } from '@element-plus/icons-vue'
import { api, download } from '../api'
import { state, refresh, labels, dateText } from '../store'
import FormDialog from '../components/FormDialog.vue'
const tab = ref('users'), data = reactive({ users: [], logs: [], settings: {} }), settings = reactive({}), open = ref(false), kind = ref('user'), initial = ref({}), editId = ref(null), busy = ref(false), restoreFile = ref(null)
const search = ref(''), accountType = ref(''), enabled = ref('')
const types = [{ label: '管理员', value: 'ADMIN' }, { label: '业务人员', value: 'STAFF' }, { label: '客户', value: 'CUSTOMER' }]
const users = computed(() => data.users.filter(user => {
  const keyword = search.value.trim().toLowerCase()
  return (!keyword || `${user.name} ${user.username}`.toLowerCase().includes(keyword))
    && (!accountType.value || user.account_type === accountType.value)
    && (typeof enabled.value !== 'boolean' || user.enabled === enabled.value)
}))
async function load() { try { Object.assign(data, await api.get('/system')); Object.assign(settings, data.settings) } catch {} }
onMounted(load)
const fields = computed(() => kind.value === 'user' ? [
  { key: 'username', label: '登录账号', tip: '3 至 30 位字母、数字或下划线' },
  { key: 'name', label: '姓名' },
  { key: 'password', label: editId.value ? '重置密码（留空不修改）' : '初始密码', type: 'password', required: !editId.value, tip: '至少 8 位，重置后原登录会话失效' },
  { key: 'account_type', label: '账号类型', type: 'select', options: types, disabled: !!editId.value && initial.value.account_type === 'CUSTOMER' },
  { key: 'new_customer', label: '同时新建客户档案', type: 'switch', required: false, show: form => !editId.value && form.account_type === 'CUSTOMER' },
  { key: 'customer_id', label: '关联客户档案', type: 'select', wide: true, disabled: !!editId.value && initial.value.account_type === 'CUSTOMER', show: form => form.account_type === 'CUSTOMER' && (editId.value || !form.new_customer), options: state.customers.filter(customer => !data.users.some(user => user.customer_id === customer.id && user.id !== editId.value)).map(customer => ({ label: customer.name + ' · ' + customer.phone, value: customer.id })), tip: '一个档案仅关联一个账号，历史订单仍归属于原客户档案' },
  ...[{ key: 'phone', label: '手机号' }, { key: 'identity_no', label: '证件号' }, { key: 'license_no', label: '驾驶证号' }, { key: 'license_expiry', label: '驾驶证有效期至', type: 'date' }].map(field => ({ ...field, show: form => !editId.value && form.account_type === 'CUSTOMER' && form.new_customer })),
  { key: 'enabled', label: '账号启用', type: 'switch', required: false }
] : [{ key: 'name', label: '门店名称', wide: true }, { key: 'address', label: '门店地址', wide: true }, { key: 'phone', label: '联系电话', wide: true }])
function create(type, row) { kind.value = type; editId.value = row?.id || null; initial.value = row ? { ...row, password: '' } : type === 'user' ? { enabled: true, account_type: 'STAFF', new_customer: true } : {}; open.value = true }
// 管理员和业务人员共用管理权限，客户使用独立权限；保存后刷新设置与全局业务数据。
async function save(input) {
  if (kind.value === 'user') {
    input.role = input.account_type === 'CUSTOMER' ? 'CUSTOMER' : 'ADMIN'
    if (!editId.value && input.role === 'CUSTOMER' && input.new_customer) delete input.customer_id
    delete input.new_customer
  }
  busy.value = true
  try { const resource = kind.value === 'user' ? 'users' : 'branches'; await api[editId.value ? 'put' : 'post'](`/system/${resource}` + (editId.value ? '/' + editId.value : ''), input); open.value = false; await Promise.all([load(), refresh()]); ElMessage.success('设置已保存') } catch {} finally { busy.value = false }
}
async function deleteAccount(user) {
  try {
    await ElMessageBox.prompt(`删除后该账号无法登录，客户档案及历史业务记录保留。请输入账号 ${user.username} 确认。`, '删除登录账号', {
      type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消',
      inputValidator: value => value === user.username || '请输入完整的登录账号'
    })
    busy.value = true
    await api.delete(`/system/users/${user.id}`, { data: { confirmation: 'DELETE' } })
    await Promise.all([load(), refresh()])
    ElMessage.success('登录账号已删除')
  } catch {} finally { busy.value = false }
}
async function saveSettings() { busy.value = true; try { await api.put('/system/settings', settings); await Promise.all([load(), refresh()]); ElMessage.success('系统参数已保存') } catch {} finally { busy.value = false } }
async function backup() { busy.value = true; try { const blob = await api.get('/system/backup', { responseType: 'blob' }); download(blob, '易行租车备份-' + new Date().toISOString().slice(0, 10) + '.zip', 'application/zip'); await load(); ElMessage.success('备份已导出，请妥善保管') } catch {} finally { busy.value = false } }
async function restore(event) {
  // 先尝试导出当前备份，再要求人工确认已保存并输入确认词；恢复后需重新登录。
  const file = event.target.files[0]; event.target.value = ''; if (!file) return
  try {
    await backup()
    const result = await ElMessageBox.prompt('当前数据将被备份内容替换，恢复后需要重新登录。系统已尝试导出当前备份，请确认已保存，再输入 RESTORE 继续。', '恢复数据', { inputPattern: /^RESTORE$/, inputErrorMessage: '请输入 RESTORE', confirmButtonText: '确认恢复', type: 'warning' })
    busy.value = true; const payload = new FormData(); payload.append('file', file); payload.append('confirmation', result.value); await api.post('/system/restore', payload); state.user = null; window.dispatchEvent(new Event('auth:expired')); ElMessage.success('数据已恢复，请重新登录')
  } catch {} finally { busy.value = false }
}
</script>
<template>
  <section class="panel settings-panel">
    <el-tabs v-model="tab">
      <el-tab-pane label="账号与权限" name="users">
        <div class="settings-section-heading">
          <h3>账号管理</h3>
          <el-button type="primary" :icon="Plus" :disabled="busy" @click="create('user')">新增账号</el-button>
        </div>
        <div class="filter-bar account-filters">
          <el-input v-model="search" class="search-input" :prefix-icon="Search" placeholder="搜索姓名、登录账号" clearable aria-label="搜索账号"/>
          <el-select v-model="accountType" placeholder="全部账号类型" clearable aria-label="账号类型筛选"><el-option v-for="item in types" :key="item.value" :label="item.label" :value="item.value"/></el-select>
          <el-select v-model="enabled" placeholder="全部状态" clearable aria-label="账号状态筛选"><el-option label="已启用" :value="true"/><el-option label="已禁用" :value="false"/></el-select>
          <span class="text-muted">{{ users.length }} 个账号</span>
        </div>
        <el-table :data="users" empty-text="暂无匹配的账号">
          <el-table-column prop="name" label="姓名" min-width="120"/>
          <el-table-column prop="username" label="登录账号" min-width="150"/>
          <el-table-column label="类型" min-width="100"><template #default="{ row }">{{ labels[row.account_type] || labels[row.role] }}</template></el-table-column>
          <el-table-column label="状态" min-width="90"><template #default="{ row }"><span :class="row.enabled ? 'text-green' : 'text-danger'">{{ row.enabled ? '已启用' : '已禁用' }}</span></template></el-table-column>
          <el-table-column label="操作" width="165" fixed="right"><template #default="{ row }">
            <el-tooltip content="编辑账号或重置密码"><el-button link type="primary" :icon="Edit" :disabled="busy" @click="create('user', row)">编辑</el-button></el-tooltip>
            <el-tooltip :content="row.id === state.user?.id ? '不能删除当前登录账号' : '删除登录账号，保留业务记录'">
              <span><el-button link type="danger" :icon="Delete" :disabled="busy || row.id === state.user?.id" @click="deleteAccount(row)">删除</el-button></span>
            </el-tooltip>
          </template></el-table-column>
        </el-table>
        <div class="permission-note"><h4>权限说明</h4><p><strong>管理员：</strong>全部业务模块、经营报表、账号、门店、计费参数与数据备份恢复。</p><p><strong>业务人员：</strong>与管理员共用完整管理权限，账号类型用于区分人员身份。</p><p><strong>客户：</strong>公开注册或由管理员开通；仅可访问本人的资料、订单及选车预约。无未完成订单时可自行注销账号。</p></div>
      </el-tab-pane>
      <el-tab-pane label="门店管理" name="branches">
        <div class="settings-section-heading"><div><h3>运营门店</h3><p>车辆所属门店通过调度或实际归还更新。</p></div><el-button type="primary" :icon="Plus" @click="create('branch')">新增门店</el-button></div>
        <el-table :data="state.branches"><el-table-column prop="name" label="门店"/><el-table-column prop="address" label="地址" min-width="240"/><el-table-column prop="phone" label="电话"/><el-table-column label="操作" width="100"><template #default="{ row }"><el-button link type="primary" @click="create('branch', row)">编辑</el-button></template></el-table-column></el-table>
      </el-tab-pane>
      <el-tab-pane label="系统参数" name="parameters">
        <div class="settings-section-heading"><div><h3>基础参数</h3><p>提醒规则任一满足即可触发。</p></div></div>
        <el-form label-position="top" class="settings-form"><el-form-item label="提前保养提醒天数"><el-input-number v-model="settings.reminder_days" :min="0" :max="365"/></el-form-item><el-form-item label="提前保养提醒里程（km）"><el-input-number v-model="settings.reminder_km" :min="0" :max="100000"/></el-form-item><el-button type="primary" :loading="busy" @click="saveSettings">保存参数</el-button></el-form>
      </el-tab-pane>
      <el-tab-pane label="操作日志" name="logs">
        <div class="settings-section-heading"><div><h3>关键操作记录</h3><p>展示最近 300 条记录，不保存密码和完整证件信息。</p></div><el-button @click="load">刷新日志</el-button></div>
        <el-table :data="data.logs" max-height="540"><el-table-column label="时间" min-width="180"><template #default="{ row }">{{ dateText(row.created_at) }}</template></el-table-column><el-table-column prop="operator" label="操作人" width="120"/><el-table-column prop="action" label="操作" min-width="180"/><el-table-column prop="target" label="对象" min-width="220"/></el-table>
      </el-tab-pane>
      <el-tab-pane label="数据备份与恢复" name="backup">
        <div class="backup-intro"><h2>保存完整业务快照</h2><p>ZIP 备份包含数据库记录和已上传车辆图片。账号密码以哈希形式保存，但备份仍包含敏感业务资料，请妥善保管，仅导入本系统生成的可信备份。</p></div>
        <div class="backup-grid"><section><el-icon><Download/></el-icon><h3>导出数据备份</h3><p>导出当前账号、车辆、客户、订单、收退款、调度、工单及图片。</p><el-button type="primary" :loading="busy" @click="backup">下载 ZIP 备份</el-button></section><section><el-icon><Upload/></el-icon><h3>恢复可信备份</h3><p>替换当前业务记录。恢复前导出当前数据，二次确认后执行，恢复完成需重新登录。</p><el-button :loading="busy" @click="restoreFile.click()">选择 ZIP 备份</el-button><input ref="restoreFile" type="file" accept=".zip" hidden @change="restore"/></section></div>
      </el-tab-pane>
    </el-tabs>
  </section>
  <FormDialog v-model="open" :title="(editId ? '编辑' : '新增') + (kind === 'user' ? '账号' : '门店')" :fields="fields" :initial="initial" :busy="busy" @submit="save"/>
</template>
<style scoped>
.account-filters { flex-wrap: wrap; }
.account-filters :deep(.el-select) { width: 160px; }
@media (max-width: 600px) {
  .account-filters .search-input { width: 100%; flex-basis: 100%; }
  .account-filters :deep(.el-select) { flex: 1; min-width: 125px; }
}
</style>
