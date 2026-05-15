<template>
  <div class="page-shell">
    <section class="page-hero">
      <div>
        <h2>观测记录</h2>
        <p>记录时间、地点、生态系统、环境参数和关联物种信息，并在录入时生成智能标签与异常核验提示。</p>
      </div>
      <el-button v-if="canWrite" type="primary" @click="openCreate">新增观测</el-button>
    </section>

    <el-card class="panel-card" shadow="never">
      <div class="toolbar toolbar--wrap">
        <el-select v-model="query.ecosystemId" placeholder="按生态系统筛选" clearable style="width: 220px">
          <el-option v-for="item in ecosystemOptions" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
        <el-input
          v-model="query.keyword"
          placeholder="地点 / 观测人员 / 物种 / 备注"
          clearable
          style="max-width: 260px"
        />
        <el-date-picker
          v-model="query.observedRange"
          type="datetimerange"
          value-format="YYYY-MM-DDTHH:mm:ss"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
        />
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="ecosystemName" label="生态系统" min-width="160" />
        <el-table-column prop="observerName" label="观测人员" min-width="120" />
        <el-table-column prop="observedAt" label="观测时间" min-width="180" />
        <el-table-column prop="locationName" label="地点说明" min-width="180" show-overflow-tooltip />
        <el-table-column label="坐标" min-width="190">
          <template #default="{ row }">
            {{ row.locationLat }}, {{ row.locationLng }}
          </template>
        </el-table-column>
        <el-table-column label="环境参数" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            {{ describeObservationEnvironment(row.envJson) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" :width="canWrite ? 220 : 90">
          <template #default="{ row }">
            <el-space>
              <el-button link type="primary" @click="showDetail(row.id)">详情</el-button>
              <el-button v-if="canWrite" link type="primary" @click="openEdit(row.id)">编辑</el-button>
              <el-button v-if="canWrite" link type="danger" @click="removeObservation(row.id)">删除</el-button>
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

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑观测记录' : '新增观测记录'" width="1080px" top="3vh">
      <el-card class="ai-card" shadow="never">
        <template #header>
          <div class="ai-card__header">
            <div>
              <strong>智能标签与异常检测</strong>
              <p>根据地点、时间、生态系统、环境参数和关联物种，生成自动标签并提示明显冲突。</p>
            </div>
            <el-button type="primary" plain :loading="aiAnalyzing" @click="runAiAnalysis">生成分析建议</el-button>
          </div>
        </template>

        <template v-if="aiAnalysis">
          <div class="ai-analysis__summary">
            <strong>AI 摘要</strong>
            <p>{{ aiAnalysis.summary || '本次分析未返回额外摘要。' }}</p>
          </div>

          <div v-if="aiAnalysis.tags.length" class="ai-analysis__tags">
            <el-tag v-for="item in aiAnalysis.tags" :key="item" effect="plain" round>{{ item }}</el-tag>
          </div>

          <div v-if="aiAnalysis.reviewNotes.length" class="ai-analysis__notes">
            <strong>人工核验提醒</strong>
            <ul>
              <li v-for="item in aiAnalysis.reviewNotes" :key="item">{{ item }}</li>
            </ul>
          </div>

          <div v-if="aiAnalysis.anomalies.length" class="anomaly-list">
            <div v-for="item in aiAnalysis.anomalies" :key="`${item.speciesName}-${item.message}`" class="anomaly-item">
              <el-tag :type="item.severity === 'HIGH' ? 'danger' : 'warning'" effect="dark">
                {{ item.severity === 'HIGH' ? '高风险' : '需核验' }}
              </el-tag>
              <div class="anomaly-item__body">
                <strong>{{ item.speciesName }}</strong>
                <p>{{ item.message }}</p>
                <small>{{ item.suggestion || '建议补充现场图像、备注或专家复核。' }}</small>
              </div>
            </div>
          </div>
        </template>
        <el-empty v-else description="点击“生成分析建议”后，这里会展示标签和异常提示" />
      </el-card>

      <div class="observation-form">
        <div>
          <el-form label-position="top">
            <div class="form-grid">
              <el-form-item label="生态系统">
                <el-select v-model="form.ecosystemId" style="width: 100%">
                  <el-option v-for="item in ecosystemOptions" :key="item.id" :label="item.name" :value="item.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="观测时间">
                <el-date-picker
                  v-model="form.observedAt"
                  type="datetime"
                  value-format="YYYY-MM-DDTHH:mm:ss"
                  style="width: 100%"
                />
              </el-form-item>
              <el-form-item label="地点说明" class="form-grid__wide">
                <el-input v-model="form.locationName" />
              </el-form-item>
              <el-form-item label="纬度">
                <el-input-number v-model="form.locationLat" :precision="6" :step="0.000001" style="width: 100%" />
              </el-form-item>
              <el-form-item label="经度">
                <el-input-number v-model="form.locationLng" :precision="6" :step="0.000001" style="width: 100%" />
              </el-form-item>
            </div>

            <el-divider>环境参数</el-divider>

            <div class="form-grid">
              <el-form-item label="水温 (°C)">
                <el-input-number v-model="environment.waterTemperature" :precision="2" :step="0.1" style="width: 100%" />
              </el-form-item>
              <el-form-item label="盐度 (‰)">
                <el-input-number v-model="environment.salinity" :precision="2" :step="0.1" style="width: 100%" />
              </el-form-item>
              <el-form-item label="pH">
                <el-input-number v-model="environment.ph" :precision="2" :step="0.1" style="width: 100%" />
              </el-form-item>
              <el-form-item label="溶解氧 (mg/L)">
                <el-input-number v-model="environment.dissolvedOxygen" :precision="2" :step="0.1" style="width: 100%" />
              </el-form-item>
              <el-form-item label="透明度 (m)">
                <el-input-number v-model="environment.transparency" :precision="2" :step="0.1" style="width: 100%" />
              </el-form-item>
              <el-form-item label="水深 (m)">
                <el-input-number v-model="environment.depthMeters" :precision="2" :step="0.1" style="width: 100%" />
              </el-form-item>
              <el-form-item label="天气">
                <el-input v-model="environment.weather" />
              </el-form-item>
              <el-form-item label="海况">
                <el-input v-model="environment.seaState" />
              </el-form-item>
            </div>

            <el-form-item label="备注">
              <el-input v-model="form.note" type="textarea" :rows="3" />
            </el-form-item>
          </el-form>
        </div>

        <div>
          <div class="map-caption">点击地图即可回填坐标，默认定位在湛江近海。</div>
          <LeafletPicker :lat="form.locationLat" :lng="form.locationLng" @update="handleMapUpdate" />
        </div>
      </div>

      <el-divider>关联物种</el-divider>

      <div class="species-list">
        <div v-if="!form.speciesItems.length" class="species-list__empty">
          当前还没有关联物种。你可以先保存观测记录，之后再回来补录。
        </div>
        <div v-for="(item, index) in form.speciesItems" :key="index" class="species-list__row">
          <el-select v-model="item.speciesId" filterable placeholder="选择物种" class="species-list__species">
            <el-option
              v-for="species in speciesOptions"
              :key="species.id"
              :label="`${species.scientificName}${species.chineseName ? ` / ${species.chineseName}` : ''}`"
              :value="species.id"
            />
          </el-select>
          <el-input-number v-model="item.countEstimated" :min="1" placeholder="数量" class="species-list__count" />
          <el-input v-model="item.behavior" placeholder="行为，例如：觅食 / 洄游 / 栖息" class="species-list__behavior" />
          <el-input v-model="item.comment" placeholder="补充说明" class="species-list__comment" />
          <el-button text type="danger" @click="removeSpeciesItem(index)">删除</el-button>
        </div>
        <el-button plain @click="addSpeciesItem">添加关联物种</el-button>
      </div>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">
          {{ editingId ? '保存修改' : '保存观测' }}
        </el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" size="680px" title="观测详情">
      <template v-if="detail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="生态系统">{{ detail.ecosystemName }}</el-descriptions-item>
          <el-descriptions-item label="观测人员">{{ detail.observerName }}</el-descriptions-item>
          <el-descriptions-item label="观测时间">{{ detail.observedAt }}</el-descriptions-item>
          <el-descriptions-item label="地点说明">{{ detail.locationName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="坐标">{{ detail.locationLat }}, {{ detail.locationLng }}</el-descriptions-item>
          <el-descriptions-item label="备注">{{ detail.note || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-divider>环境参数</el-divider>

        <div class="env-grid">
          <div v-for="item in detailEnvironmentEntries" :key="item.label" class="env-grid__item">
            <strong>{{ item.label }}</strong>
            <span>{{ item.value }}</span>
          </div>
        </div>

        <el-divider>AI 质量评分</el-divider>

        <div class="quality-panel">
          <div class="quality-panel__header">
            <div>
              <strong>观测记录完整性与异常风险</strong>
              <p>基于坐标、环境参数、物种关联和备注完整度，快速判断这条记录是否需要人工复核。</p>
            </div>
            <el-button type="primary" plain :loading="qualityChecking" @click="runQualityCheck">AI 评分</el-button>
          </div>

          <template v-if="qualityResult">
            <div class="quality-panel__score">
              <el-progress
                type="dashboard"
                :percentage="qualityResult.score"
                :color="qualityResult.needsReview ? '#ff9f43' : '#46d7c8'"
                :width="108"
              />
              <div>
                <el-tag :type="qualityGradeType(qualityResult.grade)" effect="dark">
                  {{ qualityGradeLabel(qualityResult.grade) }}
                </el-tag>
                <p>{{ qualityResult.summary }}</p>
              </div>
            </div>

            <div v-if="qualityResult.strengths.length" class="quality-panel__chips">
              <el-tag v-for="item in qualityResult.strengths" :key="item" effect="plain">{{ item }}</el-tag>
            </div>

            <div v-if="qualityResult.issues.length" class="quality-panel__issues">
              <div v-for="item in qualityResult.issues" :key="`${item.title}-${item.message}`" class="quality-issue">
                <el-tag :type="qualityIssueTagType(item.severity)" effect="dark">{{ item.severity }}</el-tag>
                <div>
                  <strong>{{ item.title }}</strong>
                  <p>{{ item.message }}</p>
                  <small>{{ item.suggestion }}</small>
                </div>
              </div>
            </div>
          </template>

          <el-empty v-else description="点击 AI 评分后展示质量分、风险项和补充建议。" :image-size="76" />
        </div>

        <el-divider>关联物种</el-divider>

        <el-table :data="detail.speciesItems" size="small">
          <el-table-column prop="scientificName" label="学名" min-width="180" />
          <el-table-column prop="chineseName" label="中文名" min-width="140" />
          <el-table-column prop="countEstimated" label="估算数量" min-width="100" />
          <el-table-column prop="behavior" label="行为" min-width="160" />
          <el-table-column prop="comment" label="说明" min-width="180" />
        </el-table>

        <el-divider>版本历史</el-divider>

        <VersionHistoryPanel
          title="观测记录版本历史"
          description="查看每次观测变更记录、字段差异和操作人，并支持一键回滚。"
          empty-text="当前观测记录还没有版本记录。"
          :versions="detailVersions"
          :loading="detailVersionsLoading"
          :can-rollback="canWrite"
          :rollbacking-version-id="rollbackingVersionId"
          @rollback="handleRollbackVersion"
        />
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { analyzeObservationWithAi, qualityCheckObservationWithAi } from '@/api/ai'
import { fetchAllEcosystems } from '@/api/ecosystems'
import {
  createObservation,
  deleteObservation,
  fetchObservationDetail,
  fetchObservationVersions,
  fetchObservations,
  rollbackObservationVersion,
  updateObservation,
} from '@/api/observations'
import { fetchSpecies } from '@/api/species'
import LeafletPicker from '@/components/LeafletPicker.vue'
import VersionHistoryPanel from '@/components/VersionHistoryPanel.vue'
import { DEFAULT_ECOSYSTEM_NAME, ZHANJIANG_OFFSHORE_CENTER } from '@/constants/ecosystem'
import { useAuthStore } from '@/stores/auth'
import { listenDataChanged, notifyDataChanged } from '@/utils/dataSync'
import {
  createEmptyObservationEnvironment,
  describeObservationEnvironment,
  observationEnvironmentEntries,
  parseObservationEnvironment,
  stringifyObservationEnvironment,
} from '@/utils/observationEnv'
import type {
  AiObservationAnalysisResponse,
  AiObservationQualityResponse,
  Ecosystem,
  EntityVersionView,
  ObservationDetailView,
  ObservationSpeciesInput,
  ObservationView,
  SpeciesView,
} from '@/types/gsmv'

const authStore = useAuthStore()

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const detailVisible = ref(false)
const editingId = ref<number | null>(null)
const detail = ref<ObservationDetailView | null>(null)
const detailVersions = ref<EntityVersionView[]>([])
const detailVersionsLoading = ref(false)
const rollbackingVersionId = ref<number | null>(null)
const rows = ref<ObservationView[]>([])
const ecosystemOptions = ref<Ecosystem[]>([])
const speciesOptions = ref<SpeciesView[]>([])
const defaultEcosystemId = ref<number | undefined>()
const aiAnalyzing = ref(false)
const aiAnalysis = ref<AiObservationAnalysisResponse | null>(null)
const qualityChecking = ref(false)
const qualityResult = ref<AiObservationQualityResponse | null>(null)

let stopDataSync: (() => void) | undefined

const canWrite = computed(() => authStore.authorities.includes('OBS_WRITE'))
const detailEnvironmentEntries = computed(() => observationEnvironmentEntries(detail.value?.envJson))

const query = reactive({
  ecosystemId: undefined as number | undefined,
  keyword: '',
  observedRange: [] as string[],
})

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0,
})

const form = reactive({
  ecosystemId: undefined as number | undefined,
  observedAt: '',
  locationLat: null as number | null,
  locationLng: null as number | null,
  locationName: '',
  note: '',
  speciesItems: [] as ObservationSpeciesInput[],
})

const environment = reactive(createEmptyObservationEnvironment())

function resetAiAnalysis() {
  aiAnalysis.value = null
}

function resetForm() {
  form.ecosystemId = defaultEcosystemId.value
  form.observedAt = new Date().toISOString().slice(0, 19)
  form.locationLat = ZHANJIANG_OFFSHORE_CENTER[0]
  form.locationLng = ZHANJIANG_OFFSHORE_CENTER[1]
  form.locationName = DEFAULT_ECOSYSTEM_NAME
  form.note = ''
  form.speciesItems = []
  applyEnvironment(createEmptyObservationEnvironment())
  resetAiAnalysis()
}

function applyEnvironment(source: ReturnType<typeof createEmptyObservationEnvironment>) {
  environment.waterTemperature = source.waterTemperature
  environment.salinity = source.salinity
  environment.ph = source.ph
  environment.dissolvedOxygen = source.dissolvedOxygen
  environment.transparency = source.transparency
  environment.depthMeters = source.depthMeters
  environment.weather = source.weather
  environment.seaState = source.seaState
}

function fillForm(detailData: ObservationDetailView) {
  form.ecosystemId = detailData.ecosystemId
  form.observedAt = detailData.observedAt
  form.locationLat = detailData.locationLat
  form.locationLng = detailData.locationLng
  form.locationName = detailData.locationName || ''
  form.note = detailData.note || ''
  form.speciesItems = (detailData.speciesItems || []).map((item) => ({
    speciesId: item.speciesId,
    countEstimated: item.countEstimated ?? null,
    behavior: item.behavior || '',
    comment: item.comment || '',
  }))
  applyEnvironment(parseObservationEnvironment(detailData.envJson))
  resetAiAnalysis()
}

async function loadOptions() {
  const [ecosystems, speciesPage] = await Promise.all([
    fetchAllEcosystems(),
    fetchSpecies({ page: 1, size: 200 }),
  ])
  ecosystemOptions.value = ecosystems
  speciesOptions.value = speciesPage.items
  defaultEcosystemId.value = ecosystems.find((item) => item.name === DEFAULT_ECOSYSTEM_NAME)?.id
  if (!form.ecosystemId && defaultEcosystemId.value) {
    form.ecosystemId = defaultEcosystemId.value
  }
  if (query.ecosystemId && !ecosystems.some((item) => item.id === query.ecosystemId)) {
    query.ecosystemId = undefined
  }
}

async function loadData() {
  loading.value = true
  try {
    const [observedFrom, observedTo] = query.observedRange
    const pageData = await fetchObservations({
      ecosystemId: query.ecosystemId,
      keyword: query.keyword || undefined,
      observedFrom: observedFrom || undefined,
      observedTo: observedTo || undefined,
      page: pagination.page,
      size: pagination.size,
    })
    rows.value = pageData.items
    pagination.total = pageData.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '观测记录加载失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.page = 1
  void loadData()
}

function handleReset() {
  query.ecosystemId = undefined
  query.keyword = ''
  query.observedRange = []
  pagination.page = 1
  void loadData()
}

function openCreate() {
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

async function openEdit(id: number) {
  editingId.value = id
  resetForm()
  dialogVisible.value = true
  try {
    fillForm(await fetchObservationDetail(id))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '观测详情加载失败')
    dialogVisible.value = false
  }
}

function addSpeciesItem() {
  form.speciesItems.push({
    speciesId: null,
    countEstimated: 1,
    behavior: '',
    comment: '',
  })
  resetAiAnalysis()
}

function removeSpeciesItem(index: number) {
  form.speciesItems.splice(index, 1)
  resetAiAnalysis()
}

function handleMapUpdate(payload: { lat: number; lng: number }) {
  form.locationLat = payload.lat
  form.locationLng = payload.lng
  resetAiAnalysis()
}

function normalizeSpeciesItems() {
  const normalizedItems: ObservationSpeciesInput[] = []
  const seenSpeciesIds = new Set<number>()

  for (const item of form.speciesItems) {
    const behavior = item.behavior?.trim() || ''
    const comment = item.comment?.trim() || ''
    const hasContent = item.speciesId != null || item.countEstimated != null || Boolean(behavior) || Boolean(comment)

    if (!hasContent) {
      continue
    }
    if (!item.speciesId) {
      throw new Error('请先为每条关联记录选择物种')
    }
    if (seenSpeciesIds.has(item.speciesId)) {
      throw new Error('同一条观测记录中不能重复关联相同物种')
    }

    seenSpeciesIds.add(item.speciesId)
    normalizedItems.push({
      speciesId: item.speciesId,
      countEstimated: item.countEstimated ?? undefined,
      behavior: behavior || undefined,
      comment: comment || undefined,
    })
  }

  return normalizedItems
}

function buildPayload() {
  const speciesItems = normalizeSpeciesItems()
  return {
    ecosystemId: form.ecosystemId,
    observedAt: form.observedAt,
    locationLat: form.locationLat,
    locationLng: form.locationLng,
    locationName: form.locationName.trim() || undefined,
    envJson: stringifyObservationEnvironment(environment),
    note: form.note.trim() || undefined,
    speciesItems: speciesItems.length ? speciesItems : undefined,
  }
}

async function runAiAnalysis() {
  if (!form.ecosystemId || !form.observedAt || form.locationLat == null || form.locationLng == null) {
    ElMessage.warning('请先填写完整的观测基础信息')
    return
  }

  const ecosystemName = ecosystemOptions.value.find((item) => item.id === form.ecosystemId)?.name || ''
  const speciesItems = form.speciesItems
    .filter((item) => item.speciesId)
    .map((item) => {
      const species = speciesOptions.value.find((option) => option.id === item.speciesId)
      return {
        speciesId: item.speciesId,
        scientificName: species?.scientificName,
        chineseName: species?.chineseName,
        countEstimated: item.countEstimated ?? undefined,
        behavior: item.behavior?.trim() || undefined,
        comment: item.comment?.trim() || undefined,
      }
    })

  aiAnalyzing.value = true
  try {
    aiAnalysis.value = await analyzeObservationWithAi({
      ecosystemId: form.ecosystemId,
      ecosystemName,
      observedAt: form.observedAt,
      locationLat: form.locationLat,
      locationLng: form.locationLng,
      locationName: form.locationName.trim() || undefined,
      note: form.note.trim() || undefined,
      environment: { ...environment },
      speciesItems,
    })
    ElMessage.success(aiAnalysis.value.needsReview ? '分析完成，建议查看异常提示' : '分析完成，未发现明显异常')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '智能分析失败')
  } finally {
    aiAnalyzing.value = false
  }
}

async function submit() {
  if (!form.ecosystemId || !form.observedAt || form.locationLat == null || form.locationLng == null) {
    ElMessage.warning('请先填写完整的基础观测信息')
    return
  }

  submitting.value = true
  try {
    const payload = buildPayload()
    const saved = editingId.value
      ? await updateObservation(editingId.value, payload)
      : await createObservation(payload)
    detail.value = saved
    detailVersions.value = []
    notifyDataChanged('observation')
    dialogVisible.value = false
    ElMessage.success(editingId.value ? '观测记录已更新' : '观测记录已创建')
    await loadData()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  } finally {
    submitting.value = false
  }
}

async function showDetail(id: number) {
  try {
    qualityResult.value = null
    const [detailData, versionsData] = await Promise.all([
      fetchObservationDetail(id),
      fetchObservationVersions(id),
    ])
    detail.value = detailData
    detailVersions.value = versionsData
    detailVisible.value = true
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '详情加载失败')
  }
}

function qualityGradeLabel(grade: string) {
  if (grade === 'HIGH') {
    return '质量较高'
  }
  if (grade === 'MEDIUM') {
    return '需要补充'
  }
  return '建议复核'
}

function qualityGradeType(grade: string) {
  if (grade === 'HIGH') {
    return 'success'
  }
  if (grade === 'MEDIUM') {
    return 'warning'
  }
  return 'danger'
}

function qualityIssueTagType(severity: string) {
  if (severity === 'HIGH') {
    return 'danger'
  }
  if (severity === 'MEDIUM') {
    return 'warning'
  }
  return 'info'
}

async function runQualityCheck() {
  if (!detail.value) {
    return
  }

  qualityChecking.value = true
  try {
    qualityResult.value = await qualityCheckObservationWithAi(detail.value.id)
    ElMessage.success(qualityResult.value.needsReview ? 'AI 已标出需要复核的风险项' : 'AI 评分完成，记录质量良好')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'AI 质量评分失败')
  } finally {
    qualityChecking.value = false
  }
}

async function refreshCurrentDetail() {
  if (!detailVisible.value || !detail.value) {
    return
  }

  const id = detail.value.id
  const [detailData, versionsData] = await Promise.all([
    fetchObservationDetail(id),
    fetchObservationVersions(id),
  ])
  detail.value = detailData
  detailVersions.value = versionsData
}

async function handleRollbackVersion(version: EntityVersionView) {
  if (!detail.value) {
    return
  }

  try {
    await ElMessageBox.confirm(
      `回滚后会将当前观测记录恢复到 V${version.versionNo}，并生成一条新的回滚记录。确认继续吗？`,
      '回滚观测记录',
      {
        type: 'warning',
        confirmButtonText: '确认回滚',
        cancelButtonText: '取消',
      },
    )
    rollbackingVersionId.value = version.id
    detail.value = await rollbackObservationVersion(detail.value.id, version.id)
    notifyDataChanged('observation')
    ElMessage.success(`已回滚到 V${version.versionNo}`)
    await Promise.all([loadData(), refreshCurrentDetail()])
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    ElMessage.error(error instanceof Error ? error.message : '观测记录回滚失败')
  } finally {
    rollbackingVersionId.value = null
  }
}

async function removeObservation(id: number) {
  try {
    await ElMessageBox.confirm(
      '删除后将一并移除这次观测关联的所有物种信息，且无法恢复。确认继续吗？',
      '删除观测记录',
      {
        type: 'warning',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
      },
    )
    await deleteObservation(id)
    notifyDataChanged('observation')
    if (detail.value?.id === id) {
      detail.value = null
      detailVersions.value = []
      detailVisible.value = false
    }
    ElMessage.success('观测记录已删除')
    await loadData()
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}

function handleFocus() {
  void loadData()
  void refreshCurrentDetail()
}

function handleVisibilityChange() {
  if (!document.hidden) {
    void loadData()
    void refreshCurrentDetail()
  }
}

onMounted(async () => {
  stopDataSync = listenDataChanged((detailData) => {
    if (detailData.type === 'observation') {
      void loadData()
      void refreshCurrentDetail()
    }
    if (detailData.type === 'ecosystem' || detailData.type === 'species') {
      void loadOptions()
    }
  })

  window.addEventListener('focus', handleFocus)
  document.addEventListener('visibilitychange', handleVisibilityChange)

  resetForm()
  await Promise.all([loadOptions(), loadData()])
})

onBeforeUnmount(() => {
  stopDataSync?.()
  window.removeEventListener('focus', handleFocus)
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})
</script>

<style scoped>
.toolbar--wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.table-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}

.ai-card {
  margin-bottom: 18px;
}

.ai-card__header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.ai-card__header p {
  margin: 8px 0 0;
  color: var(--gsmv-muted);
}

.ai-analysis__summary {
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(7, 49, 106, 0.58);
  border: 1px solid rgba(177, 234, 247, 0.14);
}

.ai-analysis__summary p {
  margin: 8px 0 0;
  color: var(--gsmv-muted);
  line-height: 1.8;
}

.ai-analysis__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 14px;
}

.ai-analysis__notes {
  margin-top: 14px;
}

.ai-analysis__notes ul {
  margin: 10px 0 0;
  padding-left: 18px;
  line-height: 1.9;
  color: var(--gsmv-muted);
}

.anomaly-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 14px;
}

