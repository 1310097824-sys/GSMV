<template>
  <div class="page-shell">
    <section class="page-hero">
      <div>
        <h2>AI复核工单</h2>
        <p>集中处理低置信度识图结果，保留原始图片、候选结论和最终确认信息，形成完整的人工复核闭环。</p>
      </div>
      <el-button type="primary" plain @click="loadData">刷新工单</el-button>
    </section>

    <el-card class="panel-card" shadow="never">
      <div class="toolbar toolbar--wrap">
        <el-input
          v-model="query.keyword"
          placeholder="工单号 / 物种名 / 提交人 / 复核人"
          clearable
          style="max-width: 260px"
        />
        <el-select v-model="query.status" placeholder="工单状态" clearable style="width: 180px">
          <el-option label="待处理" value="PENDING" />
          <el-option label="复核中" value="IN_REVIEW" />
          <el-option label="已完成" value="RESOLVED" />
        </el-select>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="id" label="工单号" width="92" />
        <el-table-column label="原始图片" width="124" align="center">
          <template #default="{ row }">
            <div class="review-thumb">
              <el-image
                v-if="ticketImageUrl(row.imageMediaId)"
                :src="ticketImageUrl(row.imageMediaId)"
                fit="cover"
                class="review-thumb__image"
                :preview-src-list="[ticketImageUrl(row.imageMediaId)]"
                preview-teleported
              />
              <span v-else class="review-thumb__empty">加载中</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="识图结果" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.likelyChineseName || row.likelyScientificName || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="置信度" width="110">
          <template #default="{ row }">
            {{ toPercent(row.confidence) }}
          </template>
        </el-table-column>
        <el-table-column prop="submittedByName" label="提交人" min-width="120" />
        <el-table-column prop="reviewerName" label="复核人" min-width="120" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="提交时间" min-width="180" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-space>
              <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
              <el-button v-if="canWrite && row.status !== 'RESOLVED'" link type="success" @click="startReview(row.id)">
                {{ row.status === 'PENDING' ? '开始复核' : '继续复核' }}
              </el-button>
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

    <el-drawer v-model="detailVisible" size="760px" title="复核工单详情">
      <template v-if="detail">
        <div class="review-detail">
          <div class="review-detail__hero">
            <div class="review-detail__image-frame">
              <el-image
                v-if="detailPreviewUrl"
                :src="detailPreviewUrl"
                fit="cover"
                class="review-detail__image"
                :preview-src-list="[detailPreviewUrl]"
                preview-teleported
              >
                <template #error>
                  <div class="review-detail__image-empty">原图加载失败</div>
                </template>
              </el-image>
              <div v-else class="review-detail__image-empty">原图加载中</div>
            </div>

            <div class="review-detail__summary">
              <strong>{{ detail.likelyChineseName || detail.likelyScientificName || '待人工确认' }}</strong>
              <span>{{ detail.likelyScientificName || '未返回学名' }}</span>
              <div class="review-detail__tags">
                <el-tag effect="plain">工单 #{{ detail.id }}</el-tag>
                <el-tag :type="statusTagType(detail.status)">{{ statusLabel(detail.status) }}</el-tag>
                <el-tag effect="plain">置信度 {{ toPercent(detail.confidence) }}</el-tag>
                <el-tag v-if="detail.needsHumanReview" type="warning" effect="dark">建议人工复核</el-tag>
              </div>
              <p class="review-detail__reasoning">{{ detail.reasoning || '本次识图没有返回额外说明。' }}</p>
            </div>
          </div>

          <el-descriptions :column="2" border>
            <el-descriptions-item label="提交人">{{ detail.submittedByName }}</el-descriptions-item>
            <el-descriptions-item label="复核人">{{ detail.reviewerName || '待分配' }}</el-descriptions-item>
            <el-descriptions-item label="提交时间">{{ detail.createdAt }}</el-descriptions-item>
            <el-descriptions-item label="完成时间">{{ detail.reviewedAt || '-' }}</el-descriptions-item>
          </el-descriptions>

          <div v-if="detail.submitNote" class="review-detail__section">
            <h3>提交备注</h3>
            <p>{{ detail.submitNote }}</p>
          </div>

          <div v-if="detail.candidates.length" class="review-detail__section">
            <h3>候选列表</h3>
            <div class="candidate-list">
              <div v-for="item in detail.candidates" :key="`${item.scientificName}-${item.confidence}`" class="candidate-item">
                <strong>{{ item.chineseName || item.scientificName || '候选项' }}</strong>
                <span>{{ item.scientificName || '待确认学名' }}</span>
                <small>置信度 {{ toPercent(item.confidence) }}</small>
                <p>{{ item.reason || '暂无补充说明' }}</p>
              </div>
            </div>
          </div>

          <div v-if="detail.relatedSpeciesRecords.length" class="review-detail__section">
            <h3>系统内已有物种档案</h3>
            <div class="candidate-list">
              <div v-for="item in detail.relatedSpeciesRecords" :key="item.id" class="candidate-item">
                <strong>{{ item.chineseName || item.scientificName }}</strong>
                <span>{{ item.scientificName }}</span>
                <small>{{ item.classificationPath || '暂无分类路径' }}</small>
                <p>{{ [item.protectionLevel, item.iucnStatus].filter(Boolean).join(' / ') || '暂无保护等级信息' }}</p>
              </div>
            </div>
          </div>

          <div v-if="detail.status === 'RESOLVED'" class="review-result">
            <h3>最终结论</h3>
            <el-tag type="success" effect="dark">{{ resolutionLabel(detail.resolutionCode) }}</el-tag>
            <p>{{ detail.finalChineseName || detail.finalScientificName || '未绑定最终物种名称' }}</p>
            <span>{{ detail.finalScientificName || '-' }}</span>
            <div class="review-result__note">{{ detail.reviewNote || '暂无复核说明' }}</div>
          </div>

          <div v-if="canWrite && detail.status !== 'RESOLVED'" class="review-detail__section">
            <div class="review-form__header">
              <h3>提交复核结论</h3>
              <el-button plain type="success" :loading="starting" @click="startReview(detail.id)">
                {{ detail.status === 'PENDING' ? '标记为开始复核' : '由我继续复核' }}
              </el-button>
            </div>

            <el-form label-position="top">
              <el-form-item label="复核结论">
                <el-select v-model="resolveForm.resolutionCode" style="width: 100%">
                  <el-option label="确认物种" value="CONFIRMED" />
                  <el-option label="与候选不匹配" value="NOT_MATCH" />
                  <el-option label="暂时无法确认" value="UNABLE_TO_CONFIRM" />
                </el-select>
              </el-form-item>

              <el-form-item label="关联已有物种档案">
                <el-select
                  v-model="resolveForm.finalSpeciesId"
                  filterable
                  clearable
                  style="width: 100%"
                  placeholder="可选择系统内已有物种档案自动回填名称"
                  @change="handleSpeciesChange"
                >
                  <el-option
                    v-for="item in speciesOptions"
                    :key="item.id"
                    :label="`${item.scientificName}${item.chineseName ? ` / ${item.chineseName}` : ''}`"
                    :value="item.id"
                  />
                </el-select>
              </el-form-item>

              <el-row :gutter="16">
                <el-col :span="12">
                  <el-form-item label="最终中文名">
                    <el-input v-model="resolveForm.finalChineseName" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="最终学名">
                    <el-input v-model="resolveForm.finalScientificName" />
                  </el-form-item>
                </el-col>
              </el-row>

              <el-form-item label="复核说明">
                <el-input
                  v-model="resolveForm.reviewNote"
                  type="textarea"
                  :rows="4"
                  placeholder="说明你确认、驳回或暂时无法确认的依据。"
                />
              </el-form-item>

              <div class="review-form__actions">
                <el-button type="primary" :loading="resolving" @click="submitResolution">提交复核结论</el-button>
              </div>
            </el-form>
          </div>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  fetchAiReviewImageBlob,
  fetchAiReviewTicketDetail,
  fetchAiReviewTickets,
  resolveAiReviewTicket,
  startAiReviewTicket,
} from '@/api/aiReview'
import { fetchSpecies } from '@/api/species'
import { useAuthStore } from '@/stores/auth'
import { listenDataChanged, notifyDataChanged } from '@/utils/dataSync'
import type { AiReviewTicketDetailView, AiReviewTicketView, SpeciesView } from '@/types/gsmv'

