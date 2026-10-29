<script setup>
import { reactive, ref, watch } from 'vue'
import { api } from '../api'
import { vehicle } from '../store'
import VehicleIdentity from './VehicleIdentity.vue'
const props = defineProps({ modelValue: Boolean, title: String, fields: Array, initial: Object, busy: Boolean, width: { type: Number, default: 660 } })
const emit = defineEmits(['update:modelValue', 'submit'])
const form = reactive({}), formRef = ref(null), uploading = ref(false)
watch(() => props.modelValue, value => { if (value) { Object.keys(form).forEach(key => delete form[key]); Object.assign(form, props.initial || {}); formRef.value?.clearValidate() } })
const options = field => typeof field.options === 'function' ? field.options(form) : field.options || []
const upload = async event => {
  const file = event.target.files[0]
  if (!file) return
  uploading.value = true
  try { const data = new FormData(); data.append('file', file); const result = await api.post('/uploads', data); form.image = result.url } catch {} finally { uploading.value = false; event.target.value = '' }
}
async function submit() { if (await formRef.value.validate().catch(() => false)) emit('submit', { ...form }) }
</script>
<template>
  <el-dialog :model-value="modelValue" @update:model-value="emit('update:modelValue', $event)" :title="title" :width="width" destroy-on-close :close-on-click-modal="false">
    <el-form ref="formRef" :model="form" label-position="top" class="form-grid">
      <el-form-item v-for="field in fields.filter(item => !item.show || item.show(form))" :key="field.key" :label="field.label" :prop="field.key" :class="{ 'full-width': field.wide }" :rules="field.required === false ? [] : [{ required: true, message: '请填写' + field.label, trigger: ['blur', 'change'] }]">
        <el-select v-if="field.type === 'select'" :popper-class="field.key === 'vehicle_id' ? 'vehicle-select-options' : ''" v-model="form[field.key]" :disabled="field.disabled" filterable placeholder="请选择"><el-option v-for="option in options(field)" :key="option.value" :label="option.label" :aria-label="option.label" :value="option.value"><VehicleIdentity v-if="field.key === 'vehicle_id'" :vehicle="vehicle(option.value)"/><span v-else>{{ option.label }}</span></el-option></el-select>
        <el-input-number v-else-if="field.type === 'number'" v-model="form[field.key]" :min="field.min ?? 0" :max="field.max ?? 99999999" :precision="field.precision ?? 0" controls-position="right" :disabled="field.disabled" />
        <el-date-picker v-else-if="field.type === 'date' || field.type === 'datetime'" v-model="form[field.key]" :type="field.type" :value-format="field.type === 'date' ? 'YYYY-MM-DD' : 'YYYY-MM-DDTHH:mm:ss'" :disabled="field.disabled" placeholder="请选择日期" />
        <el-switch v-else-if="field.type === 'switch'" v-model="form[field.key]" />
        <div v-else-if="field.type === 'upload'" class="upload-control"><img v-if="form.image" :src="form.image" alt="车辆图片"><label class="upload-label">{{ uploading ? '上传中…' : '选择 JPG / PNG 图片' }}<input type="file" accept="image/png,image/jpeg" @change="upload" :disabled="uploading" hidden></label><small>不超过 5MB</small></div>
        <el-input v-else v-model="form[field.key]" :type="field.type === 'textarea' ? 'textarea' : field.type === 'password' ? 'password' : 'text'" :rows="3" :show-password="field.type === 'password'" :disabled="field.disabled" :maxlength="field.maxlength || 200" :placeholder="field.placeholder || '请输入' + field.label" />
        <small v-if="field.tip" class="field-tip">{{ field.tip }}</small>
      </el-form-item>
    </el-form>
    <template #footer><el-button @click="emit('update:modelValue', false)">取消</el-button><el-button type="primary" :loading="busy || uploading" @click="submit">确认保存</el-button></template>
  </el-dialog>
</template>
