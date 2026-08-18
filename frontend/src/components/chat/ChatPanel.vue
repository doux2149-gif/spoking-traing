<script setup lang="ts">
import { ref, shallowRef, nextTick } from 'vue'

interface Message {
  role: 'user' | 'assistant' | 'system'
  content: string
}

const messages = ref<Message[]>([])
const inputText = shallowRef('')
const isStreaming = shallowRef(false)
const errorMessage = shallowRef('')
const chatContainer = ref<HTMLElement | null>(null)

function scrollToBottom(): void {
  nextTick(() => {
    if (chatContainer.value) {
      chatContainer.value.scrollTop = chatContainer.value.scrollHeight
    }
  })
}

async function sendMessage(): Promise<void> {
  const text = inputText.value.trim()
  if (!text || isStreaming.value) return

  errorMessage.value = ''
  inputText.value = ''
  messages.value.push({ role: 'user', content: text })
  scrollToBottom()

  const assistantMsg: Message = { role: 'assistant', content: '' }
  messages.value.push(assistantMsg)
  isStreaming.value = true

  try {
    const payload = {
      messages: messages.value.slice(0, -1).map(m => ({ role: m.role, content: m.content })),
    }

    const response = await fetch('/api/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Accept': 'text/event-stream' },
      body: JSON.stringify(payload),
    })

    if (!response.ok) {
      const err = await response.text()
      throw new Error(err || `请求失败（${response.status}）`)
    }

    const reader = response.body?.getReader()
    if (!reader) throw new Error('无法读取响应流')

    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (line.startsWith('event:done')) {
          break
        }
        if (line.startsWith('event:error')) {
          continue
        }
        if (line.startsWith('data:')) {
          const content = line.slice(5)
          assistantMsg.content += content
          scrollToBottom()
        }
      }
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '对话请求失败'
    if (assistantMsg.content === '') {
      messages.value.pop()
    }
  } finally {
    isStreaming.value = false
  }
}

function clearChat(): void {
  messages.value = []
  errorMessage.value = ''
}

function handleKeydown(event: KeyboardEvent): void {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    sendMessage()
  }
}
</script>

<template>
  <div class="chat-shell">
    <div ref="chatContainer" class="chat-messages">
      <div v-if="messages.length === 0" class="chat-empty">
        <p>开始和 AI 对话吧</p>
      </div>
      <div
        v-for="(msg, idx) in messages"
        :key="idx"
        class="chat-bubble"
        :class="msg.role === 'user' ? 'chat-bubble--user' : 'chat-bubble--assistant'"
      >
        <span class="chat-role">{{ msg.role === 'user' ? '你' : 'AI' }}</span>
        <div class="chat-content" v-text="msg.content || '...'" />
      </div>
    </div>

    <p v-if="errorMessage" class="field-error" role="alert">
      {{ errorMessage }}
    </p>

    <div class="chat-input-bar">
      <textarea
        v-model="inputText"
        class="chat-input"
        placeholder="输入消息，Enter 发送，Shift+Enter 换行"
        rows="2"
        :disabled="isStreaming"
        @keydown="handleKeydown"
      />
      <div class="chat-actions">
        <button
          class="primary-button"
          type="button"
          :disabled="isStreaming || !inputText.trim()"
          @click="sendMessage"
        >
          {{ isStreaming ? '回复中...' : '发送' }}
        </button>
        <button
          class="secondary-button"
          type="button"
          :disabled="isStreaming"
          @click="clearChat"
        >
          清空
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat-shell {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.chat-messages {
  max-height: 420px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--color-border, #e2e2e2);
  border-radius: 8px;
  background: var(--color-surface, #fafafa);
}

.chat-empty {
  text-align: center;
  color: var(--color-muted, #999);
  padding: 48px 0;
}

.chat-bubble {
  max-width: 80%;
  padding: 8px 14px;
  border-radius: 12px;
  line-height: 1.5;
  word-break: break-word;
  white-space: pre-wrap;
}

.chat-bubble--user {
  align-self: flex-end;
  background: var(--color-primary, #4f46e5);
  color: white;
}

.chat-bubble--assistant {
  align-self: flex-start;
  background: var(--color-surface-alt, #f0f0f0);
  color: var(--color-text, #1a1a1a);
}

.chat-role {
  display: block;
  font-size: 11px;
  font-weight: 600;
  opacity: 0.7;
  margin-bottom: 2px;
}

.chat-content {
  font-size: 14px;
}

.chat-input-bar {
  display: flex;
  gap: 8px;
  align-items: flex-end;
}

.chat-input {
  flex: 1;
  resize: none;
  padding: 10px 12px;
  border: 1px solid var(--color-border, #e2e2e2);
  border-radius: 8px;
  font-size: 14px;
  font-family: inherit;
  line-height: 1.4;
}

.chat-input:focus {
  outline: 2px solid var(--color-primary, #4f46e5);
  outline-offset: -1px;
}

.chat-actions {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
</style>
