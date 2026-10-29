<script setup>
import { labels, statusClass } from '../store'
defineProps({ value: String })
</script>
<template><span :class="['status-tag', statusClass(value)]"><i></i>{{ labels[value] || value }}</span></template>
