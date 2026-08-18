import request from './request'

/** 获取当前用户信息 */
export function getUserProfile() {
  return request.get('/auth/user-info')
}

/** 更新个人信息(用户名/昵称/头像/邮箱/手机号) */
export function updateProfile(data: {
  username?: string
  nickname?: string
  avatar?: string
  email?: string
  phone?: string
}) {
  const userId = JSON.parse(localStorage.getItem('userInfo') || '{}').userId
  return request.put(`/system/users/${userId}`, data)
}

/** 修改密码 */
export function changePassword(data: {
  oldPassword: string
  newPassword: string
}) {
  const userId = JSON.parse(localStorage.getItem('userInfo') || '{}').userId
  return request.put(`/system/users/${userId}/password`, data)
}

/** 上传头像 */
export function uploadAvatar(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/upload/avatar', formData)
}
