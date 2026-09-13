/**
 * 管理端扩展模块集中 API: LLM 用量/单价、公告、登录日志、在线用户、菜单。
 * 普通请求响应拦截器已解包, 业务侧消费 res.data; blob 请求返回完整 AxiosResponse。
 */
import request from './request'

// ---------------- 公共类型 ----------------
export interface PageResult<T> {
  total: number
  rows: T[]
}

export interface DateRangeParams {
  start?: string
  end?: string
}

// ---------------- LLM 用量与单价 ----------------
export interface UsageSummary {
  calls: number
  promptTokens: number
  completionTokens: number
  totalTokens: number
  cost: number
}

export interface UsageDailyRow extends UsageSummary {
  date: string
}

export interface UsageGroupRow {
  calls: number
  promptTokens: number
  completionTokens: number
  totalTokens: number
  cost: number
  model?: string
  operation?: string
}

export interface UsageTopUserRow {
  userId: number
  username: string
  calls: number
  promptTokens: number
  completionTokens: number
  totalTokens: number
  cost: number
}

export interface ModelPrice {
  id?: number
  model: string
  inputPrice: number
  outputPrice: number
  enabled?: number
  remark?: string
}

export const usageApi = {
  summary: (params: DateRangeParams) =>
    request.get('/system/llm-usage/summary', { params }),
  daily: (params: DateRangeParams) =>
    request.get('/system/llm-usage/daily', { params }),
  byModel: (params: DateRangeParams) =>
    request.get('/system/llm-usage/by-model', { params }),
  byOperation: (params: DateRangeParams) =>
    request.get('/system/llm-usage/by-operation', { params }),
  topUsers: (params: DateRangeParams) =>
    request.get('/system/llm-usage/top-users', { params }),
  exportCsv: (params: DateRangeParams) =>
    request.get('/system/llm-usage/export', { params, responseType: 'blob' })
}

export const priceApi = {
  list: () => request.get('/system/llm-prices'),
  save: (rows: ModelPrice[]) => request.put('/system/llm-prices', rows)
}

// ---------------- 公告 ----------------
export interface Notice {
  id?: number
  title: string
  content: string
  noticeType: number // 1=通知 2=维护
  displayType: number // 1=横幅 2=弹窗
  status: number // 0=草稿 1=已发布 2=已停用
  publishStart?: string | null
  publishEnd?: string | null
  createBy?: number
  createTime?: string
  updateTime?: string
}

export interface NoticeQuery {
  title?: string
  noticeType?: number | ''
  status?: number | ''
  pageNum?: number
  pageSize?: number
}

export const noticeApi = {
  list: (params: NoticeQuery) =>
    request.get('/system/notices', { params }),
  detail: (id: number) =>
    request.get(`/system/notices/${id}`),
  create: (data: Notice) =>
    request.post('/system/notices', data),
  update: (id: number, data: Notice) =>
    request.put(`/system/notices/${id}`, data),
  remove: (id: number) =>
    request.delete(`/system/notices/${id}`),
  publish: (id: number) =>
    request.put(`/system/notices/${id}/publish`),
  disable: (id: number) =>
    request.put(`/system/notices/${id}/disable`),
  /** 用户端: 当前生效公告(登录即可) */
  active: () => request.get('/notices/active')
}

// ---------------- 登录日志 ----------------
export interface LoginLog {
  id: number
  username: string
  userId: number | null
  ip: string
  userAgent: string
  status: number
  message: string
  loginTime: string
}

export interface LoginLogQuery {
  username?: string
  status?: number | ''
  startDate?: string
  endDate?: string
  pageNum?: number
  pageSize?: number
}

export const loginLogApi = {
  list: (params: LoginLogQuery) =>
    request.get('/system/login-logs', { params }),
  exportCsv: (params: LoginLogQuery) =>
    request.get('/system/login-logs/export', { params, responseType: 'blob' })
}

// ---------------- 在线用户 ----------------
export interface OnlineUser {
  tokenId: string
  userId: number
  username: string
  loginIp: string
  userAgent: string
  loginTime: string
  lastActiveTime: string
}

export const onlineApi = {
  list: (username?: string) =>
    request.get('/system/online', { params: { username } }),
  forceOffline: (tokenId: string) =>
    request.delete(`/system/online/${encodeURIComponent(tokenId)}`)
}

// ---------------- 菜单管理 ----------------
export interface SysMenu {
  id?: number
  parentId: number
  menuName: string
  path: string
  component?: string | null
  perms?: string | null
  icon?: string | null
  menuType: string // M=目录 C=菜单
  sort?: number
  visible?: number
  isInternal?: number
  createTime?: string
  updateTime?: string
  children?: SysMenu[]
}

export const menuApi = {
  list: () => request.get('/system/menus'),
  create: (data: SysMenu) => request.post('/system/menus', data),
  update: (id: number, data: SysMenu) => request.put(`/system/menus/${id}`, data),
  remove: (id: number) => request.delete(`/system/menus/${id}`)
}
