<template>
  <div class="login-log">
    <el-card shadow="never">
      <!-- 筛选栏 -->
      <div class="filter-bar">
        <el-input v-model="query.username" placeholder="用户名" clearable style="width: 170px" @keyup.enter="handleSearch" />
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px">
          <el-option label="成功" :value="1" />
          <el-option label="失败" :value="0" />
        </el-select>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          style="width: 260px"
        />
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
        <div class="spacer" />
        <el-button type="success" :loading="exporting" @click="handleExport">导出CSV</el-button>
      </div>

      <el-table :data="logList" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" width="130" />
        <el-table-column prop="ip" label="IP" width="140" />
        <el-table-column label="浏览器标识" min-width="220">
          <template #default="{ row }">
            <el-tooltip v-if="row.userAgent" :content="row.userAgent" placement="top" :show-after="200">
              <span class="ua-cell">{{ row.userAgent }}</span>
            </el-tooltip>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="消息" min-width="120" show-overflow-tooltip />
        <el-table-column label="登录时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.loginTime) }}</template>
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { loginLogApi, type LoginLog, type LoginLogQuery } from '../../api/admin'
import { saveBlobResponse, timestampName } from '../../utils/download'

const loading = ref(false)
const exporting = ref(false)
const logList = ref<LoginLog[]>([])
const total = ref(0)
const dateRange = ref<[string, string] | null>(null)

const query = reactive({
  username: '',
  status: '' as number | '',
  pageNum: 1,
  pageSize: 10
})

const fmtTime = (s?: string | null) => (s ? s.replace('T', ' ').slice(0, 19) : '')

function buildParams(): LoginLogQuery {
  return {
    username: query.username || undefined,
    status: query.status,
    startDate: dateRange.value?.[0] || undefined,
    endDate: dateRange.value?.[1] || undefined,
    pageNum: query.pageNum,
    pageSize: query.pageSize
  }
}

async function loadList() {
  loading.value = true
  try {
    const res = await loginLogApi.list(buildParams())
    logList.value = res.data?.rows || []
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
  query.username = ''
  query.status = ''
  dateRange.value = null
  query.pageNum = 1
  loadList()
}

async function handleExport() {
  exporting.value = true
  try {
    const res = await loginLogApi.exportCsv(buildParams())
    saveBlobResponse(res, timestampName('login-logs'))
    ElMessage.success('导出成功')
  } finally {
    exporting.value = false
  }
}

onMounted(loadList)
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

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
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
</style>
