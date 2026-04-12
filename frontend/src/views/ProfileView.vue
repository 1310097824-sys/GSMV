<template>
  <div class="page-shell">
    <section class="page-hero">
      <div>
        <h2>个人中心</h2>
        <p>维护个人资料、上传头像，并查看自己的活动记录与审核状态。</p>
      </div>
      <el-tag :type="approvalTagType">{{ approvalText }}</el-tag>
    </section>

    <div class="profile-grid">
      <el-card class="panel-card" shadow="never">
        <div class="avatar-panel">
          <el-avatar :size="108" :src="profile?.avatarUrl || '/default-avatar.jpg'">
            {{ profile?.displayName?.slice(0, 1) || 'U' }}
          </el-avatar>
          <div class="avatar-panel__meta">
            <strong>{{ profile?.displayName || '-' }}</strong>
            <span>{{ profile?.username || '-' }}</span>
            <span>{{ roleNames }}</span>
          </div>
          <el-upload :show-file-list="false" :http-request="handleAvatarUpload" accept="image/*">
            <el-button plain>上传头像</el-button>
          </el-upload>
        </div>
      </el-card>

      <el-card class="panel-card" shadow="never">
        <template #header>
          <strong>基本信息</strong>
        </template>

        <el-form label-position="top">
          <el-row :gutter="16">
            <el-col :md="12" :xs="24">
              <el-form-item label="显示名称">
                <el-input v-model="form.displayName" />
              </el-form-item>
            </el-col>
            <el-col :md="12" :xs="24">
              <el-form-item label="用户名">
                <el-input :model-value="profile?.username || ''" disabled />
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="16">
            <el-col :md="12" :xs="24">
              <el-form-item label="邮箱">
                <el-input v-model="form.email" />
              </el-form-item>
            </el-col>
            <el-col :md="12" :xs="24">
              <el-form-item label="手机号">
                <el-input v-model="form.phone" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-form-item label="个人简介">
            <el-input v-model="form.bio" type="textarea" :rows="4" placeholder="可填写研究方向、身份说明或个人简介" />
          </el-form-item>

          <div class="profile-actions">
            <el-button type="primary" :loading="submitting" @click="saveProfile">保存资料</el-button>
          </div>
        </el-form>
      </el-card>
    </div>

    <el-card class="panel-card" shadow="never">
      <template #header>
        <strong>我的活动日志</strong>
      </template>

      <el-table :data="activities" v-loading="activityLoading" stripe>
        <el-table-column prop="createdAt" label="时间" min-width="180" />
        <el-table-column prop="module" label="模块" min-width="120" />
        <el-table-column prop="action" label="动作" min-width="140" />
        <el-table-column label="结果" min-width="100">
          <template #default="{ row }">
            <el-tag :type="row.success === 1 ? 'success' : 'danger'">
              {{ row.success === 1 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="detailJson" label="详情" min-width="320" show-overflow-tooltip />
      </el-table>

      <div class="table-footer">
        <el-pagination
          v-model:current-page="activityPagination.page"
          v-model:page-size="activityPagination.size"
          layout="total, prev, pager, next"
          :total="activityPagination.total"
          @current-change="loadActivities"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import { fetchMyActivities } from '@/api/audits'
import { fetchCurrentProfile, updateCurrentProfile, uploadCurrentAvatar } from '@/api/users'
import { useAuthStore } from '@/stores/auth'
import { listenDataChanged, notifyDataChanged } from '@/utils/dataSync'
import type { AuditLogView, UserProfileView } from '@/types/gsmv'

const authStore = useAuthStore()

const submitting = ref(false)
const activityLoading = ref(false)
const profileLoading = ref(false)
const profile = ref<UserProfileView | null>(null)
const activities = ref<AuditLogView[]>([])
let stopDataSync: (() => void) | undefined
let refreshTimer: number | undefined

const form = reactive({
  displayName: '',
  email: '',
  phone: '',
  bio: '',
})

const activityPagination = reactive({
  page: 1,
  size: 10,
  total: 0,
})

const roleNames = computed(() => profile.value?.roles.map((item) => item.name).join('、') || '-')
const hasDraftChanges = computed(() => {
  if (!profile.value) {
    return false
  }

  return normalizeField(form.displayName) !== normalizeField(profile.value.displayName)
    || normalizeField(form.email) !== normalizeField(profile.value.email)
    || normalizeField(form.phone) !== normalizeField(profile.value.phone)
    || normalizeField(form.bio) !== normalizeField(profile.value.bio)
})

const approvalText = computed(() => {
  switch (profile.value?.approvalStatus) {
    case 'APPROVED':
      return '已审核通过'
    case 'REJECTED':
      return '审核未通过'
    default:
      return '待审核'
  }
})
const approvalTagType = computed(() => {
  switch (profile.value?.approvalStatus) {
    case 'APPROVED':
      return 'success'
    case 'REJECTED':
      return 'danger'
    default:
      return 'warning'
  }
})

function normalizeField(value?: string | null) {
  return (value ?? '').trim()
}

function syncForm(data: UserProfileView, options: { preserveDraft?: boolean } = {}) {
  profile.value = data
  if (!options.preserveDraft || !hasDraftChanges.value) {
    form.displayName = data.displayName
    form.email = data.email || ''
    form.phone = data.phone || ''
    form.bio = data.bio || ''
  }
  authStore.patchProfile({
    displayName: data.displayName,
    avatarUrl: data.avatarUrl,
  })
}

async function loadProfile(options: { preserveDraft?: boolean } = {}) {
  if (profileLoading.value) {
    return
  }

  profileLoading.value = true
  try {
    syncForm(await fetchCurrentProfile(), options)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '涓汉璧勬枡鍔犺浇澶辫触')
  } finally {
    profileLoading.value = false
  }
}

async function loadActivities() {
  if (activityLoading.value) {
    return
  }

  activityLoading.value = true
  try {
    const pageData = await fetchMyActivities({
      page: activityPagination.page,
      size: activityPagination.size,
    })
    activities.value = pageData.items
    activityPagination.total = pageData.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '活动日志加载失败')
  } finally {
    activityLoading.value = false
  }
}

