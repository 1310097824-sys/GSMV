<template>
  <div class="chart-shell">
    <div class="chart-shell__grid" />
    <div ref="chartRef" class="chart-panel" />
  </div>
</template>

<script setup lang="ts">
import * as echarts from 'echarts'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps<{
  option: echarts.EChartsOption
}>()

const chartRef = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null

const render = () => {
  if (!chartRef.value) {
    return
  }
  chart ??= echarts.init(chartRef.value)
  chart.setOption(props.option, true)
}

onMounted(() => {
  render()
  window.addEventListener('resize', render)
})

watch(() => props.option, render, { deep: true })

onBeforeUnmount(() => {
  window.removeEventListener('resize', render)
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.chart-shell {
  position: relative;
  min-height: 360px;
  border-radius: 22px;
  border: 1px solid rgba(174, 244, 255, 0.12);
  background:
    radial-gradient(circle at 14% 0%, rgba(130, 239, 255, 0.08), transparent 22%),
    linear-gradient(180deg, rgba(5, 22, 52, 0.42), rgba(3, 16, 40, 0.2));
  overflow: hidden;
}

.chart-shell__grid {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(rgba(255, 255, 255, 0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.03) 1px, transparent 1px);
  background-size: 42px 42px;
  mask-image: linear-gradient(180deg, rgba(0, 0, 0, 0.45), transparent 92%);
  pointer-events: none;
}

.chart-panel {
  position: relative;
  z-index: 1;
  min-height: 360px;
}
</style>
