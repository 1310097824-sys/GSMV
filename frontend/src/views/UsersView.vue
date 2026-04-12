<template>
  <div class="page-shell">
    <section class="page-hero">
      <div>
        <h2>用户与权限</h2>
        <p>管理系统用户、审核注册申请、角色分配与账号启停，支撑基于角色的访问控制。</p>
      </div>
      <el-button type="primary" @click="openCreate">新增用户</el-button>
    </section>

    <el-card class="panel-card" shadow="never">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="用户名 / 邮箱 / 显示名称" clearable style="max-width: 240px" />
        <el-select v-model="query.status" placeholder="账号状态" clearable style="width: 150px">
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
        <el-select v-model="query.approvalStatus" placeholder="审核状态" clearable style="width: 160px">
          <el-option label="待审核" value="PENDING" />
          <el-option label="已通过" value="APPROVED" />
          <el-option label="未通过" value="REJECTED" />
        </el-select>
        <el-button type="primary" @click="handleSearch">查询</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column label="头像" width="84">
          <template #default="{ row }">
            <el-avatar :size="36" :src="row.avatarUrl">{{ row.displayName?.slice(0, 1) || 'U' }}</el-avatar>
          </template>
        </el-table-column>
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="displayName" label="显示名称" min-width="140" />
        <el-table-column prop="email" label="邮箱" min-width="180" />
        <el-table-column label="角色" min-width="220">
          <template #default="{ row }">
            <el-space wrap>
              <el-tag v-for="role in row.roles" :key="role.id">{{ role.name }}</el-tag>
            </el-space>
          </template>
        </el-table-column>
        <el-table-column label="审核状态" min-width="130">
          <template #default="{ row }">
            <el-tag :type="approvalType(row.approvalStatus)">
              {{ approvalLabel(row.approvalStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="账号状态" min-width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastLoginAt" label="最近登录" min-width="180" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-space>
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button v-if="row.approvalStatus === 'PENDING'" link type="success" @click="approve(row.id)">通过</el-button>
              <el-button v-if="row.approvalStatus === 'PENDING'" link type="danger" @click="reject(row.id)">驳回</el-button>
            </el-space>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-footer">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          layout="total, prev, pager, next"
          :total="pagination.total"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑用户' : '新增用户'" width="720px">
      <el-form label-position="top">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item v-if="!editingId" label="用户名">
              <el-input v-model="form.username" />
            </el-form-item>
            <el-form-item v-else label="用户名">
              <el-input :model-value="editingName" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="editingId ? '重置密码（可选）' : '初始密码'">
              <el-input v-model="form.password" type="password" show-password />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="显示名称">
              <el-input v-model="form.displayName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="邮箱">
              <el-input v-model="form.email" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="手机号">
              <el-input v-model="form.phone" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="账号状态">
              <el-radio-group v-model="form.status">
                <el-radio :value="1">启用</el-radio>
                <el-radio :value="0">禁用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="角色">
          <el-select v-model="form.roleIds" multiple style="width: 100%">
            <el-option v-for="role in roles" :key="role.id" :label="role.name" :value="role.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createUser, fetchRoles, fetchUsers, reviewUser, updateUser } from '@/api/users'
import { listenDataChanged, notifyDataChanged } from '@/utils/dataSync'
import type { RoleOption, UserView } from '@/types/gsmv'

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const editingName = ref('')
const rows = ref<UserView[]>([])
const roles = ref<RoleOption[]>([])
let stopDataSync: (() => void) | undefined
let refreshTimer: number | undefined

const query = reactive({
  keyword: '',
  status: undefined as number | undefined,
  approvalStatus: '' as '' | 'PENDING' | 'APPROVED' | 'REJECTED',
})

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0,
})

const form = reactive({
  username: '',
  password: '',
  displayName: '',
  email: '',
  phone: '',
  status: 1,
  roleIds: [] as number[],
})

function approvalLabel(value: string) {
  switch (value) {
    case 'APPROVED':
      return '已通过'
    case 'REJECTED':
      return '未通过'
    default:
      return '待审核'
  }
}

function approvalType(value: string) {
  switch (value) {
    case 'APPROVED':
      return 'success'
    case 'REJECTED':
      return 'danger'
    default:
      return 'warning'
  }
}

function normalizeStatus(value: number | string | undefined | null) {
  return Number(value) === 1 ? 1 : 0
}

function statusLabel(value: number | string | undefined | null) {
  return normalizeStatus(value) === 1 ? '启用' : '禁用'
}

function statusTagType(value: number | string | undefined | null) {
  return normalizeStatus(value) === 1 ? 'success' : 'danger'
}

function normalizeUserRow(row: UserView): UserView {
  return {
    ...row,
    status: normalizeStatus(row.status),
  }
}

function resetForm() {
  form.username = ''
  form.password = ''
  form.displayName = ''
  form.email = ''
  form.phone = ''
  form.status = 1
  form.roleIds = []
  editingName.value = ''
}

async function loadRoles() {
  roles.value = await fetchRoles()
}

async function loadData() {
  if (loading.value) {
    return
  }

  loading.value = true
  try {
    const pageData = await fetchUsers({
      keyword: query.keyword || undefined,
      status: query.status,
      approvalStatus: query.approvalStatus || undefined,
      page: pagination.page,
      size: pagination.size,
    })
    rows.value = pageData.items.map(normalizeUserRow)
    pagination.total = pageData.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '用户列表加载失败')
  } finally {
    loading.value = false
  }
}

