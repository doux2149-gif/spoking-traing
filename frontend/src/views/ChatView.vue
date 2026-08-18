<template>
  <div class="chat-layout">
    <!-- 左侧会话列表 -->
    <aside class="chat-sidebar" :class="{ collapsed: sidebarCollapsed }">
      <!-- 打卡卡片(折叠时隐藏) -->
      <CheckinCard
        v-show="!sidebarCollapsed"
        ref="checkinCardRef"
        :refresh-key="checkinRefreshKey"
        @checked-in="handleMilestoneCheckin"
      />

      <div class="sidebar-header">
        <button class="new-chat-btn" @click="startNewChat">
          <el-icon><Plus /></el-icon>
          <span>新建对话</span>
        </button>
        <button class="collapse-btn" @click="sidebarCollapsed = !sidebarCollapsed">
          <el-icon><Fold v-if="!sidebarCollapsed" /><Expand v-else /></el-icon>
        </button>
      </div>

      <div class="sidebar-list" v-loading="listLoading">
        <div
          v-for="conv in conversations"
          :key="conv.id"
          class="conv-item"
          :class="{ active: conv.id === selectedConversationId }"
          @click="selectConversation(conv)"
        >
          <div class="conv-item-icon">
            <el-icon v-if="conv.status === 1"><ChatDotRound /></el-icon>
            <el-icon v-else><ChatLineRound /></el-icon>
          </div>
          <div class="conv-item-body" v-show="!sidebarCollapsed">
            <div class="conv-item-title">{{ conv.title }}</div>
            <div class="conv-item-meta">
              <span>{{ conv.roundCount }} 轮</span>
              <span>{{ formatTime(conv.updateTime) }}</span>
            </div>
          </div>
          <div class="conv-item-actions" v-show="!sidebarCollapsed" @click.stop>
            <el-dropdown trigger="click" @command="(cmd: string) => handleCommand(cmd, conv)">
              <el-icon class="more-icon"><MoreFilled /></el-icon>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="end" :disabled="conv.status !== 1">
                    结束对话
                  </el-dropdown-item>
                  <el-dropdown-item command="delete" divided>
                    删除对话
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>

        <div v-if="!listLoading && conversations.length === 0" class="sidebar-empty">
          <el-icon :size="40"><ChatLineSquare /></el-icon>
          <p>暂无对话记录</p>
        </div>
      </div>
    </aside>

    <!-- 右侧对话区 -->
    <main class="chat-main">
      <VoiceChat
        :key="chatKey"
        :load-conversation-id="selectedConversationId || undefined"
        @conversation-created="handleConversationCreated"
        @conversation-changed="handleConversationChanged"
      />
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Fold, Expand, ChatDotRound, ChatLineRound, MoreFilled, ChatLineSquare } from '@element-plus/icons-vue'
import VoiceChat from '../components/chat/VoiceChat.vue'
import CheckinCard from '../components/chat/CheckinCard.vue'
import {
  getConversations,
  endConversation,
  deleteConversation,
  type Conversation
} from '../api/conversation'

const conversations = ref<Conversation[]>([])
const listLoading = ref(false)
const selectedConversationId = ref<number | null>(null)
const sidebarCollapsed = ref(false)
/** 用于强制重新渲染 VoiceChat 组件 */
const chatKey = ref(0)
/** 触发 CheckinCard 重新拉取数据的计数器 */
const checkinRefreshKey = ref(0)
/** 打卡卡片引用,用于手动刷新 */
const checkinCardRef = ref<any>(null)

/** 连续打卡里程碑对应的激励文案 */
const MILESTONE_MESSAGES: Record<number, string> = {
  1: '打卡第 1 天，开启雅思口语提升之旅！💪',
  7: '连续打卡一周！语感正在形成，继续保持 🔥',
  14: '半个月坚持！已经看到进步了 🎉',
  30: '🎉 月打卡达成！雅思口语提升 0.5 分指日可待',
  100: '💎 百日达成！你的坚持令人敬佩',
  180: '🏅 半年坚守！你已超越 90% 的学习者',
  365: '👑 一年打卡完成！雅思 7 分近在咫尺！'
}

function formatTime(timeStr: string): string {
  if (!timeStr) return ''
  return timeStr.replace('T', ' ').substring(5, 16)
}

/** 加载会话列表 */
async function loadConversations(): Promise<void> {
  listLoading.value = true
  try {
    const res = await getConversations(1, 50)
    if (res.data) {
      conversations.value = res.data.records || res.data || []
    }
  } catch (_e) {
    // 静默忽略
  } finally {
    listLoading.value = false
  }
}

