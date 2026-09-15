<template>
  <div class="word-management">
    <!-- 工具栏 -->
    <div class="toolbar">
      <div class="filter">
        <el-input
          v-model="filters.keyword"
          placeholder="搜索单词/释义"
          clearable
          style="width: 200px"
          @keyup.enter="loadData"
        />
        <el-select
          v-model="filters.category"
          placeholder="全部类别"
          clearable
          style="width: 160px"
        >
          <el-option
            v-for="c in categoryOptions"
            :key="c"
            :label="c"
            :value="c"
          />
        </el-select>
        <el-button type="primary" @click="loadData">查询</el-button>
        <el-button @click="resetFilter">重置</el-button>
      </div>
      <div class="actions">
        <el-button type="success" @click="showGenerateDialog = true">
          🤖 AI 批量生成
        </el-button>
        <el-button type="primary" @click="openCreateDialog">新增单词</el-button>
        <el-button @click="handleExport">导出 CSV</el-button>
        <el-button
          v-if="selection.length > 0"
          type="danger"
          @click="batchDelete"
        >
          批量删除 ({{ selection.length }})
        </el-button>
      </div>
    </div>

    <!-- 表格 -->
    <el-table
      :data="records"
      v-loading="loading"
      stripe
      @selection-change="selection = $event"
    >
      <el-table-column type="selection" width="45" />
      <el-table-column prop="english" label="英文" min-width="150">
        <template #default="{ row }">
          <span class="word-en">{{ row.english }}</span>
          <el-tag
            v-if="row.source === 'GENERATED'"
            size="small"
            type="success"
            effect="plain"
            class="ml-2"
          >AI</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="chinese" label="中文释义" min-width="180" />
      <el-table-column prop="phonetic" label="音标" width="140">
        <template #default="{ row }">
          <span v-if="row.phonetic" class="phonetic">{{ row.phonetic }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="partOfSpeech" label="词性" width="100" />
      <el-table-column prop="category" label="类别" width="110">
        <template #default="{ row }">
          <el-tag size="small" effect="plain">{{ row.category }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="难度" width="90">
        <template #default="{ row }">
          <el-rate :model-value="row.difficulty || 0" disabled size="small" />
        </template>
      </el-table-column>
      <el-table-column label="发布" width="90">
        <template #default="{ row }">
          <el-switch
            :model-value="row.isPublished === 1"
            @change="(v: boolean) => togglePublish(row, v)"
          />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEditDialog(row)">编辑</el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="pageNum"
      v-model:page-size="pageSize"
      :page-sizes="[10, 20, 50, 100]"
      :total="total"
      layout="total, sizes, prev, pager, next, jumper"
      @size-change="loadData"
      @current-change="loadData"
      class="mt-3"
    />

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="showEditDialog"
      :title="editing ? '编辑单词' : '新增单词'"
      width="580px"
      destroy-on-close
    >
      <el-form :model="form" label-width="100px">
        <el-form-item label="英文" required>
          <el-input v-model="form.english" placeholder="如: initiative" :disabled="!!editing" />
        </el-form-item>
        <el-form-item label="中文释义" required>
          <el-input v-model="form.chinese" placeholder="如: 主动性; 倡议" />
        </el-form-item>
        <el-form-item label="音标">
          <el-input v-model="form.phonetic" placeholder="如: /ɪˈnɪʃətɪv/" />
        </el-form-item>
        <el-form-item label="词性">
          <el-select v-model="form.partOfSpeech" placeholder="选择" clearable style="width: 100%">
            <el-option label="名词 noun" value="noun" />
            <el-option label="动词 verb" value="verb" />
            <el-option label="形容词 adjective" value="adjective" />
            <el-option label="副词 adverb" value="adverb" />
            <el-option label="介词 preposition" value="preposition" />
            <el-option label="短语 phrase" value="phrase" />
          </el-select>
        </el-form-item>
        <el-form-item label="类别">
          <el-select v-model="form.category" placeholder="选择或输入" allow-create clearable style="width: 100%">
            <el-option
              v-for="c in categoryOptions"
              :key="c"
              :label="c"
              :value="c"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="难度">
          <el-rate v-model="form.difficulty" :max="5" />
        </el-form-item>
        <el-form-item label="例句">
          <el-input v-model="form.exampleSentence" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="例句翻译">
          <el-input v-model="form.exampleTranslation" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditDialog = false">取消</el-button>
        <el-button type="primary" @click="saveForm" :loading="saving">保存</el-button>
      </template>
    </el-dialog>

    <!-- AI 批量生成弹窗 -->
    <el-dialog
      v-model="showGenerateDialog"
      title="🤖 AI 批量生成单词"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form label-width="100px">
        <el-form-item label="生成数量" required>
          <el-input-number
            v-model="genForm.count"
            :min="1"
            :max="50"
            :step="5"
            style="width: 120px"
          />
        </el-form-item>
        <el-form-item label="类别">
          <el-select v-model="genForm.category" placeholder="留空=自动选" clearable style="width: 100%">
            <el-option label="雅思 5 分" value="IELTS5" />
            <el-option label="雅思 6 分" value="IELTS6" />
            <el-option label="雅思 7 分+" value="IELTS7" />
            <el-option label="日常口语" value="日常" />
            <el-option label="商务英语" value="商务" />
            <el-option label="CET-4" value="CET4" />
            <el-option label="CET-6" value="CET6" />
          </el-select>
        </el-form-item>
        <el-form-item label="难度">
          <el-rate v-model="genForm.difficulty" :max="5" />
        </el-form-item>
        <el-form-item label="主题/场景">
          <el-input
            v-model="genForm.topic"
            placeholder="如: 环保话题 / 面试场景 / 旅行"
            clearable
          />
        </el-form-item>
      </el-form>
      <el-alert
        v-if="genResult"
        :title="
          genResult.error
            ? '生成失败: ' + genResult.error
            : `✅ 生成完成: 新增 ${genResult.created} 个, 跳过已存在 ${genResult.skipped} 个`
        "
        :type="genResult.error ? 'error' : 'success'"
        show-icon
        class="mb-3"
      />
      <template #footer>
        <el-button @click="showGenerateDialog = false">关闭</el-button>
        <el-button type="primary" :loading="generating" @click="doGenerate">
          开始生成
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { wordApi, type WordItem, type AiGenerateResult } from '../../api/admin'
import { saveBlobResponse } from '../../utils/download'

const loading = ref(false)
const saving = ref(false)
const generating = ref(false)
const records = ref<WordItem[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const selection = ref<WordItem[]>([])
const categoryOptions = ref<string[]>([])

const filters = reactive({ keyword: '', category: '' })
const showEditDialog = ref(false)
const editing = ref<WordItem | null>(null)
const form = reactive<Partial<WordItem>>({})

const showGenerateDialog = ref(false)
const genForm = reactive({
  count: 10,
  category: '',
  difficulty: undefined as number | undefined,
  topic: ''
})
const genResult = ref<AiGenerateResult | null>(null)

function loadData() {
  loading.value = true
  wordApi
    .page({
      keyword: filters.keyword,
      category: filters.category,
      pageNum: pageNum.value,
      pageSize: pageSize.value
    })
    .then((res: any) => {
      records.value = res.data?.records ?? res.data?.rows ?? []
      total.value = res.data?.total ?? 0
    })
    .finally(() => (loading.value = false))
}

function resetFilter() {
  filters.keyword = ''
  filters.category = ''
  pageNum.value = 1
  loadData()
}

function loadCategories() {
  wordApi.categories().then((res: any) => {
    categoryOptions.value = res.data ?? []
  })
}

function openCreateDialog() {
  editing.value = null
  Object.assign(form, {
    english: '',
    chinese: '',
    phonetic: '',
    partOfSpeech: '',
    category: '',
    difficulty: 0,
    exampleSentence: '',
    exampleTranslation: '',
    isPublished: 1,
    source: 'UPLOAD'
  })
  showEditDialog.value = true
}

function openEditDialog(row: WordItem) {
  editing.value = row
  Object.assign(form, row)
  showEditDialog.value = true
}

async function saveForm() {
  if (!form.english?.trim() || !form.chinese?.trim()) {
    ElMessage.warning('英文和中文释义必填')
    return
  }
  saving.value = true
  try {
    if (editing.value?.id) {
      await wordApi.update(editing.value.id, form as WordItem)
      ElMessage.success('更新成功')
    } else {
      await wordApi.create({ ...form, english: form.english!.trim().toLowerCase() } as WordItem)
      ElMessage.success('新增成功')
    }
    showEditDialog.value = false
    loadData()
    loadCategories()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: WordItem) {
  await ElMessageBox.confirm(`确定删除 "${row.english}"?`, '提示', { type: 'warning' })
  try {
    await wordApi.remove(row.id!)
    ElMessage.success('已删除')
    loadData()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '删除失败')
  }
}

async function batchDelete() {
  if (selection.value.length === 0) return
  await ElMessageBox.confirm(`确定删除选中的 ${selection.value.length} 条?`, '提示', { type: 'warning' })
  try {
    await wordApi.batchDelete(selection.value.map((w) => w.id!))
    ElMessage.success('已批量删除')
    loadData()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '批量删除失败')
  }
}

async function togglePublish(row: WordItem, val: boolean) {
  try {
    await wordApi.togglePublish(row.id!, val ? 1 : 0)
    row.isPublished = val ? 1 : 0
    ElMessage.success(val ? '已发布' : '已下架')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  }
}

async function doGenerate() {
  genResult.value = null
  generating.value = true
  try {
    const res: any = await wordApi.aiGenerate({ ...genForm })
    genResult.value = res.data
    loadData()
    loadCategories()
  } catch (e: any) {
    genResult.value = { created: 0, skipped: 0, error: e?.response?.data?.message || '生成失败' }
  } finally {
    generating.value = false
  }
}

async function handleExport() {
  try {
    const res: any = await wordApi.exportCsv({
      keyword: filters.keyword,
      category: filters.category
    })
    saveBlobResponse(res, `单词列表_${Date.now()}.csv`)
  } catch (e: any) {
    ElMessage.error('导出失败')
  }
}

onMounted(() => {
  loadData()
  loadCategories()
})
</script>

<style scoped>
.word-management { padding: 4px; }
.toolbar {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 16px; flex-wrap: wrap; gap: 10px;
}
.filter { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.actions { display: flex; gap: 8px; flex-wrap: wrap; }
.word-en { font-weight: 600; color: #303133; }
.phonetic { font-family: "Noto Sans Phonetic", "DejaVu Sans", serif; color: #606266; }
.ml-2 { margin-left: 8px; }
.mt-3 { margin-top: 12px; }
.mb-3 { margin-bottom: 12px; }
</style>
