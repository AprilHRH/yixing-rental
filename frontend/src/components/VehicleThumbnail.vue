<script setup>
import { computed, ref, watch } from 'vue'
import { Van } from '@element-plus/icons-vue'
import { vehiclePhotoSources } from '../vehiclePhotos'
import { vehiclePlateShields } from '../vehiclePlateShields'

const props = defineProps({
  vehicle: { type: Object, default: () => ({}) },
  size: { type: String, default: 'thumbnail', validator: value => ['thumbnail', 'card', 'detail'].includes(value) },
})
const failed = ref([]), loaded = ref(false)
const sources = computed(() => vehiclePhotoSources(props.vehicle))
const photo = computed(() => sources.value.find(item => !failed.value.includes(item.src)))
watch(() => photo.value?.src, () => loaded.value = false)
const plateShield = computed(() => photo.value?.reference ? vehiclePlateShields[photo.value.src] : null)
const description = computed(() => photo.value?.reference ? `${props.vehicle.brand || ''} ${props.vehicle.model || ''} 车型参考图，非该车实拍` : `${props.vehicle.plate || ''} 车辆档案照片`)
watch(() => [props.vehicle.image, props.vehicle.brand, props.vehicle.model], () => { failed.value = [] })
function imageError() { if (photo.value) failed.value.push(photo.value.src) }
</script>

<template>
  <figure class="vehicle-thumbnail" :class="[{ 'is-reference': photo?.reference }, `vehicle-photo--${size}`]" :title="photo ? description : '暂无实车照片，上传档案照片后自动显示'">
    <template v-if="photo"><img :src="photo.src" :alt="description" decoding="async" @load="loaded = true" @error="imageError"><svg v-if="plateShield && loaded" class="vehicle-plate-shield" viewBox="0 0 960 520" preserveAspectRatio="xMidYMid meet" aria-hidden="true"><rect :x="plateShield[0]" :y="plateShield[1]" :width="plateShield[2]" :height="plateShield[3]" rx="3" fill="#2a3442" stroke="#172331" stroke-width="2"/></svg><figcaption v-if="photo.reference">{{ size === 'thumbnail' ? '参考图' : '车型参考' }}</figcaption></template>
    <span v-else class="vehicle-thumbnail-empty"><el-icon><Van /></el-icon><small>暂无照片</small></span>
  </figure>
</template>
