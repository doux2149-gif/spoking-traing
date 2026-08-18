<template>
  <div class="profile-page">
    <div class="profile-header">
      <div>
        <h2>Profile</h2>
        <p>管理你的账户信息和安全设置</p>
      </div>
      <el-button class="edit-profile-button" type="primary" :loading="profileSaving" @click="saveProfile">
        Edit Profile
      </el-button>
    </div>

    <div class="profile-sections">
      <!-- 头像修改 -->
      <div class="profile-card">
        <div class="card-title">
          <el-icon><Avatar /></el-icon>
          <span>头像</span>
        </div>
        <div class="avatar-area">
          <el-avatar :size="100" :src="avatarUrl || undefined" class="current-avatar">
            {{ userInfo?.nickname?.charAt(0) || 'U' }}
          </el-avatar>
          <div class="avatar-actions">
            <el-upload
              :show-file-list="false"
              :before-upload="beforeAvatarUpload"
              :http-request="handleAvatarUpload"
              accept="image/*"
            >
              <el-button type="primary" :loading="avatarUploading">
                <el-icon><Upload /></el-icon>
                <span>更换头像</span>
              </el-button>
            </el-upload>
            <p class="avatar-tip">支持 JPG、PNG 格式，文件不超过 2MB</p>
          </div>
        </div>
      </div>

      <!-- 账号与昵称修改 -->
      <div class="profile-card">
        <div class="card-title">
          <el-icon><User /></el-icon>
          <span>账号信息</span>
        </div>
        <el-form
          ref="profileFormRef"
          :model="profileForm"
          :rules="profileRules"
          label-width="100px"
          class="profile-form"
        >
          <el-form-item label="用户名" prop="username">
            <el-input v-model="profileForm.username" placeholder="请输入用户名" />
          </el-form-item>
          <el-form-item label="昵称" prop="nickname">
            <el-input v-model="profileForm.nickname" placeholder="请输入昵称" />
          </el-form-item>
          <el-form-item label="邮箱" prop="email">
            <el-input v-model="profileForm.email" placeholder="请输入邮箱" />
          </el-form-item>
          <el-form-item label="手机号" prop="phone">
            <el-input v-model="profileForm.phone" placeholder="请输入手机号" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="profileSaving" @click="saveProfile">
              保存修改
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 修改密码 -->
      <div class="profile-card">
        <div class="card-title">
          <el-icon><Lock /></el-icon>
          <span>修改密码</span>
        </div>
        <el-form
          ref="passwordFormRef"
          :model="passwordForm"
          :rules="passwordRules"
          label-width="100px"
          class="profile-form"
        >
          <el-form-item label="原密码" prop="oldPassword">
            <el-input
              v-model="passwordForm.oldPassword"
              type="password"
              show-password
              placeholder="请输入原密码"
            />
          </el-form-item>
          <el-form-item label="新密码" prop="newPassword">
            <el-input
              v-model="passwordForm.newPassword"
              type="password"
              show-password
              placeholder="请输入新密码（至少 6 位）"
            />
          </el-form-item>
          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input
              v-model="passwordForm.confirmPassword"
              type="password"
              show-password
              placeholder="请再次输入新密码"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="passwordSaving" @click="savePassword">
              修改密码
            </el-button>
          </el-form-item>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, type FormInstance, type FormRules, type UploadRequestOptions } from 'element-plus'
import { Avatar, User, Lock, Upload } from '@element-plus/icons-vue'
import { useUserStore } from '../store/user'
import { getUserProfile, updateProfile, changePassword, uploadAvatar } from '../api/profile'

const userStore = useUserStore()
const userInfo = userStore.state.userInfo

const profileFormRef = ref<FormInstance>()
const passwordFormRef = ref<FormInstance>()

const avatarUrl = ref(userInfo?.avatar || '')
const avatarUploading = ref(false)
const profileSaving = ref(false)
const passwordSaving = ref(false)

const profileForm = reactive({
  username: '',
  nickname: '',
  email: '',
  phone: ''
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const profileRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度在 3-50 个字符', trigger: 'blur' }
  ],
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' }
  ],
  email: [
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ]
}