.anomaly-item {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid rgba(177, 234, 247, 0.12);
  background: rgba(7, 49, 106, 0.52);
}

.anomaly-item__body p,
.anomaly-item__body small {
  color: var(--gsmv-muted);
  line-height: 1.75;
}

.observation-form {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(320px, 0.9fr);
  gap: 18px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.form-grid__wide {
  grid-column: 1 / -1;
}

.map-caption {
  margin-bottom: 10px;
  color: var(--gsmv-muted);
}

.species-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.species-list__row {
  display: grid;
  grid-template-columns: minmax(220px, 2fr) 120px minmax(180px, 1.2fr) minmax(180px, 1.2fr) auto;
  gap: 12px;
  align-items: center;
}

.species-list__species,
.species-list__count,
.species-list__behavior,
.species-list__comment {
  width: 100%;
}

.species-list__empty {
  padding: 16px;
  border: 1px dashed var(--gsmv-border);
  border-radius: 16px;
  color: var(--gsmv-muted);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.06), rgba(255, 255, 255, 0.03)),
    rgba(5, 24, 60, 0.76);
}

.env-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 8px;
}

.env-grid__item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 92px;
  padding: 16px 18px;
  border: 1px solid rgba(157, 233, 255, 0.16);
  border-radius: 20px;
  background:
    radial-gradient(circle at 12% 0%, rgba(110, 233, 255, 0.12), transparent 34%),
    linear-gradient(180deg, rgba(10, 41, 93, 0.92), rgba(5, 21, 58, 0.96));
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.05),
    0 14px 28px rgba(2, 14, 44, 0.2);
}

