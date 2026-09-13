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

    <!-- 生效中的横幅公告(多条纵向排列, 当次会话关闭后不再出现) -->
    <div v-if="bannerNotices.length" class="notice-strip-wrap" :class="{ 'notice-strip-wrap--full': isFullWidth }">
      <div
        v-for="n in bannerNotices"
        :key="n.id"
        class="notice-strip"
        :class="n.noticeType === 2 ? 'notice-strip--warning' : 'notice-strip--info'"
      >
        <span class="notice-icon">{{ n.noticeType === 2 ? '⚠️' : '📢' }}</span>
        <div class="notice-body">
          <span class="notice-title">{{ n.title }}</span>
          <span class="notice-content">{{ n.content }}</span>
        </div>
        <el-icon class="notice-close" @click="dismissBanner(n)"><Close /></el-icon>
      </div>
    </div>

    <!-- 弹窗公告(未读才弹, 读过记录在 localStorage, 内容更新后重弹) -->
    <el-dialog
      v-model="dialogVisible"
      :title="currentDialog?.title"
      width="480px"
      :close-on-click-modal="false"
      @close="onDialogClose"
    >
      <div class="notice-dialog-content">{{ currentDialog?.content }}</div>
      <template #footer>
        <el-button type="primary" @click="dialogVisible = false">我知道了</el-button>
      </template>
    </el-dialog>

    <main
      class="user-main"
      :class="{ 'user-main--full': isFullWidth, 'user-main--white': isProfilePage }"
    >
      <router-view />
    </main>
    <footer v-if="!isFullWidth" class="user-footer">
      <p>© 2024 AI 英语口语对话系统 · 雅思口语练习 · 对话内容会保存至历史记录</p>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { useUserStore } from '../store/user'
import { ArrowDown, Close } from '@element-plus/icons-vue'
import { noticeApi, type Notice } from '../api/admin'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const userInfo = userStore.state.userInfo

// ---------------- 公告: 横幅 + 弹窗 ----------------
const allNotices = ref<Notice[]>([])
/** 当次会话内手动关闭的横幅, 不再出现 */
const dismissedBannerIds = new Set<number>()
const bannerNotices = computed(() =>
  allNotices.value.filter(n => n.displayType === 1 && !dismissedBannerIds.has(n.id!))
)

function dismissBanner(n: Notice) {
  dismissedBannerIds.add(n.id!)
  allNotices.value = [...allNotices.value]
}

/** 未读弹窗公告队列, 依次弹出 */
const dialogQueue = ref<Notice[]>([])
const currentDialog = ref<Notice | null>(null)
const dialogVisible = ref(false)

const readKey = (n: Notice) => `notice_read_${n.id}_${n.updateTime ?? ''}`

function showNextDialog() {
  const next = dialogQueue.value.shift()
  if (!next) return
  currentDialog.value = next
  dialogVisible.value = true
}

function onDialogClose() {
  if (currentDialog.value) {
    localStorage.setItem(readKey(currentDialog.value), new Date().toISOString())
    currentDialog.value = null
  }
  window.setTimeout(showNextDialog, 150)
}

async function loadActiveNotices() {
  try {
    const res = await noticeApi.active()
    const list: Notice[] = res.data || []
    allNotices.value = list
    dialogQueue.value = list.filter(
      n => n.displayType === 2 && !localStorage.getItem(readKey(n))
    )
    showNextDialog()
  } catch (e) {
    console.error('加载公告失败', e)
  }
}

onMounted(loadActiveNotices)

/** 对话页面需要全屏布局,不限宽度和内边距 */
const isFullWidth = computed(() => route.path === '/chat')
const isProfilePage = computed(() => route.path === '/profile')

const handleCommand = (command: string) => {
  if (command === 'logout') {
    ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }).then(async () => {
      await userStore.logout()
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
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4e8ec 100%);
  overflow: hidden;
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
  overflow: hidden;
}

.user-main--full {
  max-width: 100%;
  padding: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
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

/* ---------------- 公告横幅 ---------------- */
.notice-strip-wrap {
  flex-shrink: 0;
  background: #fff;
}

.notice-strip {
  max-width: 1200px;
  margin: 0 auto;
  padding: 10px 24px;
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  line-height: 1.5;
}

.notice-strip--info {
  background: #ecf5ff;
  border-bottom: 1px solid #d9ecff;
  color: #337ecc;
}

.notice-strip--warning {
  background: #fdf6ec;
  border-bottom: 1px solid #faecd8;
  color: #b88230;
}

/* 全屏对话页时横幅顶到屏幕两侧, 与 /chat 零内边距对齐 */
.notice-strip-wrap--full .notice-strip {
  max-width: none;
}

.notice-icon {
  font-size: 16px;
  flex-shrink: 0;
}

.notice-body {
  flex: 1;
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
}

.notice-title {
  font-weight: 600;
  flex-shrink: 0;
}

.notice-content {
  white-space: pre-line;
  word-break: break-word;
}

.notice-close {
  cursor: pointer;
  flex-shrink: 0;
  opacity: 0.7;
}

.notice-close:hover {
  opacity: 1;
}

.notice-dialog-content {
  font-size: 14px;
  line-height: 1.8;
  color: #303133;
  white-space: pre-line;
  max-height: 50vh;
  overflow-y: auto;
}
</style>
