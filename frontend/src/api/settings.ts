import request from './request'

export interface DeepSeekSettingStatus {
  configured: boolean
}

export function getDeepSeekSettingStatus() {
  return request.get<DeepSeekSettingStatus>('/system/settings/deepseek')
}

export function updateDeepSeekApiKey(apiKey: string) {
  return request.put('/system/settings/deepseek', { apiKey })
}
