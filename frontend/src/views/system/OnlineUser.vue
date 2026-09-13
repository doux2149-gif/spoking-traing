<template>
  <div class="online-user">
    <el-card shadow="never">
      <div class="filter-bar">
        <el-input v-model="username" placeholder="用户名搜索" clearable style="width: 200px" @keyup.enter="loadList" />
        <el-button type="primary" @click="loadList">查询</el-button>
        <el-button @click="refresh">刷新</el-button>
        <div class="spacer" />
        <span class="auto-tip">每 30 秒自动刷新</span>
      </div>

      <el-table :data="onlineList" v-loading="loading" border stripe>
        <el-table-column prop="username" label="用户名" width="150" />
        <el-table-column prop="loginIp" label="登录IP" width="150" />
        <el-table-column label="浏览器标识" min-width="240">
          <template #default="{ row }">
            <el-tooltip v-if="row.userAgent" :content="row.userAgent" placement="top" :show-after="200">
              <span class="ua-cell">{{ row.userAgent }}</span>
            </el-tooltip>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="登录时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.loginTime) }}</template>
        </el-table-column>
        <el-table-column label="最后活跃" width="170">
          <template #default="{ row }">
            <span :class="{ active: isRecent(row.lastActiveTime) }">{{ fmtTime(row.lastActiveTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-tooltip v-if="row.tokenId === currentTokenId" content="当前会话不能强制下线" placement="top">
              <el-button type="danger" size="small" disabled>强制下线</el-button>
            </el-tooltip>
            <el-button v-else type="danger" size="small" @click="handleKick(row)">强制下线</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { onlineApi, type OnlineUser } from '../../api/admin'
import { useUserStore } from '../../store/user'

const loading = ref(false)
const onlineList = ref<OnlineUser[]>([])
const username = ref('')
const userStore = useUserStore()

const fmtTime = (s?: string | null) => (s ? s.replace('T', ' ').slice(0, 19) : '')

/** 从本地 JWT 解析当前会话 jti, 用于禁用"踢自己"按钮 */
function decodeJti(): string {
  try {
    const token = userStore.state.token
    const payload = token.split('.')[1]
    return JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/'))).jti || ''
  } catch {
    return ''
  }
}
const currentTokenId = ref(decodeJti())

/** 2 分钟内活跃视为"活跃中" */
function isRecent(t: string) {
  return Date.now() - new Date(t).getTime() < 120000
}

async function loadList() {
  loading.value = true
  try {
    const res = await onlineApi.list(username.value || undefined)
    onlineList.value = res.data || []
    currentTokenId.value = decodeJti()
  } finally {
    loading.value = false
  }
}

async function refresh() {
  await loadList()
}

function handleKick(row: OnlineUser) {
  ElMessageBox.confirm(
    `确定强制用户「${row.username}」下线吗？该会话的下一次请求将被要求重新登录。`,
    '强制下线确认',
    { confirmButtonText: '强制下线', cancelButtonText: '取消', type: 'warning' }
  ).then(async () => {
    await onlineApi.forceOffline(row.tokenId)
    ElMessage.success('已强制下线')
    loadList()
  }).catch(() => {})
}

let timer: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  loadList()
  timer = setInterval(() => {
    // 页面不可见时不刷新
    if (!document.hidden) loadList()
  }, 30000)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.spacer {
  flex: 1;
}

.auto-tip {
  font-size: 12px;
  color: #909399;
}

.ua-cell {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #606266;
}

.text-muted {
  color: #909399;
}

.active {
  color: #67c23a;
  font-weight: 500;
}
</style>
