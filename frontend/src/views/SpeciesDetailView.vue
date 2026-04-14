<template>
  <div class="page-shell" v-loading="loading">
    <section class="page-hero">
      <div>
        <h2>{{ detail?.chineseName || '物种详情' }}</h2>
        <p>{{ detail?.scientificName || '查看物种分类、形态特征、分布范围、多媒体资料与参考文献。' }}</p>
      </div>
      <div class="detail-actions">
        <el-button @click="goBack">返回列表</el-button>
      </div>
    </section>

    <template v-if="detail">
      <el-card class="panel-card" shadow="never">
        <template #header>
          <strong>基础信息</strong>
        </template>

        <el-descriptions :column="2" border>
          <el-descriptions-item label="中文名">{{ detail.chineseName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="学名">{{ detail.scientificName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="门">{{ detail.phylumName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="纲">{{ detail.className || '-' }}</el-descriptions-item>
          <el-descriptions-item label="目">{{ detail.orderName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="科">{{ detail.familyName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="属">{{ detail.genusName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="分类路径">{{ detail.classificationPath || '-' }}</el-descriptions-item>
          <el-descriptions-item label="保护等级">{{ detail.protectionLevel || '-' }}</el-descriptions-item>
          <el-descriptions-item label="濒危状态">{{ detail.iucnStatus || '-' }}</el-descriptions-item>
          <el-descriptions-item label="档案状态">
            <el-tag :type="detail.status === 1 ? 'success' : 'info'">{{ detail.status === 1 ? '启用' : '归档' }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ detail.updatedAt || '-' }}</el-descriptions-item>
          <el-descriptions-item label="分布纬度">{{ detail.distributionLat ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="分布经度">{{ detail.distributionLng ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="地理范围" :span="2">{{ detail.geoRangeText || '-' }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card v-if="detail.images.length" class="panel-card" shadow="never">
        <template #header>
          <strong>图片资料</strong>
        </template>

        <div class="image-gallery">
          <el-image
            v-for="item in detail.images"
            :key="item.id"
            :src="item.url"
            :preview-src-list="imageUrls"
            fit="cover"
            class="image-gallery__item"
          />
        </div>
      </el-card>

      <div class="detail-grid">
        <el-card class="panel-card" shadow="never">
          <template #header>
            <strong>形态与生态</strong>
          </template>

          <div class="detail-block">
            <h3>形态特征</h3>
            <p>{{ detail.morphology || '暂无记录' }}</p>
          </div>
          <div class="detail-block">
            <h3>生活习性</h3>
            <p>{{ detail.habit || '暂无记录' }}</p>
          </div>
          <div class="detail-block">
            <h3>栖息环境</h3>
            <p>{{ detail.habitat || '暂无记录' }}</p>
          </div>
          <div class="detail-block">
            <h3>分布描述</h3>
            <p>{{ detail.distribution || '暂无记录' }}</p>
          </div>
        </el-card>

        <el-card class="panel-card" shadow="never">
          <template #header>
            <strong>扩展资料</strong>
          </template>

          <div class="detail-block">
            <h3>物种简介</h3>
            <p>{{ detail.description || '暂无记录' }}</p>
          </div>
          <div class="detail-block">
            <h3>视频链接</h3>
            <template v-if="detail.videoUrl">
              <video v-if="isDirectVideo" :src="detail.videoUrl" controls class="detail-video" />
              <a v-else :href="detail.videoUrl" target="_blank" rel="noreferrer" class="detail-link">{{ detail.videoUrl }}</a>
            </template>
            <p v-else>暂无记录</p>
          </div>
          <div class="detail-block">
            <h3>参考文献</h3>
            <ul v-if="referenceItems.length" class="reference-list">
              <li v-for="item in referenceItems" :key="item">{{ item }}</li>
            </ul>
            <p v-else>暂无记录</p>
          </div>
        </el-card>
      </div>

      <VersionHistoryPanel
        title="版本历史与回溯"
        description="查看每次档案变更的字段差异、操作人和时间，并可一键回滚到指定版本。"
        empty-text="当前物种还没有版本记录。"
        :versions="versions"
        :loading="versionsLoading"
        :can-rollback="canRollback"
        :rollbacking-version-id="rollbackingVersionId"
        @rollback="handleRollback"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { fetchSpeciesDetail, fetchSpeciesVersions, rollbackSpeciesVersion } from '@/api/species'
import VersionHistoryPanel from '@/components/VersionHistoryPanel.vue'
import { useAuthStore } from '@/stores/auth'
import { listenDataChanged, notifyDataChanged } from '@/utils/dataSync'
import type { EntityVersionView, SpeciesDetailView } from '@/types/gsmv'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const loading = ref(false)
const detail = ref<SpeciesDetailView | null>(null)
const versions = ref<EntityVersionView[]>([])
const versionsLoading = ref(false)
const rollbackingVersionId = ref<number | null>(null)
let stopDataSync: (() => void) | undefined
let refreshTimer: number | undefined

const speciesId = computed(() => Number(route.params.id))
const imageUrls = computed(() => detail.value?.images.map((item) => item.url) || [])
const referenceItems = computed(() =>
  (detail.value?.referenceText || '')
    .split('\n')
    .map((item) => item.trim())
    .filter(Boolean),
)
const isDirectVideo = computed(() => /\.(mp4|webm|ogg)(\?.*)?$/i.test(detail.value?.videoUrl || ''))
const canRollback = computed(() => authStore.authorities.includes('SPECIES_WRITE'))

async function loadDetail() {
  if (!speciesId.value || Number.isNaN(speciesId.value)) {
    router.replace('/species')
    return
  }

  loading.value = true
  try {
    detail.value = await fetchSpeciesDetail(speciesId.value)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '物种详情加载失败')
    router.replace('/species')
  } finally {
    loading.value = false
  }
}

async function loadVersions() {
  if (!speciesId.value || Number.isNaN(speciesId.value)) {
    return
  }

  versionsLoading.value = true
  try {
    versions.value = await fetchSpeciesVersions(speciesId.value)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '版本历史加载失败')
  } finally {
    versionsLoading.value = false
  }
}

async function refreshPage() {
  await Promise.all([loadDetail(), loadVersions()])
}

function goBack() {
  router.push('/species')
}

async function handleRollback(version: EntityVersionView) {
  if (!speciesId.value || Number.isNaN(speciesId.value)) {
    return
  }

  try {
    await ElMessageBox.confirm(
      `回滚后会将当前物种档案恢复到 V${version.versionNo}，并生成一条新的回滚记录。确认继续吗？`,
      '回滚物种档案',
      {
        type: 'warning',
        confirmButtonText: '确认回滚',
        cancelButtonText: '取消',
      },
    )
    rollbackingVersionId.value = version.id
    detail.value = await rollbackSpeciesVersion(speciesId.value, version.id)
    notifyDataChanged('species')
    ElMessage.success(`已回滚到 V${version.versionNo}`)
    await refreshPage()
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    ElMessage.error(error instanceof Error ? error.message : '物种档案回滚失败')
  } finally {
    rollbackingVersionId.value = null
  }
}

function handleFocus() {
  void refreshPage()
}

function handleVisibilityChange() {
  if (!document.hidden) {
    void refreshPage()
  }
}

watch(
  () => route.params.id,
  () => {
    void refreshPage()
  },
)

onMounted(async () => {
  stopDataSync = listenDataChanged((detailEvent) => {
    if (detailEvent.type === 'species') {
      void refreshPage()
    }
  })
  window.addEventListener('focus', handleFocus)
  document.addEventListener('visibilitychange', handleVisibilityChange)
  refreshTimer = window.setInterval(() => {
    if (!document.hidden) {
      void refreshPage()
    }
  }, 10000)
  await refreshPage()
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
.detail-actions {
  display: flex;
  gap: 12px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.detail-block + .detail-block {
  margin-top: 18px;
}

.detail-block h3 {
  margin: 0 0 8px;
  font-size: 15px;
}

.detail-block p,
.detail-link {
  margin: 0;
  color: var(--gsmv-text);
  line-height: 1.7;
}

.image-gallery {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 14px;
}

.image-gallery__item {
  width: 100%;
  height: 160px;
  border-radius: 18px;
  overflow: hidden;
}

.detail-video {
  width: 100%;
  border-radius: 18px;
  background: #000;
}

.reference-list {
  margin: 0;
  padding-left: 18px;
  line-height: 1.8;
}

@media (max-width: 980px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
