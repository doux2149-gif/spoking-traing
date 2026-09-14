<script setup lang="ts">
import { ref, shallowRef, nextTick, computed, onMounted } from 'vue'
import GrammarCard from './GrammarCard.vue'
import ReportDialog from './ReportDialog.vue'
import type { GrammarCorrection } from '../../types/grammar'
import { useUserStore } from '../../store/user'
import {
  createConversation,
  addConversationMessage,
  endConversation,
  getActiveConversation,
  getConversationMessages as getConversationMessagesApi,
  getConversation as getConversationApi
} from '../../api/conversation'
import request from '../../api/request'
import { ChatDotRound, InfoFilled } from '@element-plus/icons-vue'

/** 获取认证请求头 */
function authHeaders(extra?: Record<string, string>): Record<string, string> {
  const userStore = useUserStore()
  const headers: Record<string, string> = { ...extra }
  if (userStore.token) {
    headers['Authorization'] = `Bearer ${userStore.token}`
  }
  return headers
}

/** 场景模式 props(ScenePractice 外部传入) */
const props = defineProps<{
  sceneMode?: boolean
  scenePrompt?: string
  openingLine?: string
  fixedVcn?: string
  sceneId?: number
  /** 从外部加载指定会话(ChatGPT 布局传入) */
  loadConversationId?: number
}>()

const emit = defineEmits<{
  finish: [data: { rounds: number; duration: number; errorCount: number; conversation: string }]
  /** 新会话创建时通知父组件刷新列表 */
  conversationCreated: [id: number]
  /** 会话结束/删除时通知父组件刷新列表 */
  conversationChanged: []
}>()

/** 报告弹窗 ref */
const reportDialogRef = ref<InstanceType<typeof ReportDialog> | null>(null)

/** 内部场景选择状态(用于 /chat 页面的场景选择面板) */
const availableScenes = ref<any[]>([])
const scenesLoading = ref(false)
const internalSceneId = ref<number | null>(null)
const internalSceneName = ref<string>('')
const internalScenePrompt = ref<string>('')
const internalOpeningLine = ref<string>('')
const internalSceneMode = ref(false)
const conversationStarted = ref(false)

/** 计算生效的场景配置:外部 props 优先,否则用内部选择 */
const effectiveSceneMode = computed(() => props.sceneMode || internalSceneMode.value)
const effectiveScenePrompt = computed(() => props.scenePrompt || internalScenePrompt.value || '')
const effectiveOpeningLine = computed(() => props.openingLine || internalOpeningLine.value || '')

const showScenePanel = computed(() =>
  !props.sceneMode && !conversationStarted.value && messages.value.length === 0
)

const difficultyLabel = (d: number) => ['', '入门', '初级', '中级', '高级'][d] || '入门'
const difficultyType = (d: number): any => ['', 'success', 'info', 'warning', 'danger'][d] || 'success'

function formatTime(timeStr: string): string {
  if (!timeStr) return ''
  return timeStr.replace('T', ' ').substring(0, 16)
}

interface ToolEvent {
  name: string
  arguments: string
  result: string
}

interface Message {
  role: 'user' | 'assistant'
  content: string
  /** 仅 user 消息可能携带语法纠错结果 */
  grammar?: GrammarCorrection
  /** 仅 assistant 消息:本轮工具调用记录(用于展示"正在查询 xxx"卡片) */
  toolEvents?: ToolEvent[]
  /** AI 回答的中文翻译(点翻译按钮后填充) */
  translation?: string
  /** 翻译请求进行中 */
  translating?: boolean
  /** 是否展开显示翻译 */
  showTranslation?: boolean
}

/** 工具友好名称(展示用) */
const TOOL_LABELS: Record<string, string> = {
  get_current_datetime: '时间日期',
  query_weather: '天气查询',
  query_github_repo: 'GitHub 仓库',
  query_dictionary: '词典查询',
}

function toolLabel(name: string): string {
  return TOOL_LABELS[name] || name
}

/** 参数摘要:把 JSON 参数转成 k=v, v2 形式 */
function toolSummary(args: string): string {
  try {
    const obj = JSON.parse(args) as Record<string, unknown>
    const parts = Object.entries(obj).map(([k, v]) => `${k}=${String(v)}`)
    const joined = parts.join(', ')
    return joined.length > 40 ? joined.slice(0, 40) + '…' : joined
  } catch {
    return args.length > 40 ? args.slice(0, 40) + '…' : args
  }
}

const messages = ref<Message[]>([])
const status = shallowRef<'idle' | 'recording' | 'recognizing' | 'thinking' | 'speaking'>('idle')
const errorMessage = shallowRef('')
const textInput = shallowRef('')
const chatContainer = ref<HTMLElement | null>(null)

/** 上下文管理元信息(是否截断/token估算/是否触发摘要) */
const ctxMeta = ref<{ truncated: boolean; estimatedTokens: number; needSummarize: boolean } | null>(null)

/** 会话管理状态 */
const currentConversationId = ref<number | null>(null)
const conversationStartTime = ref<number | null>(null)

let stream: MediaStream | null = null
let audioContext: AudioContext | null = null
let workletNode: AudioWorkletNode | null = null
let sourceNode: MediaStreamAudioSourceNode | null = null
let silentGain: GainNode | null = null
let chunks: ArrayBuffer[] = []
let elapsedTimer: number | null = null
const elapsedSeconds = shallowRef(0)