async function refreshProfileState() {
  await Promise.all([loadProfile({ preserveDraft: true }), loadActivities()])
}

function handleFocus() {
  void refreshProfileState()
}

function handleVisibilityChange() {
  if (!document.hidden) {
    void refreshProfileState()
  }
}

async function saveProfile() {
  if (!form.displayName.trim()) {
    ElMessage.warning('请输入显示名称')
    return
  }

  submitting.value = true
  try {
    const saved = await updateCurrentProfile({
      displayName: form.displayName.trim(),
      email: form.email.trim() || undefined,
      phone: form.phone.trim() || undefined,
      bio: form.bio.trim() || undefined,
    })
    syncForm(saved)
    notifyDataChanged('user')
    ElMessage.success('个人资料已更新')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '个人资料更新失败')
  } finally {
    submitting.value = false
  }
}

async function handleAvatarUpload(options: UploadRequestOptions) {
  try {
    const saved = await uploadCurrentAvatar(options.file as File)
    syncForm(saved)
    notifyDataChanged('user')
    ElMessage.success('头像已更新')
    options.onSuccess?.(saved)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '头像上传失败')
    options.onError?.(error as never)
  }
}

onMounted(async () => {
  stopDataSync = listenDataChanged((detail) => {
    if (detail.type === 'user') {
      void refreshProfileState()
    }
  })
  window.addEventListener('focus', handleFocus)
  document.addEventListener('visibilitychange', handleVisibilityChange)
  refreshTimer = window.setInterval(() => {
    if (!document.hidden) {
      void loadProfile({ preserveDraft: true })
    }
  }, 5000)
  await Promise.all([loadProfile(), loadActivities()])
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
.profile-grid {
  display: grid;
  grid-template-columns: minmax(280px, 0.8fr) minmax(0, 1.2fr);
  gap: 18px;
}

.avatar-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 18px;
  text-align: center;
}

.avatar-panel__meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.avatar-panel__meta span {
  color: var(--gsmv-muted);
}

.profile-actions,
.table-footer {
  display: flex;
  justify-content: flex-end;
}

.table-footer {
  margin-top: 18px;
}

@media (max-width: 980px) {
  .profile-grid {
    grid-template-columns: 1fr;
  }
}
</style>
