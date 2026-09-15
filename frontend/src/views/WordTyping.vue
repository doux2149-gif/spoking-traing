<template>
  <div class="typing-practice">
    <!-- 选择页 -->
    <div v-if="state === 'setup'" class="setup">
      <h2>⌨️ 单词打字练习</h2>
      <p class="hint">选择范围, 开始敲击键盘练习拼写吧</p>

      <el-form label-width="80px" class="setup-form">
        <el-form-item label="练习数量">
          <el-radio-group v-model="setup.count">
            <el-radio-button :value="10">10 个</el-radio-button>
            <el-radio-button :value="20">20 个</el-radio-button>
            <el-radio-button :value="50">50 个</el-radio-button>
            <el-radio-button :value="0">全部 ({{ totalCount }})</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="类别">
          <el-select v-model="setup.category" style="width: 200px">
            <el-option label="全部类别" value="" />
            <el-option
              v-for="c in categories"
              :key="c"
              :label="c"
              :value="c"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="模式">
          <el-radio-group v-model="setup.mode">
            <el-radio-button value="sequential">顺序</el-radio-button>
            <el-radio-button value="random">随机打乱</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" :loading="loadingWords" @click="start">
            开始练习
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 练习页 -->
    <div v-else class="practice">
      <!-- 顶部进度 -->
      <div class="top-bar">
        <div class="progress-info">
          <span class="cur">{{ currentIdx + 1 }}</span>
          <span class="sep">/</span>
          <span class="total">{{ words.length }}</span>
          <span class="dot">•</span>
          <span class="acc">正确率 {{ stats.accuracy }}%</span>
          <span class="dot">•</span>
          <span class="timer">⏱ {{ formatTime(stats.elapsedMs) }}</span>
        </div>
        <el-button link type="info" @click="quit">退出</el-button>
      </div>
      <el-progress
        :percentage="progressPct"
        :stroke-width="6"
        :color="progressPct === 100 ? '#67c23a' : '#409eff'"
      />

      <!-- 单词展示 -->
      <div class="word-area">
        <!-- 中文翻译 + 音标 + 词性 (提示, 但打字目标是英文) -->
        <div class="word-meta">
          <span v-if="currentWord.phonetic" class="phonetic">{{ currentWord.phonetic }}</span>
          <el-tag v-if="currentWord.partOfSpeech" size="small" effect="plain">
            {{ posLabel(currentWord.partOfSpeech) }}
          </el-tag>
          <span v-if="currentWord.category" class="category">{{ currentWord.category }}</span>
        </div>

        <!-- 英文单词: 按字母分 span 渲染, 不同状态不同颜色 -->
        <div class="target-word" ref="wordContainer" tabindex="0">
          <span
            v-for="(ch, i) in currentWord.english.toLowerCase()"
            :key="i"
            class="letter"
            :class="letterClass(i)"
            @click="focus"
          >
            {{ ch }}
          </span>
        </div>

        <div class="cn-translation">{{ currentWord.chinese }}</div>

        <!-- 例句 (可选显示) -->
        <div v-if="currentWord.exampleSentence" class="example">
          <span class="example-en">{{ currentWord.exampleSentence }}</span>
          <span v-if="currentWord.exampleTranslation" class="example-cn">
            {{ currentWord.exampleTranslation }}
          </span>
        </div>
      </div>

      <!-- 底部状态 -->
      <div class="status-bar">
        <div class="typed-preview">
          已输入: <span class="typed">{{ typedSoFar || '（空）' }}</span>
        </div>
        <div class="hints">
          <el-tag size="small" effect="plain" type="info">⌫ Backspace 撤回</el-tag>
          <el-tag size="small" effect="plain" type="info">⏎ Enter / Space 提交</el-tag>
          <el-tag size="small" effect="plain" type="info">Esc 退出</el-tag>
        </div>
      </div>
    </div>

    <!-- 完成总结 dialog -->
    <el-dialog
      v-model="showResult"
      title="🎉 练习完成!"
      width="480px"
      center
    >
      <div class="result-body">
        <div class="result-score">
          <div class="score-circle">
            <span class="score-num">{{ finalStats.accuracy }}</span>
            <span class="score-unit">%</span>
            <span class="score-label">正确率</span>
          </div>
        </div>
        <div class="result-stats">
          <div class="stat-item">
            <div class="stat-num">{{ words.length }}</div>
            <div class="stat-label">单词总数</div>
          </div>
          <div class="stat-item">
            <div class="stat-num">{{ finalStats.correctWords }}</div>
            <div class="stat-label">一次性通过</div>
          </div>
          <div class="stat-item">
            <div class="stat-num">{{ formatTime(finalStats.elapsedMs) }}</div>
            <div class="stat-label">总耗时</div>
          </div>
          <div class="stat-item">
            <div class="stat-num">{{ finalStats.avgMsPerWord }}s</div>
            <div class="stat-label">平均每词</div>
          </div>
        </div>
        <div v-if="mistakes.length" class="mistakes">
          <div class="mistakes-title">需要复习 ({{ mistakes.length }})</div>
          <div class="mistakes-list">
            <div v-for="m in mistakes" :key="m.english" class="mistake-item">
              <span class="m-en">{{ m.english }}</span>
              <span class="m-cn">{{ m.chinese }}</span>
              <span class="m-times">错 {{ m.wrongCount }} 次</span>
            </div>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="state = 'setup'">再来一次</el-button>
        <el-button type="primary" @click="practiceMistakes">只练错词</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../api/request'
