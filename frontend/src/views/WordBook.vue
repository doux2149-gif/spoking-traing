<template>
  <div class="word-book">
    <div class="header">
      <h2>📚 单词本</h2>
      <p class="subtitle">按类别浏览, 点击任意单词可朗读发音</p>
    </div>

    <!-- 类别标签 -->
    <div class="categories">
      <el-tag
        v-for="c in allCategories"
        :key="c"
        :type="activeCategory === c ? 'primary' : 'info'"
        :effect="activeCategory === c ? 'dark' : 'plain'"
        class="cat-tag"
        @click="selectCategory(c)"
      >
        {{ c }}
      </el-tag>
    </div>

    <!-- 搜索 + 分页信息 -->
    <div class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="搜索英文/中文"
        clearable
        style="width: 240px"
        @keyup.enter="loadData"
        @clear="loadData"
      />
      <span class="total-info">共 {{ total }} 个单词</span>
    </div>

    <!-- 单词卡片网格 -->
    <el-row :gutter="16">
      <el-col
        v-for="w in words"
        :key="w.id"
        :xs="24"
        :sm="12"
        :md="8"
        :lg="6"
      >
        <div class="word-card">
          <div class="card-top">
            <div class="word-en" @click="speak(w.english)">
              {{ w.english }}
              <el-icon class="speaker"><Promotion /></el-icon>
            </div>
            <el-tag size="small" effect="plain" type="info">{{ w.category }}</el-tag>
          </div>
          <div v-if="w.phonetic" class="word-phonetic">{{ w.phonetic }}</div>
          <div class="word-cn">{{ w.chinese }}</div>
          <div v-if="w.partOfSpeech" class="word-pos">{{ w.partOfSpeech }}</div>
          <div v-if="w.exampleSentence" class="word-example">
            <div class="example-en" @click="speak(w.exampleSentence)">{{ w.exampleSentence }}</div>
            <div v-if="w.exampleTranslation" class="example-cn">{{ w.exampleTranslation }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-empty v-if="!loading && words.length === 0" description="暂无单词, 去管理端添加吧!" />

    <el-pagination
      v-model:current-page="pageNum"
      v-model:page-size="pageSize"
      :page-sizes="[12, 24, 48]"
      :total="total"
      layout="total, sizes, prev, pager, next"
      @size-change="loadData"
      @current-change="loadData"
      class="mt-4"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Promotion } from '@element-plus/icons-vue'
import request from '../api/request'
import type { WordItem } from '../api/admin'

const words = ref<WordItem[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(12)
const keyword = ref('')
const allCategories = ref<string[]>(['全部'])
const activeCategory = ref('全部')
const loading = ref(false)

function selectCategory(c: string) {
  activeCategory.value = c
  pageNum.value = 1
  loadData()
}

function loadData() {
  loading.value = true
  const params: any = { keyword: keyword.value, pageNum: pageNum.value, pageSize: pageSize.value }
  if (activeCategory.value !== '全部') {
    params.category = activeCategory.value
  }
  request.get('/words', { params }).then((res: any) => {
    words.value = res.data?.records ?? res.data?.rows ?? []
    total.value = res.data?.total ?? 0
  }).finally(() => (loading.value = false))
}

function loadCategories() {
  request.get('/words/categories').then((res: any) => {
    const list = res.data ?? []
    allCategories.value = ['全部', ...list]
  })
}

/** 浏览器 TTS 朗读单词 */
function speak(text: string) {
  if (!text || !('speechSynthesis' in window)) return
  try {
    window.speechSynthesis.cancel()
    const utter = new SpeechSynthesisUtterance(text)
    utter.lang = 'en-US'
    utter.rate = 0.9
    window.speechSynthesis.speak(utter)
  } catch (_e) {}
}

onMounted(() => {
  loadCategories()
  loadData()
})
</script>

<style scoped>
.word-book { padding: 8px; }
.header { margin-bottom: 20px; }
.header h2 { margin: 0 0 6px; font-size: 22px; color: #303133; }
.subtitle { margin: 0; color: #909399; font-size: 13px; }

.categories {
  display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 14px;
}
.cat-tag { cursor: pointer; font-size: 13px; }

.toolbar {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 16px;
}
.total-info { color: #909399; font-size: 13px; }

.word-card {
  border: 1px solid #ebeef5; border-radius: 10px; padding: 16px 18px;
  margin-bottom: 16px; transition: box-shadow 0.2s, transform 0.2s;
  background: #fff; height: 100%;
}
.word-card:hover {
  box-shadow: 0 4px 16px rgba(64, 158, 255, 0.15);
  transform: translateY(-2px);
}
.card-top {
  display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px;
}
.word-en {
  font-size: 20px; font-weight: 700; color: #303133;
  cursor: pointer; display: flex; align-items: center; gap: 6px;
}
.word-en:hover { color: #409eff; }
.speaker { opacity: 0.5; font-size: 16px; }
.word-phonetic { color: #67c23a; font-family: "DejaVu Sans", serif; font-size: 13px; margin-bottom: 6px; }
.word-cn { color: #606266; font-size: 15px; margin-bottom: 4px; }
.word-pos {
  font-size: 11px; color: #909399; background: #f4f4f5;
  display: inline-block; padding: 1px 8px; border-radius: 10px; margin-bottom: 8px;
}
.word-example {
  margin-top: 10px; padding-top: 10px; border-top: 1px dashed #ebeef5;
}
.example-en {
  color: #303133; font-size: 13px; font-style: italic;
  cursor: pointer; margin-bottom: 4px; line-height: 1.6;
}
.example-en:hover { color: #409eff; }
.example-cn { color: #909399; font-size: 12px; line-height: 1.6; }
.mt-4 { margin-top: 16px; }
</style>
