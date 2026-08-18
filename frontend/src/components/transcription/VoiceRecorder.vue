<script setup lang="ts">
import { computed, shallowRef } from 'vue'
import { LANGUAGE_OPTIONS } from '../../constants'

const emit = defineEmits<{
  submit: [file: File, options: { encoding: 'raw', sampleRate: 16000, language: string }]
  clearError: []
}>()

const props = defineProps<{
  isSubmitting: boolean
}>()

const status = shallowRef<'idle' | 'recording' | 'submitting'>('idle')
const elapsedSeconds = shallowRef(0)
const language = shallowRef('')

let stream: MediaStream | null = null
let audioContext: AudioContext | null = null
let workletNode: AudioWorkletNode | null = null
let sourceNode: MediaStreamAudioSourceNode | null = null
let silentGain: GainNode | null = null
let chunks: ArrayBuffer[] = []
let timer: number | null = null

const MAX_SECONDS = 60

const isRecording = computed(() => status.value === 'recording')
const isBusy = computed(() => props.isSubmitting || status.value === 'submitting')

const timeLabel = computed(() => {
  const m = Math.floor(elapsedSeconds.value / 60)
  const s = elapsedSeconds.value % 60
  return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`
})

const statusLabel = computed(() => {
  if (status.value === 'recording')
    return '正在录音，请开始说话'
  if (props.isSubmitting)
    return '正在识别，请稍候'
  return '点击下方按钮开始录音'
})

async function startRecording(): Promise<void> {
  if (status.value !== 'idle')
    return

  emit('clearError')
  chunks = []
  elapsedSeconds.value = 0

  try {
    stream = await navigator.mediaDevices.getUserMedia({
      audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true, autoGainControl: true },
    })
    audioContext = new AudioContext({ latencyHint: 'interactive' })
    await audioContext.resume()
    await audioContext.audioWorklet.addModule('/pcm-recorder-worklet.js')
    sourceNode = audioContext.createMediaStreamSource(stream)
    workletNode = new AudioWorkletNode(audioContext, 'pcm-recorder-processor')
    silentGain = audioContext.createGain()
    silentGain.gain.value = 0

    workletNode.port.onmessage = (event: MessageEvent<ArrayBuffer>) => {
      chunks.push(event.data)
    }

    sourceNode.connect(workletNode)
    workletNode.connect(silentGain)
    silentGain.connect(audioContext.destination)

    status.value = 'recording'
    timer = window.setInterval(() => {
      elapsedSeconds.value += 1
      if (elapsedSeconds.value >= MAX_SECONDS)
        stopRecording()
    }, 1000)
  }
  catch {
    cleanup()
    emit('clearError')
  }
}

function stopRecording(): void {
  if (status.value !== 'recording')
    return

  stopTimer()
  stopAudio()

  const totalBytes = chunks.reduce((sum, buf) => sum + buf.byteLength, 0)
  if (totalBytes === 0) {
    status.value = 'idle'
    return
  }

  const merged = new Uint8Array(totalBytes)
  let offset = 0
  for (const chunk of chunks) {
    merged.set(new Uint8Array(chunk), offset)
    offset += chunk.byteLength
  }
  chunks = []

  const blob = new Blob([merged], { type: 'audio/pcm' })
  const file = new File([blob], `录音-${new Date().toLocaleTimeString()}.pcm`, { type: 'audio/pcm' })

  status.value = 'idle'
  emit('submit', file, { encoding: 'raw', sampleRate: 16000, language: language.value })
}

function stopAudio(): void {
  workletNode?.disconnect()
  sourceNode?.disconnect()
  silentGain?.disconnect()
  workletNode = null
  sourceNode = null
  silentGain = null
  stream?.getTracks().forEach(t => t.stop())
  stream = null
  if (audioContext) {
    void audioContext.close()
    audioContext = null
  }
}

function stopTimer(): void {
  if (timer !== null) {
    clearInterval(timer)
    timer = null
  }
}

function cleanup(): void {
  stopTimer()
  stopAudio()
  status.value = 'idle'
}
</script>

<template>
  <div class="recorder-shell">
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
        {{ isBusy ? '正在识别' : '开始录音' }}
      </button>
      <button v-else class="stop-button" type="button" @click="stopRecording">
        <span class="stop-button-icon" aria-hidden="true" />
        停止并转写
      </button>
    </div>
  </div>
</template>