.env-grid__item strong {
  color: #f3fdff;
  font-size: 15px;
  letter-spacing: 0.04em;
}

.env-grid__item span {
  color: rgba(222, 246, 255, 0.88);
  font-size: 28px;
  font-weight: 700;
  line-height: 1.16;
  letter-spacing: -0.03em;
}

.quality-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-top: 8px;
  padding: 18px;
  border: 1px solid rgba(152, 225, 255, 0.2);
  border-radius: 24px;
  background:
    radial-gradient(circle at 12% 0%, rgba(83, 217, 255, 0.2), transparent 34%),
    linear-gradient(145deg, rgba(9, 42, 96, 0.94), rgba(5, 20, 58, 0.98));
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.05),
    0 18px 32px rgba(2, 14, 44, 0.18);
}

.quality-panel__header,
.quality-panel__score,
.quality-issue {
  display: flex;
  gap: 14px;
  align-items: flex-start;
}

.quality-panel__header {
  justify-content: space-between;
}

.quality-panel__header strong {
  display: block;
  color: #f6fdff;
  font-size: 18px;
}

.quality-panel__header p,
.quality-panel__score p,
.quality-issue p,
.quality-issue small {
  margin: 8px 0 0;
  color: rgba(225, 247, 255, 0.78);
  line-height: 1.75;
}

