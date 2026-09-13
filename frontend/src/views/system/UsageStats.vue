<template>
  <div class="usage-stats">
    <!-- 筛选栏 -->
    <el-card shadow="never">
      <div class="filter-bar">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          :shortcuts="dateShortcuts"
          :clearable="false"
          style="width: 280px"
        />
        <el-button type="primary" @click="loadAll">查询</el-button>
        <el-button-group class="quick-group">
          <el-button :type="quickActive === 7 ? 'primary' : ''" @click="setQuickRange(7)">近7天</el-button>
          <el-button :type="quickActive === 30 ? 'primary' : ''" @click="setQuickRange(30)">近30天</el-button>
        </el-button-group>
        <div class="spacer" />
        <el-button @click="openPriceDialog">单价配置</el-button>
        <el-button type="success" :loading="exporting" @click="handleExport">导出CSV</el-button>
      </div>
    </el-card>

    <!-- 统计卡片 -->
    <div class="stat-grid">
      <el-card shadow="never" class="stat-card">
        <div class="stat-label">调用次数</div>
        <div class="stat-value">{{ fmtNum(summary.calls) }}</div>
        <div class="stat-sub">区间内 LLM 请求总数</div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="stat-label">总 Token</div>
        <div class="stat-value">{{ fmtNum(summary.totalTokens) }}</div>
        <div class="stat-sub">输入 {{ fmtNum(summary.promptTokens) }} / 输出 {{ fmtNum(summary.completionTokens) }}</div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="stat-label">估算成本（元）</div>
        <div class="stat-value cost">¥ {{ fmtCost(summary.cost) }}</div>
        <div class="stat-sub">按当前模型单价估算</div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="stat-label">日均 Token</div>
        <div class="stat-value">{{ fmtNum(avgDailyTokens) }}</div>
        <div class="stat-sub">总 Token / 区间天数</div>
      </el-card>
    </div>

    <!-- 图表区 -->
    <el-card shadow="never">
      <template #header><span>每日趋势</span></template>
      <div ref="trendChartRef" class="chart-box" />
    </el-card>

    <div class="chart-row">
      <el-card shadow="never" class="chart-col">
        <template #header><span>模型 Token 分布</span></template>
        <div ref="modelChartRef" class="chart-box" />
      </el-card>
      <el-card shadow="never" class="chart-col">
        <template #header><span>操作类型分布</span></template>
        <div ref="operationChartRef" class="chart-box" />
      </el-card>
    </div>

    <!-- Top 用户 -->
    <el-card shadow="never">
      <template #header><span>用量 Top 10 用户</span></template>
      <el-table :data="topUsers" v-loading="loading" border stripe>
        <el-table-column type="index" label="排名" width="80" />
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column prop="calls" label="调用次数" width="120" />
        <el-table-column prop="promptTokens" label="输入Token" width="140">
          <template #default="{ row }">{{ fmtNum(row.promptTokens) }}</template>
        </el-table-column>
        <el-table-column prop="completionTokens" label="输出Token" width="140">
          <template #default="{ row }">{{ fmtNum(row.completionTokens) }}</template>
        </el-table-column>
        <el-table-column prop="totalTokens" label="总Token" width="140">
          <template #default="{ row }">{{ fmtNum(row.totalTokens) }}</template>
        </el-table-column>
        <el-table-column prop="cost" label="估算成本（元）" min-width="140">
          <template #default="{ row }">{{ fmtCost(row.cost) }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 单价配置弹窗 -->
    <el-dialog v-model="priceDialogVisible" title="模型单价配置（元 / 百万 Token）" width="720px">
      <el-alert type="info" :closable="false" show-icon class="price-tip">
        修改单价后，历史记录的成本估算也会按新价重新计算。
      </el-alert>
      <el-table :data="priceRows" border size="small">
        <el-table-column label="模型" min-width="170">
          <template #default="{ row }">
            <el-input v-if="!row.id" v-model="row.model" placeholder="如 deepseek-chat" />
            <span v-else>{{ row.model }}</span>
          </template>
        </el-table-column>
        <el-table-column label="输入单价" width="150">
          <template #default="{ row }">
            <el-input-number v-model="row.inputPrice" :min="0" :precision="6" :step="0.1" :controls="false" style="width: 120px" />
          </template>
        </el-table-column>
        <el-table-column label="输出单价" width="150">
          <template #default="{ row }">
            <el-input-number v-model="row.outputPrice" :min="0" :precision="6" :step="0.1" :controls="false" style="width: 120px" />
          </template>
        </el-table-column>
        <el-table-column label="启用" width="80">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" :active-value="1" :inactive-value="0" />
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="120">
          <template #default="{ row }">
            <el-input v-model="row.remark" placeholder="可选" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70">
          <template #default="{ $index }">
            <el-button link type="danger" size="small" @click="removePriceRow($index)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="add-price">
        <el-button size="small" @click="addPriceRow">+ 新增模型</el-button>
      </div>
      <template #footer>
        <el-button @click="priceDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="priceSaving" @click="handleSavePrices">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import {
  usageApi,
  priceApi,
  type UsageSummary,
  type UsageDailyRow,
  type UsageGroupRow,
  type UsageTopUserRow,
  type ModelPrice
} from '../../api/admin'
import { saveBlobResponse, timestampName } from '../../utils/download'

