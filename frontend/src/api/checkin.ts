import request from './request'

export interface CheckinTodayResponse {
  todayCheckedIn: boolean
  checkinDate: string
  checkinType?: number
  todayRounds?: number
  todaySeconds?: number
  todayErrors?: number
  todaySuggestions?: number
  streakCount: number
  maxStreak: number
  totalDays: number
  totalSeconds: number
  totalRounds: number
}

export interface CheckinStatsResponse {
  todayCheckedIn: boolean
  checkinDate: string
  streakCount: number
  maxStreak: number
  totalDays: number
  totalSeconds: number
  totalRounds: number
}

export interface CheckinDayDetail {
  checkinType: number
  totalSeconds: number
  totalRounds: number
  totalErrors: number
  totalSuggestions: number
}

/** 今日打卡状态 + 统计 */
export function getCheckinToday() {
  return request.get<any, CheckinTodayResponse>('/checkin/today')
}

/** 累计统计 */
export function getCheckinStats() {
  return request.get<any, CheckinStatsResponse>('/checkin/stats')
}

/** 月度打卡日历,返回 { 'YYYY-MM-DD': DayDetail } */
export function getCheckinCalendar(year?: number, month?: number) {
  const params: Record<string, any> = {}
  if (year !== undefined) params.year = year
  if (month !== undefined) params.month = month
  return request.get<any, Record<string, CheckinDayDetail>>('/checkin/calendar', { params })
}
