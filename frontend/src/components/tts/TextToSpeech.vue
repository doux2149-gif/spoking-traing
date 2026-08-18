<script setup lang="ts">
import { shallowRef } from 'vue'

const text = shallowRef('')
const isSynthesizing = shallowRef(false)
const errorMessage = shallowRef('')
const audioUrl = shallowRef<string | null>(null)
let audioElement: HTMLAudioElement | null = null

const VCN_OPTIONS = [
  { value: 'x4_lingxiaolu_oral', label: '玲小璐（中文口语）' },
  { value: 'x4_lingfeizhe_oral', label: '凌飞哲（中文口语）' },
  { value: 'xiaoyan', label: '小燕（中文）' },
  { value: 'aisjiuxu', label: '许久（中文）' },
  { value: 'x4_yezi', label: '叶子（中文温柔）' },
  { value: 'catherine', label: 'Catherine（英文女声）' },
  { value: 'henry', label: 'Henry（英文男声）' },
  { value: 'vimary', label: 'Mary（英文女声）' },
] as const

const vcn = shallowRef(VCN_OPTIONS[0].value)

async function synthesize(): Promise<void> {
  if (!text.value.trim()) {
    errorMessage.value = '请输入需要合成的文字'
    return
  }
  errorMessage.value = ''
  isSynthesizing.value = true
  cleanup()

  try {
    const response = await fetch('/api/tts', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ text: text.value.trim(), vcn: vcn.value }),
    })
    if (!response.ok) {
      const err = await response.json().catch(() => ({ message: `请求失败（${response.status}）` }))
      throw new Error(err.message || `请求失败（${response.status}）`)
    }
    const blob = await response.blob()
    audioUrl.value = URL.createObjectURL(blob)
    audioElement = new Audio(audioUrl.value)
    audioElement.play()
  }
  catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '语音合成失败'
  }
  finally {
    isSynthesizing.value = false
  }
}

function replay(): void {
  if (audioElement) {
    audioElement.currentTime = 0
    audioElement.play()
  }
}

function cleanup(): void {
  if (audioUrl.value) {
    URL.revokeObjectURL(audioUrl.value)
    audioUrl.value = null
  }
  audioElement = null
}
</script>

<template>
  <div class="tts-shell">
    <div class="field-group">
      <span class="field-label">输入文字</span>
      <textarea
        v-model="text"
        class="tts-textarea"
        placeholder="输入需要合成语音的文字，最多约 2000 字"
        rows="4"
        :disabled="isSynthesizing"
      />
    </div>

    <div class="options-grid">
      <label class="field-group">
        <span class="field-label">发音人</span>
        <select v-model="vcn" :disabled="isSynthesizing">
          <option v-for="option in VCN_OPTIONS" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </label>
    </div>

    <p v-if="errorMessage" class="field-error" role="alert">
      {{ errorMessage }}
    </p>

    <div class="tts-actions">
      <button class="primary-button" type="button" :disabled="isSynthesizing" @click="synthesize">
        <span v-if="isSynthesizing" class="button-loader" aria-hidden="true" />
        {{ isSynthesizing ? '正在合成' : '合成语音' }}
      </button>
      <button
        v-if="audioUrl"
        class="secondary-button"
        type="button"
        :disabled="isSynthesizing"
        @click="replay"
      >
        重新播放
      </button>
    </div>

    <div v-if="audioUrl" class="tts-player">
      <audio :src="audioUrl" controls class="tts-audio" />
    </div>
  </div>
</template>