.quality-panel__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.quality-panel__issues {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.quality-issue {
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid rgba(174, 231, 255, 0.12);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.05), rgba(255, 255, 255, 0.02)),
    rgba(5, 24, 60, 0.72);
}

:deep(.el-drawer__body > .el-descriptions) {
  margin-top: 6px;
  border-radius: 22px;
  overflow: hidden;
  border: 1px solid rgba(152, 225, 255, 0.14);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.04),
    0 16px 28px rgba(2, 14, 44, 0.14);
}

:deep(.el-drawer__body > .el-descriptions:first-child) {
  position: relative;
  margin-top: 0;
  padding-top: 58px;
  background:
    radial-gradient(circle at 12% 0%, rgba(83, 217, 255, 0.18), transparent 34%),
    linear-gradient(145deg, rgba(9, 42, 96, 0.94), rgba(5, 20, 58, 0.98));
}

:deep(.el-drawer__body > .el-descriptions:first-child::before) {
  content: 'Observation Summary';
  position: absolute;
  top: 18px;
  left: 18px;
  display: inline-flex;
  align-items: center;
  padding: 7px 11px;
  border-radius: 999px;
  background: rgba(90, 232, 255, 0.1);
  border: 1px solid rgba(156, 241, 255, 0.18);
  color: #7feaff;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  z-index: 1;
}

