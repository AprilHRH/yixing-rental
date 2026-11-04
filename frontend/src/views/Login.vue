<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Right, User, Lock } from '@element-plus/icons-vue'
import { api } from '../api'
import { state } from '../store'
import { securityQuestions } from '../security'
import PasswordRecovery from '../components/PasswordRecovery.vue'
const recoveryOpen = ref(false)
const router = useRouter(), portal = ref('ADMIN'), registering = ref(false), busy = ref(false), formRef = ref(null)
// 公网构建可关闭本地演示账号提示，避免自动填入已经更换的默认密码。
const showDemoAccounts = import.meta.env.VITE_SHOW_DEMO_ACCOUNTS !== 'false'
const form = reactive({ username: showDemoAccounts ? 'admin' : '', password: showDemoAccounts ? '88888888' : '', confirm: '', name: '', phone: '', identity_no: '', license_no: '', license_expiry: '', security_question: '', security_answer: '' })
const customerMode = computed(() => portal.value === 'CUSTOMER')
function changePortal() { registering.value = false; formRef.value?.clearValidate(); form.username = ''; form.password = ''; form.confirm = '' }
function account(type) { portal.value = 'ADMIN'; registering.value = false; form.username = type; form.password = '88888888' }
async function submit() {
  // 同一表单支持客户注册和登录；注册成功不自动登录，需再次验证账号密码。
  if (!await formRef.value.validate().catch(() => false)) return
  if (registering.value && form.password !== form.confirm) return ElMessage.warning('两次输入的密码不一致')
  busy.value = true
  try {
    if (registering.value) {
      await api.post('/auth/register', { ...form, role: 'CUSTOMER' }); registering.value = false; form.password = ''; form.confirm = ''; ElMessage.success('注册成功，请使用客户身份登录'); return
    }
    state.user = await api.post('/auth/login', { username: form.username, password: form.password, portal: portal.value })
    await router.push(customerMode.value ? '/portal' : '/')
  } catch {} finally { busy.value = false }
}
const required = label => [{ required: true, message: '请填写' + label, trigger: ['blur', 'change'] }]
</script>
<template>
  <div class="login-page"><section class="login-story"><a class="brand" href="#/login"><span class="brand-mark"><svg viewBox="0 0 32 32"><path d="M7 23 16 8h6L13 23H7Zm12 0 5-8h5l-5 8h-5Z" fill="currentColor"/></svg></span><span>易行租车<small>YIXING RENTAL</small></span></a><div class="login-story-copy"><h1>{{ customerMode ? '每段旅程，' : '出发有方向，' }}<br>{{ customerMode ? '都有好车相伴。' : '管理有章法。' }}</h1><p>{{ customerMode ? '选好车，约好时间，安心出发。' : '车辆、订单、运营，在同一个工作空间。' }}</p></div><figure class="login-photograph"><img src="/images/rental-forest.webp" alt="森林公路上的汽车品牌配图，非库存实拍" width="1500" height="1876"><figcaption>易行租车 · 品质出行</figcaption></figure></section>
    <section class="login-form-section"><div class="login-form" :class="{ 'registration-form': registering }"><el-radio-group v-model="portal" class="login-identity-tabs" aria-label="登录身份" @change="changePortal"><el-radio-button value="ADMIN">管理员登录</el-radio-button><el-radio-button value="CUSTOMER">客户登录</el-radio-button></el-radio-group><h2>{{ registering ? '开启你的易行账户' : customerMode ? '下一程，从这里出发' : '欢迎回到易行' }}</h2><p>{{ registering ? (showDemoAccounts ? '填写真实资料，用于驾驶资格校验与预约。' : '填写演示资料，用于驾驶资格校验与预约。') : customerMode ? '登录后可预约车辆、查看订单和管理个人资料。' : '管理员与业务人员共用管理端，所有业务集中办理。' }}</p>
      <el-form ref="formRef" :model="form" label-position="top" :class="{ 'registration-grid': registering }" @submit.prevent="submit"><el-form-item label="账号" prop="username" :rules="required('账号')"><el-input v-model="form.username" :prefix-icon="User" placeholder="3–30 位字母、数字或下划线" maxlength="30" size="large" autocomplete="username"/></el-form-item><el-form-item label="密码" prop="password" :rules="registering ? [...required('密码'), { min: 8, message: '密码至少 8 位', trigger: 'blur' }] : required('密码')"><el-input v-model="form.password" :prefix-icon="Lock" type="password" show-password placeholder="请输入密码" maxlength="64" size="large" :autocomplete="registering ? 'new-password' : 'current-password'"/></el-form-item>
      <template v-if="registering"><el-form-item label="确认密码" prop="confirm" :rules="required('确认密码')"><el-input v-model="form.confirm" type="password" show-password autocomplete="new-password" size="large"/></el-form-item><el-form-item label="姓名" prop="name" :rules="required('姓名')"><el-input v-model="form.name" maxlength="60" size="large"/></el-form-item><el-form-item label="手机号" prop="phone" :rules="required('手机号')"><el-input v-model="form.phone" maxlength="20" size="large" autocomplete="tel"/></el-form-item><el-form-item label="身份证号" prop="identity_no" :rules="required('身份证号')"><el-input v-model="form.identity_no" maxlength="30" size="large"/></el-form-item><el-form-item label="驾驶证号" prop="license_no" :rules="required('驾驶证号')"><el-input v-model="form.license_no" maxlength="80" size="large"/></el-form-item><el-form-item label="驾驶证有效期至" prop="license_expiry" :rules="required('驾驶证有效期')"><el-date-picker v-model="form.license_expiry" type="date" value-format="YYYY-MM-DD" :disabled-date="date => date.getTime() < new Date().setHours(0, 0, 0, 0)" size="large"/></el-form-item></template>
      <template v-if="registering">
        <el-form-item label="密保问题" prop="security_question" :rules="required('密保问题')"><el-select v-model="form.security_question" size="large"><el-option v-for="item in securityQuestions" :key="item.value" :label="item.label" :value="item.value"/></el-select></el-form-item>
        <el-form-item label="密保答案" prop="security_answer" :rules="[...required('密保答案'), { min: 2, message: '答案至少 2 个字符', trigger: 'blur' }]"><el-input v-model="form.security_answer" type="password" show-password maxlength="60" size="large" autocomplete="off"/></el-form-item>
      </template>
      <el-button type="primary" size="large" class="login-submit full-width" native-type="submit" :loading="busy">{{ registering ? '注册客户账户' : customerMode ? '登录我的易行' : '登录工作空间' }}<el-icon><Right/></el-icon></el-button></el-form>
      <el-button v-if="customerMode && !registering" link type="primary" class="forgot-password" @click="recoveryOpen = true">忘记密码 / 密保？</el-button>
      <div v-if="customerMode" class="register-entry"><span>{{ registering ? '已有账户？' : '还没有账户？' }}</span><el-button link type="primary" @click="registering = !registering; formRef?.clearValidate()">{{ registering ? '返回登录' : '注册客户账户' }}</el-button></div>
      <div v-else-if="showDemoAccounts" class="demo-account"><strong>课程演示账号</strong><p>两类团队账号拥有相同管理权限</p><div><button @click="account('admin')">管理员 <span>admin</span></button><button @click="account('staff')">业务人员 <span>staff</span></button></div><small>初始密码统一为 88888888</small></div><div class="login-note">{{ showDemoAccounts ? '本地课程项目 · 车型图片为参考图' : '演示环境 · 请勿提交真实个人资料' }}</div>
    </div></section>
    <PasswordRecovery v-model="recoveryOpen" :username="form.username"/>
  </div>