const VCN_OPTIONS = [
  { value: 'catherine', label: 'Catherine（英文女声）' },
  { value: 'henry', label: 'Henry（英文男声）' },
  { value: 'x4_lingxiaolu_oral', label: '玲小璐（中文口语）' },
  { value: 'x4_lingfeizhe_oral', label: '凌飞哲（中文口语）' },
  { value: 'xiaoyan', label: '小燕（中文）' },
  { value: 'x4_yezi', label: '叶子（中文温柔）' },
] as const

const vcn = shallowRef(props.fixedVcn || VCN_OPTIONS[0].value)
const speed = shallowRef(50)

/** 场景模式练习数据追踪 */
const practiceStartTime = shallowRef<number | null>(null)
const practiceRounds = shallowRef(0)
const practiceErrors = shallowRef(0)

const SYSTEM_PROMPTS: Record<string, string> = {
  x4_lingxiaolu_oral: '请用中文简短回答。',
  x4_lingfeizhe_oral: '请用中文简短回答。',
  xiaoyan: '请用中文简短回答。',
  x4_yezi: '请用中文简短回答。',
  catherine: 'Please reply briefly in English.',
  henry: 'Please reply briefly in English.',
}

const statusLabel = computed(() => {
  switch (status.value) {
    case 'recording': return '正在聆听...'
    case 'recognizing': return '正在识别语音...'
    case 'thinking': return 'AI 思考中...'
    case 'speaking': return 'AI 正在回答...'
    default: return '点击麦克风开始语音对话'
  }
})

function scrollToBottom(): void {
  nextTick(() => {
    if (chatContainer.value) {
      chatContainer.value.scrollTop = chatContainer.value.scrollHeight
    }
  })
}

/** 加载可用场景列表 */
async function loadScenes(): Promise<void> {
  scenesLoading.value = true
  try {
    const res = await request.get('/scenes')
    if (res.data) {
      availableScenes.value = res.data
    }
  } catch (_e) {
    // 静默忽略
  } finally {
    scenesLoading.value = false
  }
}

/** 选择场景并开始对话 */
function selectScene(scene: any): void {
  internalSceneId.value = scene.id
  internalSceneName.value = scene.name
  internalScenePrompt.value = scene.systemPrompt || ''
  internalOpeningLine.value = scene.openingLine || ''
  internalSceneMode.value = true
  conversationStarted.value = true

  // 如果有开场白,自动添加到消息并播放
  if (internalOpeningLine.value) {
    messages.value.push({ role: 'assistant', content: internalOpeningLine.value })
    scrollToBottom()
    fetchTtsBlob(internalOpeningLine.value, vcn.value)
      .then(blob => playBlob(blob))
      .catch(() => {})
  }
}

/** 选择自由对话模式 */
function selectFreeChat(): void {
  internalSceneMode.value = false
  internalSceneId.value = null
  internalSceneName.value = ''
  internalScenePrompt.value = ''
  internalOpeningLine.value = ''
  conversationStarted.value = true
}

async function startRecording(): Promise<void> {
  if (status.value !== 'idle') return
  errorMessage.value = ''
  chunks = []
  elapsedSeconds.value = 0

  try {
    stream = await navigator.mediaDevices.getUserMedia({
      audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true, autoGainControl: true },
    })
    audioContext = new AudioContext({ latencyHint: 'interactive' })
    await audioContext.resume()
    await audioContext.audioWorklet.addModule('/pcm-recorder-worklet.js')
    sourceNode = audioContext.createMediaStreamSource(stream)
    workletNode = new AudioWorkletNode(audioContext, 'pcm-recorder-processor')
    silentGain = audioContext.createGain()
    silentGain.gain.value = 0

    workletNode.port.onmessage = (event: MessageEvent<ArrayBuffer>) => {
      chunks.push(event.data)
    }

    sourceNode.connect(workletNode)
    workletNode.connect(silentGain)
    silentGain.connect(audioContext.destination)

    status.value = 'recording'
    elapsedTimer = window.setInterval(() => {
      elapsedSeconds.value += 1
      if (elapsedSeconds.value >= 30) stopAndProcess()
    }, 1000)
  } catch {
    cleanup()
    errorMessage.value = '无法访问麦克风'
  }
}

async function stopAndProcess(): Promise<void> {
  if (status.value !== 'recording') return

  stopTimer()
  stopAudio()

  const totalBytes = chunks.reduce((sum, buf) => sum + buf.byteLength, 0)
  if (totalBytes === 0) {
    status.value = 'idle'
    return
  }

  const merged = new Uint8Array(totalBytes)
  let offset = 0
  for (const chunk of chunks) {
    merged.set(new Uint8Array(chunk), offset)
    offset += chunk.byteLength
  }
  chunks = []

  status.value = 'recognizing'

  try {
    const userText = await recognizeSpeech(merged)
    if (!userText.trim()) {
      errorMessage.value = '未识别到有效语音内容'
      status.value = 'idle'
      return
    }

    await sendUserText(userText)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '处理失败'
    status.value = 'idle'
  }
}

async function sendTypedMessage(): Promise<void> {
  const text = textInput.value.trim()
  if (!text || status.value !== 'idle') return

  errorMessage.value = ''
  textInput.value = ''
  try {
    await sendUserText(text)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '处理失败'
  }
}

async function sendUserText(userText: string): Promise<void> {
  await ensureConversation()

  messages.value.push({ role: 'user', content: userText })
  scrollToBottom()

  if (effectiveSceneMode.value) {
    practiceRounds.value++
    if (!practiceStartTime.value) {
      practiceStartTime.value = Date.now()
    }
  }

  status.value = 'thinking'
  const assistantMsg: Message = { role: 'assistant', content: '' }
  messages.value.push(assistantMsg)

  try {
    await streamChatAndSpeak(messages.value.slice(0, -1), assistantMsg)
    await saveConversationMessages(assistantMsg)
  } catch (error) {
    if (!assistantMsg.content) {
      messages.value.pop()
    }
    throw error
  } finally {
    status.value = 'idle'
  }
}