/** 新建对话 */
function startNewChat(): void {
  selectedConversationId.value = null
  chatKey.value++
}

/** 选择会话 */
function selectConversation(conv: Conversation): void {
  if (selectedConversationId.value === conv.id) return
  selectedConversationId.value = conv.id
  chatKey.value++
}

/** 处理会话创建事件 */
function handleConversationCreated(id: number): void {
  selectedConversationId.value = id
  void loadConversations()
}

/** 会话变动事件 → 刷新会话列表 + 打卡卡(轮次/时长改变了) */
function handleConversationChanged(): void {
  void loadConversations()
  // 触发打卡卡片刷新
  checkinRefreshKey.value++
  // 3 秒后额外再刷一次(后台异步事件处理有延迟)
  setTimeout(() => {
    if (checkinCardRef.value && typeof checkinCardRef.value.refresh === 'function') {
      void checkinCardRef.value.refresh()
    }
  }, 3000)
}

/** 打卡里程碑弹窗 */
function handleMilestoneCheckin(milestone: number | null): void {
  if (milestone == null) {
    ElMessage({
      type: 'success',
      message: '今日打卡成功！🎯',
      duration: 2500
    })
    return
  }
  const msg = MILESTONE_MESSAGES[milestone] || `连续打卡 ${milestone} 天，继续加油！`
  ElMessage({
    type: 'success',
    message: msg,
    duration: 4000,
    showClose: true
  })
}

/** 处理下拉菜单命令 */
async function handleCommand(cmd: string, conv: Conversation): Promise<void> {
  if (cmd === 'end') {
    try {
      await ElMessageBox.confirm('确定要结束这个对话吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      })
      await endConversation(conv.id)
      ElMessage.success('对话已结束')
      if (selectedConversationId.value === conv.id) {
        selectedConversationId.value = null
        chatKey.value++
      }
      handleConversationChanged()
    } catch (_e) {
      // 用户取消
    }
  } else if (cmd === 'delete') {
    try {
      await ElMessageBox.confirm('删除后不可恢复，确定要删除这个对话吗？', '警告', {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'error'
      })
      await deleteConversation(conv.id)
      ElMessage.success('已删除')
      if (selectedConversationId.value === conv.id) {
        selectedConversationId.value = null
        chatKey.value++
      }
      handleConversationChanged()
    } catch (_e) {
      // 用户取消
    }
  }
}

onMounted(() => {
  void loadConversations()
})
</script>

<style scoped>
.chat-layout {
  display: flex;
  height: 100%;
  overflow: hidden;
}

/* 左侧侧边栏 */
.chat-sidebar {
  width: 280px;
  background: #f7f7f8;
  border-right: 1px solid #e5e5e5;
  display: flex;
  flex-direction: column;
  transition: width 0.2s ease;
  flex-shrink: 0;
}

.chat-sidebar.collapsed {
  width: 60px;
}

.sidebar-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  border-bottom: 1px solid #e5e5e5;
}

.new-chat-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px 16px;
  border: 1px solid #d9d9e3;
  border-radius: 8px;
  background: #fff;
  color: #303133;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.15s;
}

.new-chat-btn:hover {
  background: #ececf1;
}

.collapse-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: #606266;
  cursor: pointer;
  transition: background 0.15s;
}

.collapse-btn:hover {
  background: #e5e5e5;
}

.sidebar-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.conv-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;
  margin-bottom: 2px;
}

.conv-item:hover {
  background: #ececf1;
}

.conv-item.active {
  background: #e3e3e8;
}

.conv-item-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #606266;
  flex-shrink: 0;
}

.conv-item-body {
  flex: 1;
  min-width: 0;
}

.conv-item-title {
  font-size: 14px;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: 2px;
}

.conv-item-meta {
  display: flex;
  gap: 8px;
  font-size: 12px;
  color: #909399;
}

.conv-item-actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  opacity: 0;
  transition: opacity 0.15s;
}

.conv-item:hover .conv-item-actions {
  opacity: 1;
}

.more-icon {
  cursor: pointer;
  color: #909399;
  font-size: 16px;
  padding: 4px;
  border-radius: 4px;
}

.more-icon:hover {
  color: #303133;
  background: #d9d9e3;
}

.sidebar-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 0;
  color: #c0c4cc;
  gap: 12px;
}

.sidebar-empty p {
  font-size: 14px;
  margin: 0;
}

/* 右侧对话区 */
.chat-main {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
</style>
