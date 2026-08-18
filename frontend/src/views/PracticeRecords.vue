<template>
  <div class="records-page">
    <el-page-header @back="goBack">
      <template #content>
        <span class="page-title">练习记录</span>
      </template>
    </el-page-header>

    <el-card class="records-card" v-loading="loading">
      <el-table :data="records" border stripe>
        <el-table-column prop="sceneName" label="场景" width="150" />
        <el-table-column prop="score" label="评分" width="100">
          <template #default="{ row }">
            <el-tag :type="scoreType(row.score)">{{ row.score }}分</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="rounds" label="对话轮数" width="100" />
        <el-table-column prop="duration" label="时长" width="100">
          <template #default="{ row }">{{ row.duration }}秒</template>
        </el-table-column>
        <el-table-column prop="errorCount" label="语法错误" width="100" />
        <el-table-column prop="createTime" label="练习时间" width="180" />
        <el-table-column label="AI 评价" min-width="250">
          <template #default="{ row }">
            <el-popover trigger="click" width="400" placement="left">
              <template #reference>
                <el-button type="primary" link>查看评价</el-button>
              </template>
              <div class="summary-popover">
                <h4>{{ row.sceneName }} - {{ row.score }}分</h4>
                <p>{{ row.summary }}</p>
              </div>
            </el-popover>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && records.length === 0" description="还没有练习记录，去练一次吧" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '../api/request'

const router = useRouter()
const loading = ref(false)
const records = ref<any[]>([])

const scoreType = (score: number): any => {
  if (score >= 80) return 'success'
  if (score >= 60) return 'warning'
  return 'danger'
}

const goBack = () => {
  router.push('/scenes')
}

const loadRecords = async () => {
  loading.value = true
  try {
    const res = await request.get('/scenes/practice/records')
    if (res.data) {
      records.value = res.data
    }
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadRecords()
})
</script>

<style scoped>
.records-page {
  max-width: 1000px;
  margin: 0 auto;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
}

.records-card {
  margin-top: 20px;
}

.summary-popover h4 {
  margin: 0 0 8px 0;
  color: #303133;
}

.summary-popover p {
  color: #606266;
  font-size: 14px;
  line-height: 1.8;
  white-space: pre-wrap;
}
</style>