async function saveConversationMessages(assistantMsg: Message): Promise<void> {
  const lastUserMsg = messages.value[messages.value.length - 2]
  if (lastUserMsg?.role === 'user') {
    const grammar = lastUserMsg.grammar
    await saveMessageToConversation(
      'user',
      lastUserMsg.content,
      grammar ? JSON.stringify(grammar) : null,
      grammar ? !grammar.noError : false,
      grammar ? !grammar.noError && Boolean(grammar.suggestion && grammar.suggestion.level !== 'none') : false
    )
  }
  if (assistantMsg.content) {
    await saveMessageToConversation('assistant', assistantMsg.content, null, false, false)
  }
}

function playAltText(text: string): void {
  fetchTtsBlob(text, 'catherine')
    .then(blob => playBlob(blob))
    .catch(() => {})
}

/** 翻译/收起 AI 回答:首次点击调翻译接口,之后切换显示 */
async function toggleTranslation(msg: Message): Promise<void> {
  if (msg.translating) return
  if (msg.translation) {
    msg.showTranslation = !msg.showTranslation
    return
  }
  msg.translating = true
  try {
    const res = await request.post('/translate', { text: msg.content })
    msg.translation = res?.data?.translation || '(翻译失败)'
    msg.showTranslation = true
  } catch {
    msg.translation = '(翻译失败, 请稍后重试)'
    msg.showTranslation = true
  } finally {
    msg.translating = false
  }
}

function handleTextInputKeydown(event: KeyboardEvent): void {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    void sendTypedMessage()
  }
}

async function recognizeSpeech(pcmData: Uint8Array): Promise<string> {
  const blob = new Blob([pcmData as BlobPart], { type: 'audio/pcm' })
  const file = new File([blob], 'voice.pcm', { type: 'audio/pcm' })

  const formData = new FormData()
  formData.append('file', file)
  formData.append('encoding', 'raw')
  formData.append('sampleRate', '16000')
  formData.append('language', '')

  const response = await fetch('/api/transcriptions', { method: 'POST', headers: authHeaders(), body: formData })
  if (!response.ok) {
    const err = await response.json().catch(() => ({ message: '语音识别失败' }))
    throw new Error(err.message || '语音识别失败')
  }
  const result = await response.json()
  return result.text || ''
}

/** 规范化 grammar JSON,确保 always 包含 suggestion 字段(兜底),供 LLM 模仿格式 */
function normalizeGrammarForHistory(g: GrammarCorrection): GrammarCorrection {
  return {
    ...g,
    suggestion: g.suggestion ?? {
      level: 'none',
      original: '',
      alternatives: [],
      tip: '',
      speechText: '',
    },
  }
}

