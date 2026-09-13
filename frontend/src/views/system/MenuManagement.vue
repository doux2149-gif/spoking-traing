<template>
  <div class="menu-management">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>菜单管理</span>
          <el-button type="primary" @click="openCreateDialog(null)">新增目录</el-button>
        </div>
      </template>

      <el-table
        :data="menuTree"
        v-loading="loading"
        row-key="id"
        border
        stripe
        default-expand-all
        :tree-props="{ children: 'children' }"
      >
        <el-table-column prop="menuName" label="菜单名称" min-width="200" />
        <el-table-column label="图标" width="90">
          <template #default="{ row }">
            <span class="text-muted">{{ row.icon || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="80">
          <template #default="{ row }">
            <el-tag :type="row.menuType === 'M' ? 'primary' : 'success'">
              {{ row.menuType === 'M' ? '目录' : '菜单' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="path" label="路由路径" min-width="140" />
        <el-table-column prop="component" label="组件" min-width="160">
          <template #default="{ row }">{{ row.component || '-' }}</template>
        </el-table-column>
        <el-table-column prop="perms" label="权限标识" min-width="150">
          <template #default="{ row }">{{ row.perms || '-' }}</template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="70" />
        <el-table-column label="可见" width="70">
          <template #default="{ row }">
            <el-tag :type="row.visible === 1 ? 'success' : 'info'">{{ row.visible === 1 ? '显示' : '隐藏' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="内置" width="70">
          <template #default="{ row }">
            <el-tag v-if="row.isInternal === 1" type="warning">是</el-tag>
            <span v-else class="text-muted">否</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openCreateDialog(row)">新增子项</el-button>
            <el-button link type="primary" size="small" @click="openEditDialog(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-form-item label="上级菜单">
          <el-tree-select
            v-model="form.parentId"
            :data="parentOptions"
            :props="{ label: 'menuName', children: 'children', value: 'id' }"
            node-key="id"
            check-strictly
            default-expand-all
            placeholder="请选择上级菜单"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="菜单名称" prop="menuName">
          <el-input v-model="form.menuName" placeholder="请输入菜单名称" />
        </el-form-item>
        <el-form-item label="类型" prop="menuType">
          <el-radio-group v-model="form.menuType">
            <el-radio value="M">目录</el-radio>
            <el-radio value="C">菜单</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="路由路径" prop="path">
          <el-input v-model="form.path" placeholder="目录如 /system；子菜单如 user" />
        </el-form-item>
        <el-form-item label="组件">
          <el-input v-model="form.component" placeholder="如 system/UserManagement，目录留空" />
        </el-form-item>
        <el-form-item label="权限标识">
          <el-input v-model="form.perms" placeholder="如 system:user:list，可留空" />
        </el-form-item>
        <el-form-item label="图标">
          <el-input v-model="form.icon" placeholder="Element Plus 图标名，如 Setting，可留空" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" />
        </el-form-item>
        <el-form-item label="显示">
          <el-switch v-model="form.visible" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="内置菜单">
          <el-switch v-model="form.isInternal" :active-value="1" :inactive-value="0" />
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
import { menuApi, type SysMenu } from '../../api/admin'

const loading = ref(false)
const submitting = ref(false)
const flatMenus = ref<SysMenu[]>([])
const menuTree = ref<SysMenu[]>([])

const dialogVisible = ref(false)
const dialogType = ref<'create' | 'edit'>('create')
const dialogTitle = computed(() => dialogType.value === 'create' ? '新增菜单' : '编辑菜单')
const formRef = ref<FormInstance>()

interface MenuForm {
  id?: number
  parentId: number
  menuName: string
  menuType: string
  path: string
  component: string
  perms: string
  icon: string
  sort: number
  visible: number
  isInternal: number
}

const defaultForm = (): MenuForm => ({
  id: undefined,
  parentId: 0,
  menuName: '',
  menuType: 'C',
  path: '',
  component: '',
  perms: '',
  icon: '',
  sort: 0,
  visible: 1,
  isInternal: 0
})

const form = reactive<MenuForm>(defaultForm())

const rules: FormRules = {
  menuName: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  menuType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  path: [{ required: true, message: '请输入路由路径', trigger: 'blur' }]
}

/** 平铺列表 -> 树 */
function buildTree(list: SysMenu[]): SysMenu[] {
  const map = new Map<number, SysMenu>()
  list.forEach(m => map.set(m.id!, { ...m, children: [] }))
  const roots: SysMenu[] = []
  map.forEach(node => {
    if (node.parentId && map.has(node.parentId)) {
      map.get(node.parentId)!.children!.push(node)
    } else {
      roots.push(node)
    }
  })
  // 清空叶子 children, 避免 el-table 出现空展开箭头
  const clean = (nodes: SysMenu[]) => {
    nodes.forEach(n => {
      if (n.children && n.children.length === 0) {
        n.children = undefined
      } else if (n.children) {
        clean(n.children)
      }
    })
  }
  clean(roots)
  return roots
}

/** 上级菜单选项: 始终含"根目录 0"; 编辑时排除自身及其子孙 */
const parentOptions = computed<SysMenu[]>(() => {
  const virtualRoot: SysMenu = { id: 0, parentId: -1, menuName: '根目录', path: '', menuType: 'M', sort: 0, children: [] }
  if (dialogType.value === 'edit' && form.id) {
    const excluded = collectSubtreeIds(form.id)
    const allowed = flatMenus.value.filter(m => !excluded.has(m.id!))
    virtualRoot.children = buildTree(allowed)
  } else {
    virtualRoot.children = buildTree(flatMenus.value)
  }
  return [virtualRoot]
})

/** 收集某节点自身及全部子孙 id */
function collectSubtreeIds(rootId: number): Set<number> {
  const result = new Set<number>([rootId])
  let changed = true
  while (changed) {
    changed = false
    flatMenus.value.forEach(m => {
      if (m.parentId && result.has(m.parentId) && !result.has(m.id!)) {
        result.add(m.id!)
        changed = true
      }
    })
  }
  return result
}

async function loadMenus() {
  loading.value = true
  try {
    const res = await menuApi.list()
    flatMenus.value = res.data || []
    menuTree.value = buildTree(flatMenus.value)
  } finally {
    loading.value = false
  }
}

/** 新增: parent=null 建顶级目录, 否则在该节点下建子项 */
function openCreateDialog(parent: SysMenu | null) {
  dialogType.value = 'create'
  Object.assign(form, defaultForm())
  if (parent) {
    form.parentId = parent.id!
    form.menuType = parent.menuType === 'M' ? 'C' : 'C'
  }
  dialogVisible.value = true
}

function openEditDialog(row: SysMenu) {
  dialogType.value = 'edit'
  Object.assign(form, {
    ...defaultForm(),
    id: row.id,
    parentId: row.parentId ?? 0,
    menuName: row.menuName,
    menuType: row.menuType,
    path: row.path,
    component: row.component || '',
    perms: row.perms || '',
    icon: row.icon || '',
    sort: row.sort ?? 0,
    visible: row.visible ?? 1,
    isInternal: row.isInternal ?? 0
  })
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      const payload: SysMenu = {
        parentId: form.parentId,
        menuName: form.menuName,
        menuType: form.menuType,
        path: form.path,
        component: form.menuType === 'C' ? form.component : null,
        perms: form.perms || null,
        icon: form.icon || null,
        sort: form.sort,
        visible: form.visible,
        isInternal: form.isInternal
      }
      if (dialogType.value === 'create') {
        await menuApi.create(payload)
        ElMessage.success('创建成功')
      } else {
        await menuApi.update(form.id!, payload)
        ElMessage.success('更新成功')
      }
      dialogVisible.value = false
      loadMenus()
    } finally {
      submitting.value = false
    }
  })
}

function handleDelete(row: SysMenu) {
  ElMessageBox.confirm(`确定删除菜单「${row.menuName}」吗？存在子菜单时无法删除。`, '删除确认', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await menuApi.remove(row.id!)
    ElMessage.success('删除成功')
    loadMenus()
  }).catch(() => {})
}

onMounted(loadMenus)
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.text-muted {
  color: #909399;
}
</style>
