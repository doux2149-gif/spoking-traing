<template>
  <div class="scene-list-page">
    <div class="page-header">
      <h1>场景口语练习</h1>
      <p>选择一个场景，开始沉浸式英语口语对话</p>
    </div>

    <div class="difficulty-filter">
      <el-radio-group v-model="selectedDifficulty" @change="filterScenes">
        <el-radio-button :value="0">全部</el-radio-button>
        <el-radio-button :value="1">入门</el-radio-button>
        <el-radio-button :value="2">初级</el-radio-button>
        <el-radio-button :value="3">中级</el-radio-button>
        <el-radio-button :value="4">高级</el-radio-button>
      </el-radio-group>
    </div>

    <div v-loading="loading" class="scene-grid">
      <el-card
        v-for="scene in filteredScenes"
        :key="scene.id"
        class="scene-card"
        shadow="hover"
        @click="enterScene(scene)"
      >
        <div class="scene-card-body">
          <el-icon class="scene-icon" :size="40">
            <component :is="scene.icon || 'ChatDotRound'" />
          </el-icon>
          <h3 class="scene-name">{{ scene.name }}</h3>
          <p class="scene-desc">{{ scene.description }}</p>
          <div class="scene-meta">
            <el-tag :type="difficultyType(scene.difficulty)" size="small">
              {{ difficultyLabel(scene.difficulty) }}
            </el-tag>
            <span class="scene-role">
              <el-icon><User /></el-icon>
              {{ scene.aiRole }}
            </span>
          </div>
        </div>
      </el-card>

      <el-empty v-if="!loading && filteredScenes.length === 0" description="暂无场景" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '../api/request'

const router = useRouter()
const loading = ref(false)
const scenes = ref<any[]>([])
const selectedDifficulty = ref(0)

const filteredScenes = computed(() => {
  if (selectedDifficulty.value === 0) return scenes.value
  return scenes.value.filter(s => s.difficulty === selectedDifficulty.value)
})

const difficultyLabel = (d: number) => ['', '入门', '初级', '中级', '高级'][d] || '入门'
const difficultyType = (d: number): any => ['', 'success', 'info', 'warning', 'danger'][d] || 'success'

const enterScene = (scene: any) => {
  router.push(`/scenes/${scene.id}/practice`)
}

const loadScenes = async () => {
  loading.value = true
  try {
    const res = await request.get('/scenes')
    if (res.data) {
      scenes.value = res.data
    }
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadScenes()
})
</script>

<style scoped>
.scene-list-page {
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  text-align: center;
  margin-bottom: 32px;
}

.page-header h1 {
  font-size: 28px;
  color: #303133;
  margin: 0 0 8px 0;
}

.page-header p {
  color: #909399;
  font-size: 15px;
  margin: 0;
}

.difficulty-filter {
  text-align: center;
  margin-bottom: 24px;
}

.scene-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 20px;
}

.scene-card {
  cursor: pointer;
  transition: transform 0.2s;
}

.scene-card:hover {
  transform: translateY(-4px);
}

.scene-card-body {
  text-align: center;
  padding: 12px;
}

.scene-icon {
  color: #409EFF;
  margin-bottom: 12px;
}

.scene-name {
  font-size: 18px;
  color: #303133;
  margin: 0 0 8px 0;
}

.scene-desc {
  color: #909399;
  font-size: 13px;
  line-height: 1.6;
  margin: 0 0 16px 0;
  min-height: 42px;
}

.scene-meta {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 12px;
}

.scene-role {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #606266;
  font-size: 13px;
}
</style>