import type { WordItem } from '../api/admin'
import { ElMessageBox } from 'element-plus'

type Mode = 'sequential' | 'random'
type PracticeState = 'setup' | 'practice'

const state = ref<PracticeState>('setup')
const loadingWords = ref(false)
const words = ref<WordItem[]>([])
const categories = ref<string[]>([])
const totalCount = ref(0)
const currentIdx = ref(0)
const typed = ref('')                  // 当前单词已敲入的字符 (小写)
const mistakes = ref<WordMistake[]>([]) // 本次练习的错词
const showResult = ref(false)
const wordContainer = ref<HTMLElement | null>(null)

const setup = reactive({
  count: 10,
  category: '',
  mode: 'random' as Mode
})

interface WordMistake {
  english: string
  chinese: string
  wrongCount: number
}

const stats = reactive({
  totalKeystrokes: 0,
  correctKeystrokes: 0,
  correctWords: 0,
  wrongCountThisWord: 0,
  wordStartMs: 0,
  sessionStartMs: 0,
  elapsedMs: 0,
  accuracy: 100
})

const finalStats = reactive({
  accuracy: 0,
  correctWords: 0,
  elapsedMs: 0,
  avgMsPerWord: 0
})

const currentWord = computed(() => words.value[currentIdx.value] || { english: '', chinese: '' })
const progressPct = computed(() => {
  if (words.value.length === 0) return 0
  return Math.round((currentIdx.value / words.value.length) * 100)
})
const typedSoFar = computed(() => typed.value)

// 实时正确率
watch([() => stats.correctKeystrokes, () => stats.totalKeystrokes], () => {
  if (stats.totalKeystrokes > 0) {
    stats.accuracy = Math.round((stats.correctKeystrokes / stats.totalKeystrokes) * 100)
  } else {
    stats.accuracy = 100
  }
})

function letterClass(idx: number) {
  const targetLen = currentWord.value.english.toLowerCase().length
  if (idx < typed.value.length) {
    // 已敲入
    const typedCh = typed.value[idx]
    const targetCh = currentWord.value.english.toLowerCase()[idx]
    if (typedCh === targetCh) {
      return 'correct'
    } else {
      return 'wrong'
    }
  } else if (idx === typed.value.length && idx < targetLen) {
    return 'cursor'
  } else {
    return 'pending'
  }
}

async function start() {
  loadingWords.value = true
  try {
    const params: any = { pageNum: 1, pageSize: 200 }
    if (setup.category) params.category = setup.category
    const res: any = await request.get('/words', { params })
    let list: WordItem[] = res.data?.records ?? res.data?.rows ?? []
    totalCount.value = list.length

    if (setup.count > 0 && setup.count < list.length) {
      list = list.slice(0, setup.count)
    }

    if (setup.mode === 'random') {
      list = list.sort(() => Math.random() - 0.5)
    }

    words.value = list
    currentIdx.value = 0
    typed.value = ''
    mistakes.value = []
    Object.assign(stats, {
      totalKeystrokes: 0,
      correctKeystrokes: 0,
      correctWords: 0,
      wrongCountThisWord: 0,
      wordStartMs: Date.now(),
      sessionStartMs: Date.now(),
      elapsedMs: 0,
      accuracy: 100
    })
    state.value = 'practice'
    showResult.value = false
    focus()
    startTimer()
  } catch (e: any) {
    ElMessage.error('加载单词失败')
  } finally {
    loadingWords.value = false
  }
}

function startTimer() {
  const timer = setInterval(() => {
    if (state.value === 'practice') {
      stats.elapsedMs = Date.now() - stats.sessionStartMs
    } else {
      clearInterval(timer)
    }
  }, 500)
}