const authStore = useAuthStore()

const loading = ref(false)
const detailVisible = ref(false)
const starting = ref(false)
const resolving = ref(false)
const rows = ref<AiReviewTicketView[]>([])
const detail = ref<AiReviewTicketDetailView | null>(null)
const speciesOptions = ref<SpeciesView[]>([])
const ticketImageUrls = ref<Record<number, string>>({})
let stopDataSync: (() => void) | undefined

const canWrite = computed(() => authStore.authorities.includes('AI_REVIEW_WRITE'))
const detailPreviewUrl = computed(() => ticketImageUrl(detail.value?.imageMediaId))

const query = reactive({
  keyword: '',
  status: '',
})

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0,
})

const resolveForm = reactive({
  resolutionCode: 'CONFIRMED',
  finalSpeciesId: undefined as number | undefined,
  finalChineseName: '',
  finalScientificName: '',
  reviewNote: '',
})

function statusLabel(status: string) {
  switch (status) {
    case 'IN_REVIEW':
      return '复核中'
    case 'RESOLVED':
      return '已完成'
    default:
      return '待处理'
  }
}

function statusTagType(status: string) {
  switch (status) {
    case 'IN_REVIEW':
      return 'warning'
    case 'RESOLVED':
      return 'success'
    default:
      return 'info'
  }
}

