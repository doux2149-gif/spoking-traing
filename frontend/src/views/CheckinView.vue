<template>
  <div class="checkin-page">
    <div class="checkin-header">
      <h2>我的打卡</h2>
      <p>坚持每天练习，提升雅思口语水平</p>
    </div>

    <!-- 顶部 4 个统计卡片 -->
    <div class="stats-grid" v-loading="statsLoading">
      <div class="stat-card stat-card--calendar">
        <div class="stat-icon">📅</div>
        <div class="stat-content">
          <div class="stat-value">{{ stats.totalDays }}</div>
          <div class="stat-label">累计打卡</div>
        </div>
      </div>
      <div class="stat-card stat-card--streak">
        <div class="stat-icon">🔥</div>
        <div class="stat-content">
          <div class="stat-value">{{ stats.streakCount }}</div>
          <div class="stat-label">当前连续</div>
        </div>
      </div>
      <div class="stat-card stat-card--crown">
        <div class="stat-icon">🏆</div>
        <div class="stat-content">
          <div class="stat-value">{{ stats.maxStreak }}</div>
          <div class="stat-label">最长连续</div>
        </div>
      </div>
      <div class="stat-card stat-card--time">
        <div class="stat-icon">⏱️</div>
        <div class="stat-content">
          <div class="stat-value">{{ formatHours(stats.totalSeconds || 0) }}</div>
          <div class="stat-label">累计练习</div>
        </div>
      </div>
    </div>

    <!-- 打卡日历 -->
    <div class="calendar-card" v-loading="calLoading">
      <div class="calendar-toolbar">
        <button class="cal-nav" @click="prevMonth">
          <el-icon><ArrowLeft /></el-icon>
        </button>
        <div class="cal-title">{{ curYear }} 年 {{ curMonth }} 月</div>
        <button class="cal-nav" @click="nextMonth">
          <el-icon><ArrowRight /></el-icon>
        </button>
        <el-button class="today-btn" size="small" @click="goToday">今天</el-button>
      </div>

      <div class="cal-weekdays">
        <div v-for="w in weekdays" :key="w" class="weekday">{{ w }}</div>
      </div>

      <div class="cal-grid">
        <div
          v-for="(cell, idx) in calendarCells"
          :key="idx"
          class="cal-cell"
          :class="{
            'cal-cell--other': cell.otherMonth,
            'cal-cell--today': cell.isToday,
            'cal-cell--checked': cell.checked,
          }"
        >
          <div class="cell-date">{{ cell.day }}</div>
          <div v-if="cell.checked" class="cell-meta">
            <span>{{ cell.totalRounds }}轮</span>
          </div>
          <el-tooltip v-if="cell.checked" placement="top" :show-after="200">
            <template #content>
              <div class="cal-tooltip">
                <div><strong>{{ cell.dateStr }}</strong></div>
                <div>对话轮次: {{ cell.totalRounds }}</div>
                <div>练习时长: {{ formatDuration(cell.totalSeconds || 0) }}</div>
                <div>语法错误: {{ cell.totalErrors || 0 }}</div>
                <div>获得建议: {{ cell.totalSuggestions || 0 }}</div>
              </div>
            </template>
            <div class="cell-hover-mask"></div>
          </el-tooltip>
        </div>
      </div>

      <div class="cal-legend">
        <div class="legend-item">
          <span class="legend-swatch legend-swatch--checked"></span>
          <span>已打卡</span>
        </div>
        <div class="legend-item">
          <span class="legend-swatch legend-swatch--today"></span>
          <span>今天</span>
        </div>
        <div class="legend-item">
          <span class="legend-swatch legend-swatch--none"></span>
          <span>未打卡</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import {
  getCheckinStats,
  getCheckinCalendar,
  type CheckinStatsResponse,
  type CheckinDayDetail
} from '../api/checkin'

const weekdays = ['一', '二', '三', '四', '五', '六', '日']

// 当前显示的年月
const now = new Date()
const curYear = ref(now.getFullYear())
const curMonth = ref(now.getMonth() + 1)

const statsLoading = ref(false)
const calLoading = ref(false)

