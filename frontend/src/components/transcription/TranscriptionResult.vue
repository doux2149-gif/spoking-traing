<script setup lang="ts">
import type { Transcription } from '../../types/transcription'
import { shallowRef } from 'vue'

const props = defineProps<{
  result: Transcription
}>()

const copied = shallowRef(false)

async function copyText(): Promise<void> {
  if (!props.result.text)
    return
  await navigator.clipboard.writeText(props.result.text)
  copied.value = true
  window.setTimeout(() => {
    copied.value = false
  }, 1800)
}
</script>

<template>
  <section class="result-section" aria-labelledby="result-title">
    <div class="section-heading">
      <div>
        <h2 id="result-title">
          转写结果
        </h2>
        <p>{{ props.result.filename }}</p>
      </div>
      <button class="secondary-button" type="button" :disabled="!props.result.text" @click="copyText">
        {{ copied ? '已复制' : '复制文字' }}
      </button>
    </div>
    <div class="result-text" tabindex="0">
      {{ props.result.text || '本段音频没有识别到文字。' }}
    </div>
    <p v-if="props.result.sid" class="result-meta" :title="props.result.sid">
      会话编号 {{ props.result.sid }}
    </p>
  </section>
</template>
