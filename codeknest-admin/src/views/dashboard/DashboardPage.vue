<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { getOverview, getTrends } from '@/api/dashboard'
import type { Overview, TrendPoint } from '@/api/types'

const router = useRouter()
const overview = ref<Overview | null>(null)
const days = ref(7)
const loading = ref(false)

const chartRef = ref<HTMLDivElement | null>(null)
const chart = shallowRef<echarts.ECharts | null>(null)

onMounted(async () => {
  if (chartRef.value) {
    chart.value = echarts.init(chartRef.value)
    window.addEventListener('resize', handleResize)
  }
  await Promise.all([loadOverview(), loadTrends()])
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chart.value?.dispose()
})

function handleResize() {
  chart.value?.resize()
}

async function loadOverview() {
  const res = await getOverview()
  overview.value = res.data
}

async function loadTrends() {
  loading.value = true
  try {
    const res = await getTrends(days.value)
    renderChart(res.data)
  } finally {
    loading.value = false
  }
}

async function switchDays(n: number) {
  days.value = n
  await loadTrends()
}

function renderChart(points: TrendPoint[]) {
  if (!chart.value) return
  chart.value.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['新增用户', '新增文章', '新增评论'], bottom: 0 },
    grid: { left: 40, right: 20, top: 30, bottom: 40 },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: points.map((p) => p.date.slice(5)),
    },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        name: '新增用户',
        type: 'line',
        smooth: true,
        itemStyle: { color: '#409eff' },
        areaStyle: { opacity: 0.08 },
        data: points.map((p) => p.newUsers),
      },
      {
        name: '新增文章',
        type: 'line',
        smooth: true,
        itemStyle: { color: '#67c23a' },
        areaStyle: { opacity: 0.08 },
        data: points.map((p) => p.newPosts),
      },
      {
        name: '新增评论',
        type: 'line',
        smooth: true,
        itemStyle: { color: '#e6a23c' },
        areaStyle: { opacity: 0.08 },
        data: points.map((p) => p.newComments),
      },
    ],
  })
}

function gotoPending() {
  router.push({ path: '/posts', query: { status: '2' } })
}
</script>

<template>
  <div>
    <h2 class="page-title">仪表盘</h2>

    <el-row :gutter="16">
      <el-col :span="6">
        <el-card shadow="hover" class="metric">
          <div class="metric-label">总用户数</div>
          <div class="metric-value blue">{{ overview?.userCount ?? '-' }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="metric">
          <div class="metric-label">总文章数</div>
          <div class="metric-value green">{{ overview?.postCount ?? '-' }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="metric">
          <div class="metric-label">总评论数</div>
          <div class="metric-value orange">{{ overview?.commentCount ?? '-' }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="metric">
          <div class="metric-label">总访问量</div>
          <div class="metric-value purple">{{ overview?.totalViews ?? '-' }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="8">
        <el-card shadow="hover" class="sub-metric">
          <div class="metric-label">今日新增用户</div>
          <div class="sub-value">{{ overview?.todayNewUsers ?? '-' }}</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="sub-metric">
          <div class="metric-label">今日新增文章</div>
          <div class="sub-value">{{ overview?.todayNewPosts ?? '-' }}</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="sub-metric clickable" @click="gotoPending">
          <div class="metric-label">
            待审核文章
            <el-icon class="goto-icon"><ArrowRight /></el-icon>
          </div>
          <div class="sub-value red">{{ overview?.pendingPosts ?? '-' }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="chart-header">
          <span>近 {{ days }} 天新增趋势</span>
          <el-radio-group :model-value="days" size="small" @change="switchDays(Number($event))">
            <el-radio-button :value="7">7 天</el-radio-button>
            <el-radio-button :value="30">30 天</el-radio-button>
          </el-radio-group>
        </div>
      </template>
      <div ref="chartRef" v-loading="loading" class="trend-chart"></div>
    </el-card>
  </div>
</template>

<style lang="scss" scoped>
.page-title {
  margin: 0 0 20px;
  font-size: 20px;
}
.metric-label {
  font-size: 13px;
  color: #909399;
}
.metric-value {
  font-size: 28px;
  font-weight: 600;
  margin-top: 8px;
}
.sub-value {
  font-size: 24px;
  font-weight: 600;
  margin-top: 6px;
}
.blue {
  color: #409eff;
}
.green {
  color: #67c23a;
}
.orange {
  color: #e6a23c;
}
.purple {
  color: #722ed1;
}
.red {
  color: #f56c6c;
}
.clickable {
  cursor: pointer;
}
.goto-icon {
  margin-left: 4px;
  vertical-align: middle;
}
.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.trend-chart {
  height: 340px;
}
</style>
