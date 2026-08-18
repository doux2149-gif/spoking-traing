<template>
  <div class="scene-management">
    <el-card class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="场景名称">
          <el-input v-model="searchForm.name" placeholder="请输入场景名称" clearable />
        </el-form-item>
        <el-form-item label="难度">
          <el-select v-model="searchForm.difficulty" placeholder="全部" clearable>
            <el-option label="入门" :value="1" />
            <el-option label="初级" :value="2" />
            <el-option label="中级" :value="3" />
            <el-option label="高级" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable>
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card">
      <template #header>
        <div class="card-header">
          <span>场景列表</span>
          <el-button type="primary" @click="openCreateDialog">新增场景</el-button>
        </div>
      </template>

      <el-table :data="filteredList" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="name" label="场景名称" width="120" />
        <el-table-column prop="aiRole" label="AI角色" width="120" />
        <el-table-column prop="difficulty" label="难度" width="80">
          <template #default="{ row }">
            <el-tag :type="difficultyType(row.difficulty)" size="small">
              {{ difficultyLabel(row.difficulty) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="openingLine" label="开场白" show-overflow-tooltip min-width="200" />
        <el-table-column prop="sort" label="排序" width="60" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="openEditDialog(row)">编辑</el-button>
            <el-button type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="700px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="场景名称" prop="name">
          <el-input v-model="form.name" placeholder="如:餐厅点餐" />
        </el-form-item>
        <el-form-item label="场景描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="场景的简要描述" />
        </el-form-item>
        <el-form-item label="AI角色" prop="aiRole">
          <el-input v-model="form.aiRole" placeholder="如:餐厅服务员" />
        </el-form-item>
        <el-form-item label="难度等级" prop="difficulty">
          <el-select v-model="form.difficulty" style="width: 100%">
            <el-option label="入门" :value="1" />
            <el-option label="初级" :value="2" />
            <el-option label="中级" :value="3" />
            <el-option label="高级" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="AI开场白" prop="openingLine">
          <el-input v-model="form.openingLine" type="textarea" :rows="2" placeholder="AI的第一句话(英文)" />
        </el-form-item>
        <el-form-item label="场景指令" prop="systemPrompt">
          <el-input v-model="form.systemPrompt" type="textarea" :rows="4" placeholder="注入给LLM的场景设定指令(中文)" />
        </el-form-item>
        <el-form-item label="图标" prop="icon">
          <el-input v-model="form.icon" placeholder="Element Plus 图标名,如:Food" />
        </el-form-item>
        <el-form-item label="排序" prop="sort">
          <el-input-number v-model="form.sort" :min="0" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import request from '../../api/request'

const loading = ref(false)
const submitting = ref(false)
const list = ref<any[]>([])

const searchForm = reactive({
  name: '',
  difficulty: undefined as number | undefined,
  status: undefined as number | undefined
})

const filteredList = computed(() => {
  return list.value.filter(item => {
    if (searchForm.name && !item.name.includes(searchForm.name)) return false
    if (searchForm.difficulty !== undefined && item.difficulty !== searchForm.difficulty) return false
    if (searchForm.status !== undefined && item.status !== searchForm.status) return false
    return true
  })
})

const dialogVisible = ref(false)
const dialogType = ref<'create' | 'edit'>('create')
const dialogTitle = computed(() => (dialogType.value === 'create' ? '新增场景' : '编辑场景'))
const formRef = ref<FormInstance>()

const defaultForm = () => ({
  id: undefined as number | undefined,
  name: '',
  description: '',
  aiRole: '',
  difficulty: 1,
  openingLine: '',
  systemPrompt: '',
  icon: '',
  sort: 0,
  status: 1
})
const form = reactive(defaultForm())

const rules: FormRules = {
  name: [{ required: true, message: '请输入场景名称', trigger: 'blur' }],
  aiRole: [{ required: true, message: '请输入AI角色', trigger: 'blur' }],
  openingLine: [{ required: true, message: '请输入AI开场白', trigger: 'blur' }],
  systemPrompt: [{ required: true, message: '请输入场景指令', trigger: 'blur' }]
}

const difficultyLabel = (d: number) => ['', '入门', '初级', '中级', '高级'][d] || '入门'
const difficultyType = (d: number): any => ['', 'success', 'info', 'warning', 'danger'][d] || 'success'

const loadList = async () => {
  loading.value = true
  try {
    const res = await request.get('/system/scenes')
    if (res.data) {
      list.value = res.data
    }
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {}
const handleReset = () => {
  searchForm.name = ''
  searchForm.difficulty = undefined
  searchForm.status = undefined
}

const openCreateDialog = () => {
  dialogType.value = 'create'
  Object.assign(form, defaultForm())
  dialogVisible.value = true
}

const openEditDialog = (row: any) => {
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
        await request.post('/system/scenes', form)
        ElMessage.success('创建成功')
      } else {
        await request.put(`/system/scenes/${form.id}`, form)
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

const handleDelete = (row: any) => {
  ElMessageBox.confirm(`确定要删除场景 "${row.name}" 吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await request.delete(`/system/scenes/${row.id}`)
      ElMessage.success('删除成功')
      loadList()
    } catch (e) {
      console.error(e)
    }
  }).catch(() => {})
}

onMounted(() => {
  loadList()
})
</script>

<style scoped>
.scene-management {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
