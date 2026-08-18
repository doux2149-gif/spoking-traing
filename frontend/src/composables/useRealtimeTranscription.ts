import type { RealtimeEvent, RecordingStatus } from '../types/transcription'
import { computed, onBeforeUnmount, readonly, shallowRef } from 'vue'

const MAX_RECORDING_SECONDS = 60
const MAX_AUDIO_BYTES = 16000 * 2 * MAX_RECORDING_SECONDS

export function useRealtimeTranscription() {
  const status = shallowRef<RecordingStatus>('idle')
  const liveText = shallowRef('')
  const errorMessage = shallowRef('')
  const elapsedSeconds = shallowRef(0)
  const taskId = shallowRef<number | null>(null)
  const sid = shallowRef('')

  let socket: WebSocket | null = null
  let stream: MediaStream | null = null
  let audioContext: AudioContext | null = null
  let sourceNode: MediaStreamAudioSourceNode | null = null
  let workletNode: AudioWorkletNode | null = null
  let silentGain: GainNode | null = null
  let timer: number | null = null
  let hasSentAudio = false
  let sentAudioBytes = 0

  const isRecording = computed(() => status.value === 'recording')
  const isBusy = computed(() => status.value === 'connecting' || status.value === 'finishing')

  async function start(language: string): Promise<void> {
    if (status.value !== 'idle' && status.value !== 'error')
      return

    errorMessage.value = ''
    liveText.value = ''
    elapsedSeconds.value = 0
    taskId.value = null
    sid.value = ''
    hasSentAudio = false
    sentAudioBytes = 0
    status.value = 'connecting'

    try {
      ensureRecordingSupport()
      stream = await navigator.mediaDevices.getUserMedia({
        audio: {
          channelCount: 1,
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
        },
      })
      await connectSocket(language)
    }
    catch (error) {
      handleFailure(error)
    }
  }

  function stop(): void {
    if (status.value !== 'recording' || !hasSentAudio)
      return
    status.value = 'finishing'
    stopAudioCapture()
    socket?.send(JSON.stringify({ type: 'stop' }))
  }

  function reset(): void {
    cleanup()
    status.value = 'idle'
    liveText.value = ''
    errorMessage.value = ''
    elapsedSeconds.value = 0
    taskId.value = null
    sid.value = ''
  }

  function clearError(): void {
    errorMessage.value = ''
    if (status.value === 'error')
      status.value = 'idle'
  }

  function connectSocket(language: string): Promise<void> {
    return new Promise((resolve, reject) => {
      let connectionReady = false
      const failConnection = (error: Error): void => {
        if (connectionReady)
          handleFailure(error)
        else
          reject(error)
      }
      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
      socket = new WebSocket(`${protocol}//${window.location.host}/ws/transcriptions`)
      socket.binaryType = 'arraybuffer'

      socket.onopen = () => {
        socket?.send(JSON.stringify({ type: 'start', language }))
      }
      socket.onmessage = (message) => {
        const event = JSON.parse(String(message.data)) as RealtimeEvent
        if (event.type === 'ready') {
          taskId.value = event.taskId ?? null
          void beginAudioCapture()
            .then(() => {
              connectionReady = true
              resolve()
            })
            .catch(failConnection)
          return
        }
        if (event.type === 'partial' || event.type === 'final') {
          liveText.value = event.text ?? liveText.value
          sid.value = event.sid ?? sid.value
          taskId.value = event.taskId ?? taskId.value
        }
        if (event.type === 'finishing') {
          status.value = 'finishing'
        }
        if (event.type === 'final') {
          stopAudioCapture()
          status.value = 'idle'
          socket?.close(1000, 'recognition completed')
          socket = null
        }
        if (event.type === 'error')
          failConnection(new Error(event.message || '实时识别失败'))
      }
      socket.onerror = () => failConnection(new Error('无法连接实时识别服务'))
      socket.onclose = (event) => {
        if (!event.wasClean && status.value !== 'idle')
          handleFailure(new Error('实时识别连接已断开'))
      }
    })
  }

  async function beginAudioCapture(): Promise<void> {
    if (!stream)
      throw new Error('未获取到麦克风音频')

    audioContext = new AudioContext({ latencyHint: 'interactive' })
    await audioContext.resume()
    await audioContext.audioWorklet.addModule('/pcm-recorder-worklet.js')
    sourceNode = audioContext.createMediaStreamSource(stream)
    workletNode = new AudioWorkletNode(audioContext, 'pcm-recorder-processor')
    silentGain = audioContext.createGain()
    silentGain.gain.value = 0
    workletNode.port.onmessage = (event: MessageEvent<ArrayBuffer>) => {
      if (socket?.readyState === WebSocket.OPEN && status.value === 'recording') {
        if (sentAudioBytes + event.data.byteLength > MAX_AUDIO_BYTES) {
          stop()
          return
        }
        socket.send(event.data)
        sentAudioBytes += event.data.byteLength
        hasSentAudio = true
      }
    }
    sourceNode.connect(workletNode)
    workletNode.connect(silentGain)
    silentGain.connect(audioContext.destination)
    status.value = 'recording'
    timer = window.setInterval(() => {
      elapsedSeconds.value += 1
      if (elapsedSeconds.value >= MAX_RECORDING_SECONDS)
        stop()
    }, 1000)
  }

  function stopAudioCapture(): void {
    stopTimer()
    workletNode?.disconnect()
    sourceNode?.disconnect()
    silentGain?.disconnect()
    workletNode = null
    sourceNode = null
    silentGain = null
    stream?.getTracks().forEach(track => track.stop())
    stream = null
    if (audioContext) {
      void audioContext.close()
      audioContext = null
    }
  }

  function stopTimer(): void {
    if (timer !== null) {
      window.clearInterval(timer)
      timer = null
    }
  }

  function cleanup(): void {
    stopAudioCapture()
    if (socket) {
      socket.close()
      socket = null
    }
  }

  function handleFailure(error: unknown): void {
    cleanup()
    status.value = 'error'
    if (error instanceof DOMException && error.name === 'NotAllowedError') {
      errorMessage.value = '没有麦克风权限，请在浏览器设置中允许访问麦克风'
      return
    }
    errorMessage.value = error instanceof Error ? error.message : '实时识别失败，请稍后重试'
  }

  function ensureRecordingSupport(): void {
    if (!navigator.mediaDevices?.getUserMedia || !window.AudioWorkletNode)
      throw new Error('当前浏览器不支持实时录音，请使用最新版 Chrome、Edge 或 Safari')
  }

  onBeforeUnmount(cleanup)

  return {
    status: readonly(status),
    liveText: readonly(liveText),
    errorMessage: readonly(errorMessage),
    elapsedSeconds: readonly(elapsedSeconds),
    taskId: readonly(taskId),
    sid: readonly(sid),
    isRecording,
    isBusy,
    start,
    stop,
    reset,
    clearError,
  }
}
