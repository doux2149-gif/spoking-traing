<script setup lang="ts">
import type { Transcription } from '../../types/transcription'

defineProps<{
  items: readonly Transcription[]
  isLoading: boolean
}>()

function formatDate(value: string): string {
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}
</script>

<template>
  <section class="history-section" aria-labelledby="history-title">
    <h2 id="history-title">
      最近转写
    </h2>
    <div v-if="isLoading" class="history-skeleton" aria-label="正在加载最近转写">
      <span v-for="index in 3" :key="index" />
    </div>
    <div v-else-if="items.length" class="history-list">
      <article v-for="item in items" :key="item.id" class="history-item">
        <div>
          <h3 :title="item.filename">
            {{ item.filename }}
          </h3>
          <p>{{ item.text || '未识别到文字' }}</p>
        </div>
        <time :datetime="item.createdAt">{{ formatDate(item.createdAt) }}</time>
      </article>
    </div>
    <div v-else class="empty-state">
      <p>还没有转写记录</p>
      <span>完成第一次识别后，结果会显示在这里。</span>
    </div>
  </section>
</template>
