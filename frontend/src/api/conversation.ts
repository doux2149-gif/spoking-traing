import request from './request'

export interface Conversation {
  id: number
  userId: number
  sceneId: number | null
  title: string
  isStarred: number
  duration: number
  roundCount: number
  errorCount: number
  suggestionCount: number
  status: number // 1:进行中 2:已结束
  summary: string | null
  createTime: string
  updateTime: string
}

export interface ConversationMessage {
  id: number
  conversationId: number
  role: string
  content: string
  grammarJson: string | null
  hasError: number
  hasSuggestion: number
  createTime: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

/** 创建会话 */
export function createConversation(data: { sceneId?: number; title?: string }) {
  return request.post('/conversations', data)
}

/** 分页查询会话列表 */
export function getConversations(page = 1, size = 10) {
  return request.get(`/conversations?page=${page}&size=${size}`)
}

/** 获取会话详情 */
export function getConversation(id: number) {
  return request.get(`/conversations/${id}`)
}

/** 获取会话消息列表 */
export function getConversationMessages(id: number) {
  return request.get(`/conversations/${id}/messages`)
}

/** 添加消息到会话 */
export function addConversationMessage(id: number, data: {
  role: string
  content: string
  grammarJson?: string | null
  hasError: boolean
  hasSuggestion: boolean
}) {
  return request.post(`/conversations/${id}/messages`, data)
}

/** 结束会话 */
export function endConversation(id: number, data?: { duration?: number; summary?: string }) {
  return request.put(`/conversations/${id}/end`, data || {})
}

/** 删除会话 */
export function deleteConversation(id: number) {
  return request.delete(`/conversations/${id}`)
}

/** 收藏/取消收藏会话 */
export function toggleConversationStar(id: number) {
  return request.put(`/conversations/${id}/star`)
}

/** 获取当前进行中的会话 */
export function getActiveConversation() {
  return request.get('/conversations/active')
}

// --- 会话报告 ---

export interface ConversationReport {
  id: number
  conversationId: number
  userId: number
  roundCount: number
  duration: number
  errorCount: number
  suggestionCount: number
  grammarScore: number
  vocabularyScore: number
  fluencyScore: number
  pronunciationScore: number | null
  overallScore: number
  /** JSON 文本 */
  topErrors: string | null
  topSuggestions: string | null
  summary: string | null
  /** 0 待生成 1 规则聚合完成 2 LLM 已生成 3 失败 */
  status: number
  createTime: string
  updateTime: string
}

export interface ReportTopError {
  wrong: string
  correct: string
  reason: string
  dimension: 'grammar' | 'vocabulary' | string
}

export interface ReportTopSuggestion {
  level: 'none' | 'better' | 'advanced' | string
  alternatives: string
  tip: string
  dimension: 'grammar' | 'vocabulary' | string
}

export const conversationReportApi = {
  get: (conversationId: number) => request.get(`/conversations/${conversationId}/report`),
  regenerate: (conversationId: number) => request.post(`/conversations/${conversationId}/report/regenerate`),
  myReports: () => request.get('/conversations/reports')
}

/** 安全解析后端 JSON 文本字段 */
export function parseReportJson<T>(text: string | null | undefined): T[] {
  if (!text) return []
  try { return JSON.parse(text) as T[] } catch { return [] }
}