const OPERATION_LABELS: Record<string, string> = {
  chat: '对话',
  translate: '翻译',
  summary: '摘要',
  unknown: '未知'
}

const loading = ref(false)
const exporting = ref(false)

// 默认近 7 天(含今天)
const fmtDate = (d: Date) => {
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}
const defaultRange = (): [string, string] => {
  const end = new Date()
  const start = new Date(); start.setDate(start.getDate() - 6)
  return [fmtDate(start), fmtDate(end)]
}

const dateRange = ref<[string, string]>(defaultRange())
const quickActive = ref<number>(7)
const dateShortcuts = [
  { text: '近7天', value: () => { const e = new Date(); const s = new Date(); s.setDate(s.getDate() - 6); return [s, e] } },
  { text: '近30天', value: () => { const e = new Date(); const s = new Date(); s.setDate(s.getDate() - 29); return [s, e] } }
]

const summary = reactive<UsageSummary>({ calls: 0, promptTokens: 0, completionTokens: 0, totalTokens: 0, cost: 0 })
const dailyRows = ref<UsageDailyRow[]>([])
const modelRows = ref<UsageGroupRow[]>([])
const operationRows = ref<UsageGroupRow[]>([])
const topUsers = ref<UsageTopUserRow[]>([])

const rangeDays = computed(() => {
  if (!dateRange.value?.[0] || !dateRange.value?.[1]) return 1
  const ms = new Date(dateRange.value[1]).getTime() - new Date(dateRange.value[0]).getTime()
  return Math.max(1, Math.round(ms / 86400000) + 1)
})
const avgDailyTokens = computed(() => Math.round((summary.totalTokens || 0) / rangeDays.value))

const fmtNum = (v: number | string | undefined | null) => Number(v || 0).toLocaleString('zh-CN')
const fmtCost = (v: number | string | undefined | null) => Number(v || 0).toFixed(4)

function setQuickRange(days: number) {
  const end = new Date()
  const start = new Date(); start.setDate(start.getDate() - (days - 1))
  dateRange.value = [fmtDate(start), fmtDate(end)]
  quickActive.value = days
  loadAll()
}

async function loadAll() {
  if (!dateRange.value || !dateRange.value[0] || !dateRange.value[1]) {
    ElMessage.warning('请选择日期范围')
    return
  }
  loading.value = true
  const params = { start: dateRange.value[0], end: dateRange.value[1] }
  try {
    const [s, d, m, o, u] = await Promise.all([
      usageApi.summary(params),
      usageApi.daily(params),
      usageApi.byModel(params),
      usageApi.byOperation(params),
      usageApi.topUsers(params)
    ])
    Object.assign(summary, s.data || {})
    dailyRows.value = d.data || []
    modelRows.value = m.data || []
    operationRows.value = o.data || []
    topUsers.value = u.data || []
    await nextTick()
    renderCharts()
  } finally {
    loading.value = false
  }
}

async function handleExport() {
  exporting.value = true
  try {
    const params = { start: dateRange.value[0], end: dateRange.value[1] }
    const res = await usageApi.exportCsv(params)
    saveBlobResponse(res, timestampName('llm-usage'))
  } finally {
    exporting.value = false
  }
}

// ---------------- 单价配置 ----------------
const priceDialogVisible = ref(false)
const priceRows = ref<ModelPrice[]>([])
const priceSaving = ref(false)

async function openPriceDialog() {
  const res = await priceApi.list()
  priceRows.value = (res.data || []).map((r: ModelPrice) => ({ ...r }))
  priceDialogVisible.value = true
}

function addPriceRow() {
  priceRows.value.push({ model: '', inputPrice: 0, outputPrice: 0, enabled: 1, remark: '' })
}

function removePriceRow(index: number) {
  priceRows.value.splice(index, 1)
}