const passwordRules: FormRules = {
  oldPassword: [
    { required: true, message: '请输入原密码', trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 100, message: '密码长度在 6-100 个字符', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== passwordForm.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

/** 加载用户信息 */
async function loadProfile(): Promise<void> {
  try {
    const res = await getUserProfile()
    const data = (res as any)?.data ?? res
    if (data) {
      profileForm.username = data.username || ''
      profileForm.nickname = data.nickname || ''
      profileForm.email = data.email || ''
      profileForm.phone = data.phone || ''
      avatarUrl.value = data.avatar || ''
    }
  } catch (_e) {
    // 静默
  }
}

/** 头像上传前校验 */
function beforeAvatarUpload(file: File): boolean {
  if (!file.type.startsWith('image/')) {
    ElMessage.error('只支持图片文件')
    return false
  }
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.error('图片大小不能超过 2MB')
    return false
  }
  return true
}

/** 自定义头像上传 */
async function handleAvatarUpload(options: UploadRequestOptions): Promise<void> {
  avatarUploading.value = true
  try {
    const res = await uploadAvatar(options.file as File)
    const data = (res as any)?.data ?? res
    const url = data?.url
    if (url) {
      avatarUrl.value = url
      // 保存头像到用户信息
      await updateProfile({
        username: profileForm.username,
        nickname: profileForm.nickname,
        avatar: url,
        email: profileForm.email,
        phone: profileForm.phone
      })
      // 更新本地 store
      if (userStore.state.userInfo) {
        userStore.setUserInfo({
          ...userStore.state.userInfo,
          avatar: url
        })
      }
      ElMessage.success('头像更新成功')
    }
  } catch (_e) {
    ElMessage.error('头像上传失败')
  } finally {
    avatarUploading.value = false
  }
}

/** 保存账号信息 */
async function saveProfile(): Promise<void> {
  if (!profileFormRef.value) return
  await profileFormRef.value.validate(async (valid) => {
    if (!valid) return
    profileSaving.value = true
    try {
      await updateProfile({
        username: profileForm.username,
        nickname: profileForm.nickname,
        avatar: avatarUrl.value,
        email: profileForm.email,
        phone: profileForm.phone
      })
      // 更新本地 store
      if (userStore.state.userInfo) {
        userStore.setUserInfo({
          ...userStore.state.userInfo,
          username: profileForm.username,
          nickname: profileForm.nickname,
          avatar: avatarUrl.value,
          email: profileForm.email,
          phone: profileForm.phone
        })
      }
      ElMessage.success('保存成功')
    } catch (_e) {
      // 错误已由拦截器处理
    } finally {
      profileSaving.value = false
    }
  })
}

/** 修改密码 */
async function savePassword(): Promise<void> {
  if (!passwordFormRef.value) return
  await passwordFormRef.value.validate(async (valid) => {
    if (!valid) return
    passwordSaving.value = true
    try {
      await changePassword({
        oldPassword: passwordForm.oldPassword,
        newPassword: passwordForm.newPassword
      })
      ElMessage.success('密码修改成功')
      passwordForm.oldPassword = ''
      passwordForm.newPassword = ''
      passwordForm.confirmPassword = ''
    } catch (_e) {
      // 错误已由拦截器处理
    } finally {
      passwordSaving.value = false
    }
  })
}

onMounted(() => {
  void loadProfile()
})
</script>

<style scoped>
.profile-page {
  max-width: 700px;
  min-height: 100%;
  margin: 0 auto;
  padding: 24px;
  background: #fff;
}

.profile-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
}

.profile-header h2 {
  margin: 0 0 4px;
  font-size: 24px;
  color: #1f2937;
}

.profile-header p {
  margin: 0;
  font-size: 14px;
  color: #6b7280;
}

.edit-profile-button {
  margin-top: 8px;
  align-self: flex-start;
}

.profile-sections {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.profile-card {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  border: 1px solid #f1f5f9;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 17px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 20px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f1f5f9;
}

.card-title .el-icon {
  color: #3b82f6;
}

/* 头像区域 */
.avatar-area {
  display: flex;
  align-items: center;
  gap: 24px;
}

.current-avatar {
  background: #3b82f6;
  color: #fff;
  font-size: 32px;
  font-weight: 600;
  flex-shrink: 0;
}

.avatar-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.avatar-tip {
  font-size: 12px;
  color: #9ca3af;
  margin: 0;
}

/* 表单 */
.profile-form {
  max-width: 500px;
}

@media (max-width: 640px) {
  .profile-header {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