const stats = ref<CheckinStatsResponse>({
  todayCheckedIn: false,
  checkinDate: '',
  streakCount: 0,
  maxStreak: 0,
  totalDays: 0,
  totalSeconds: 0,
  totalRounds: 0
})

const calendarMap = ref<Record<string, CheckinDayDetail>>({})

type CalendarCell = {
  day: number
  dateStr: string
  otherMonth: boolean
  isToday: boolean
  checked: boolean
  totalRounds?: number
  totalSeconds?: number
  totalErrors?: number
  totalSuggestions?: number
}

const calendarCells = computed<CalendarCell[]>(() => {
  const cells: CalendarCell[] = []
  const firstDay = new Date(curYear.value, curMonth.value - 1, 1)
  const lastDay = new Date(curYear.value, curMonth.value, 0)
  // 星期: 周日=0 → 转为周一=0
  let startWeekday = firstDay.getDay() - 1
  if (startWeekday < 0) startWeekday = 6
  // 前置(上月末尾)
  const prevMonthLast = new Date(curYear.value, curMonth.value - 1, 0).getDate()
  for (let i = startWeekday - 1; i >= 0; i--) {
    const d = prevMonthLast - i
    cells.push(cellFor(curYear.value, curMonth.value - 1, d, true))
  }
  // 当月
  for (let d = 1; d <= lastDay.getDate(); d++) {
    cells.push(cellFor(curYear.value, curMonth.value, d, false))
  }
  // 后置(下月开始)补齐 42 格(6行) 或 35(5行)
  const fillTarget = cells.length <= 35 ? 35 : 42
  let n = 1
  while (cells.length < fillTarget) {
    cells.push(cellFor(curYear.value, curMonth.value + 1, n, true))
    n++
  }
  return cells
})

function pad(n: number): string {
  return n < 10 ? '0' + n : String(n)
}

function normalizeDate(y: number, m: number): { year: number; month: number } {
  let year = y, month = m
  if (month < 1) { month += 12; year-- }
  if (month > 12) { month -= 12; year++ }
  return { year, month }
}

function cellFor(y: number, m: number, day: number, otherMonth: boolean): CalendarCell {
  const { year, month } = normalizeDate(y, m)
  const dateStr = `${year}-${pad(month)}-${pad(day)}`
  const today = new Date()
  const isToday = !otherMonth
    && today.getFullYear() === year
    && today.getMonth() + 1 === month
    && today.getDate() === day
  const detail = calendarMap.value[dateStr]
  return {
    day,
    dateStr,
    otherMonth,
    isToday,
    checked: !!detail,
    totalRounds: detail?.totalRounds,
    totalSeconds: detail?.totalSeconds,
    totalErrors: detail?.totalErrors,
    totalSuggestions: detail?.totalSuggestions,
  }
}

function prevMonth(): void {
  curMonth.value--
  if (curMonth.value < 1) {
    curMonth.value = 12
    curYear.value--
  }
  void loadCalendar()
}

function nextMonth(): void {
  curMonth.value++
  if (curMonth.value > 12) {
    curMonth.value = 1
    curYear.value++
  }
  void loadCalendar()
}

function goToday(): void {
  const today = new Date()
  curYear.value = today.getFullYear()
  curMonth.value = today.getMonth() + 1
  void loadCalendar()
}

function formatDuration(totalSeconds: number): string {
  const m = Math.floor(totalSeconds / 60)
  const s = totalSeconds % 60
  if (m <= 0) return `${s}秒`
  if (m < 60) return `${m}分${s > 0 ? s + '秒' : ''}`
  const h = Math.floor(m / 60)
  const rm = m % 60
  return `${h}小时${rm > 0 ? rm + '分' : ''}`
}

function formatHours(totalSeconds: number): string {
  const h = totalSeconds / 3600
  if (h < 1) {
    const m = Math.round(totalSeconds / 60)
    return `${m}分钟`
  }
  return `${h.toFixed(1)}小时`
}

async function loadStats(): Promise<void> {
  statsLoading.value = true
  try {
    const res = await getCheckinStats()
    const data = (res as any)?.data ?? res
    stats.value = data as CheckinStatsResponse
  } finally {
    statsLoading.value = false
  }
}