async function handleSavePrices() {
  for (const row of priceRows.value) {
    if (!row.model || !row.model.trim()) {
      ElMessage.warning('模型名不能为空（未配置的新行请移除）')
      return
    }
  }
  priceSaving.value = true
  try {
    await priceApi.save(priceRows.value)
    ElMessage.success('单价已保存')
    priceDialogVisible.value = false
    await loadAll()
  } finally {
    priceSaving.value = false
  }
}

// ---------------- ECharts ----------------
const trendChartRef = ref<HTMLElement>()
const modelChartRef = ref<HTMLElement>()
const operationChartRef = ref<HTMLElement>()
let trendChart: echarts.ECharts | null = null
let modelChart: echarts.ECharts | null = null
let operationChart: echarts.ECharts | null = null
let resizeObserver: ResizeObserver | null = null

function renderCharts() {
  // 每日趋势: Token 柱 + 成本折线(双轴)
  trendChart = trendChart || echarts.init(trendChartRef.value)
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['总Token', '输入Token', '输出Token', '成本（元）'], top: 0 },
    grid: { left: 60, right: 60, top: 40, bottom: 40 },
    xAxis: { type: 'category', data: dailyRows.value.map(r => r.date) },
    yAxis: [
      { type: 'value', name: 'Token' },
      { type: 'value', name: '元', scale: true }
    ],
    series: [
      { name: '总Token', type: 'bar', data: dailyRows.value.map(r => r.totalTokens), itemStyle: { color: '#409eff' } },
      { name: '输入Token', type: 'line', smooth: true, data: dailyRows.value.map(r => r.promptTokens) },
      { name: '输出Token', type: 'line', smooth: true, data: dailyRows.value.map(r => r.completionTokens) },
      {
        name: '成本（元）', type: 'line', smooth: true, yAxisIndex: 1,
        data: dailyRows.value.map(r => Number(Number(r.cost || 0).toFixed(4))),
        itemStyle: { color: '#f56c6c' }
      }
    ]
  }, true)

  // 模型饼图
  modelChart = modelChart || echarts.init(modelChartRef.value)
  modelChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}<br/>Token: {c} ({d}%)' },
    legend: { bottom: 0 },
    series: [{
      type: 'pie',
      radius: ['40%', '68%'],
      center: ['50%', '45%'],
      data: modelRows.value.map(r => ({ name: r.model || '未知', value: r.totalTokens }))
    }]
  }, true)

  // 操作类型柱状
  operationChart = operationChart || echarts.init(operationChartRef.value)
  operationChart.setOption({
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        const idx = params[0]?.dataIndex ?? 0
        const r = operationRows.value[idx]
        if (!r) return ''
        return `${params[0].name}<br/>调用: ${r.calls} 次<br/>Token: ${r.totalTokens}<br/>成本: ${fmtCost(r.cost)} 元`
      }
    },
    grid: { left: 60, right: 20, top: 30, bottom: 40 },
    xAxis: {
      type: 'category',
      data: operationRows.value.map(r => OPERATION_LABELS[r.operation || ''] || r.operation || '未知')
    },
    yAxis: { type: 'value', name: 'Token' },
    series: [{
      type: 'bar',
      barMaxWidth: 60,
      data: operationRows.value.map(r => r.totalTokens),
      itemStyle: { color: '#67c23a' }
    }]
  }, true)
}

onMounted(() => {
  loadAll()
  resizeObserver = new ResizeObserver(() => {
    trendChart?.resize()
    modelChart?.resize()
    operationChart?.resize()
  })
  if (trendChartRef.value) resizeObserver.observe(trendChartRef.value)
  if (modelChartRef.value) resizeObserver.observe(modelChartRef.value)
  if (operationChartRef.value) resizeObserver.observe(operationChartRef.value)
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  trendChart?.dispose()
  modelChart?.dispose()
  operationChart?.dispose()
})
</script>

<style scoped>
.usage-stats {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.spacer {
  flex: 1;
}

.quick-group {
  margin-left: -4px;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
}

.stat-card {
  border-top: 3px solid #409eff;
}

.stat-label {
  font-size: 13px;
  color: #909399;
}

.stat-value {
  font-size: 26px;
  font-weight: 600;
  color: #303133;
  margin: 8px 0 4px;
}

.stat-value.cost {
  color: #f56c6c;
}

.stat-sub {
  font-size: 12px;
  color: #c0c4cc;
}

.chart-box {
  height: 320px;
  width: 100%;
}

.chart-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

@media (max-width: 1100px) {
  .chart-row {
    grid-template-columns: 1fr;
  }
}

.price-tip {
  margin-bottom: 12px;
}

.add-price {
  margin-top: 10px;
}
</style>
