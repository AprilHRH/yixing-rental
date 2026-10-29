<script setup>
import VehicleThumbnail from './VehicleThumbnail.vue'
defineProps({ vehicle: { type: Object, default: () => ({}) } })
</script>
<template>
  <div class="vehicle-identity"><VehicleThumbnail :vehicle="vehicle"/><div><strong>{{ vehicle.brand }} {{ vehicle.model || '车辆档案已变更' }}</strong><span>{{ vehicle.plate || '—' }}</span></div></div>
</template>