async function loadCalendar(): Promise<void> {
  calLoading.value = true
  try {
    const res = await getCheckinCalendar(curYear.value, curMonth.value)
    const data = (res as any)?.data ?? res
    calendarMap.value = (data || {}) as Record<string, CheckinDayDetail>
  } finally {
    calLoading.value = false
  }
}

onMounted(() => {
  void loadStats()
  void loadCalendar()
})
</script>

<style scoped>
.checkin-page {
  max-width: 1000px;
  margin: 0 auto;
  padding: 24px;
}

.checkin-header {
  margin-bottom: 24px;
}

.checkin-header h2 {
  margin: 0 0 4px;
  font-size: 24px;
  color: #1f2937;
}

.checkin-header p {
  margin: 0;
  font-size: 14px;
  color: #6b7280;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

@media (max-width: 720px) {
  .stats-grid { grid-template-columns: repeat(2, 1fr); }
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 20px;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.04);
  border: 1px solid #f1f5f9;
}

.stat-icon {
  width: 50px;
  height: 50px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
  flex-shrink: 0;
}

.stat-card--calendar .stat-icon { background: #eff6ff; }
.stat-card--streak   .stat-icon { background: #fff7ed; }
.stat-card--crown    .stat-icon { background: #fef3c7; }
.stat-card--time     .stat-icon { background: #f0fdf4; }

.stat-value {
  font-size: 26px;
  font-weight: 700;
  color: #111827;
  line-height: 1.1;
}

.stat-label {
  font-size: 13px;
  color: #6b7280;
  margin-top: 2px;
}

/* 日历卡片 */
.calendar-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px 24px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.04);
  border: 1px solid #f1f5f9;
}

.calendar-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}

.cal-nav {
  width: 32px;
  height: 32px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  color: #4b5563;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}

.cal-nav:hover {
  background: #f3f4f6;
  color: #111827;
}

.cal-title {
  font-size: 17px;
  font-weight: 600;
  color: #111827;
  flex: 1;
  text-align: center;
}

.today-btn {
  margin-left: auto;
}

.cal-weekdays,
.cal-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
}

.cal-weekdays {
  margin-bottom: 6px;
}

.weekday {
  text-align: center;
  font-size: 13px;
  font-weight: 500;
  color: #6b7280;
  padding: 8px 0;
}

.cal-cell {
  position: relative;
  aspect-ratio: 1 / 0.9;
  padding: 6px;
  border-radius: 8px;
  margin: 2px;
  border: 1px solid transparent;
  cursor: default;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-start;
  padding-top: 8px;
  transition: all 0.15s;
}

.cal-cell--other {
  color: #d1d5db;
}

.cal-cell--today {
  border-color: #3b82f6;
  background: #eff6ff;
}

.cal-cell--checked {
  background: linear-gradient(135deg, #bbf7d0 0%, #86efac 100%);
  border-color: #22c55e55;
  color: #14532d;
}

.cal-cell--today.cal-cell--checked {
  border-color: #22c55e;
}

.cell-date {
  font-size: 14px;
  font-weight: 500;
  line-height: 1;
}

.cell-meta {
  margin-top: 4px;
  font-size: 11px;
  opacity: 0.8;
}

.cell-hover-mask {
  position: absolute;
  inset: 0;
  cursor: help;
}

.cal-tooltip {
  font-size: 13px;
  line-height: 1.7;
}

.cal-tooltip > div + div {
  margin-top: 2px;
}

.cal-legend {
  display: flex;
  gap: 20px;
  justify-content: center;
  margin-top: 18px;
  font-size: 13px;
  color: #6b7280;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.legend-swatch {
  width: 16px;
  height: 16px;
  border-radius: 4px;
  display: inline-block;
  border: 1px solid transparent;
}

.legend-swatch--checked {
  background: linear-gradient(135deg, #bbf7d0 0%, #86efac 100%);
  border-color: #22c55e55;
}

.legend-swatch--today {
  background: #eff6ff;
  border-color: #3b82f6;
}

.legend-swatch--none {
  background: #f9fafb;
  border-color: #e5e7eb;
}
</style>