function resolutionLabel(code?: string) {
  switch (code) {
    case 'CONFIRMED':
      return '确认物种'
    case 'NOT_MATCH':
      return '与候选不匹配'
    case 'UNABLE_TO_CONFIRM':
      return '暂时无法确认'
    default:
      return '待提交'
  }
}

function toPercent(value: number) {
  return `${Math.round((value || 0) * 100)}%`
}

function ticketImageUrl(mediaId?: number) {
  if (!mediaId) {
    return ''
  }
  return ticketImageUrls.value[mediaId] || ''
}

async function ensureTicketImage(mediaId?: number) {
  if (!mediaId || ticketImageUrls.value[mediaId]) {
    return
  }
  try {
    const blob = await fetchAiReviewImageBlob(mediaId)
    ticketImageUrls.value[mediaId] = URL.createObjectURL(blob)
  } catch (error) {
    console.error('AI review image load failed', error)
  }
}

async function preloadTicketImages(items: Array<{ imageMediaId?: number }>) {
  const mediaIds = items
    .map((item) => item.imageMediaId)
    .filter((mediaId): mediaId is number => typeof mediaId === 'number' && mediaId > 0)
  await Promise.all(mediaIds.map((mediaId) => ensureTicketImage(mediaId)))
}

function pruneTicketImages(extraMediaId?: number) {
  const activeMediaIds = new Set<number>()

  rows.value.forEach((row) => {
    if (row.imageMediaId) {
      activeMediaIds.add(row.imageMediaId)
    }
  })

  if (extraMediaId) {
    activeMediaIds.add(extraMediaId)
  }

  Object.entries(ticketImageUrls.value).forEach(([key, objectUrl]) => {
    const mediaId = Number(key)
    if (!activeMediaIds.has(mediaId)) {
      URL.revokeObjectURL(objectUrl)
      delete ticketImageUrls.value[mediaId]
    }
  })
}

function revokeAllTicketImages() {
  Object.values(ticketImageUrls.value).forEach((objectUrl) => URL.revokeObjectURL(objectUrl))
  ticketImageUrls.value = {}
}

function fillResolveForm(ticket: AiReviewTicketDetailView) {
  resolveForm.resolutionCode = ticket.resolutionCode || 'CONFIRMED'
  resolveForm.finalSpeciesId = ticket.finalSpeciesId
  resolveForm.finalChineseName = ticket.finalChineseName || ''
  resolveForm.finalScientificName = ticket.finalScientificName || ''
  resolveForm.reviewNote = ticket.reviewNote || ''
}

async function loadSpeciesOptions() {
  if (!canWrite.value) {
    return
  }
  const pageData = await fetchSpecies({ page: 1, size: 200 })
  speciesOptions.value = pageData.items
}

async function loadData() {
  loading.value = true
  try {
    const pageData = await fetchAiReviewTickets({
      keyword: query.keyword || undefined,
      status: query.status || undefined,
      page: pagination.page,
      size: pagination.size,
    })
    rows.value = pageData.items
    pagination.total = pageData.total
    await preloadTicketImages(pageData.items)
    pruneTicketImages(detail.value?.imageMediaId)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '复核工单加载失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.page = 1
  void loadData()
}

function handleReset() {
  query.keyword = ''
  query.status = ''
  pagination.page = 1
  void loadData()
}

async function openDetail(id: number) {
  detailVisible.value = true
  try {
    const ticket = await fetchAiReviewTicketDetail(id)
    await ensureTicketImage(ticket.imageMediaId)
    detail.value = ticket
    fillResolveForm(ticket)
    pruneTicketImages(ticket.imageMediaId)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '复核工单详情加载失败')
    detailVisible.value = false
  }
}

