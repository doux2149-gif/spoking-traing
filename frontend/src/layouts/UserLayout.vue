<template>
  <div class="user-layout">
    <header class="user-header">
      <div class="header-inner">
        <div class="logo-area">
          <router-link to="/" class="brand">
            <span class="brand-icon">🎙️</span>
            <span class="brand-text">AI 英语口语对话</span>
          </router-link>
        </div>
        <nav class="nav-links">
          <router-link to="/chat" class="nav-link">语音对话</router-link>
          <router-link to="/scenes" class="nav-link">场景练习</router-link>
          <router-link to="/scenes/records" class="nav-link">练习记录</router-link>
          <router-link to="/checkin" class="nav-link">我的打卡</router-link>
        </nav>
        <div class="user-area">
          <el-dropdown trigger="click" @command="handleCommand">
            <div class="user-info">
              <el-avatar :size="32" :src="userInfo?.avatar">
                {{ userInfo?.nickname?.charAt(0) || 'U' }}
              </el-avatar>
              <span class="username">{{ userInfo?.nickname || userInfo?.username }}</span>
              <el-icon class="arrow-icon"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </header>
    <main
      class="user-main"
      :class="{ 'user-main--full': isFullWidth, 'user-main--white': isProfilePage }"
    >
      <router-view />
    </main>
    <footer class="user-footer">
      <p>© 2024 AI 英语口语对话系统 · 雅思口语练习 · 对话内容会保存至历史记录</p>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { useUserStore } from '../store/user'
import { ArrowDown } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const userInfo = userStore.state.userInfo

/** 对话页面需要全屏布局,不限宽度和内边距 */
const isFullWidth = computed(() => route.path === '/chat')
const isProfilePage = computed(() => route.path === '/profile')

const handleCommand = (command: string) => {
  if (command === 'logout') {
    ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }).then(() => {
      userStore.logout()
      ElMessage.success('已退出登录')
      router.push('/login')
    }).catch(() => {})
  } else if (command === 'profile') {
    router.push('/profile')
  }
}
</script>

<style scoped>
.user-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4e8ec 100%);
}

.user-header {
  background: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  position: sticky;
  top: 0;
  z-index: 100;
}

.header-inner {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 24px;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  text-decoration: none;
  color: #303133;
}

.brand-icon {
  font-size: 24px;
}

.brand-text {
  font-size: 18px;
  font-weight: 600;
  background: linear-gradient(135deg, #667eea, #764ba2);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.nav-links {
  display: flex;
  gap: 8px;
}

.nav-link {
  padding: 8px 16px;
  border-radius: 6px;
  text-decoration: none;
  color: #606266;
  font-size: 14px;
  transition: all 0.2s;
}

.nav-link:hover {
  color: #409EFF;
  background-color: #ecf5ff;
}

.nav-link.router-link-active {
  color: #409EFF;
  background-color: #ecf5ff;
}

.user-area {
  display: flex;
  align-items: center;
}

.user-info {
  display: flex;
  align-items: center;
  cursor: pointer;
  padding: 5px 12px;
  border-radius: 20px;
  transition: background 0.2s;
}

.user-info:hover {
  background-color: #f5f7fa;
}

.username {
  margin: 0 6px;
  color: #606266;
  font-size: 14px;
}

.arrow-icon {
  color: #909399;
  font-size: 12px;
}

.user-main {
  flex: 1;
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px;
  width: 100%;
  box-sizing: border-box;
}

.user-main--full {
  max-width: 100%;
  padding: 0;
  display: flex;
  flex-direction: column;
}

.user-main--white {
  background: #fff;
}

.user-footer {
  text-align: center;
  padding: 20px;
  color: #909399;
  font-size: 13px;
}
</style>
