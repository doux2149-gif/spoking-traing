import type { Transcription, TranscriptionOptions } from '../types/transcription'
import { readonly, ref, shallowRef } from 'vue'
import { createTranscription, getRecentTranscriptions } from '../services/transcription'

export function useTranscription() {
  const result = shallowRef<Transcription | null>(null)
  const recent = ref<Transcription[]>([])
  const isSubmitting = shallowRef(false)
  const isLoadingHistory = shallowRef(false)
  const errorMessage = shallowRef('')

  async function submit(file: File, options: TranscriptionOptions): Promise<void> {
    isSubmitting.value = true
    errorMessage.value = ''
    try {
      result.value = await createTranscription(file, options)
      recent.value = [result.value, ...recent.value.filter(item => item.id !== result.value?.id)].slice(0, 10)
    }
    catch (error) {
      errorMessage.value = error instanceof Error ? error.message : '识别失败，请稍后重试'
    }
    finally {
      isSubmitting.value = false
    }
  }

  async function loadRecent(): Promise<void> {
    isLoadingHistory.value = true
    try {
      recent.value = await getRecentTranscriptions()
    }
    catch {
      recent.value = []
    }
    finally {
      isLoadingHistory.value = false
    }
  }

  function clearError(): void {
    errorMessage.value = ''
  }

  return {
    result: readonly(result),
    recent: readonly(recent),
    isSubmitting: readonly(isSubmitting),
    isLoadingHistory: readonly(isLoadingHistory),
    errorMessage: readonly(errorMessage),
    submit,
    loadRecent,
    clearError,
  }
}
