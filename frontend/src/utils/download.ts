import type { AxiosResponse } from 'axios'

/**
 * 从 blob 响应中读取 Content-Disposition 文件名(支持 filename*=UTF-8''),
 * 取不到时用 fallback, 触发浏览器下载并释放对象 URL。
 */
export function saveBlobResponse(response: AxiosResponse, fallbackName: string) {
  const data: Blob = response.data instanceof Blob
    ? response.data
    : new Blob([response.data], { type: 'text/csv;charset=utf-8' })
  let filename = fallbackName
  const disposition = response.headers?.['content-disposition']
  if (typeof disposition === 'string') {
    const utf8Match = disposition.match(/filename\*=UTF-8''([^;]+)/i)
    const plainMatch = disposition.match(/filename="?([^";]+)"?/i)
    const raw = utf8Match?.[1] || plainMatch?.[1]
    if (raw) {
      try {
        filename = decodeURIComponent(raw.trim())
      } catch {
        filename = raw.trim()
      }
    }
  }
  const url = URL.createObjectURL(data)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

/** 下载文件名后缀: login-logs-yyyyMMddHHmmss.csv */
export function timestampName(prefix: string, ext = 'csv') {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${prefix}-${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}`
    + `${p(d.getHours())}${p(d.getMinutes())}${p(d.getSeconds())}.${ext}`
}