async function streamChatAndSpeak(chatMessages: Message[], assistantMsg: Message): Promise<void> {
  const payload = {
    messages: chatMessages.map((m, idx) => {
      if (m.role === 'assistant' && idx > 0) {
        const prevMsg = chatMessages[idx - 1]
        if (prevMsg.role === 'user' && prevMsg.grammar) {
          const jsonStr = JSON.stringify(normalizeGrammarForHistory(prevMsg.grammar))
          return {
            role: 'assistant',
            content: m.content + '\n##GRAMMAR_JSON##\n' + jsonStr + '\n##END_JSON##',
          }
        }
      }
      return { role: m.role, content: m.content }
    }),
    vcn: vcn.value,
    // 场景模式:传 scenePrompt,后端会用场景 system prompt
    scenePrompt: effectiveSceneMode.value ? effectiveScenePrompt.value : undefined,
    conversationId: currentConversationId.value ?? undefined,
  }

  const response = await fetch('/api/chat', {
    method: 'POST',
    headers: authHeaders({ 'Content-Type': 'application/json', Accept: 'text/event-stream' }),
    body: JSON.stringify(payload),
  })

  if (!response.ok) {
    const err = await response.text()
    throw new Error(err || 'AI 对话失败')
  }

  const reader = response.body?.getReader()
  if (!reader) throw new Error('无法读取响应')

  const decoder = new TextDecoder()
  let sseBuffer = ''
  let sentenceBuffer = ''
  const SENTENCE_DELIMITERS = /([。！？.!?\n])/

  let playChain: Promise<void> = Promise.resolve()

  /** 把一句话加入 TTS 播放队列,串行播放。vcnOverride 用于纠错朗读切换中文发音人 */
  function enqueueSentence(sentence: string, vcnOverride?: string): void {
    const blobPromise = fetchTtsBlob(sentence, vcnOverride)
    playChain = playChain.then(async () => {
      const blob = await blobPromise
      await playBlob(blob)
    }).catch(() => {})
  }

  /** 播放建议卡片中的替代表达(英文,用英文 vcn) */
  function playAltText(text: string): void {
    fetchTtsBlob(text, 'catherine')
      .then(blob => playBlob(blob))
      .catch(() => {})
  }

  /** 处理 text 事件:追加到 assistant 消息并按句切分入 TTS 队列 */
  function processTextChunk(chunk: string): void {
    assistantMsg.content += chunk
    sentenceBuffer += chunk
    scrollToBottom()

    if (status.value === 'thinking') {
      status.value = 'speaking'
    }

    const parts = sentenceBuffer.split(SENTENCE_DELIMITERS)
    while (parts.length > 2) {
      const sentence = parts.shift()! + parts.shift()!
      if (sentence.trim()) {
        enqueueSentence(sentence.trim())
      }
    }
    sentenceBuffer = parts.join('')
  }

  /** 处理 grammar 事件:挂到上一条 user 消息,把朗读文本加入 TTS 队列(中文 vcn) */
  function processGrammar(data: string): void {
    try {
      const grammar = JSON.parse(data) as GrammarCorrection
      // assistant 是最后一条,上一条 user 是倒数第二条
      const userMsg = messages.value[messages.value.length - 2]
      if (userMsg && userMsg.role === 'user') {
        userMsg.grammar = grammar
      }
      // 纠错朗读用固定中文 vcn,因为解释以中文为主
      if (!grammar.noError && grammar.speechText) {
        enqueueSentence(grammar.speechText, 'xiaoyan')
      }
      // 建议朗读:无语法错误但有表达建议时,用中文 vcn 朗读建议
      if (grammar.noError
        && grammar.suggestion
        && grammar.suggestion.level !== 'none'
        && grammar.suggestion.speechText) {
        enqueueSentence(grammar.suggestion.speechText, 'xiaoyan')
      }
      // 场景模式:统计语法错误数
      if (effectiveSceneMode.value && !grammar.noError) {
        practiceErrors.value++
      }
    } catch {
      // JSON 解析失败,静默丢弃
    }
  }

  /** 处理 tool 事件:工具调用记录挂到当前 assistant 消息,渲染查询卡片 */
  function processTool(data: string): void {
    try {
      const ev = JSON.parse(data) as ToolEvent
      const last = messages.value[messages.value.length - 1]
      if (last && last.role === 'assistant') {
        if (!last.toolEvents) {
          last.toolEvents = []
        }
        last.toolEvents.push(ev)
        scrollToBottom()
      }
    } catch {
      // 解析失败,静默丢弃
    }
  }

  /** 从 SSE 事件块中解析 event 名和 data */
  function parseSseEvent(evt: string): { eventName: string; data: string } {
    let eventName = 'message'
    let data = ''
    for (const line of evt.split('\n')) {
      if (line.startsWith('event:')) {
        eventName = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        data += line.slice(5)
      }
    }
    return { eventName, data }
  }

  let streamEnded = false
  while (!streamEnded) {
    const { done, value } = await reader.read()
    if (done) break

    sseBuffer += decoder.decode(value, { stream: true })
    // SSE 事件以双换行分隔
    const events = sseBuffer.split('\n\n')
    sseBuffer = events.pop() || ''

    for (const evt of events) {
      const { eventName, data } = parseSseEvent(evt)
      switch (eventName) {
        case 'meta': {
          try {
            const m = JSON.parse(data)
            ctxMeta.value = {
              truncated: !!m.truncated,
              estimatedTokens: m.estimatedTokens ?? 0,
              needSummarize: !!m.needSummarize,
            }
          } catch { /* ignore */ }
          break
        }
        case 'text':
          processTextChunk(data)
          break
        case 'tool':
          processTool(data)
          break
        case 'grammar':
          processGrammar(data)
          break
        case 'done':
          streamEnded = true
          break
        case 'error':
          throw new Error(data || 'AI 对话失败')
      }
      if (streamEnded) break
    }
  }

  // 处理最后未切句的残留文本
  if (sentenceBuffer.trim()) {
    enqueueSentence(sentenceBuffer.trim())
  }

  await playChain
}

async function fetchTtsBlob(text: string, vcnOverride?: string): Promise<Blob> {
  const response = await fetch('/api/tts', {
    method: 'POST',
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ text, vcn: vcnOverride ?? vcn.value, speed: speed.value }),
  })
  if (!response.ok) {
    throw new Error('语音合成失败')
  }
  return response.blob()
}

function playBlob(blob: Blob): Promise<void> {
  const url = URL.createObjectURL(blob)
  return new Promise<void>((resolve, reject) => {
    const audio = new Audio(url)
    audio.onended = () => { URL.revokeObjectURL(url); resolve() }
    audio.onerror = () => { URL.revokeObjectURL(url); reject(new Error('音频播放失败')) }
    audio.play().catch(reject)
  })
}

function stopAudio(): void {
  workletNode?.disconnect()
  sourceNode?.disconnect()
  silentGain?.disconnect()
  workletNode = null
  sourceNode = null
  silentGain = null
  stream?.getTracks().forEach(t => t.stop())
  stream = null
  if (audioContext) {
    void audioContext.close()
    audioContext = null
  }
}

function stopTimer(): void {
  if (elapsedTimer !== null) {
    clearInterval(elapsedTimer)
    elapsedTimer = null
  }
}

function cleanup(): void {
  stopTimer()
  stopAudio()
  status.value = 'idle'
}

/** 确保存在当前会话,如果没有则创建一个新的 */
async function ensureConversation(): Promise<void> {
  if (currentConversationId.value !== null) return
  // 如果外部指定了要加载的会话ID,直接使用
  if (props.loadConversationId) {
    currentConversationId.value = props.loadConversationId
    conversationStartTime.value = Date.now()
    return
  }
  try {
    // 先尝试获取进行中的会话
    const res = await getActiveConversation()
    if (res.data) {
      currentConversationId.value = res.data.id
      return
    }
  } catch (_e) {
    // 忽略错误,创建新会话
  }
  // 创建新会话
  const createRes = await createConversation({
    sceneId: props.sceneId || internalSceneId.value || undefined,
    title: effectiveSceneMode.value
      ? `场景练习 - ${internalSceneName.value || effectiveScenePrompt.value.substring(0, 20)}`
      : '自由对话'
  })
  if (createRes.data) {
    currentConversationId.value = createRes.data.id
    conversationStartTime.value = Date.now()
    emit('conversationCreated', createRes.data.id)
  }
}

