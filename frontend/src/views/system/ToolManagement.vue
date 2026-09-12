<template>
  <div class="tool-management">
    <!-- 顶部说明 -->
    <el-alert
      title="LLM 工具动态注册"
      type="info"
      :closable="false"
      show-icon
    >
      <template #default>
        代码工具(Spring Bean, 如 get_current_datetime)由后端自动注册, 不可在此修改;
        HTTP 工具可在此增删改查, 保存后即时生效, 无需重启服务。
      </template>
    </el-alert>

    <!-- 搜索 + 新增 -->
    <el-card class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="关键词">
          <el-input
            v-model="searchForm.keyword"
            placeholder="工具名 / 描述"
            clearable
            style="width: 220px"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.enabled" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadList">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card class="table-card">
      <template #header>
        <div class="card-header">
          <span>工具列表</span>
          <el-button type="primary" @click="openCreateDialog">新增 HTTP 工具</el-button>
        </div>
      </template>

      <el-table :data="filteredList" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="toolName" label="工具名" width="200" />
        <el-table-column prop="description" label="描述" show-overflow-tooltip min-width="240" />
        <el-table-column prop="toolType" label="类型" width="90">
          <template #default="{ row }">
            <el-tag :type="row.toolType === 'HTTP' ? 'success' : 'info'" size="small">
              {{ row.toolType || 'HTTP' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="httpEndpoint" label="Endpoint" show-overflow-tooltip min-width="200">
          <template #default="{ row }">
            {{ row.httpEndpoint || '—' }}
          </template>
        </el-table-column>
        <el-table-column prop="enabled" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'danger'" size="small">
              {{ row.enabled === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="70" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button
              size="small"
              type="primary"
              :disabled="row.toolType !== 'HTTP'"
              @click="openTestDialog(row)"
            >试运行</el-button>
            <el-button
              size="small"
              :type="row.enabled === 1 ? 'warning' : 'success'"
              @click="handleToggle(row)"
            >{{ row.enabled === 1 ? '禁用' : '启用' }}</el-button>
            <el-button size="small" @click="openEditDialog(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="780px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="工具名" prop="toolName">
          <el-input v-model="form.toolName" placeholder="小写下划线, 如 query_weather" />
        </el-form-item>
        <el-form-item label="工具描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="描述工具用途, 以及模型在什么场景应该调用此工具(描述越准确, 触发越可靠)"
          />
        </el-form-item>
        <el-form-item label="类型" prop="toolType">
          <el-select v-model="form.toolType" style="width: 200px">
            <el-option label="HTTP" value="HTTP" />
            <el-option label="SCRIPT" value="SCRIPT" disabled />
          </el-select>
        </el-form-item>
        <el-form-item label="HTTP Endpoint" prop="httpEndpoint" v-if="form.toolType === 'HTTP'">
          <el-input
            v-model="form.httpEndpoint"
            placeholder="如 https://api.example.com/weather (完整 URL)"
          />
        </el-form-item>
        <el-form-item label="HTTP Method" prop="httpMethod" v-if="form.toolType === 'HTTP'">
          <el-select v-model="form.httpMethod" style="width: 120px">
            <el-option label="POST" value="POST" />
            <el-option label="GET" value="GET" />
            <el-option label="PUT" value="PUT" />
          </el-select>
        </el-form-item>
        <el-form-item label="请求头" prop="httpHeaders" v-if="form.toolType === 'HTTP'">
          <el-input
            v-model="form.httpHeaders"
            type="textarea"
            :rows="2"
            placeholder='JSON 对象, 如 {"Authorization":"Bearer xxx"} (可空)'
          />
        </el-form-item>
        <el-form-item label="超时(ms)" prop="timeoutMs" v-if="form.toolType === 'HTTP'">
          <el-input-number v-model="form.timeoutMs" :min="500" :max="60000" :step="500" />
        </el-form-item>
        <el-form-item label="参数 Schema" prop="parametersSchema">
          <el-input
            v-model="form.parametersSchema"
            type="textarea"
            :rows="6"
            placeholder='JSON Schema, 如 {"type":"object","properties":{"city":{"type":"string","description":"城市名"}},"required":["city"]}'
          />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" placeholder="可选" />
        </el-form-item>
        <el-form-item label="状态" prop="enabled">
          <el-radio-group v-model="form.enabled">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 试运行对话框 -->
    <el-dialog v-model="testDialogVisible" title="工具试运行" width="720px">
      <div class="test-row">
        <span class="test-label">工具:</span>
        <el-tag>{{ testForm.toolName }}</el-tag>
      </div>
      <div class="test-row">
        <span class="test-label">参数(JSON):</span>
      </div>
      <el-input
        v-model="testForm.arguments"
        type="textarea"
        :rows="5"
        placeholder='如 {"city":"Beijing"} (无参数工具可留空)'
      />
      <div class="test-row" style="margin-top: 12px">
        <el-button type="primary" :loading="testing" @click="runTest">执行</el-button>
      </div>
      <div class="test-row" style="margin-top: 12px" v-if="testForm.result !== null">
        <span class="test-label">返回结果:</span>
      </div>
      <el-input
        v-model="testForm.result"
        type="textarea"
        :rows="6"
        readonly
        v-if="testForm.result !== null"
      />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import request from '../../api/request'

interface ToolRow {
  id: number
  toolName: string
  description: string
  parametersSchema: string | null
  toolType: string
  httpEndpoint: string | null
  httpMethod: string
  httpHeaders: string | null
  timeoutMs: number
  enabled: number
  version: number
  remark: string | null
}

const loading = ref(false)
const submitting = ref(false)
const list = ref<ToolRow[]>([])

const searchForm = reactive({
  keyword: '',
  enabled: undefined as number | undefined
})

const filteredList = computed(() => {
  return list.value.filter(item => {
    if (searchForm.keyword) {
      const kw = searchForm.keyword.toLowerCase()
      if (!item.toolName.toLowerCase().includes(kw) &&
          !item.description.toLowerCase().includes(kw)) return false
    }
    if (searchForm.enabled !== undefined && item.enabled !== searchForm.enabled) return false
    return true
  })
})

// 新增/编辑
const dialogVisible = ref(false)
const dialogType = ref<'create' | 'edit'>('create')
const dialogTitle = computed(() => (dialogType.value === 'create' ? '新增工具' : '编辑工具'))
const formRef = ref<FormInstance>()

const defaultForm = () => ({
  id: undefined as number | undefined,
  toolName: '',
  description: '',
  parametersSchema: '',
  toolType: 'HTTP',
  httpEndpoint: '',
  httpMethod: 'POST',
  httpHeaders: '',
  timeoutMs: 5000,
  enabled: 1,
  version: 0,
  remark: ''
})
const form = reactive(defaultForm())

const rules: FormRules = {
  toolName: [
    { required: true, message: '请输入工具名', trigger: 'blur' },
    { pattern: /^[a-z][a-z0-9_]*$/, message: '只能小写字母/数字/下划线, 字母开头', trigger: 'blur' }
  ],
  description: [{ required: true, message: '请输入工具描述', trigger: 'blur' }],
  toolType: [{ required: true, message: '请选择类型', trigger: 'change' }]
}

// 试运行
const testDialogVisible = ref(false)
const testing = ref(false)
const testForm = reactive({
  toolName: '',
  arguments: '',
  result: null as string | null
})

const loadList = async () => {
  loading.value = true
  try {
    const res = await request.get('/system/llmtool', {
      params: {
        keyword: searchForm.keyword || undefined,
        enabled: searchForm.enabled
      }
    })
    if (res.data) {
      list.value = res.data
    }
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const resetSearch = () => {
  searchForm.keyword = ''
  searchForm.enabled = undefined
  loadList()
}

const openCreateDialog = () => {
  dialogType.value = 'create'
  Object.assign(form, defaultForm())
  dialogVisible.value = true
}

const openEditDialog = (row: ToolRow) => {
  dialogType.value = 'edit'
  Object.assign(form, { ...row })
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      if (dialogType.value === 'create') {
        await request.post('/system/llmtool', form)
        ElMessage.success('创建成功')
      } else {
        await request.put(`/system/llmtool/${form.id}`, form)
        ElMessage.success('更新成功')
      }
      dialogVisible.value = false
      loadList()
    } catch (e) {
      console.error(e)
    } finally {
      submitting.value = false
    }
  })
}

const handleDelete = (row: ToolRow) => {
  ElMessageBox.confirm(`确定要删除工具 "${row.toolName}" 吗?`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await request.delete(`/system/llmtool/${row.id}`)
      ElMessage.success('删除成功')
      loadList()
    } catch (e) {
      console.error(e)
    }
  }).catch(() => {})
}

const handleToggle = async (row: ToolRow) => {
  try {
    await request.put(`/system/llmtool/${row.id}/toggle`)
    ElMessage.success(row.enabled === 1 ? '已禁用' : '已启用')
    loadList()
  } catch (e) {
    console.error(e)
  }
}

const openTestDialog = (row: ToolRow) => {
  testForm.toolName = row.toolName
  testForm.arguments = ''
  testForm.result = null
  testDialogVisible.value = true
}

const runTest = async () => {
  testing.value = true
  testForm.result = null
  try {
    const payload: Record<string, unknown> = {}
    if (testForm.arguments && testForm.arguments.trim()) {
      payload.arguments = JSON.parse(testForm.arguments)
    }
    const res: any = await request.post(`/system/llmtool/${getCurrentTestId()}/test`, payload)
    testForm.result = res?.data?.result ?? '(空)'
  } catch (e: any) {
    testForm.result = '请求失败: ' + (e?.message || '未知错误')
  } finally {
    testing.value = false
  }
}

// 当前试运行目标 id(从列表中按 toolName 查)
const getCurrentTestId = () => {
  const row = list.value.find(r => r.toolName === testForm.toolName)
  return row?.id
}

onMounted(() => {
  loadList()
})
</script>

<style scoped>
.tool-management {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.test-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.test-label {
  font-weight: bold;
  color: #606266;
  min-width: 80px;
}
</style>
