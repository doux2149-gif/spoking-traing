<template>
  <div class="scene-practice-page">
    <div v-if="scene" class="scene-header">
      <el-page-header @back="goBack">
        <template #content>
          <span class="scene-title">
            <el-icon><component :is="scene.icon || 'ChatDotRound'" /></el-icon>
            {{ scene.name }}
            <el-tag :type="difficultyType(scene.difficulty)" size="small">
              {{ difficultyLabel(scene.difficulty) }}
            </el-tag>
          </span>
        </template>
      </el-page-header>
      <div class="scene-info">
        <span><el-icon><User /></el-icon> AI 角色: {{ scene.aiRole }}</span>
        <span><el-icon><ChatLineRound /></el-icon> {{ scene.description }}</span>
      </div>
    </div>

    <VoiceChat
      v-if="scene"
      scene-mode
      :scene-id="scene.id"
      :scene-prompt="scene.systemPrompt"
      :opening-line="scene.openingLine"
      fixed-vcn="catherine"
      @finish="handleFinish"
    />

    <!-- 练习报告弹窗 -->
    <el-dialog v-model="reportVisible" title="练习报告" width="500px" :close-on-click-modal="false">
      <div v-if="report" class="report-content">
        <div class="report-score">
          <el-progress
            type="dashboard"
            :percentage="report.score"
            :color="scoreColor(report.score)"
          />
        </div>
        <el-descriptions :column="3" border class="report-stats">
          <el-descriptions-item label="对话轮数">{{ report.rounds }}</el-descriptions-item>
          <el-descriptions-item label="练习时长">{{ report.duration }}秒</el-descriptions-item>
          <el-descriptions-item label="语法错误">{{ report.errorCount }}</el-descriptions-item>
        </el-descriptions>
        <div class="report-summary">
          <h4>AI 评价</h4>
          <p>{{ report.summary }}</p>
        </div>
      </div>
      <template #footer>
        <el-button @click="reportVisible = false">关闭</el-button>
        <el-button type="primary" @click="goRecords">查看记录</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import VoiceChat from '../components/chat/VoiceChat.vue'
import request from '../api/request'

const route = useRoute()
const router = useRouter()
const scene = ref<any>(null)
const reportVisible = ref(false)
const report = ref<any>(null)

const difficultyLabel = (d: number) => ['', '入门', '初级', '中级', '高级'][d] || '入门'
const difficultyType = (d: number): any => ['', 'success', 'info', 'warning', 'danger'][d] || 'success'
const scoreColor = (score: number) => {
  if (score >= 80) return '#67c23a'
  if (score >= 60) return '#e6a23c'
  return '#f56c6c'
}

const loadScene = async () => {
  const id = route.params.id
  try {
    const res = await request.get(`/scenes/${id}`)
    if (res.data) {
      scene.value = res.data
    }
  } catch (e) {
    ElMessage.error('场景加载失败')
    router.push('/scenes')
  }
}

const handleFinish = async (data: { rounds: number; duration: number; errorCount: number; conversation: string }) => {
  try {
    const res = await request.post(`/scenes/${route.params.id}/finish`, data)
    if (res.data) {
      report.value = res.data
      reportVisible.value = true
    }
  } catch (e) {
    ElMessage.error('生成练习报告失败')
  }
}

const goBack = () => {
  router.push('/scenes')
}

const goRecords = () => {
  reportVisible.value = false
  router.push('/scenes/records')
}

onMounted(() => {
  loadScene()
})
</script>

<style scoped>
.scene-practice-page {
  max-width: 900px;
  margin: 0 auto;
}

.scene-header {
  margin-bottom: 20px;
}

.scene-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 600;
}

.scene-info {
  display: flex;
  gap: 24px;
  margin-top: 12px;
  color: #909399;
  font-size: 13px;
}

.scene-info span {
  display: flex;
  align-items: center;
  gap: 4px;
}

.report-content {
  text-align: center;
}

.report-score {
  margin-bottom: 20px;
}

.report-stats {
  margin-bottom: 20px;
}

.report-summary {
  text-align: left;
}

.report-summary h4 {
  color: #303133;
  margin: 0 0 8px 0;
}

.report-summary p {
  color: #606266;
  line-height: 1.8;
  font-size: 14px;
  white-space: pre-wrap;
}
</style>