function handleKey(e: KeyboardEvent) {
  if (state.value !== 'practice') return
  const target = currentWord.value.english.toLowerCase()

  // Backspace
  if (e.key === 'Backspace') {
    e.preventDefault()
    if (typed.value.length > 0) {
      typed.value = typed.value.slice(0, -1)
    }
    return
  }

  // Enter / Space 提交
  if (e.key === 'Enter' || e.key === ' ') {
    e.preventDefault()
    if (typed.value.length > 0) {
      submitWord()
    }
    return
  }

  // Esc 退出
  if (e.key === 'Escape') {
    quit()
    return
  }

  // 字母键 (a-z, 含 shift 的大写会自动 .toLowerCase())
  if (/^[a-zA-Z]$/.test(e.key)) {
    e.preventDefault()
    const idx = typed.value.length
    if (idx >= target.length) return // 已打满
    const ch = e.key.toLowerCase()
    stats.totalKeystrokes++
    if (ch === target[idx]) {
      stats.correctKeystrokes++
      typed.value += ch
      // 打完整个单词
      if (typed.value.length === target.length) {
        stats.correctWords++
        nextWord()
      }
    } else {
      stats.wrongCountThisWord++
      typed.value += ch // 还是放进去, 让用户看到哪个字母错了
    }
  }
}

function submitWord() {
  const target = currentWord.value.english.toLowerCase()
  if (typed.value === target) {
    stats.correctWords++
  } else {
    recordMistake()
  }
  nextWord()
}

function nextWord() {
  if (stats.wrongCountThisWord > 0) {
    recordMistake()
  }
  stats.wrongCountThisWord = 0

  if (currentIdx.value + 1 >= words.value.length) {
    finish()
  } else {
    currentIdx.value++
    typed.value = ''
    stats.wordStartMs = Date.now()
  }
  nextTick(focus)
}

function recordMistake() {
  const en = currentWord.value.english
  const existing = mistakes.value.find((m) => m.english === en)
  if (existing) {
    existing.wrongCount++
  } else {
    mistakes.value.push({
      english: en,
      chinese: currentWord.value.chinese,
      wrongCount: 1
    })
  }
}

function finish() {
  finalStats.accuracy = stats.totalKeystrokes > 0
    ? Math.round((stats.correctKeystrokes / stats.totalKeystrokes) * 100)
    : 100
  finalStats.correctWords = stats.correctWords
  finalStats.elapsedMs = stats.elapsedMs
  finalStats.avgMsPerWord = words.value.length > 0
    ? Math.round(stats.elapsedMs / words.value.length / 100) / 10
    : 0
  showResult.value = true
}

function practiceMistakes() {
  if (mistakes.value.length === 0) {
    ElMessage.success('太厉害啦, 没有错词!')
    return
  }
  // 只保留错词重新练
  const errorEns = new Set(mistakes.value.map((m) => m.english))
  words.value = words.value.filter((w: WordItem) => errorEns.has(w.english))
  currentIdx.value = 0
  typed.value = ''
  mistakes.value = []
  Object.assign(stats, {
    totalKeystrokes: 0, correctKeystrokes: 0, correctWords: 0,
    wrongCountThisWord: 0, wordStartMs: Date.now(),
    sessionStartMs: Date.now(), elapsedMs: 0, accuracy: 100
  })
  showResult.value = false
  focus()
}

function quit() {
  if (stats.totalKeystrokes > 0 && currentIdx.value < words.value.length) {
    ElMessageBox.confirm(
      `已练习 ${currentIdx.value + 1} / ${words.value.length}, 确定退出?`,
      '退出确认',
      { type: 'warning' }
    ).then(() => {
      state.value = 'setup'
    }).catch(() => {})
  } else {
    state.value = 'setup'
  }
}

function focus() {
  nextTick(() => {
    const el = wordContainer.value as HTMLElement | null
    el?.focus()
  })
}

function posLabel(pos: string) {
  return ({
    noun: '名词', verb: '动词', adjective: '形容词',
    adverb: '副词', preposition: '介词', phrase: '短语'
  } as any)[pos] || pos
}

function formatTime(ms: number) {
  const s = Math.floor(ms / 1000)
  const m = Math.floor(s / 60)
  const sec = s % 60
  if (m === 0) return `${sec}s`
  return `${m}m ${sec}s`
}

async function loadCategories() {
  try {
    const res: any = await request.get('/words/categories')
    categories.value = res.data ?? []
  } catch (_e) {}
}

onMounted(() => {
  loadCategories()
  window.addEventListener('keydown', handleKey)
})
onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKey)
})
</script>

<style scoped>
.typing-practice { max-width: 720px; margin: 0 auto; padding: 12px; }

