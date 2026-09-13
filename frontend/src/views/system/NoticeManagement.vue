<template>
  <div class="notice-management">
    <el-card shadow="never">
      <!-- 筛选栏 -->
      <div class="filter-bar">
        <el-input v-model="query.title" placeholder="公告标题" clearable style="width: 200px" @keyup.enter="handleSearch" />
        <el-select v-model="query.noticeType" placeholder="类型" clearable style="width: 130px">
          <el-option label="通知" :value="1" />
          <el-option label="维护" :value="2" />
        </el-select>
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px">
          <el-option label="草稿" :value="0" />
          <el-option label="已发布" :value="1" />
          <el-option label="已停用" :value="2" />
        </el-select>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
        <div class="spacer" />
        <el-button type="primary" @click="openCreateDialog">新增公告</el-button>
      </div>

      <!-- 列表 -->
      <el-table :data="noticeList" v-loading="loading" border stripe class="notice-table">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag :type="row.noticeType === 2 ? 'warning' : ''">
              {{ row.noticeType === 2 ? '维护' : '通知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="展示" width="90">
          <template #default="{ row }">
            {{ row.displayType === 2 ? '弹窗' : '横幅' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="有效期" min-width="200">
          <template #default="{ row }">
            <span v-if="!row.publishStart && !row.publishEnd" class="text-muted">长期有效</span>
            <span v-else>
              {{ row.publishStart ? fmtTime(row.publishStart) : '立即' }}
              ~
              {{ row.publishEnd ? fmtTime(row.publishEnd) : '长期' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEditDialog(row)">编辑</el-button>
            <el-button
              v-if="row.status !== 1"
              link type="success" size="small"
              @click="handlePublish(row)"
            >发布</el-button>
            <el-button
              v-else
              link type="warning" size="small"
              @click="handleDisable(row)"
            >停用</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="loadList"
          @current-change="loadList"
        />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="200" show-word-limit placeholder="请输入公告标题" />
        </el-form-item>
        <el-form-item label="类型" prop="noticeType">
          <el-radio-group v-model="form.noticeType">
            <el-radio :value="1">通知</el-radio>
            <el-radio :value="2">维护</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="展示方式" prop="displayType">
          <el-radio-group v-model="form.displayType">
            <el-radio :value="1">横幅（页面顶部条）</el-radio>
            <el-radio :value="2">弹窗（登录后弹出）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="有效期" prop="timeRange">
          <el-date-picker
            v-model="form.timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间（空=立即）"
            end-placeholder="结束时间（空=长期）"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
          <div class="form-tip">不选表示长期有效；只改单侧时可清空另一侧</div>
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="5"
            placeholder="公告内容（纯文本，支持换行）"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="0">草稿</el-radio>
            <el-radio :value="1">发布</el-radio>
            <el-radio :value="2">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { noticeApi, type Notice } from '../../api/admin'

const loading = ref(false)
const submitting = ref(false)
const noticeList = ref<Notice[]>([])
const total = ref(0)

const query = reactive({
  title: '',
  noticeType: '' as number | '',
  status: '' as number | '',
  pageNum: 1,
  pageSize: 10
})

const dialogVisible = ref(false)
const dialogType = ref<'create' | 'edit'>('create')
const dialogTitle = computed(() => dialogType.value === 'create' ? '新增公告' : '编辑公告')
const formRef = ref<FormInstance>()

interface NoticeForm {
  id?: number
  title: string
  content: string
  noticeType: number
  displayType: number
  status: number
  timeRange: [string, string] | null
}

const defaultForm = (): NoticeForm => ({
  id: undefined,
  title: '',
  content: '',
  noticeType: 1,
  displayType: 1,
  status: 0,
  timeRange: null
})

const form = reactive<NoticeForm>(defaultForm())

const rules: FormRules = {
  title: [{ required: true, message: '请输入公告标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入公告内容', trigger: 'blur' }],
  noticeType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  displayType: [{ required: true, message: '请选择展示方式', trigger: 'change' }]
}

const fmtTime = (s?: string | null) => (s ? s.replace('T', ' ').slice(0, 19) : '')
const statusText = (s: number) => ['草稿', '已发布', '已停用'][s] || '未知'
const statusTag = (s: number) => (s === 1 ? 'success' : s === 2 ? 'info' : 'warning')

async function loadList() {
  loading.value = true
  try {
    const res = await noticeApi.list({
      title: query.title || undefined,
      noticeType: query.noticeType,
      status: query.status,
      pageNum: query.pageNum,
      pageSize: query.pageSize
    })
    noticeList.value = res.data?.rows || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.pageNum = 1
  loadList()
}

function handleReset() {
  query.title = ''
  query.noticeType = ''
  query.status = ''
  query.pageNum = 1
  loadList()
}

function openCreateDialog() {
  dialogType.value = 'create'
  Object.assign(form, defaultForm())
  dialogVisible.value = true
}

function openEditDialog(row: Notice) {
  dialogType.value = 'edit'
  Object.assign(form, {
    ...defaultForm(),
    id: row.id,
    title: row.title,
    content: row.content,
    noticeType: row.noticeType,
    displayType: row.displayType,
    status: row.status,
    timeRange: row.publishStart || row.publishEnd ? [row.publishStart || '', row.publishEnd || ''] : null
  })
  dialogVisible.value = true
}

function buildPayload(): Notice {
  const [start, end] = form.timeRange || [null, null]
  return {
    id: form.id,
    title: form.title,
    content: form.content,
    noticeType: form.noticeType,
    displayType: form.displayType,
    status: form.status,
    publishStart: start || null,
    publishEnd: end || null
  }
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    // 前端兜底时间窗校验
    if (form.timeRange && form.timeRange[0] && form.timeRange[1]
      && new Date(form.timeRange[0]) > new Date(form.timeRange[1])) {
      ElMessage.warning('生效开始时间不能晚于结束时间')
      return
    }
    submitting.value = true
    try {
      const payload = buildPayload()
      if (dialogType.value === 'create') {
        await noticeApi.create(payload)
        ElMessage.success('创建成功')
      } else {
        await noticeApi.update(form.id!, payload)
        ElMessage.success('更新成功')
      }
      dialogVisible.value = false
      loadList()
    } finally {
      submitting.value = false
    }
  })
}

function handlePublish(row: Notice) {
  ElMessageBox.confirm(`确定发布公告「${row.title}」吗？发布后用户端立即可见。`, '发布确认', {
    confirmButtonText: '发布',
    cancelButtonText: '取消',
    type: 'success'
  }).then(async () => {
    await noticeApi.publish(row.id!)
    ElMessage.success('已发布')
    loadList()
  }).catch(() => {})
}

function handleDisable(row: Notice) {
  ElMessageBox.confirm(`确定停用公告「${row.title}」吗？停用后用户端不再展示。`, '停用确认', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await noticeApi.disable(row.id!)
    ElMessage.success('已停用')
    loadList()
  }).catch(() => {})
}

function handleDelete(row: Notice) {
  ElMessageBox.confirm(`确定删除公告「${row.title}」吗？删除后不可恢复。`, '删除确认', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await noticeApi.remove(row.id!)
    ElMessage.success('删除成功')
    loadList()
  }).catch(() => {})
}

onMounted(loadList)
</script>

<style scoped>
.notice-management {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

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

.notice-table {
  width: 100%;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.text-muted {
  color: #909399;
}

.form-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
}
</style>
