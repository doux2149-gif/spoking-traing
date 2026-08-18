<script setup lang="ts">
import type { TranscriptionOptions } from '../../types/transcription'
import { computed, reactive, shallowRef } from 'vue'
import { LANGUAGE_OPTIONS, MAX_FILE_SIZE } from '../../constants'

const props = defineProps<{
  isSubmitting: boolean
}>()

const emit = defineEmits<{
  submit: [file: File, options: TranscriptionOptions]
  clearError: []
}>()

const file = shallowRef<File | null>(null)
const localError = shallowRef('')
const options = reactive<TranscriptionOptions>({
  encoding: 'raw',
  sampleRate: 16000,
  language: '',
})

const acceptedTypes = computed(() => options.encoding === 'raw' ? '.pcm,.raw' : '.mp3')
const fileDescription = computed(() => file.value
  ? `${file.value.name} · ${formatBytes(file.value.size)}`
  : options.encoding === 'raw' ? '选择 PCM 或 RAW 文件' : '选择 MP3 文件')

function selectFile(event: Event): void {
  const input = event.target as HTMLInputElement
  const selected = input.files?.[0] ?? null
  localError.value = ''
  emit('clearError')
  if (selected && selected.size > MAX_FILE_SIZE) {
    localError.value = '文件不能超过 10 MB'
    file.value = null
    input.value = ''
    return
  }
  file.value = selected
}

function submit(): void {
  localError.value = ''
  if (!file.value) {
    localError.value = '请先选择音频文件'
    return
  }
  emit('submit', file.value, { ...options })
}

function formatBytes(bytes: number): string {
  return bytes < 1024 * 1024
    ? `${(bytes / 1024).toFixed(1)} KB`
    : `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
</script>

<template>
  <form class="transcription-form" @submit.prevent="submit">
    <div class="field-group">
      <span class="field-label">音频文件</span>
      <label class="file-picker" :class="{ 'file-picker-selected': file }">
        <input
          class="file-input"
          type="file"
          :accept="acceptedTypes"
          :disabled="props.isSubmitting"
          @change="selectFile"
        >
        <span class="file-icon" aria-hidden="true">↑</span>
        <span>
          <strong>{{ fileDescription }}</strong>
          <small>最长 60 秒，单声道，16 bit</small>
        </span>
      </label>
    </div>

    <div class="options-grid">
      <label class="field-group">
        <span class="field-label">音频格式</span>
        <select v-model="options.encoding" :disabled="props.isSubmitting || Boolean(file)">
          <option value="raw">PCM 原始音频</option>
          <option value="lame">MP3 音频</option>
        </select>
      </label>

      <label class="field-group">
        <span class="field-label">采样率</span>
        <select v-model.number="options.sampleRate" :disabled="props.isSubmitting">
          <option :value="16000">16 kHz</option>
          <option :value="8000">8 kHz</option>
        </select>
      </label>

      <label class="field-group">
        <span class="field-label">识别语种</span>
        <select v-model="options.language" :disabled="props.isSubmitting">
          <option v-for="language in LANGUAGE_OPTIONS" :key="language.value" :value="language.value">
            {{ language.label }}
          </option>
        </select>
      </label>
    </div>

    <p v-if="localError" class="field-error" role="alert">
      {{ localError }}
    </p>

    <button class="primary-button" type="submit" :disabled="props.isSubmitting">
      <span v-if="props.isSubmitting" class="button-loader" aria-hidden="true" />
      {{ props.isSubmitting ? '正在识别，请稍候' : '开始转写' }}
    </button>
  </form>
</template>
