export const LANGUAGE_OPTIONS = [
  { value: '', label: '自动识别语种' },
  { value: 'zh', label: '中文' },
  { value: 'en', label: '英文' },
  { value: 'zh|en', label: '中文与英文' },
  { value: 'zh|en|ja', label: '中文、英文与日文' },
  { value: 'ja', label: '日文' },
  { value: 'ko', label: '韩文' },
  { value: 'fr', label: '法文' },
  { value: 'de', label: '德文' },
] as const

export const MAX_FILE_SIZE = 10 * 1024 * 1024