:deep(.el-drawer__body > .el-descriptions:first-child .el-descriptions__table) {
  position: relative;
  z-index: 1;
}

:deep(.el-drawer__body > .el-descriptions:first-child .el-descriptions__content.el-descriptions__cell) {
  color: #f5fdff !important;
  font-size: 15px;
}

:deep(.el-drawer__body > .el-divider),
:deep(.el-drawer__body .quality-panel + .el-divider),
:deep(.el-drawer__body .env-grid + .el-divider),
:deep(.el-drawer__body .el-table + .el-divider) {
  margin: 24px 0 18px;
}

:deep(.el-drawer__body > .el-table),
:deep(.el-drawer__body > .version-history-panel),
:deep(.el-drawer__body > .quality-panel),
:deep(.el-drawer__body > .env-grid) {
  position: relative;
  margin-top: 6px;
}

:deep(.el-drawer__body > .env-grid) {
  padding-top: 46px;
}

:deep(.el-drawer__body > .env-grid::before) {
  content: 'Environmental Snapshot';
  position: absolute;
  top: 0;
  left: 0;
  display: inline-flex;
  align-items: center;
  padding: 7px 11px;
  border-radius: 999px;
  background: rgba(90, 232, 255, 0.1);
  border: 1px solid rgba(156, 241, 255, 0.18);
  color: #7feaff;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

:deep(.el-drawer__body > .quality-panel) {
  padding-top: 54px;
}

:deep(.el-drawer__body > .quality-panel::before) {
  content: 'AI Quality Review';
  position: absolute;
  top: 18px;
  left: 18px;
  display: inline-flex;
  align-items: center;
  padding: 7px 11px;
  border-radius: 999px;
  background: rgba(90, 232, 255, 0.1);
  border: 1px solid rgba(156, 241, 255, 0.18);
  color: #7feaff;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

:deep(.el-drawer__body > .el-table) {
  padding: 54px 10px 10px;
  border-radius: 22px;
  border: 1px solid rgba(150, 232, 255, 0.12);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.04), rgba(255, 255, 255, 0.02)),
    rgba(4, 20, 52, 0.62);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.04),
    0 16px 28px rgba(2, 14, 44, 0.14);
}

