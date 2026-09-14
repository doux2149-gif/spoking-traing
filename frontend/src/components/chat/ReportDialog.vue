<template>
  <el-dialog
    v-model="visible"
    title="练习诊断报告"
    width="min(720px, 92vw)"
    top="6vh"
    destroy-on-close
    :close-on-click-modal="false"
  >
    <div v-if="loading && !report" class="report-loading">
      <el-icon class="is-loading" :size="36"><Loading /></el-icon>
      <div>{{ loadingTip }}</div>
    </div>

    <div v-else-if="report" class="report-body">
      <!-- 总分卡 + 四维度 -->
      <div class="score-head">
        <div class="overall">
          <div class="overall-num">{{ report.overallScore }}</div>
          <div class="overall-label">总分</div>
          <div class="overall-band">{{ ieltsBand(report.overallScore) }}</div>
        </div>
        <div class="dims">
          <div v-for="d in dims" :key="d.key" class="dim-card">
            <div class="dim-name">{{ d.label }}</div>
            <el-progress
              :percentage="report[d.key]"
              :stroke-width="10"
              :show-text="false"
              :color="d.color"
              :indeterminate="report[d.key] == null"
            />
            <div class="dim-score">
              {{ report[d.key] ?? '—' }}
              <span class="dim-suffix">/ 100</span>
            </div>
            <div class="dim-ielts">≈ {{ report[d.key] != null ? ieltsBand(report[d.key]) : '本次未评估' }}</div>
          </div>
        </div>
      </div>

      <!-- 统计条 -->
      <el-descriptions :column="4" border size="small" class="stat-bar">
        <el-descriptions-item label="对话轮数">{{ report.roundCount }}</el-descriptions-item>
        <el-descriptions-item label="总时长">{{ report.duration }}s</el-descriptions-item>
        <el-descriptions-item label="语法错误">{{ report.errorCount }}</el-descriptions-item>
        <el-descriptions-item label="建议条数">{{ report.suggestionCount }}</el-descriptions-item>
      </el-descriptions>

      <!-- LLM 总评 -->
      <div v-if="report.summary" class="summary-block">
        <div class="sec-title">📝 总评</div>
        <div class="summary-text">{{ report.summary }}</div>
      </div>

      <!-- top errors -->
      <div v-if="topErrors.length" class="errors-block">
        <div class="sec-title">⚠️ 代表性错误 <span class="dim-tag dim-tag--grammar">语法多样性</span></div>
        <el-table :data="topErrors" border size="small" stripe>
          <el-table-column prop="wrong" label="你的表达" min-width="180" />
          <el-table-column prop="correct" label="推荐表达" min-width="180" />
          <el-table-column prop="reason" label="错误原因" min-width="200">
            <template #default="{ row }">
              {{ row.dimension === 'vocabulary' ? '📚 ' : '🔤 ' }}{{ row.reason }}
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- top suggestions -->
      <div v-if="topSuggestions.length" class="suggs-block">
        <div class="sec-title">💡 地道表达建议</div>
        <div class="suggestion-list">
          <div v-for="(s, i) in topSuggestions" :key="i" class="suggestion-item">
            <el-tag size="small" :type="s.level === 'advanced' ? 'danger' : 'warning'" effect="plain">
              {{ s.level === 'advanced' ? '高级' : '更好' }}
            </el-tag>
            <el-tag
              size="small"
              effect="plain"
              class="dim-tag-inline"
              :class="s.dimension === 'vocabulary' ? 'dim-tag--vocab' : 'dim-tag--grammar'"
            >
              {{ s.dimension === 'vocabulary' ? '词汇多样性' : '语法多样性' }}
            </el-tag>
            <div class="suggestion-text">
              <div v-if="s.alternatives" class="alt">{{ s.alternatives }}</div>
              <div class="tip">{{ s.tip }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button
          v-if="report && report.status === 3"
          :loading="regenerating"
          @click="doRegenerate"
        >重新生成摘要</el-button>
        <el-button v-if="report && report.status === 1 && !llmReady" disabled>摘要生成中…</el-button>
        <el-button type="primary" @click="close">好的</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import {
  conversationReportApi,
  parseReportJson,
  type ConversationReport,
  type ReportTopError,
  type ReportTopSuggestion
} from '../../api/conversation'

const visible = ref(false)
const report = ref<ConversationReport | null>(null)
const loading = ref(false)
const regenerating = ref(false)
const loadingTip = ref('正在分析对话...')
const pollTimer = ref<number | null>(null)

const topErrors = computed<ReportTopError[]>(() =>
  parseReportJson<ReportTopError>(report.value?.topErrors)
)
const topSuggestions = computed<ReportTopSuggestion[]>(() =>
  parseReportJson<ReportTopSuggestion>(report.value?.topSuggestions)
)
const llmReady = computed(() => report.value?.status === 2)

const dims = [
  { key: 'grammarScore', label: '语法多样性', color: '#409eff' },
  { key: 'vocabularyScore', label: '词汇多样性', color: '#67c23a' },
  { key: 'fluencyScore', label: '流利性与连贯性', color: '#e6a23c' },
  { key: 'pronunciationScore', label: '发音', color: '#909399' }
] as const

function ieltsBand(score: number | null | undefined): string {
  if (score == null) return ''
  if (score >= 90) return '雅思 8.0+'
  if (score >= 80) return '雅思 7.0+'
  if (score >= 70) return '雅思 6.0+'
  if (score >= 60) return '雅思 5.0+'
  return '基础水平'
}

/** 对外: 开始拉取并弹窗; 会先尝试立即拿, 没拿到就后台轮询最多 10 秒 */
async function show(conversationId: number) {
  visible.value = true
  report.value = null
  loading.value = true
  loadingTip.value = '正在拉取报告...'

  for (let i = 0; i < 10; i++) {
    await new Promise(r => setTimeout(r, 600))
    try {
      const res = await conversationReportApi.get(conversationId)
      if (res.data) {
        report.value = res.data
        break
      }
    } catch (_e) {
      // 404 说明会话还没结束/报告未生成, 其他错误继续等
    }
  }

  loading.value = false
  if (!report.value) {
    visible.value = false
    ElMessage.warning('该会话还没有报告, 先在左侧菜单选择"结束对话"')
    return
  }

  // 如果规则聚合完成但 LLM 摘要还没出来, 继续轮询最多 15 秒
  if (report.value.status === 1) {
    loadingTip.value = '正在生成 AI 总评...'
    for (let i = 0; i < 15; i++) {
      await new Promise(r => setTimeout(r, 1000))
      try {
        const res = await conversationReportApi.get(conversationId)
        if (res.data && res.data.status === 2) {
          report.value = res.data
          break
        }
      } catch (_e) {}
    }
  }
}

async function doRegenerate() {
  if (!report.value) return
  regenerating.value = true
  try {
    const res = await conversationReportApi.regenerate(report.value.conversationId)
    if (res.data) report.value = res.data
    // 继续轮询等待 LLM
    for (let i = 0; i < 15; i++) {
      await new Promise(r => setTimeout(r, 1000))
      const fresh = await conversationReportApi.get(report.value!.conversationId)
      if (fresh.data && fresh.data.status === 2) { report.value = fresh.data; break }
    }
  } finally {
    regenerating.value = false
  }
}

function close() {
  visible.value = false
  report.value = null
}

watch(visible, v => {
  if (!v && pollTimer.value) {
    clearInterval(pollTimer.value)
    pollTimer.value = null
  }
})

defineExpose({ show, close })
</script>

<style scoped>
.report-loading {
  display: flex; flex-direction: column; align-items: center; gap: 16px;
  padding: 48px 0; color: #606266;
}
.report-body .score-head {
  display: flex; gap: 24px; margin-bottom: 20px;
}
.overall {
  flex: 0 0 160px; text-align: center;
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
  color: #fff; border-radius: 12px; padding: 20px 0 16px;
}
.overall-num { font-size: 52px; font-weight: 700; line-height: 1 }
.overall-label { font-size: 14px; opacity: 0.9; margin-top: 6px }
.overall-band { font-size: 12px; margin-top: 4px; opacity: 0.85 }
.dims { flex: 1; display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px }
.dim-card { background: #f8f9fb; border-radius: 8px; padding: 10px 14px }
.dim-name { font-size: 13px; color: #606266; margin-bottom: 6px }
.dim-score { font-size: 22px; font-weight: 600; color: #303133; margin: 4px 0 2px }
.dim-suffix { font-size: 12px; color: #909399; font-weight: 400 }
.dim-ielts { font-size: 11px; color: #909399 }
.stat-bar { margin-bottom: 16px }
.sec-title {
  font-size: 14px; font-weight: 600; color: #303133;
  margin: 18px 0 10px; display: flex; align-items: center; gap: 8px;
}
.summary-block {
  background: #ecf5ff; border-left: 3px solid #409eff;
  padding: 12px 16px; border-radius: 6px;
}
.summary-text { color: #303133; font-size: 14px; line-height: 1.8; white-space: pre-wrap }
.errors-block, .suggs-block { margin-top: 16px }
.dim-tag {
  font-size: 11px; padding: 2px 8px; border-radius: 10px;
  background: #ecf5ff; color: #409eff;
}
.dim-tag--vocab { background: #f0f9eb; color: #67c23a }
.dim-tag-inline {
  font-size: 11px; padding: 0 6px !important; height: 18px !important;
  line-height: 16px !important; margin-left: 6px;
}
.suggestion-list { display: flex; flex-direction: column; gap: 10px }
.suggestion-item {
  border: 1px solid #ebeef5; border-radius: 8px;
  padding: 10px 14px; display: flex; align-items: flex-start; gap: 8px;
  background: #fafbfc;
}
.suggestion-text { flex: 1 }
.suggestion-text .alt {
  color: #303133; font-size: 13px; margin-bottom: 4px; font-weight: 500;
}
.suggestion-text .tip {
  color: #606266; font-size: 12px; line-height: 1.7;
}
.dialog-footer { display: flex; justify-content: flex-end; gap: 8px }
</style>