async function startReview(id?: number) {
  const targetId = id || detail.value?.id
  if (!targetId) {
    return
  }

  starting.value = true
  try {
    const ticket = await startAiReviewTicket(targetId)
    detail.value = ticket
    fillResolveForm(ticket)
    notifyDataChanged('aiReview')
    await loadData()
    ElMessage.success('工单已标记为复核中')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '开始复核失败')
  } finally {
    starting.value = false
  }
}

function handleSpeciesChange(speciesId?: number) {
  const species = speciesOptions.value.find((item) => item.id === speciesId)
  if (!species) {
    return
  }
  resolveForm.finalChineseName = species.chineseName || ''
  resolveForm.finalScientificName = species.scientificName || ''
}

async function submitResolution() {
  if (!detail.value) {
    return
  }
  if (!resolveForm.reviewNote.trim()) {
    ElMessage.warning('请先填写复核说明')
    return
  }

  resolving.value = true
  try {
    const ticket = await resolveAiReviewTicket(detail.value.id, {
      resolutionCode: resolveForm.resolutionCode,
      finalSpeciesId: resolveForm.finalSpeciesId,
      finalChineseName: resolveForm.finalChineseName || undefined,
      finalScientificName: resolveForm.finalScientificName || undefined,
      reviewNote: resolveForm.reviewNote.trim(),
    })
    detail.value = ticket
    fillResolveForm(ticket)
    notifyDataChanged('aiReview')
    await loadData()
    ElMessage.success('复核结论已提交')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '复核结论提交失败')
  } finally {
    resolving.value = false
  }
}

onMounted(async () => {
  stopDataSync = listenDataChanged((detailChange) => {
    if (detailChange.type === 'aiReview') {
      void loadData()
    }
  })
  await Promise.all([loadData(), loadSpeciesOptions()])
})

onBeforeUnmount(() => {
  stopDataSync?.()
  revokeAllTicketImages()
})
</script>

<style scoped>
.review-detail {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.review-detail__hero {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}

.review-thumb {
  width: 72px;
  height: 72px;
  margin: 0 auto;
  border-radius: 18px;
  overflow: hidden;
  border: 1px solid rgba(177, 234, 247, 0.16);
  background: rgba(8, 33, 74, 0.72);
  display: flex;
  align-items: center;
  justify-content: center;
}

.review-thumb__image {
  width: 100%;
  height: 100%;
}

.review-thumb__empty,
.review-detail__image-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  color: var(--gsmv-muted);
  font-size: 13px;
  text-align: center;
  padding: 12px;
  line-height: 1.6;
}

.review-detail__image-frame {
  width: 100%;
  height: 220px;
  border-radius: 22px;
  overflow: hidden;
  border: 1px solid rgba(177, 234, 247, 0.12);
  background: rgba(6, 34, 79, 0.66);
}

.review-detail__image {
  width: 100%;
  height: 220px;
}

.review-detail__summary {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.review-detail__summary strong {
  font-size: 24px;
}

.review-detail__summary span,
.review-detail__reasoning,
.review-result span {
  color: var(--gsmv-muted);
  line-height: 1.8;
}

.review-detail__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.review-detail__section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.review-detail__section h3,
.review-result h3 {
  margin: 0;
  font-size: 16px;
}

.candidate-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
}

.candidate-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px;
  border-radius: 18px;
  border: 1px solid rgba(177, 234, 247, 0.12);
  background: rgba(7, 49, 106, 0.5);
}

.candidate-item span,
.candidate-item small,
.candidate-item p {
  color: var(--gsmv-muted);
  line-height: 1.7;
}

.candidate-item p {
  margin: 0;
}

.review-result {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 18px;
  border-radius: 22px;
  border: 1px solid rgba(120, 235, 182, 0.16);
  background: linear-gradient(135deg, rgba(19, 99, 112, 0.3), rgba(18, 61, 124, 0.42));
}

.review-result__note {
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.05);
  line-height: 1.8;
}

.review-form__header,
.review-form__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

@media (max-width: 980px) {
  .review-detail__hero {
    grid-template-columns: 1fr;
  }
}
</style>
