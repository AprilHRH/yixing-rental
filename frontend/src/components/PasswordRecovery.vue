<script setup>
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'
const props = defineProps({ modelValue: Boolean, username: String })
const emit = defineEmits(['update:modelValue'])
const tab = ref('answer'), form = reactive({}), question = ref(null), formRef = ref(null), busy = ref(false)
const rules = label => [{ required: true, message: '请填写' + label, trigger: ['blur', 'change'] }]
watch(() => props.modelValue, value => {
  if (value) { Object.assign(form, { username: props.username || '', security_answer: '', new_password: '', confirm: '', phone: '', identity_no: '', reason: '' }); tab.value = 'answer'; question.value = null; formRef.value?.clearValidate() }
})
watch(() => form.username, () => question.value = null)
async function loadQuestion() {
  if (!form.username?.trim()) return ElMessage.warning('请先输入客户账号')
  busy.value = true
  try { question.value = await api.post('/auth/recovery/question', { username: form.username }) } catch {} finally { busy.value = false }
}
async function submit() {
  if (!await formRef.value.validate().catch(() => false)) return
  if (tab.value === 'answer' && form.new_password !== form.confirm) return ElMessage.warning('两次输入的新密码不一致')
  busy.value = true
  try {
    if (tab.value === 'answer') {
      await api.post('/auth/recovery/password', form)
      ElMessage.success('密码已更新，请重新登录')
    } else {
      const result = await api.post('/auth/recovery/tickets', form)
      ElMessage.success({ message: result.message, duration: 8000 })
    }
    emit('update:modelValue', false)
  } catch {} finally { busy.value = false }
}
</script>
<template>
  <el-dialog :model-value="modelValue" title="找回客户密码" width="520px" :close-on-click-modal="false" @update:model-value="emit('update:modelValue', $event)">
    <el-tabs v-model="tab" @tab-change="formRef?.clearValidate()"><el-tab-pane label="密保找回" name="answer"/><el-tab-pane label="忘记密保：提交工单" name="ticket"/></el-tabs>
    <el-form ref="formRef" :model="form" label-position="top" @submit.prevent="submit">
      <el-form-item label="客户账号" prop="username" :rules="rules('客户账号')"><el-input v-model="form.username" maxlength="30" autocomplete="username"/></el-form-item>
      <template v-if="tab === 'answer'">
        <el-button :loading="busy" @click="loadQuestion">查询密保问题</el-button>
        <p class="recovery-question">{{ question?.question || (question ? '暂未设置有效密保，请提交工单或联系门店。' : '') }}</p>
        <template v-if="question?.question">
          <el-form-item label="密保答案" prop="security_answer" :rules="rules('密保答案')"><el-input v-model="form.security_answer" type="password" show-password maxlength="60"/></el-form-item>
          <el-form-item label="新密码" prop="new_password" :rules="[...rules('新密码'), { min: 8, message: '密码至少 8 位', trigger: 'blur' }]"><el-input v-model="form.new_password" type="password" show-password maxlength="64" autocomplete="new-password"/></el-form-item>
          <el-form-item label="确认新密码" prop="confirm" :rules="rules('确认新密码')"><el-input v-model="form.confirm" type="password" show-password maxlength="64" autocomplete="new-password"/></el-form-item>
        </template>
      </template>
      <template v-else>
        <el-alert title="由门店核验本人身份后重置为 88888888，重新登录后须设置新密保。" type="info" :closable="false" class="ticket-notice"/>
        <el-form-item label="档案预留手机号" prop="phone" :rules="rules('预留手机号')"><el-input v-model="form.phone" maxlength="20"/></el-form-item>
        <el-form-item label="档案证件号" prop="identity_no" :rules="rules('档案证件号')"><el-input v-model="form.identity_no" maxlength="30"/></el-form-item>
        <el-form-item label="申请原因" prop="reason" :rules="rules('申请原因')"><el-input v-model="form.reason" type="textarea" :rows="3" maxlength="500" show-word-limit/></el-form-item>
      </template>
    </el-form>
    <template #footer><el-button :disabled="busy" @click="emit('update:modelValue', false)">取消</el-button><el-button type="primary" :loading="busy" :disabled="tab === 'answer' && !question?.question" @click="submit">{{ tab === 'answer' ? '确认修改密码' : '提交重置工单' }}</el-button></template>
  </el-dialog>
</template>
<style scoped>
.recovery-question { min-height: 24px; line-height: 1.6; margin: 16px 0; }
.ticket-notice { margin-bottom: 18px; }
</style>