/** 保存消息到会话 */
async function saveMessageToConversation(
  role: string,
  content: string,
  grammarJson: string | null,
  hasError: boolean,
  hasSuggestion: boolean
): Promise<void> {
  if (currentConversationId.value === null) return
  try {
    await addConversationMessage(currentConversationId.value, {
      role,
      content,
      grammarJson,
      hasError,
      hasSuggestion
    })
  } catch (_e) {
    // 保存失败静默忽略,不影响对话流程
  }
}

/** 结束当前会话, 并展示报告弹窗 */
async function endCurrentConversation(summary?: string): Promise<void> {
  if (currentConversationId.value === null) return
  const convId = currentConversationId.value
  let endOk = false
  try {
    const duration = conversationStartTime.value
      ? Math.round((Date.now() - conversationStartTime.value) / 1000)
      : 0
    await endConversation(convId, { duration, summary })
    endOk = true
    emit('conversationChanged')
  } catch (_e) {
    // 忽略错误
  } finally {
    currentConversationId.value = null
    conversationStartTime.value = null
  }
  // 后端已在 end 接口内触发报告生成, 这里拉取并弹窗
  if (endOk) {
    reportDialogRef.value?.show(convId)
  }
}

/** 清空当前对话, 结束会话并展示报告 */
async function clearChat(): Promise<void> {
  // 结束当前会话并生成报告 (内部会 show dialog)
  await endCurrentConversation()
  messages.value = []
  textInput.value = ''
  errorMessage.value = ''
  ctxMeta.value = null
  // 重置场景选择状态,显示场景选择面板
  conversationStarted.value = false
  internalSceneId.value = null
  internalSceneName.value = ''
  internalScenePrompt.value = ''
  internalOpeningLine.value = ''
  internalSceneMode.value = false
}

/** 场景模式:结束练习,发送数据给父组件 */
function finishPractice(): void {
  const duration = practiceStartTime.value
    ? Math.round((Date.now() - practiceStartTime.value) / 1000)
    : 0
  const conversation = messages.value
    .map(m => `${m.role === 'user' ? 'User' : 'AI'}: ${m.content}`)
    .join('\n')
  emit('finish', {
    rounds: practiceRounds.value,
    duration,
    errorCount: practiceErrors.value,
    conversation,
  })
  // 结束会话并生成总结
  const summary = practiceRounds.value > 0
    ? `完成 ${practiceRounds.value} 轮对话，语法错误 ${practiceErrors.value} 处`
    : undefined
  void endCurrentConversation(summary)
  // 仅内部场景模式需要重置(ScenePractice 外部传入的 props 由父组件控制)
  if (!props.sceneMode) {
    messages.value = []
    ctxMeta.value = null
    conversationStarted.value = false
    internalSceneId.value = null
    internalSceneName.value = ''
    internalScenePrompt.value = ''
    internalOpeningLine.value = ''
    internalSceneMode.value = false
  }
}

/** 加载指定会话的消息 */
async function loadConversationMessages(conversationId: number): Promise<void> {
  try {
    const res = await getConversationMessagesApi(conversationId)
    if (res.data) {
      messages.value = (res.data as any[]).map((m: any) => ({
        role: m.role.toLowerCase() === 'user' ? 'user' : 'assistant',
        content: m.content,
        grammar: m.grammarJson ? JSON.parse(m.grammarJson) : undefined
      }))
      currentConversationId.value = conversationId
      conversationStartTime.value = Date.now()
      conversationStarted.value = true
      ctxMeta.value = null
      // 如果有关联场景,恢复场景配置
      const convRes = await getConversationApi(conversationId)
      if (convRes.data && convRes.data.sceneId) {
        internalSceneId.value = convRes.data.sceneId
        internalSceneMode.value = true
        const matchedScene = availableScenes.value.find((s: any) => s.id === convRes.data.sceneId)
        if (matchedScene) {
          internalSceneName.value = matchedScene.name
          internalScenePrompt.value = matchedScene.systemPrompt || ''
          internalOpeningLine.value = matchedScene.openingLine || ''
        }
      }
      scrollToBottom()
    }
  } catch (_e) {
    // 静默忽略
  }
}

/** 初始化:加载场景列表(供场景选择面板使用) */
onMounted(async () => {
  // /chat 页面:预先加载场景列表供选择
  if (!props.sceneMode) {
    await loadScenes()
  }
  // 外部传入要加载的会话ID(ChatGPT 布局点击侧边栏会话)
  if (props.loadConversationId) {
    await loadConversationMessages(props.loadConversationId)
    return
  }
  // ScenePractice 外部传入场景模式时,直接加载开场白
  if (props.sceneMode && props.openingLine) {
    messages.value.push({ role: 'assistant', content: props.openingLine })
    scrollToBottom()
    fetchTtsBlob(props.openingLine, vcn.value)
      .then(blob => playBlob(blob))
      .catch(() => {})
  }
})
</script>

