<template>
  <div class="page-shell">
    <section class="page-hero">
      <div>
        <h2>用户与权限</h2>
        <p>管理系统用户、审核注册申请、角色分配与账号启停，并通过路由权限控制不同角色可进入的工作区。</p>
      </div>
      <div class="page-hero__actions">
        <el-button v-if="activePanel === 'users'" type="primary" @click="openCreate">新增用户</el-button>
        <el-button v-else type="primary" @click="openCreateRole">新增角色</el-button>
      </div>
    </section>

    <el-tabs v-model="activePanel" class="permission-tabs">
      <el-tab-pane label="用户账号" name="users">
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
      </el-tab-pane>

      <el-tab-pane label="角色路由权限" name="roles">
        <el-card class="panel-card" shadow="never">
          <div class="role-toolbar">
            <div>
              <strong>角色列表</strong>
              <p>每个角色绑定一组权限码，前端按路由权限隐藏菜单，后端接口仍按同一权限码校验访问。</p>
            </div>
            <el-button type="primary" @click="openCreateRole">新增角色</el-button>
          </div>

          <el-table :data="roleRows" v-loading="roleLoading" stripe>
            <el-table-column label="角色" min-width="210">
              <template #default="{ row }">
                <div class="role-name-cell">
                  <strong>{{ row.name }}</strong>
                  <span class="role-code">{{ row.code }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="类型" width="110">
              <template #default="{ row }">
                <el-tag :type="row.builtIn ? 'warning' : 'success'">
                  {{ row.builtIn ? '内置' : '自定义' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="路由覆盖" min-width="280">
              <template #default="{ row }">
                <el-space wrap>
                  <el-tag v-for="label in routeLabelsFor(row.permissionCodes)" :key="label" type="success">
                    {{ label }}
                  </el-tag>
                  <el-tag v-if="routeLabelsFor(row.permissionCodes).length === 0" type="info">暂无路由</el-tag>
                </el-space>
              </template>
            </el-table-column>
            <el-table-column label="权限数" width="110">
              <template #default="{ row }">
                {{ row.permissionCodes.length }}
              </template>
            </el-table-column>
            <el-table-column label="使用用户" width="120">
              <template #default="{ row }">
                {{ row.userCount }}
              </template>
            </el-table-column>
            <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
            <el-table-column label="操作" width="170" fixed="right">
              <template #default="{ row }">
                <el-space>
                  <el-button link type="primary" @click="openEditRole(row)">配置</el-button>
                  <el-button
                    link
                    type="danger"
                    :disabled="row.builtIn || row.userCount > 0"
                    @click="removeRole(row)"
                  >
                    删除
                  </el-button>
                </el-space>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

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

    <el-dialog
      v-model="roleDialogVisible"
      :title="editingRoleId ? '配置角色路由权限' : '新增角色'"
      width="920px"
      class="role-dialog"
    >
      <el-form label-position="top">
        <el-row :gutter="16">
          <el-col :span="10">
            <el-form-item label="角色编码">
              <el-input
                v-model="roleForm.code"
                placeholder="例如 MARINE_EDITOR"
                :disabled="editingRoleBuiltIn"
                @input="normalizeRoleCodeInput"
              />
            </el-form-item>
          </el-col>
          <el-col :span="14">
            <el-form-item label="角色名称">
              <el-input v-model="roleForm.name" placeholder="例如 海洋资料编辑员" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="角色说明">
          <el-input v-model="roleForm.description" type="textarea" :rows="2" placeholder="说明该角色的职责范围" />
        </el-form-item>

        <div class="role-dialog-summary">
          <span>已选择 {{ roleForm.permissionCodes.length }} 项权限</span>
          <el-tag v-if="roleForm.code.trim().toUpperCase() === 'ADMIN'" type="warning">ADMIN 必须保留用户权限管理</el-tag>
        </div>

        <div class="permission-matrix" v-loading="roleLoading">
          <section v-for="group in permissionGroups" :key="group.key" class="permission-group">
            <div class="permission-group__head">
              <div>
                <strong>{{ group.title }}</strong>
                <span>{{ group.route }}</span>
                <p>{{ group.description }}</p>
              </div>
              <div class="permission-group__actions">
                <el-button size="small" @click="selectPermissionGroup(group)">全选</el-button>
                <el-button size="small" @click="clearPermissionGroup(group)">清空</el-button>
              </div>
            </div>

            <el-checkbox-group v-model="roleForm.permissionCodes" class="permission-checks">
              <el-checkbox
                v-for="permission in group.permissions"
                :key="permission.code"
                :value="permission.code"
                :disabled="isPermissionLocked(permission.code)"
              >
                <span class="permission-check">
                  <strong>{{ permission.name }}</strong>
                  <small>{{ permission.code }}</small>
                  <em>{{ permission.description || '暂无说明' }}</em>
                </span>
              </el-checkbox>
            </el-checkbox-group>
          </section>

          <el-empty v-if="permissionGroups.length === 0 && !roleLoading" description="暂无权限数据" />
        </div>
      </el-form>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="roleSubmitting" @click="submitRole">保存角色</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createUser, fetchRoles, fetchUsers, reviewUser, updateUser } from '@/api/users'
import {
  createRole,
  deleteRole as deleteRoleRequest,
  fetchPermissions,
  fetchRolePermissions,
  updateRole,
} from '@/api/roles'
import { listenDataChanged, notifyDataChanged } from '@/utils/dataSync'
import type { PermissionOption, RoleOption, RolePermissionView, UserView } from '@/types/gsmv'

interface BasePermissionGroup {
  key: string
  title: string
  route: string
  description: string
  codes: string[]
}

interface PermissionGroup {
  key: string
  title: string
  route: string
  description: string
  permissions: PermissionOption[]
}

const basePermissionGroups: BasePermissionGroup[] = [
  {
    key: 'dashboard',
    title: '仪表盘与报表',
    route: '/dashboard、/reports、/ai-reports',
    description: '查看统计概览、常规报表与 AI 科研报告。',
    codes: ['REPORT_READ'],
  },
  {
    key: 'species',
    title: '物种档案',
    route: '/species',
    description: '控制物种档案的访问、创建、编辑、图片和版本回滚。',
    codes: ['SPECIES_READ', 'SPECIES_WRITE'],
  },
  {
    key: 'ecosystem',
    title: '生态系统',
    route: '/ecosystems',
    description: '控制生态系统基础资料的查看与维护。',
    codes: ['ECOSYSTEM_READ', 'ECOSYSTEM_WRITE'],
  },
  {
    key: 'observation',
    title: '观测与地图',
    route: '/observations、/eco-map',
    description: '控制观测记录、地图点位和相关 AI 观测分析能力。',
    codes: ['OBS_READ', 'OBS_WRITE'],
  },
  {
    key: 'aiReview',
    title: 'AI 复核',
    route: '/ai-reviews',
    description: '控制 AI 识图复核工单的查看、领取和提交结论。',
    codes: ['AI_REVIEW_READ', 'AI_REVIEW_WRITE'],
  },
  {
    key: 'rag',
    title: 'RAG 知识中台',
    route: '/rag-knowledge',
    description: '控制知识库资料、分块、索引和检索结果。',
    codes: ['RAG_READ', 'RAG_MANAGE'],
  },
  {
    key: 'audit',
    title: '审计日志',
    route: '/audits',
    description: '查看系统操作审计和追踪记录。',
    codes: ['AUDIT_READ'],
  },
  {
    key: 'users',
    title: '用户与权限',
    route: '/users',
    description: '进入用户权限面板，维护用户、角色和权限。',
    codes: ['USER_ADMIN'],
  },
  {
    key: 'media',
    title: '附件资源',
    route: '媒体接口',
    description: '控制图片、附件元数据的读取与上传。',
    codes: ['MEDIA_READ', 'MEDIA_WRITE'],
  },
]

const activePanel = ref<'users' | 'roles'>('users')
const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const editingName = ref('')
const rows = ref<UserView[]>([])
const roles = ref<RoleOption[]>([])
const permissions = ref<PermissionOption[]>([])
const roleRows = ref<RolePermissionView[]>([])
const roleLoading = ref(false)
const roleSubmitting = ref(false)
const roleDialogVisible = ref(false)
const editingRoleId = ref<number | null>(null)
const editingRoleBuiltIn = ref(false)
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

const roleForm = reactive({
  code: '',
  name: '',
  description: '',
  permissionCodes: [] as string[],
})

const permissionLookup = computed(() => new Map(permissions.value.map((permission) => [permission.code, permission])))

const permissionGroups = computed<PermissionGroup[]>(() => {
  const groupedCodes = new Set<string>()
  const groups = basePermissionGroups
    .map((group) => {
      const groupPermissions = group.codes
        .map((code) => permissionLookup.value.get(code))
        .filter(isPermissionOption)
      groupPermissions.forEach((permission) => groupedCodes.add(permission.code))
      return {
        key: group.key,
        title: group.title,
        route: group.route,
        description: group.description,
        permissions: groupPermissions,
      }
    })
    .filter((group) => group.permissions.length > 0)

  const extraPermissions = permissions.value.filter((permission) => !groupedCodes.has(permission.code))
  if (extraPermissions.length === 0) {
    return groups
  }

  return [
    ...groups,
    {
      key: 'other',
      title: '其他接口权限',
      route: '未绑定到前端菜单',
      description: '这些权限仍参与后端接口校验，可按实际业务需要授权。',
      permissions: extraPermissions,
    },
  ]
})

function isPermissionOption(value: PermissionOption | undefined): value is PermissionOption {
  return Boolean(value)
}

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

function resetRoleForm() {
  roleForm.code = ''
  roleForm.name = ''
  roleForm.description = ''
  roleForm.permissionCodes = []
  editingRoleId.value = null
  editingRoleBuiltIn.value = false
}

async function loadRoles() {
  roles.value = await fetchRoles()
}

async function loadRolePanel() {
  if (roleLoading.value) {
    return
  }

  roleLoading.value = true
  try {
    const [permissionData, roleData] = await Promise.all([fetchPermissions(), fetchRolePermissions()])
    permissions.value = permissionData
    roleRows.value = roleData
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '角色权限加载失败')
  } finally {
    roleLoading.value = false
  }
}

async function ensureRolePanelLoaded() {
  if (permissions.value.length === 0 || roleRows.value.length === 0) {
    await loadRolePanel()
  }
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
  if (activePanel.value === 'roles') {
    void loadRolePanel()
  }
}

function handleVisibilityChange() {
  if (!document.hidden) {
    void loadData()
    if (activePanel.value === 'roles') {
      void loadRolePanel()
    }
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

async function openCreateRole() {
  activePanel.value = 'roles'
  await ensureRolePanelLoaded()
  resetRoleForm()
  roleDialogVisible.value = true
}

async function openEditRole(row: RolePermissionView) {
  await ensureRolePanelLoaded()
  editingRoleId.value = row.id
  editingRoleBuiltIn.value = row.builtIn
  roleForm.code = row.code
  roleForm.name = row.name
  roleForm.description = row.description || ''
  roleForm.permissionCodes = [...row.permissionCodes]
  roleDialogVisible.value = true
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

async function submitRole() {
  const code = roleForm.code.trim().toUpperCase()
  if (!code || !roleForm.name.trim()) {
    ElMessage.warning('请填写角色编码和角色名称')
    return
  }
  if (roleForm.permissionCodes.length === 0) {
    ElMessage.warning('请至少选择一个权限')
    return
  }

  roleSubmitting.value = true
  try {
    const payload = {
      code,
      name: roleForm.name.trim(),
      description: roleForm.description.trim() || undefined,
      permissionCodes: [...roleForm.permissionCodes],
    }

    if (editingRoleId.value) {
      await updateRole(editingRoleId.value, payload)
      ElMessage.success('角色权限已更新')
    } else {
      await createRole(payload)
      ElMessage.success('角色已创建')
    }

    roleDialogVisible.value = false
    notifyDataChanged('user')
    await Promise.all([loadRolePanel(), loadRoles(), loadData()])
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '角色保存失败')
  } finally {
    roleSubmitting.value = false
  }
}

async function removeRole(row: RolePermissionView) {
  if (row.builtIn || row.userCount > 0) {
    return
  }

  try {
    await ElMessageBox.confirm(`确认删除角色“${row.name}”？`, '删除角色', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await deleteRoleRequest(row.id)
    ElMessage.success('角色已删除')
    notifyDataChanged('user')
    await Promise.all([loadRolePanel(), loadRoles(), loadData()])
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    ElMessage.error(error instanceof Error ? error.message : '角色删除失败')
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

function normalizeRoleCodeInput(value: string) {
  roleForm.code = value.toUpperCase().replace(/[^A-Z0-9_]/g, '')
}

function selectPermissionGroup(group: PermissionGroup) {
  const nextCodes = new Set(roleForm.permissionCodes)
  group.permissions.forEach((permission) => nextCodes.add(permission.code))
  roleForm.permissionCodes = [...nextCodes]
}

function clearPermissionGroup(group: PermissionGroup) {
  const groupCodes = new Set(group.permissions.map((permission) => permission.code))
  roleForm.permissionCodes = roleForm.permissionCodes.filter(
    (code) => !groupCodes.has(code) || isPermissionLocked(code),
  )
}

function isPermissionLocked(code: string) {
  return roleForm.code.trim().toUpperCase() === 'ADMIN' && code === 'USER_ADMIN'
}

function routeLabelsFor(permissionCodes: string[]) {
  const codeSet = new Set(permissionCodes)
  return basePermissionGroups
    .filter((group) => group.codes.some((code) => codeSet.has(code)))
    .map((group) => group.title)
}

watch(activePanel, (panel) => {
  if (panel === 'roles') {
    void loadRolePanel()
  }
})

onMounted(async () => {
  stopDataSync = listenDataChanged((detail) => {
    if (detail.type === 'user') {
      void loadData()
      if (activePanel.value === 'roles') {
        void loadRolePanel()
      }
    }
  })
  window.addEventListener('focus', handleFocus)
  document.addEventListener('visibilitychange', handleVisibilityChange)
  refreshTimer = window.setInterval(() => {
    if (!document.hidden) {
      void loadData()
      if (activePanel.value === 'roles') {
        void loadRolePanel()
      }
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
.permission-tabs {
  --el-tabs-header-height: 48px;
}

.permission-tabs :deep(.el-tabs__nav-wrap::after) {
  background: rgba(150, 232, 255, 0.12);
}

.permission-tabs :deep(.el-tabs__item) {
  color: rgba(225, 246, 255, 0.76);
  font-weight: 700;
}

.permission-tabs :deep(.el-tabs__item.is-active) {
  color: var(--gsmv-text);
}

.permission-tabs :deep(.el-tabs__active-bar) {
  background: linear-gradient(90deg, var(--gsmv-primary), var(--gsmv-accent));
}

.table-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}

.role-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 18px;
}

.role-toolbar strong {
  display: block;
  margin-bottom: 6px;
  color: var(--gsmv-text);
  font-size: 20px;
}

.role-toolbar p {
  margin: 0;
  max-width: 760px;
  color: var(--gsmv-muted);
  line-height: 1.7;
}

.role-name-cell {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 6px;
}

.role-name-cell strong {
  color: var(--gsmv-text);
}

.role-code {
  width: fit-content;
  padding: 4px 9px;
  border-radius: 999px;
  border: 1px solid rgba(150, 232, 255, 0.14);
  background: rgba(255, 255, 255, 0.05);
  color: rgba(205, 240, 255, 0.86);
  font-family: "Cascadia Mono", "SFMono-Regular", Consolas, monospace;
  font-size: 12px;
}

.role-dialog-summary {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin: 4px 0 14px;
  color: var(--gsmv-muted);
}

.role-dialog-summary span {
  font-weight: 700;
  color: var(--gsmv-text);
}

.permission-matrix {
  display: grid;
  max-height: 520px;
  gap: 14px;
  padding-right: 4px;
  overflow-y: auto;
}

.permission-group {
  border: 1px solid rgba(150, 232, 255, 0.14);
  border-radius: 20px;
  padding: 16px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.06), rgba(255, 255, 255, 0.02)),
    rgba(255, 255, 255, 0.03);
}

.permission-group__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 14px;
}

.permission-group__head strong {
  display: block;
  margin-bottom: 6px;
  color: var(--gsmv-text);
  font-size: 16px;
}

.permission-group__head span {
  display: inline-flex;
  margin-bottom: 8px;
  color: var(--gsmv-primary);
  font-family: "Cascadia Mono", "SFMono-Regular", Consolas, monospace;
  font-size: 12px;
}

.permission-group__head p {
  margin: 0;
  color: var(--gsmv-muted);
  line-height: 1.62;
}

.permission-group__actions {
  display: flex;
  flex: 0 0 auto;
  gap: 8px;
}

.permission-checks {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}

.permission-checks :deep(.el-checkbox) {
  align-items: flex-start;
  height: auto;
  margin-right: 0;
  padding: 12px;
  border: 1px solid rgba(150, 232, 255, 0.12);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.035);
}

.permission-checks :deep(.el-checkbox.is-checked) {
  border-color: rgba(124, 245, 220, 0.34);
  background: rgba(124, 245, 220, 0.08);
}

.permission-checks :deep(.el-checkbox__label) {
  min-width: 0;
  padding-left: 10px;
}

.permission-check {
  display: grid;
  min-width: 0;
  gap: 5px;
  white-space: normal;
}

.permission-check strong {
  color: var(--gsmv-text);
  line-height: 1.35;
}

.permission-check small {
  color: var(--gsmv-primary);
  font-family: "Cascadia Mono", "SFMono-Regular", Consolas, monospace;
  font-size: 11px;
}

.permission-check em {
  color: var(--gsmv-muted);
  font-size: 12px;
  font-style: normal;
  line-height: 1.5;
}

@media (max-width: 860px) {
  .role-toolbar,
  .permission-group__head {
    flex-direction: column;
  }

  .permission-group__actions {
    width: 100%;
  }
}
</style>
