export interface Transcription {
  id: number
  filename: string
  status: 'PROCESSING' | 'SUCCESS' | 'FAILED'
  text: string | null
  sid: string | null
  createdAt: string
}

export interface RealtimeEvent {
  type: 'ready' | 'partial' | 'finishing' | 'final' | 'error'
  text?: string
  sid?: string
  taskId?: number
  message?: string
}

export type RecordingStatus = 'idle' | 'connecting' | 'recording' | 'finishing' | 'error'

export interface TranscriptionOptions {
  encoding: 'raw' | 'lame'
  sampleRate: 8000 | 16000
  language: string
}

export interface ApiError {
  message: string
  timestamp: string
}
