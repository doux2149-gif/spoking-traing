import type { ApiError, Transcription, TranscriptionOptions } from '../types/transcription'

async function parseResponse<T>(response: Response): Promise<T> {
  if (response.ok)
    return response.json() as Promise<T>

  const fallback = `请求失败（${response.status}）`
  try {
    const error = await response.json() as ApiError
    throw new Error(error.message || fallback)
  }
  catch (error) {
    if (error instanceof Error && error.message !== 'Unexpected end of JSON input')
      throw error
    throw new Error(fallback)
  }
}

export async function createTranscription(
  file: File,
  options: TranscriptionOptions,
): Promise<Transcription> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('encoding', options.encoding)
  formData.append('sampleRate', options.sampleRate.toString())
  if (options.language)
    formData.append('language', options.language)

  return parseResponse<Transcription>(await fetch('/api/transcriptions', {
    method: 'POST',
    body: formData,
  }))
}

export async function getRecentTranscriptions(): Promise<Transcription[]> {
  return parseResponse<Transcription[]>(await fetch('/api/transcriptions'))
}