<template>
  <div class="voice-chat-shell">
    <div v-if="showScenePanel" class="scene-panel">
      <div class="scene-panel-header">
        <h3>选择练习场景</h3>
        <p>选择一个场景开始针对性练习，或直接进入自由对话</p>
      </div>
      <div v-loading="scenesLoading" class="scene-panel-grid">
        <div
          v-for="scene in availableScenes"
          :key="scene.id"
          class="scene-panel-card"
          @click="selectScene(scene)"
        >
          <div class="scene-panel-icon">
            <el-icon :size="32"><component :is="scene.icon || 'ChatDotRound'" /></el-icon>
          </div>
          <div class="scene-panel-info">
            <h4>{{ scene.name }}</h4>
            <p>{{ scene.description }}</p>
            <div class="scene-panel-meta">
              <el-tag :type="difficultyType(scene.difficulty)" size="small">
                {{ difficultyLabel(scene.difficulty) }}
              </el-tag>
              <span class="scene-role-label">{{ scene.aiRole }}</span>
            </div>
          </div>
        </div>
        <div v-if="!scenesLoading && availableScenes.length === 0" class="scene-panel-empty">
          <p>暂无可用场景</p>
        </div>
      </div>
      <div class="scene-panel-footer">
        <el-button type="primary" size="large" @click="selectFreeChat">
          <el-icon><ChatDotRound /></el-icon>
          直接开始自由对话
        </el-button>
      </div>
    </div>

    <template v-else>
      <div v-if="ctxMeta && (ctxMeta.truncated || ctxMeta.needSummarize)" class="ctx-truncated-banner">
        <el-icon class="ctx-banner-icon"><InfoFilled /></el-icon>
        <div class="ctx-banner-text">
          <template v-if="ctxMeta.truncated">
            <strong>上下文已压缩：</strong>
            最近 10 轮对话作为上下文，早期内容已合并为摘要。
          </template>
          <template v-else-if="ctxMeta.needSummarize">
            当前对话较长，后台正在生成上下文压缩摘要…
          </template>
          <span v-if="ctxMeta.estimatedTokens > 0" class="ctx-token-info">
            (约 {{ ctxMeta.estimatedTokens }} tokens)
          </span>
        </div>
      </div>
      <div ref="chatContainer" class="voice-chat-messages">
        <div v-if="messages.length === 0" class="voice-chat-empty">
          <p>点击麦克风，用语音和 AI 对话</p>
          <p v-if="internalSceneName" class="current-scene-label">
            当前场景：{{ internalSceneName }}
          </p>
        </div>
        <template v-for="(msg, idx) in messages" :key="idx">
          <div
            class="voice-msg"
            :class="msg.role === 'user' ? 'voice-msg--user' : 'voice-msg--ai'"
          >
            <span class="voice-msg-role">{{ msg.role === 'user' ? '你' : 'AI' }}</span>
            <div class="voice-msg-body">
              <!-- 工具调用卡片:展示本轮 AI 使用了哪些工具 -->
              <div
                v-if="msg.role === 'assistant' && msg.toolEvents?.length"
                class="tool-chips"
              >
                <div v-for="(te, ti) in msg.toolEvents" :key="ti" class="tool-chip">
                  <svg class="tool-chip-icon" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <circle cx="11" cy="11" r="8" />
                    <line x1="21" y1="21" x2="16.65" y2="16.65" />
                  </svg>
                  <span class="tool-chip-label">{{ toolLabel(te.name) }}</span>
                  <span v-if="toolSummary(te.arguments)" class="tool-chip-args">
                    {{ toolSummary(te.arguments) }}
                  </span>
                  <span class="tool-chip-done">✓</span>
                </div>
              </div>
              <div class="voice-msg-content" v-text="msg.content" />
              <!-- 翻译按钮 + 中文翻译块(仅 AI 消息) -->
              <div v-if="msg.role === 'assistant' && msg.content" class="translate-row">
                <button
                  class="translate-btn"
                  type="button"
                  :disabled="msg.translating"
                  @click="toggleTranslation(msg)"
                >
                  {{ msg.translating ? '翻译中…' : (msg.showTranslation ? '收起翻译' : '翻译') }}
                </button>
              </div>
              <div v-if="msg.role === 'assistant' && msg.showTranslation && msg.translation" class="translation-block">
                {{ msg.translation }}
              </div>
            </div>
          </div>
          <div
            v-if="msg.role === 'user'
              && msg.grammar
              && ((!msg.grammar.noError && msg.grammar.errors?.length)
                || (msg.grammar.suggestion && msg.grammar.suggestion.level !== 'none'))"
            class="voice-msg-grammar"
          >
            <GrammarCard :grammar="msg.grammar" @play-text="playAltText" />
          </div>
        </template>
      </div>

      <div class="voice-chat-controls">
        <p v-if="errorMessage" class="field-error" role="alert">{{ errorMessage }}</p>

        <!-- ChatGPT 风格大圆角输入框 -->
        <div class="gpt-input-wrap">
          <!-- 左侧麦克风按钮 -->
          <button
            v-if="status === 'idle'"
            class="gpt-icon-btn gpt-icon-btn--left"
            type="button"
            title="开始说话"
            @click="startRecording"
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <rect x="9" y="1" width="6" height="13" rx="3" />
              <path d="M19 10v2a7 7 0 0 1-14 0v-2" />
              <line x1="12" y1="19" x2="12" y2="23" />
              <line x1="8" y1="23" x2="16" y2="23" />
            </svg>
          </button>
          <button
            v-else-if="status === 'recording'"
            class="gpt-icon-btn gpt-icon-btn--left gpt-icon-btn--recording"
            type="button"
            title="停止录音"
            @click="stopAndProcess"
          >
            <span class="pulse-ring" />
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <rect x="9" y="1" width="6" height="13" rx="3" />
              <path d="M19 10v2a7 7 0 0 1-14 0v-2" />
              <line x1="12" y1="19" x2="12" y2="23" />
              <line x1="8" y1="23" x2="16" y2="23" />
            </svg>
          </button>
          <div v-else class="gpt-icon-btn gpt-icon-btn--left gpt-icon-btn--processing">
            <span class="spinner" />
          </div>

          <!-- 输入区域 -->
          <textarea
            v-model="textInput"
            class="gpt-textarea"
            rows="1"
            placeholder="输入消息，Enter 发送，Shift+Enter 换行"
            :disabled="status !== 'idle'"
            @keydown="handleTextInputKeydown"
          />

          <!-- 右侧发送按钮 -->
          <button
            class="gpt-send-btn"
            type="button"
            :disabled="status !== 'idle' || !textInput.trim()"
            @click="sendTypedMessage"
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
              <line x1="12" y1="19" x2="12" y2="5" />
              <polyline points="5 12 12 5 19 12" />
            </svg>
          </button>
        </div>

        <!-- 底部状态和设置 -->
        <div class="gpt-footer">
          <span class="gpt-status" aria-live="polite">{{ statusLabel }}</span>

          <div class="gpt-settings">
            <div v-if="!effectiveSceneMode" class="gpt-setting-item">
              <span class="gpt-setting-label">语音</span>
              <select v-model="vcn" :disabled="status !== 'idle'" class="gpt-select">
                <option v-for="option in VCN_OPTIONS" :key="option.value" :value="option.value">
                  {{ option.label }}
                </option>
              </select>
            </div>
            <div v-if="!effectiveSceneMode" class="gpt-setting-item">
              <span class="gpt-setting-label">语速</span>
              <input
                v-model.number="speed"
                type="range"
                min="0"
                max="100"
                step="5"
                :disabled="status !== 'idle'"
                class="gpt-range"
              />
              <span class="gpt-setting-value">{{ speed }}</span>
            </div>
            <button
              v-if="effectiveSceneMode"
              class="gpt-action gpt-action--danger"
              type="button"
              :disabled="status !== 'idle'"
              @click="finishPractice"
            >
              结束练习
            </button>
            <button
              v-else
              class="gpt-action"
              type="button"
              :disabled="status !== 'idle'"
              @click="clearChat"
            >
              清空对话
            </button>
          </div>
        </div>
      </div>
    </template>

    <ReportDialog ref="reportDialogRef" />
  </div>