function matchesFilters(row: UserView) {
  const keyword = query.keyword.trim().toLowerCase()
  const matchKeyword =
    !keyword ||
    [row.username, row.email || '', row.displayName].some((value) => value.toLowerCase().includes(keyword))
  const matchStatus = query.status == null || normalizeStatus(row.status) === query.status
  const matchApproval = !query.approvalStatus || row.approvalStatus === query.approvalStatus

  return matchKeyword && matchStatus && matchApproval
}

function syncRow(row: UserView) {
  const normalizedRow = normalizeUserRow(row)
  const index = rows.value.findIndex((item) => item.id === normalizedRow.id)
  if (index === -1) {
    return
  }

  if (matchesFilters(normalizedRow)) {
    rows.value.splice(index, 1, normalizedRow)
    return
  }

  rows.value.splice(index, 1)
  pagination.total = Math.max(0, pagination.total - 1)
}

function handleFocus() {
  void loadData()
}

function handleVisibilityChange() {
  if (!document.hidden) {
    void loadData()
  }
}

function handleSearch() {
  pagination.page = 1
  void loadData()
}

function openCreate() {
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: UserView) {
  editingId.value = row.id
  editingName.value = row.username
  form.username = row.username
  form.password = ''
  form.displayName = row.displayName
  form.email = row.email || ''
  form.phone = row.phone || ''
  form.status = normalizeStatus(row.status)
  form.roleIds = row.roles.map((role) => role.id)
  dialogVisible.value = true
}

async function submit() {
  if (!editingId.value && (!form.username.trim() || !form.password.trim())) {
    ElMessage.warning('请填写用户名和密码')
    return
  }
  if (!form.displayName.trim() || form.roleIds.length === 0) {
    ElMessage.warning('请填写显示名称并至少分配一个角色')
    return
  }

  submitting.value = true
  try {
    if (editingId.value) {
      const updated = await updateUser(editingId.value, {
        displayName: form.displayName.trim(),
        email: form.email.trim() || undefined,
        phone: form.phone.trim() || undefined,
        status: normalizeStatus(form.status),
        password: form.password || undefined,
        roleIds: form.roleIds,
      })
      syncRow(updated)
      ElMessage.success('用户已更新')
    } else {
      await createUser({
        username: form.username.trim(),
        password: form.password,
        displayName: form.displayName.trim(),
        email: form.email.trim() || undefined,
        phone: form.phone.trim() || undefined,
        status: normalizeStatus(form.status),
        roleIds: form.roleIds,
      })
      ElMessage.success('用户已创建')
    }
    notifyDataChanged('user')
    dialogVisible.value = false
    void loadData()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  } finally {
    submitting.value = false
  }
}

async function approve(id: number) {
  try {
    const updated = await reviewUser(id, { approvalStatus: 'APPROVED' })
    syncRow(updated)
    notifyDataChanged('user')
    ElMessage.success('已审核通过')
    void loadData()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '审核失败')
  }
}

async function reject(id: number) {
  try {
    const result = await ElMessageBox.prompt('可填写驳回原因（选填）', '驳回注册申请', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPlaceholder: '例如：资料不完整',
    })
    const updated = await reviewUser(id, {
      approvalStatus: 'REJECTED',
      approvalRemark: result.value || undefined,
    })
    syncRow(updated)
    notifyDataChanged('user')
    ElMessage.success('已驳回注册申请')
    void loadData()
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    ElMessage.error(error instanceof Error ? error.message : '驳回失败')
  }
}

onMounted(async () => {
  stopDataSync = listenDataChanged((detail) => {
    if (detail.type === 'user') {
      void loadData()
    }
  })
  window.addEventListener('focus', handleFocus)
  document.addEventListener('visibilitychange', handleVisibilityChange)
  refreshTimer = window.setInterval(() => {
    if (!document.hidden) {
      void loadData()
    }
  }, 5000)
  await Promise.all([loadRoles(), loadData()])
})

onBeforeUnmount(() => {
  stopDataSync?.()
  window.removeEventListener('focus', handleFocus)
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  if (refreshTimer) {
    window.clearInterval(refreshTimer)
  }
})
</script>

<style scoped>
.table-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}
</style>