:deep(.el-drawer__body > .el-table::before) {
  content: 'Species Matrix';
  position: absolute;
  top: 14px;
  left: 14px;
  display: inline-flex;
  align-items: center;
  padding: 7px 11px;
  border-radius: 999px;
  background: rgba(90, 232, 255, 0.1);
  border: 1px solid rgba(156, 241, 255, 0.18);
  color: #7feaff;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  z-index: 2;
}

:deep(.el-drawer__body > .version-history-panel) {
  padding: 54px 14px 14px;
  border-radius: 22px;
  border: 1px solid rgba(150, 232, 255, 0.12);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.04), rgba(255, 255, 255, 0.02)),
    rgba(4, 20, 52, 0.62);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.04),
    0 16px 28px rgba(2, 14, 44, 0.14);
}

:deep(.el-drawer__body > .version-history-panel::before) {
  content: 'Version Timeline';
  position: absolute;
  top: 14px;
  left: 14px;
  display: inline-flex;
  align-items: center;
  padding: 7px 11px;
  border-radius: 999px;
  background: rgba(90, 232, 255, 0.1);
  border: 1px solid rgba(156, 241, 255, 0.18);
  color: #7feaff;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  z-index: 2;
}

@media (max-width: 1080px) {
  .ai-card__header,
  .quality-panel__header,
  .quality-panel__score,
  .quality-issue,
  .anomaly-item,
  .observation-form {
    grid-template-columns: 1fr;
    flex-direction: column;
  }

  .species-list__row {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .form-grid,
  .env-grid {
    grid-template-columns: 1fr;
  }
}
</style>
