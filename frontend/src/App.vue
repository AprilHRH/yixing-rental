<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Odometer, Van, User, Tickets, Wallet, Connection, Tools, DataAnalysis, Setting, ArrowRight, Refresh, SwitchButton, Lock, Bell } from '@element-plus/icons-vue'
import { api } from './api'
import { state, refresh, isAdmin, today } from './store'
import FormDialog from './components/FormDialog.vue'
import CustomerSecurity from './components/CustomerSecurity.vue'
const route = useRoute(), router = useRouter(), ready = ref(false), passwordOpen = ref(false), busy = ref(false)
const modules = [
  { path: '/', label: '工作台', icon: Odometer, group: '总览' },
  { path: '/vehicles', label: '车辆信息', icon: Van, group: '业务办理' },
  { path: '/customers', label: '客户管理', icon: User },
  { path: '/orders', label: '租赁订单', icon: Tickets },
  { path: '/pricing', label: '租金计算', icon: Wallet },
  { path: '/dispatch', label: '车辆调度', icon: Connection, group: '车队运营' },
  { path: '/maintenance', label: '维修保养', icon: Tools },
  { path: '/reports', label: '统计报表', icon: DataAnalysis, group: '经营与设置', admin: true },
  { path: '/settings', label: '系统管理', icon: Setting, admin: true },
]
const customerMode = computed(() => state.user?.role === 'CUSTOMER')
const securityOpen = ref(false)
const securityRequired = computed(() => customerMode.value && !state.user.security_configured)
const securityVisible = computed({ get: () => route.path !== '/login' && (securityRequired.value || securityOpen.value), set: value => securityOpen.value = value })
// 管理端与客户端共用页面外壳，菜单随登录身份切换；菜单隐藏本身不代替接口权限检查。
const visibleModules = computed(() => customerMode.value ? [{ path: '/portal', label: '选车出行', icon: Van, group: '我的易行' }, { path: '/portal/orders', label: '我的订单', icon: Tickets }, { path: '/portal/profile', label: '个人资料', icon: User }] : modules.filter(module => !module.admin || isAdmin.value))
const date = computed(() => new Date(state.updatedAt || Date.now()).toLocaleDateString('zh-CN', { month: 'long', day: 'numeric', weekday: 'long' }))
const pendingOrders = computed(() => state.orders.filter(order => ['RESERVED', 'RETURNED'].includes(order.status)).length)
const hasDemo = computed(() => state.orders.some(order => order.order_no?.startsWith('YX-DEMO-')))
let syncing = false
async function load() {
  if (!state.user || syncing) return
  syncing = true
  try { await refresh(); ready.value = true } catch { ready.value = false } finally { syncing = false }
}
onMounted(load)
let syncTimer
onMounted(() => {
  syncTimer = window.setInterval(() => {
    if (state.user && document.visibilityState === 'visible' && Date.now() - (state.updatedAt || 0) >= 10000) load()
  }, 15000)
  document.addEventListener('visibilitychange', syncWhenVisible)
})
function syncWhenVisible() {
  if (document.visibilityState === 'visible' && state.user && Date.now() - (state.updatedAt || 0) >= 5000) load()
}
onUnmounted(() => {
  window.clearInterval(syncTimer)
  document.removeEventListener('visibilitychange', syncWhenVisible)
})
// 登录或退出后重新判断是否加载业务数据，加载成功才显示内部页面。
watch(() => state.user?.id, value => { if (value) load(); else ready.value = false })
async function logout() { await api.post('/auth/logout'); state.user = null; router.push('/login') }
async function command(value) {
  if (value === 'logout') { try { await ElMessageBox.confirm('确认退出当前账号？', '退出登录'); await logout() } catch {} }
  else if (value === 'security' || (customerMode.value && securityRequired.value)) securityOpen.value = true
  else passwordOpen.value = true
}
async function password(data) {
  busy.value = true
  try { await api.post('/auth/password', data); passwordOpen.value = false; state.user = null; router.push('/login'); ElMessage.success('密码已修改，请重新登录') } catch {} finally { busy.value = false }
}
</script>

