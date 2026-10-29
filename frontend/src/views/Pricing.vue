<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Right, Wallet } from '@element-plus/icons-vue'
import { api } from '../api'
import { state, rule, vehicle, money, today, localTime } from '../store'
import VehicleThumbnail from '../components/VehicleThumbnail.vue'
import VehicleIdentity from '../components/VehicleIdentity.vue'
const year = new Date().getFullYear()
const form = reactive({ vehicle_id: state.vehicles[0]?.id, billing_mode: 'DAILY', dates: [today(), localTime(new Date(Date.now() + 86400000 * 2)).slice(0, 10)], mileage: 200, month: new Date().getMonth() + 1 })
const result = ref(null), busy = ref(false), selectedCar = computed(() => vehicle(form.vehicle_id)), selectedRule = computed(() => rule(selectedCar.value.pricing_id))
watch(form, () => result.value = null)
const monthlyStart = computed(() => `${year}-${String(form.month).padStart(2, '0')}-01T00:00:00`)
const monthlyEnd = computed(() => { const date = new Date(year, form.month - 1, 1); date.setDate(date.getDate() + 30); return localTime(date) })
const unitPrice = computed(() => form.billing_mode === 'DAILY' ? selectedRule.value.daily_rate : form.billing_mode === 'MONTHLY' ? selectedRule.value.monthly_rate : selectedRule.value.mileage_rate)
async function calculate() {
  if (!form.vehicle_id) return ElMessage.warning('请选择车辆')
  if (form.billing_mode === 'DAILY' && (!form.dates?.[0] || !form.dates?.[1] || form.dates[1] <= form.dates[0])) return ElMessage.warning('请选择租赁日期，还车日期须晚于取车日期')
  if (form.billing_mode === 'MILEAGE' && (!Number.isInteger(form.mileage) || form.mileage < 0)) return ElMessage.warning('请输入 0 至 1000000 的整数公里数')
  const payload = { vehicle_id: form.vehicle_id, billing_mode: form.billing_mode, expected_mileage: form.billing_mode === 'MILEAGE' ? form.mileage : 0, planned_start: form.billing_mode === 'DAILY' ? form.dates[0] + 'T00:00:00' : form.billing_mode === 'MONTHLY' ? monthlyStart.value : today() + 'T00:00:00', planned_end: form.billing_mode === 'DAILY' ? form.dates[1] + 'T00:00:00' : form.billing_mode === 'MONTHLY' ? monthlyEnd.value : localTime(new Date(new Date().setDate(new Date().getDate() + 1))).slice(0, 10) + 'T00:00:00' }
  busy.value = true
  try { result.value = await api.post('/pricing/quote', payload) } catch { result.value = null } finally { busy.value = false }
}
</script>
<template>
  <div class="two-column pricing-layout calculator-layout"><section class="panel"><div class="panel-heading"><div><h3>租金试算</h3><p>按所选车辆的实际计费价格计算，不创建订单。</p></div><el-icon class="panel-symbol"><Wallet/></el-icon></div><el-form label-position="top" class="pricing-form" @submit.prevent="calculate"><el-form-item label="选择车辆"><el-select v-model="form.vehicle_id" filterable popper-class="vehicle-select-options"><el-option v-for="car in state.vehicles" :key="car.id" :label="car.plate + ' · ' + car.model" :aria-label="car.plate + ' · ' + car.model" :value="car.id"><VehicleIdentity :vehicle="car"/></el-option></el-select></el-form-item><el-form-item label="计费方式"><el-radio-group v-model="form.billing_mode" aria-label="计费方式"><el-radio-button value="DAILY">按天</el-radio-button><el-radio-button value="MILEAGE">按里程</el-radio-button><el-radio-button value="MONTHLY">包月</el-radio-button></el-radio-group></el-form-item>
  <el-form-item v-if="form.billing_mode === 'DAILY'" label="租赁日期"><el-date-picker v-model="form.dates" type="daterange" value-format="YYYY-MM-DD" start-placeholder="取车日期" end-placeholder="还车日期" range-separator="至" :clearable="false"/></el-form-item>
  <el-form-item v-else-if="form.billing_mode === 'MILEAGE'" label="预计行驶里程（km）"><el-input-number v-model="form.mileage" :min="0" :max="1000000" :precision="0" controls-position="right"/></el-form-item>
  <template v-else><el-form-item :label="year + ' 年起租月份'"><el-select v-model="form.month" aria-label="起租月份"><el-option v-for="month in 12" :key="month" :label="year + ' 年 ' + month + ' 月'" :value="month"/></el-select></el-form-item><p class="calculator-month-note">从 {{ monthlyStart.slice(0, 10) }} 起租，至 {{ monthlyEnd.slice(0, 10) }}。沿用系统 30 天计费月口径，不将自然月天数混入已有价格规则。</p></template>
  <el-button type="primary" size="large" native-type="submit" :loading="busy">计算预计租金<el-icon><Right/></el-icon></el-button></el-form>
  <div v-if="result" class="calculator-result" role="status"><span>预计基础租金</span><strong>¥{{ money(result.rent) }}</strong><p>{{ result.formula }}</p><div><span>{{ form.billing_mode === 'MILEAGE' ? result.mileage + ' km' : '计费时长 ' + result.days + ' 天' }}</span><span>应收押金 ¥{{ money(result.deposit) }}</span></div><small>不含附加费用和优惠。可预约情况与实际租金以订单校验、还车结算为准。</small></div>
  </section><section class="panel selected-car-quote"><div class="panel-heading"><div><h3>当前车辆</h3><p>车辆切换后价格自动更新</p></div></div><VehicleThumbnail :vehicle="selectedCar" size="detail"/><h2>{{ selectedCar.brand }} {{ selectedCar.model }}</h2><p>{{ selectedCar.plate }} · {{ selectedCar.seats }} 座 · {{ selectedCar.color }}</p><div class="quote-unit-price"><span>{{ form.billing_mode === 'DAILY' ? '日租价格' : form.billing_mode === 'MONTHLY' ? '30 天包月价格' : '每公里价格' }}</span><strong>¥{{ money(unitPrice) }}<small>{{ form.billing_mode === 'DAILY' ? '/ 天' : form.billing_mode === 'MONTHLY' ? '/ 30 天' : '/ km' }}</small></strong></div><p v-if="form.billing_mode === 'MILEAGE'">另加起租费 ¥{{ money(selectedRule.base_fee) }}</p><p class="quote-footnote">按天：每 24 小时计 1 天。按里程：起租费 + 公里数 × 单价。包月：30 天计 1 个月。押金不计入租金收入。</p></section></div>
</template>
