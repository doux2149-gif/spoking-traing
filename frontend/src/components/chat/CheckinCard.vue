<template>
  <div class="checkin-card" v-loading="loading" @click="goDetail">
    <div class="streak-info">
      <div class="streak-fire">🔥</div>
      <div class="streak-count">{{ streakCount }}</div>
      <div class="streak-label">连续打卡</div>
    </div>

    <div class="today-status" :class="{ 'checked-in': checkedIn }">
      <div v-if="checkedIn" class="status-ok">
        <el-icon><CircleCheckFilled /></el-icon>
        <span>今天打卡成功</span>
      </div>
      <div v-else class="status-pending">
        <el-icon><Clock /></el-icon>
        <span>今天还没打卡</span>
      </div>
    </div>

    <div v-if="checkedIn" class="today-stats">
      <div class="stat-item">
        <span class="stat-value">{{ todayRounds }}</span>
        <span class="stat-label">轮</span>
      </div>
      <div class="stat-divider"></div>
      <div class="stat-item">
        <span class="stat-value">{{ formatDuration(todaySeconds || 0) }}</span>
        <span class="stat-label">时长</span>
      </div>
    </div>
    <div v-else class="today-tip">
      完成 1 轮对话即可打卡
    </div>

    <div class="detail-link">
      <span>查看详细统计</span>
      <el-icon><ArrowRight /></el-icon>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { CircleCheckFilled, Clock, ArrowRight } from '@element-plus/icons-vue'
import { getCheckinToday, type CheckinTodayResponse } from '../../api/checkin'

const props = defineProps<{
  /** 刷新触发key变化时自动重新拉取 */
  refreshKey?: number | string
}>()

const emit = defineEmits<{
  /** 打卡状态从"未打卡→已打卡"时触发,用于弹出里程碑 toast */
  checkedIn: [milestone: number | null]
}>()

const router = useRouter()

const loading = ref(false)
const checkedIn = ref(false)
const streakCount = ref(0)
const todayRounds = ref(0)
const todaySeconds = ref(0)
// 上一次是否未打卡(用于检测里程碑)
const prevCheckedIn = ref(false)
const prevStreakCount = ref(0)

/** 连续打卡里程碑(连续天数) */
const MILESTONES = [1, 7, 14, 30, 100, 180, 365]

function formatDuration(totalSeconds: number): string {
  const m = Math.floor(totalSeconds / 60)
  const s = totalSeconds % 60
  if (m <= 0) return `${s}秒`
  if (m < 60) return `${m}分${s > 0 ? s + '秒' : ''}`
  const h = Math.floor(m / 60)
  const rm = m % 60
  return `${h}小时${rm > 0 ? rm + '分' : ''}`
}

async function refresh(): Promise<void> {
  loading.value = true
  try {
    prevCheckedIn.value = checkedIn.value
    prevStreakCount.value = streakCount.value
    const res = await getCheckinToday()
    const data = (res as any)?.data ?? res
    const d = data as CheckinTodayResponse
    checkedIn.value = !!d.todayCheckedIn
    streakCount.value = d.streakCount ?? 0
    todayRounds.value = d.todayRounds ?? 0
    todaySeconds.value = d.todaySeconds ?? 0

    // 检测到今日打卡完成:发布里程碑事件
    if (!prevCheckedIn.value && checkedIn.value) {
      const newStreak = streakCount.value
      const milestone = MILESTONES.includes(newStreak) ? newStreak : null
      emit('checkedIn', milestone)
    }
  } finally {
    loading.value = false
  }
}

function goDetail(): void {
  router.push('/checkin')
}

watch(() => props.refreshKey, () => {
  void refresh()
}, { immediate: false })

onMounted(() => {
  void refresh()
})

// 暴露方法供父组件手动刷新
defineExpose({ refresh })
</script>

<style scoped>
.checkin-card {
  margin: 10px;
  padding: 14px 14px 10px;
  border-radius: 10px;
  background: linear-gradient(135deg, #fff7ed 0%, #fff1e6 100%);
  border: 1px solid #f5d5a0;
  cursor: pointer;
  transition: box-shadow 0.2s, transform 0.15s;
}

.checkin-card:hover {
  box-shadow: 0 4px 14px rgba(245, 158, 11, 0.2);
  transform: translateY(-1px);
}

.streak-info {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-bottom: 8px;
}

.streak-fire {
  font-size: 18px;
  line-height: 1;
}

.streak-count {
  font-size: 22px;
  font-weight: 700;
  color: #d97706;
  line-height: 1;
}

.streak-label {
  font-size: 12px;
  color: #92400e;
  margin-left: 2px;
}

.today-status {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 6px 10px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  margin-bottom: 8px;
}

.today-status.checked-in {
  background: #dcfce7;
  color: #166534;
}

.today-status:not(.checked-in) {
  background: #fef3c7;
  color: #92400e;
}

.today-stats {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-bottom: 8px;
  padding: 6px 0;
}

.stat-item {
  display: flex;
  align-items: baseline;
  gap: 3px;
}

.stat-value {
  font-size: 15px;
  font-weight: 600;
  color: #7c2d12;
}

.stat-label {
  font-size: 11px;
  color: #a16207;
}

.stat-divider {
  width: 1px;
  height: 16px;
  background: #f59e0b55;
}

.today-tip {
  font-size: 12px;
  color: #a16207;
  text-align: center;
  padding: 6px 0;
  margin-bottom: 8px;
}

.detail-link {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px dashed #f5d5a0;
  padding-top: 8px;
  font-size: 12px;
  color: #92400e;
}

.detail-link:hover {
  color: #78350f;
}
</style>
