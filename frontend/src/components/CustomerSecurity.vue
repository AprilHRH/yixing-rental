<script setup>
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'
import { state } from '../store'
import { securityQuestions } from '../security'
const props = defineProps({ modelValue: Boolean, required: Boolean })
const emit = defineEmits(['update:modelValue'])
const form = reactive({}), formRef = ref(null), busy = ref(false)
const rules = label => [{ required: true, message: '请填写' + label, trigger: ['blur', 'change'] }]
watch(() => props.modelValue, value => {
  if (value) { Object.assign(form, { password: '', old_security_answer: '', security_question: '', security_answer: '', confirm: '' }); formRef.value?.clearValidate() }
}, { immediate: true })
async function save() {
  if (!await formRef.value.validate().catch(() => false)) return
  if (form.security_answer !== form.confirm) return ElMessage.warning('两次输入的密保答案不一致')
  busy.value = true
  try {
    state.user = await api.post('/auth/security', form)
    emit('update:modelValue', false)
    ElMessage.success('密保已保存')
  } catch {} finally { busy.value = false }
}
</script>
<template>
  <el-dialog :model-value="modelValue" :title="required ? '首次登录：设置密保' : '修改密保问题'" width="500px"
    :show-close="!required && !busy" :close-on-click-modal="false" :close-on-press-escape="!required && !busy"
    @update:model-value="emit('update:modelValue', $event)">
    <el-alert v-if="required" title="请先设置本人的密保答案，再办理预约或修改密码。" type="warning" :closable="false"/>
    <el-form ref="formRef" :model="form" label-position="top" class="security-form" @submit.prevent="save">
      <el-form-item label="当前登录密码" prop="password" :rules="rules('当前登录密码')"><el-input v-model="form.password" type="password" show-password maxlength="64" autocomplete="current-password"/></el-form-item>
      <el-form-item v-if="state.user?.security_configured" label="原密保答案" prop="old_security_answer" :rules="rules('原密保答案')"><el-input v-model="form.old_security_answer" type="password" show-password maxlength="60"/><small class="field-tip">忘记答案请退出登录后提交密码重置工单。</small></el-form-item>
      <el-form-item label="密保问题" prop="security_question" :rules="rules('密保问题')"><el-select v-model="form.security_question"><el-option v-for="item in securityQuestions" :key="item.value" :value="item.value" :label="item.label"/></el-select></el-form-item>
      <el-form-item label="密保答案" prop="security_answer" :rules="[...rules('密保答案'), { min: 2, message: '答案至少 2 个字符', trigger: 'blur' }]"><el-input v-model="form.security_answer" type="password" show-password maxlength="60" autocomplete="off"/></el-form-item>
      <el-form-item label="确认密保答案" prop="confirm" :rules="rules('确认密保答案')"><el-input v-model="form.confirm" type="password" show-password maxlength="60" autocomplete="off"/></el-form-item>
    </el-form>
    <template #footer><el-button v-if="!required" :disabled="busy" @click="emit('update:modelValue', false)">取消</el-button><el-button type="primary" :loading="busy" @click="save">保存密保</el-button></template>
  </el-dialog>
</template>
<style scoped>
.security-form { margin-top: 18px; }
.security-form :deep(.el-select) { width: 100%; }
</style>