/* ===== setup ===== */
.setup { text-align: center; padding: 60px 0; }
.setup h2 { margin: 0 0 8px; font-size: 26px; color: #303133; }
.setup .hint { color: #909399; margin-bottom: 30px; }
.setup-form { max-width: 480px; margin: 0 auto; text-align: left; }

/* ===== practice ===== */
.practice { padding-top: 8px; }
.top-bar {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 10px;
}
.progress-info {
  font-size: 15px; color: #606266; font-weight: 500;
}
.progress-info .cur { color: #409eff; font-size: 20px; font-weight: 700; }
.progress-info .total { color: #909399; }
.progress-info .dot { margin: 0 8px; color: #c0c4cc; }
.progress-info .acc { color: #67c23a; }
.progress-info .timer { color: #e6a23c; }

.word-area {
  text-align: center; padding: 40px 0;
  min-height: 240px;
}
.word-meta {
  display: flex; gap: 10px; justify-content: center;
  align-items: center; margin-bottom: 22px;
}
.phonetic { color: #67c23a; font-family: "DejaVu Sans", serif; font-size: 16px; }
.category { color: #909399; font-size: 12px; }

.target-word {
  display: inline-flex; gap: 6px;
  padding: 12px 24px; border-radius: 12px;
  background: #f8f9fb;
  font-size: 40px; font-weight: 700;
  letter-spacing: 2px;
  outline: none;
}
.letter {
  display: inline-block; min-width: 28px; text-align: center;
  padding: 4px 2px; border-radius: 6px; transition: all 0.08s;
  font-family: "JetBrains Mono", "SF Mono", Consolas, monospace;
}
.letter.pending { color: #c0c4cc; }
.letter.correct { color: #67c23a; background: rgba(103, 194, 58, 0.12); }
.letter.wrong {
  color: #f56c6c; background: rgba(245, 108, 108, 0.15);
  animation: shake 0.25s ease;
}
.letter.cursor {
  color: #409eff;
  border-bottom: 3px solid #409eff;
  animation: blink 1.1s infinite;
}

@keyframes shake {
  0%,100% { transform: translateX(0); }
  25% { transform: translateX(-3px); }
  75% { transform: translateX(3px); }
}
@keyframes blink {
  0%, 50% { border-bottom-color: #409eff; }
  51%, 100% { border-bottom-color: transparent; }
}

.cn-translation {
  font-size: 18px; color: #606266; margin-top: 20px;
  font-weight: 500;
}
.example {
  margin-top: 18px; padding: 10px 16px; border-radius: 8px;
  background: #f4f4f5; color: #606266;
  font-size: 13px; line-height: 1.6;
  display: inline-block;
}
.example .example-en { font-style: italic; margin-right: 8px; }
.example .example-cn { color: #909399; }

.status-bar {
  display: flex; justify-content: space-between;
  align-items: center; margin-top: 20px;
  padding-top: 16px; border-top: 1px solid #ebeef5;
}
.typed-preview { color: #606266; font-size: 13px; }
.typed-preview .typed {
  font-family: monospace; font-size: 15px; color: #303133;
}
.hints { display: flex; gap: 8px; }

/* ===== result ===== */
.result-body { padding: 10px 0; }
.result-score { text-align: center; margin-bottom: 20px; }
.score-circle {
  display: inline-flex; flex-direction: column; align-items: center;
  width: 120px; height: 120px; border-radius: 50%;
  background: linear-gradient(135deg, #409eff, #67c23a);
  color: #fff; justify-content: center;
}
.score-num { font-size: 42px; font-weight: 700; line-height: 1 }
.score-unit { font-size: 18px; }
.score-label { font-size: 12px; opacity: 0.9; margin-top: 4px; }

.result-stats {
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px;
  margin-bottom: 20px;
}
.stat-item { text-align: center; padding: 10px; background: #f8f9fb; border-radius: 8px; }
.stat-num { font-size: 20px; font-weight: 700; color: #303133; }
.stat-label { font-size: 12px; color: #909399; margin-top: 4px; }

.mistakes-title { font-size: 14px; font-weight: 600; color: #303133; margin-bottom: 8px; }
.mistakes-list { max-height: 160px; overflow-y: auto; }
.mistake-item {
  display: flex; justify-content: space-between; align-items: center;
  padding: 8px 12px; border-bottom: 1px solid #f0f0f0; font-size: 13px;
}
.m-en { font-weight: 600; color: #409eff; }
.m-cn { color: #606266; margin-left: 10px; flex: 1; }
.m-times { color: #f56c6c; font-size: 12px; }
</style>
