<script setup lang="ts">
import { computed, watch } from 'vue'
import { useRealtimeTranscription } from '../../composables/useRealtimeTranscription'
import { LANGUAGE_OPTIONS } from '../../constants'

const emit = defineEmits<{
  completed: []
}>()

const language = defineModel<string>('language', { default: '' })
const {
  status,
  liveText,
  errorMessage,
  elapsedSeconds,
  sid,
  isRecording,
  isBusy,
  start,
  stop,
  reset,
  clearError,
} = useRealtimeTranscription()

const timeLabel = computed(() => {
  const minutes = Math.floor(elapsedSeconds.value / 60)
  const seconds = elapsedSeconds.value % 60
  return `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`
})

const statusLabel = computed(() => {
  if (status.value === 'connecting')
    return '正在连接识别服务'
  if (status.value === 'recording')
    return '正在聆听，请开始说话'
  if (status.value === 'finishing')
    return '正在整理最后的文字'
  return '准备好后点击开始录音'
})

watch(status, (current, previous) => {
  if (current === 'idle' && (previous === 'recording' || previous === 'finishing'))
    emit('completed')
})

function startRecording(): void {
  void start(language.value)
}
</script>

<template>
  <div class="recorder-shell">
    <div v-if="errorMessage" class="error-banner" role="alert">
      <div>
        <strong>实时识别没有启动</strong>
        <p>{{ errorMessage }}</p>
      </div>
      <button type="button" aria-label="关闭错误提示" @click="clearError">
        关闭
      </button>
    </div>

    <label class="field-group language-field">
      <span class="field-label">识别语种</span>
      <select v-model="language" :disabled="isRecording || isBusy">
        <option v-for="option in LANGUAGE_OPTIONS" :key="option.value" :value="option.value">
          {{ option.label }}
        </option>
      </select>
    </label>

    <div class="recorder-stage" :class="{ 'recorder-stage-active': isRecording }">
      <div class="recording-visual" aria-hidden="true">
        <span v-for="index in 5" :key="index" />
      </div>
      <p class="recording-status" aria-live="polite">
        {{ statusLabel }}
      </p>
      <strong class="recording-time">{{ timeLabel }}</strong>
      <p class="recording-limit">
        最长可连续录制 60 秒
      </p>

      <button
        v-if="!isRecording"
        class="record-button"
        type="button"
        :disabled="isBusy"
        @click="startRecording"
      >
        <span class="record-button-dot" aria-hidden="true" />
        {{ isBusy ? '请稍候' : '开始录音' }}
      </button>
      <button v-else class="stop-button" type="button" @click="stop">
        <span class="stop-button-icon" aria-hidden="true" />
        停止并完成转写
      </button>
    </div>

    <section class="live-result" aria-labelledby="live-result-title">
      <div class="live-result-heading">
        <h3 id="live-result-title">
          实时文字
        </h3>
        <button v-if="liveText && status === 'idle'" type="button" @click="reset">
          开始新录音
        </button>
      </div>
      <div class="live-result-text" aria-live="polite" tabindex="0">
        {{ liveText || '开始录音后，识别到的文字会显示在这里。' }}
      </div>
      <p v-if="sid" class="result-meta" :title="sid">
        会话编号 {{ sid }}
      </p>
    </section>
  </div>
</template>
