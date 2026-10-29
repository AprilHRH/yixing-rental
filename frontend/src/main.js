import { createApp } from 'vue'
import { createRouter, createWebHashHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import './styles.css'
import './workspace.css'
import './refinement.css'
import App from './App.vue'
import Login from './views/Login.vue'
import Dashboard from './views/Dashboard.vue'
import Vehicles from './views/Vehicles.vue'
import Customers from './views/Customers.vue'
import Orders from './views/Orders.vue'
import Pricing from './views/Pricing.vue'
import Dispatch from './views/Dispatch.vue'
import Maintenance from './views/Maintenance.vue'
import Reports from './views/Reports.vue'
import Settings from './views/Settings.vue'
import CustomerPortal from './views/CustomerPortal.vue'
import { api } from './api'
import { state } from './store'

const router = createRouter({ history: createWebHashHistory(), routes: [
  { path: '/portal', component: CustomerPortal, meta: { title: '选车出行', subtitle: '挑选合适的车，为下一段旅程做好准备。', customer: true } },
  { path: '/portal/orders', component: CustomerPortal, meta: { title: '我的订单', subtitle: '跟踪预约、取还车安排和收退款记录。', customer: true } },
  { path: '/portal/profile', component: CustomerPortal, meta: { title: '个人资料', subtitle: '保持联系方式与驾驶证信息准确。', customer: true } },
  { path: '/login', component: Login, meta: { title: '登录' } },
  { path: '/', component: Dashboard, meta: { title: '工作台', subtitle: '掌握经营动态，让每一次出行井然有序。', group: '概览' } },
  { path: '/vehicles', component: Vehicles, meta: { title: '车辆信息', subtitle: '管理车辆档案、门店归属、租赁状态与保养信息。', group: '车辆信息管理' } },
  { path: '/customers', component: Customers, meta: { title: '客户管理', subtitle: '查询客户资料、驾驶资格与信用记录。', group: '客户管理' } },
  { path: '/orders', component: Orders, meta: { title: '租赁订单', subtitle: '办理预约、交车、还车与结算，查看订单和收退款记录。', group: '租赁订单管理' } },
  { path: '/pricing', component: Pricing, meta: { title: '租金计算', subtitle: '选择车辆与计费方式，按实际价格试算租金。', group: '租金计算' } },
  { path: '/dispatch', component: Dispatch, meta: { title: '车辆调度', subtitle: '查看各门店可用车辆，安排调拨并跟踪到达情况。', group: '车辆调度' } },
  { path: '/maintenance', component: Maintenance, meta: { title: '维修保养', subtitle: '处理维修保养工单，查看养护提醒与历史费用。', group: '车辆维修保养' } },
  { path: '/reports', component: Reports, meta: { title: '统计报表', subtitle: '按日期查看已结算收入、订单情况与车辆使用记录。', group: '报表统计', admin: true } },
  { path: '/settings', component: Settings, meta: { title: '系统管理', subtitle: '统一管理账号、门店与系统参数。', group: '系统管理', admin: true } },
  { path: '/:pathMatch(.*)*', redirect: '/' },
] })
router.beforeEach(to => {
  if (!state.user && to.path !== '/login') return '/login'
  if (state.user && to.path === '/login') return state.user.role === 'CUSTOMER' ? '/portal' : '/'
  if (state.user?.role === 'CUSTOMER' && !to.meta.customer) return '/portal'
  if (state.user && state.user.role !== 'CUSTOMER' && to.meta.customer) return '/'
  if (to.meta.admin && state.user?.role !== 'ADMIN') return '/'
})
window.addEventListener('auth:expired', () => { state.user = null; router.push('/login') })
async function start() {
  try { state.user = await api.get('/auth/me', { silent: true }) } catch { state.user = null }
  createApp(App).use(router).use(ElementPlus, { locale: zhCn }).mount('#app')
}
start()
