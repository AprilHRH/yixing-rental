<script setup>
import { onMounted, onBeforeUnmount, watch, ref } from 'vue'
import * as echarts from 'echarts/core'
import { LineChart, BarChart, PieChart } from 'echarts/charts'
import { TooltipComponent, GridComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
echarts.use([LineChart, BarChart, PieChart, TooltipComponent, GridComponent, LegendComponent, CanvasRenderer])
const props = defineProps({ option: Object, height: { type: Number, default: 280 }, label: { type: String, default: '业务数据统计图' } })
const element = ref(null)
let chart, observer
onMounted(() => { chart = echarts.init(element.value); chart.setOption(props.option); observer = new ResizeObserver(() => chart.resize()); observer.observe(element.value) })
watch(() => props.option, value => chart?.setOption(value, true), { deep: true })
onBeforeUnmount(() => { observer?.disconnect(); chart?.dispose() })
</script>
<template><div ref="element" :style="{ height: height + 'px', width: '100%' }" role="img" :aria-label="label"></div></template>