</template>

<style scoped>
.forgot-password { margin-top: 14px; }
.login-page {
  font-family: "Microsoft YaHei UI", "PingFang SC", "Microsoft YaHei", "Segoe UI", sans-serif;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.login-story, .login-form-section {
  min-width: 0;
}
.login-form {
  min-width: 0;
}
.login-story {
  position: relative;
  isolation: isolate;
  display: flex;
  flex-direction: column;
  min-height: 100svh;
  padding: 44px 48px;
  gap: 0;
  background: #192d35;
}
.login-photograph {
  position: absolute;
  inset: 0;
  z-index: -2;
  display: block;
  min-height: 0;
  margin: 0;
  border-radius: 0;
}
.login-photograph img {
  object-position: 50% 64%;
}
.login-story::after {
  content: "";
  position: absolute;
  inset: 0;
  z-index: -1;
  pointer-events: none;
  background: linear-gradient(180deg, #0c1824b8 0%, #0c182470 30%, #0c18240a 58%, #0c182452 100%);
}
.login-story .brand {
  align-self: flex-start;
  color: #fff;
  font-size: 25px;
  font-weight: 700;
  letter-spacing: .5px;
}
.login-story .brand small {
  margin-top: 6px;
  color: #e6edf3;
  font-family: "Arial", "Segoe UI", sans-serif;
  font-size: 10px;
  font-weight: 500;
  letter-spacing: 2px;
}
.login-story-copy {
  display: block;
  margin: 64px 0 0;
  padding: 0;
  max-width: 570px;
}
.login-story-copy h1 {
  margin: 0;
  color: #fff;
  font-size: clamp(34px, 3.3vw, 52px);
  font-weight: 700;
  line-height: 1.42;
  letter-spacing: 1px;
  text-shadow: 0 2px 16px #0c182430;
}
.login-story-copy p {
  display: block;
  margin-top: 22px;
  color: #f2f4f6;
  font-size: 14px;
  font-weight: 400;
  line-height: 1.8;
  letter-spacing: .4px;
}
.login-photograph figcaption {
  left: 48px;
  bottom: 32px;
  padding: 0;
  color: #fff;
  font-size: 12px;
  letter-spacing: 1px;
  background: none;
  text-shadow: 0 1px 6px #0c1824;
}
.login-form h2 {
  font-weight: 700;
  letter-spacing: .3px;
}
.login-form > p {
  font-size: 14px;
  line-height: 1.8;
}
.login-form :deep(.el-form-item__label),
.login-form :deep(.el-input__inner),
.login-form :deep(.el-radio-button__inner),
.login-form :deep(.el-button) {
  font-family: inherit;
  font-size: 14px;
}
.login-form :deep(.el-form-item__label) {
  font-weight: 500;
}
@media (max-width: 1100px) and (min-width: 769px) {
  .login-story { padding: 36px 30px; }
  .login-story-copy { margin-top: 54px; }
  .login-story-copy h1 { font-size: 36px; }
  .login-photograph figcaption { left: 30px; }
}
@media (max-width: 768px) {
  .login-page { grid-template-columns: minmax(0, 1fr); }
  .login-story { min-height: 280px; padding: 24px; }
  .login-story .brand { font-size: 21px; }
  .login-story-copy { margin-top: 24px; }
  .login-story-copy h1 { font-size: 28px; line-height: 1.4; letter-spacing: .5px; }
  .login-story-copy br { display: block; }
  .login-story-copy p { margin-top: 12px; font-size: 12px; }
  .login-photograph img { object-position: 50% 55%; }
  .login-story::after { background: linear-gradient(90deg, #0c1824cf, #0c18244a); }
  .login-photograph figcaption { display: none; }
}
</style>