</template>

<style scoped>
.voice-chat-shell {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.ctx-truncated-banner {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 14px;
  background: linear-gradient(90deg, #ecfeff, #eff6ff);
  border: 1px solid #bae6fd;
  border-radius: 8px;
  color: #0c4a6e;
  font-size: 13px;
  line-height: 1.5;
}

.ctx-banner-icon {
  color: #0ea5e9;
  flex-shrink: 0;
  margin-top: 2px;
}

.ctx-banner-text {
  flex: 1;
}

.ctx-token-info {
  display: inline-block;
  margin-left: 6px;
  color: #0369a1;
  font-size: 12px;
  opacity: 0.85;
}

.voice-chat-messages {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 24px;
}

.voice-chat-empty {
  text-align: center;
  color: var(--color-muted, #999);
  padding: 40px 0;
}

.voice-msg {
  max-width: 75%;
  padding: 12px 16px;
  border-radius: 16px;
  line-height: 1.6;
  word-break: break-word;
  white-space: pre-wrap;
  box-sizing: border-box;
}

.voice-msg--user {
  align-self: flex-end;
  background: linear-gradient(135deg, #409EFF 0%, #66b1ff 100%);
  color: white;
  border-bottom-right-radius: 4px;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.25);
}

.voice-msg--ai {
  align-self: flex-start;
  background: #fff;
  color: #303133;
  border: 1px solid #ebeef5;
  border-bottom-left-radius: 4px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}

.voice-msg-role {
  display: block;
  font-size: 11px;
  font-weight: 600;
  opacity: 0.7;
  margin-bottom: 4px;
}

.voice-msg-content {
  font-size: 14px;
}

/* 工具调用卡片 */
.voice-msg-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.tool-chips {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.tool-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  line-height: 1;
  color: #606266;
  background: rgba(64, 158, 255, 0.08);
  border: 1px solid rgba(64, 158, 255, 0.2);
  border-radius: 999px;
  padding: 4px 10px;
  width: fit-content;
}

.tool-chip-icon {
  color: #409eff;
  flex-shrink: 0;
}

.tool-chip-label {
  font-weight: 600;
  color: #409eff;
}

.tool-chip-args {
  color: #909399;
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tool-chip-done {
  color: #67c23a;
  font-weight: 700;
}

/* 翻译按钮 + 翻译块 */
.translate-row {
  display: flex;
  justify-content: flex-end;
}

.translate-btn {
  font-size: 12px;
  color: #909399;
  background: transparent;
  border: 1px solid #dcdfe6;
  border-radius: 999px;
  padding: 2px 10px;
  cursor: pointer;
  transition: all 0.15s;
}

.translate-btn:hover:not(:disabled) {
  color: #409eff;
  border-color: #409eff;
}

.translate-btn:disabled {
  opacity: 0.6;
  cursor: default;
}

.translation-block {
  font-size: 13px;
  line-height: 1.6;
  color: #606266;
  background: #f5f7fa;
  border-left: 3px solid #409eff;
  border-radius: 6px;
  padding: 8px 12px;
}

.voice-msg-grammar {
  align-self: flex-end;
  max-width: 80%;
  width: 100%;
}

.voice-chat-controls {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px 24px 20px;
  background: transparent;
}

/* GPT 风格大圆角输入框 */
.gpt-input-wrap {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 10px 12px;
  background: #fff;
  border: 1px solid #e5e5e5;
  border-radius: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  transition: border-color 0.2s, box-shadow 0.2s;
}

.gpt-input-wrap:focus-within {
  border-color: #409EFF;
  box-shadow: 0 2px 16px rgba(64, 158, 255, 0.15);
}

.gpt-icon-btn {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 50%;
  background: #f0f0f0;
  color: #606266;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
  position: relative;
}

.gpt-icon-btn:hover {
  background: #e0e0e0;
  color: #303133;
}

.gpt-icon-btn--recording {
  background: #fef0f0;
  color: #f56c6c;
}

.gpt-icon-btn--recording:hover {
  background: #fde2e2;
}

.gpt-icon-btn--processing {
  background: #ecf5ff;
  color: #409EFF;
  cursor: not-allowed;
}

.gpt-textarea {
  flex: 1;
  min-height: 36px;
  max-height: 160px;
  padding: 8px 4px;
  border: none;
  outline: none;
  resize: none;
  background: transparent;
  font-size: 15px;
  line-height: 1.5;
  color: #303133;
  font-family: inherit;
}

.gpt-textarea::placeholder {
  color: #c0c4cc;
}

.gpt-textarea:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.gpt-send-btn {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 50%;
  background: #409EFF;
  color: #fff;
  cursor: pointer;
  transition: background 0.15s, transform 0.15s, opacity 0.15s;
}

.gpt-send-btn:hover:not(:disabled) {
  background: #337ecc;
  transform: scale(1.05);
}

.gpt-send-btn:disabled {
  background: #c0c4cc;
  cursor: not-allowed;
}

/* 底部状态栏 */
.gpt-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 8px;
}

.gpt-status {
  font-size: 12px;
  color: #909399;
}

.gpt-settings {
  display: flex;
  align-items: center;
  gap: 14px;
}

.gpt-setting-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.gpt-setting-label {
  font-size: 12px;
  color: #909399;
}

.gpt-select {
  padding: 3px 8px;
  border: 1px solid #e5e5e5;
  border-radius: 6px;
  font-size: 12px;
  color: #606266;
  background: #fff;
  cursor: pointer;
  outline: none;
}

.gpt-select:focus {
  border-color: #409EFF;
}

.gpt-select:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.gpt-range {
  width: 80px;
  height: 4px;
  cursor: pointer;
  accent-color: #409EFF;
}

.gpt-range:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.gpt-setting-value {
  font-size: 12px;
  color: #606266;
  min-width: 24px;
  text-align: right;
}

.gpt-action {
  padding: 4px 12px;
  border: 1px solid #e5e5e5;
  border-radius: 14px;
  background: #fff;
  color: #606266;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
}

.gpt-action:hover:not(:disabled) {
  border-color: #409EFF;
  color: #409EFF;
}

.gpt-action:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.gpt-action--danger {
  color: #f56c6c;
  border-color: #f56c6c;
}

.gpt-action--danger:hover:not(:disabled) {
  background: #fef0f0;
}

/* 脉冲动画 */
.pulse-ring {
  position: absolute;
  inset: -4px;
  border-radius: 50%;
  border: 2px solid #f56c6c;
  animation: pulse 1.2s ease-out infinite;
}

@keyframes pulse {
  0% { transform: scale(1); opacity: 0.7; }
  100% { transform: scale(1.3); opacity: 0; }
}

.spinner {
  width: 18px;
  height: 18px;
  border: 2px solid #e5e5e5;
  border-top-color: #409EFF;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.field-error {
  font-size: 13px;
  color: #f56c6c;
  margin: 0;
  padding: 0 8px;
}

/* 场景选择面板样式 */
.scene-panel {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.scene-panel-header {
  text-align: center;
  margin-bottom: 20px;
}

.scene-panel-header h3 {
  margin: 0 0 8px 0;
  font-size: 20px;
  color: #303133;
}

.scene-panel-header p {
  margin: 0;
  color: #909399;
  font-size: 14px;
}

.scene-panel-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 16px;
  margin-bottom: 20px;
  min-height: 100px;
}

.scene-panel-card {
  display: flex;
  gap: 12px;
  padding: 14px;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
  background: #fafbfc;
}

.scene-panel-card:hover {
  border-color: #409eff;
  background: #ecf5ff;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.15);
}

.scene-panel-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 10px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff;
  flex-shrink: 0;
}

.scene-panel-info {
  flex: 1;
  min-width: 0;
}

.scene-panel-info h4 {
  margin: 0 0 4px 0;
  font-size: 15px;
  color: #303133;
}

.scene-panel-info p {
  margin: 0 0 8px 0;
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.scene-panel-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.scene-role-label {
  font-size: 12px;
  color: #606266;
}

.scene-panel-empty {
  grid-column: 1 / -1;
  text-align: center;
  padding: 40px 0;
  color: #909399;
}

.scene-panel-footer {
  text-align: center;
  padding-top: 16px;
  border-top: 1px solid #ebeef5;
}

.current-scene-label {
  margin-top: 8px;
  font-size: 13px;
  color: #409eff;
  font-weight: 500;
}

@media (max-width: 560px) {
  .text-input-bar {
    align-items: stretch;
    flex-direction: column;
  }

  .text-send-button {
    width: 100%;
  }
}
</style>