<template>
  <router-view v-if="route.path === '/login'" />
  <div v-else class="app-shell">
    <aside class="sidebar">
      <router-link :to="customerMode ? '/portal' : '/'" class="brand"><span class="brand-mark"><svg viewBox="0 0 32 32"><path d="M7 23 16 8h6L13 23H7Zm12 0 5-8h5l-5 8h-5Z" fill="currentColor"/></svg></span><span>易行租车<small>YIXING RENTAL</small></span></router-link>
      <div class="workspace-label">{{ customerMode ? '易行出行服务' : '租赁业务管理' }}</div>
      <nav aria-label="业务模块">
        <template v-for="module in visibleModules" :key="module.path">
          <div v-if="module.group" class="nav-group">{{ module.group }}</div>
          <router-link :to="module.path" :title="module.label" :aria-label="module.label" :aria-current="route.path === module.path ? 'page' : undefined" :class="['nav-item', { active: route.path === module.path }]"><el-icon><component :is="module.icon" /></el-icon><span>{{ module.label }}</span><small v-if="module.path === '/orders' && pendingOrders" class="nav-count" :aria-label="`${pendingOrders} 笔待取车或待结算订单`">{{ pendingOrders }}</small></router-link>
        </template>
      </nav>
      <div class="sidebar-bottom"><div><strong>让出行更简单</strong>本地工作空间<small>{{ hasDemo ? '含教学演示数据' : '连接当前业务数据库' }}</small></div></div>
    </aside>
    <section class="main-shell">
      <header class="topbar">
        <div class="breadcrumbs"><router-link :to="customerMode ? '/portal' : '/'">{{ customerMode ? '我的易行' : '工作空间' }}</router-link><el-icon><ArrowRight /></el-icon><h1 v-if="route.path === '/'" class="workspace-title">{{ route.meta.title }}</h1><span v-else>{{ route.meta.title }}</span></div>
        <div class="topbar-actions"><span class="header-date">{{ date }}</span><button class="icon-button" title="刷新业务数据" aria-label="刷新业务数据" :disabled="state.loading" @click="load"><el-icon :class="{ spinning: state.loading }"><Refresh /></el-icon></button><button v-if="!customerMode && state.reminders.length" class="icon-button notification" title="查看保养提醒" :aria-label="`查看保养提醒，${state.reminders.length} 项`" @click="router.push('/maintenance')"><el-icon><Bell /></el-icon><i v-if="state.reminders.length"></i></button><div class="top-divider"></div><el-dropdown @command="command"><button class="user-button"><span class="avatar">{{ state.user?.name?.slice(-2) }}</span><span>{{ state.user?.name }}<small>{{ customerMode ? '客户' : state.user?.account_type === 'STAFF' ? '业务人员 · 管理权限' : '系统管理员' }}</small></span></button><template #dropdown><el-dropdown-menu><el-dropdown-item command="password" :icon="Lock">修改密码</el-dropdown-item><el-dropdown-item v-if="customerMode" command="security" :icon="Lock">密保设置</el-dropdown-item><el-dropdown-item command="logout" :icon="SwitchButton">退出登录</el-dropdown-item></el-dropdown-menu></template></el-dropdown></div>
      </header>
      <main class="page-main" v-loading="state.loading && !ready">
        <div v-if="route.path !== '/'" class="page-heading"><div><h1>{{ route.meta.title }}</h1><p>{{ route.meta.subtitle }}</p></div></div>
        <router-view v-if="ready" />
        <el-result v-else-if="!state.loading" icon="warning" title="暂未连接到业务服务" sub-title="请确认后端已启动后重试"><template #extra><el-button type="primary" @click="load">重新连接</el-button></template></el-result>
        <footer v-if="route.path !== '/'" class="page-footer"><span>易行租车 · 租赁业务管理</span><span>{{ state.updatedAt ? `数据更新于 ${new Date(state.updatedAt).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}` : today() }}</span></footer>
      </main>
    </section>
    <CustomerSecurity v-model="securityVisible" :required="securityRequired"/>
    <FormDialog v-model="passwordOpen" title="修改登录密码" :initial="{}" :busy="busy" :fields="[{ key: 'old_password', label: '原密码', type: 'password', wide: true }, { key: 'new_password', label: '新密码', type: 'password', wide: true, tip: '至少 8 位，修改后需重新登录' }]" @submit="password" />
  </div>
</template>
