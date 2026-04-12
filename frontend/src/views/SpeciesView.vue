<template>
  <div class="page-shell">
    <section class="page-hero">
      <div>
        <h2>物种档案</h2>
        <p>维护物种核心档案、分类路径、分布范围、多媒体资料与参考文献，并支持 AI 识图、补全、润色与翻译。</p>
      </div>
      <div class="hero-actions">
        <el-button type="primary" plain @click="openIdentifyDialog">AI 识图鉴定</el-button>
        <el-button v-if="canWrite" type="primary" @click="openCreate">新增物种</el-button>
      </div>
    </section>

    <el-card class="panel-card" shadow="never">
      <div class="toolbar toolbar--wrap">
        <el-input v-model="query.keyword" placeholder="中文名 / 学名" clearable style="max-width: 220px" />
        <el-cascader
          v-model="query.taxonId"
          :options="taxonOptions"
          :props="taxonCascaderProps"
          filterable
          clearable
          style="width: 240px"
          placeholder="按门纲目科属种筛选"
        />
        <el-input v-model="query.protectionLevel" placeholder="保护等级" clearable style="max-width: 150px" />
        <el-input v-model="query.iucnStatus" placeholder="濒危状态" clearable style="max-width: 150px" />
        <el-input v-model="query.distributionKeyword" placeholder="分布区域 / 地理范围" clearable style="max-width: 220px" />
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px">
          <el-option label="启用" :value="1" />
          <el-option label="归档" :value="0" />
        </el-select>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="chineseName" label="中文名" min-width="150" />
        <el-table-column prop="scientificName" label="学名" min-width="180" />
        <el-table-column label="分类路径" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.classificationPath || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="protectionLevel" label="保护等级" min-width="120" />
        <el-table-column prop="iucnStatus" label="濒危状态" min-width="110" />
        <el-table-column label="地理范围" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.geoRangeText || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '归档' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" min-width="180" />
        <el-table-column label="操作" fixed="right" :width="canWrite ? 220 : 90">
          <template #default="{ row }">
            <el-space>
              <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
              <el-button v-if="canWrite" link type="primary" @click="openEdit(row.id)">编辑</el-button>
              <el-button v-if="canWrite" link type="danger" @click="removeSpecies(row.id)">删除</el-button>
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

    <el-dialog v-model="identifyDialogVisible" title="AI 图像识别与物种鉴定" width="760px">
      <div class="identify-panel">
        <el-upload
          v-model:file-list="identifyFileList"
          action="#"
          :auto-upload="false"
          list-type="picture-card"
          accept="image/*"
          :limit="1"
        >
          <el-icon><Plus /></el-icon>
        </el-upload>

        <div class="identify-panel__actions">
          <span>上传海洋生物图片后，系统会调用百炼视觉模型进行识别。</span>
          <el-button type="primary" :loading="aiIdentifying" @click="runIdentifySpecies">开始识别</el-button>
        </div>

        <template v-if="identifyResult">
          <el-alert
            :type="identifyResult.needsHumanReview ? 'warning' : 'success'"
            :closable="false"
            show-icon
            :title="identifyResult.confidenceLabel"
          />

          <div class="identify-result">
            <div class="identify-result__main">
              <strong>{{ identifyResult.likelyChineseName || identifyResult.likelyScientificName || '未识别出明确物种' }}</strong>
              <span>{{ identifyResult.likelyScientificName || '待补充学名' }}</span>
              <p>{{ identifyResult.reasoning || '系统未返回额外说明。' }}</p>
              <el-tag effect="plain">置信度 {{ toPercent(identifyResult.confidence) }}</el-tag>
            </div>
            <el-button type="primary" plain @click="applyIdentifyResult">带入新建表单</el-button>
          </div>

          <div v-if="identifyResult.candidates.length" class="identify-section">
            <h3>候选列表</h3>
            <div class="candidate-list">
              <div v-for="item in identifyResult.candidates" :key="`${item.scientificName}-${item.confidence}`" class="candidate-item">
                <strong>{{ item.chineseName || item.scientificName || '候选项' }}</strong>
                <span>{{ item.scientificName || '待确认学名' }}</span>
                <small>置信度 {{ toPercent(item.confidence) }}</small>
                <p>{{ item.reason || '暂无补充说明' }}</p>
                <el-button plain size="small" @click="applyIdentifyCandidate(item)">采用此候选</el-button>
              </div>
            </div>
          </div>

          <div v-if="identifyResult.relatedSpeciesRecords.length" class="identify-section">
            <h3>关联的已有物种档案</h3>
            <div class="related-list">
              <button
                v-for="item in identifyResult.relatedSpeciesRecords"
                :key="item.id"
                type="button"
                class="related-item"
                @click="openDetail(item.id)"
              >
                <strong>{{ item.chineseName || item.scientificName }}</strong>
                <span>{{ item.scientificName }}</span>
                <small>{{ item.classificationPath || '暂无分类路径' }}</small>
              </button>
            </div>
          </div>

          <div class="identify-section">
            <h3>人工复核工单</h3>
            <el-input
              v-model="manualReviewNote"
              type="textarea"
              :rows="3"
              placeholder="可补充现场情况、拍摄环境或需要复核的重点，帮助科研人员更快确认。"
            />
            <div class="identify-panel__actions identify-panel__actions--review">
              <span>
                {{ identifyResult.needsHumanReview ? '当前识图置信度偏低，建议发起人工复核工单。' : '如需保留人工确认流程，也可以直接创建复核工单。' }}
              </span>
              <el-button type="warning" :loading="aiReviewSubmitting" @click="submitReviewTicket">发起人工复核工单</el-button>
            </div>
          </div>
        </template>
      </div>
    </el-dialog>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑物种' : '新增物种'" width="1120px" top="3vh">
      <div v-loading="dialogLoading">
        <el-card class="ai-card" shadow="never">
          <template #header>
            <div class="ai-card__header">
              <div>
                <strong>AI 助理</strong>
                <p>可根据名称自动补全分类和描述，也可以对已有文本做润色与翻译。</p>
              </div>
              <div class="ai-card__actions">
                <el-button type="primary" plain :loading="aiAutocompleting" @click="runAutocomplete">AI 补全档案</el-button>
                <el-select v-model="polishField" style="width: 170px">
                  <el-option label="物种简介" value="description" />
                  <el-option label="形态特征" value="morphology" />
                  <el-option label="生活习性" value="habit" />
                  <el-option label="栖息环境" value="habitat" />
                  <el-option label="分布区域" value="distribution" />
                  <el-option label="地理范围" value="geoRangeText" />
                </el-select>
                <el-button plain :loading="aiPolishing" @click="runPolish">润色当前字段</el-button>
                <el-select v-model="translationTarget" style="width: 150px">
                  <el-option label="英文" value="English" />
                  <el-option label="日文" value="Japanese" />
                  <el-option label="西班牙文" value="Spanish" />
                </el-select>
                <el-button plain :loading="aiTranslating" @click="runTranslate">翻译描述</el-button>
              </div>
            </div>
          </template>

          <div v-if="aiSummary || aiNotes.length || autocompleteRelatedSpecies.length" class="ai-card__body">
            <div v-if="aiSummary" class="ai-summary">
              <strong>AI 摘要</strong>
              <p>{{ aiSummary }}</p>
            </div>

            <div v-if="aiNotes.length" class="ai-note-list">
              <el-tag v-for="item in aiNotes" :key="item" effect="plain" round>{{ item }}</el-tag>
            </div>

            <div v-if="autocompleteRelatedSpecies.length" class="related-list">
              <button
                v-for="item in autocompleteRelatedSpecies"
                :key="item.id"
                type="button"
                class="related-item"
                @click="openDetail(item.id)"
              >
                <strong>{{ item.chineseName || item.scientificName }}</strong>
                <span>{{ item.scientificName }}</span>
                <small>{{ item.classificationPath || '暂无分类路径' }}</small>
              </button>
            </div>
          </div>
        </el-card>

        <el-card v-if="translationResult" class="ai-card translation-card" shadow="never">
          <template #header>
            <div class="translation-card__header">
              <strong>多语言翻译结果</strong>
              <el-tag effect="plain">{{ translationResult.targetLanguage }}</el-tag>
            </div>
          </template>
          <div class="translation-grid">
            <div v-if="translationResult.description" class="translation-item">
              <h3>物种简介</h3>
              <p>{{ translationResult.description }}</p>
            </div>
            <div v-if="translationResult.morphology" class="translation-item">
              <h3>形态特征</h3>
              <p>{{ translationResult.morphology }}</p>
            </div>
            <div v-if="translationResult.habit" class="translation-item">
              <h3>生活习性</h3>
              <p>{{ translationResult.habit }}</p>
            </div>
            <div v-if="translationResult.habitat" class="translation-item">
              <h3>栖息环境</h3>
              <p>{{ translationResult.habitat }}</p>
            </div>
            <div v-if="translationResult.distribution" class="translation-item">
              <h3>分布区域</h3>
              <p>{{ translationResult.distribution }}</p>
            </div>
            <div v-if="translationResult.geoRangeText" class="translation-item">
              <h3>地理范围</h3>
              <p>{{ translationResult.geoRangeText }}</p>
            </div>
          </div>
          <div v-if="translationResult.summary" class="translation-summary">
            <strong>翻译摘要</strong>
            <p>{{ translationResult.summary }}</p>
          </div>
        </el-card>

        <el-form label-position="top">
          <div class="species-form__grid">
            <el-form-item label="中文名">
              <el-input v-model="form.chineseName" />
            </el-form-item>
            <el-form-item label="学名">
              <el-input v-model="form.scientificName" />
            </el-form-item>
            <el-form-item label="门">
              <el-input v-model="form.phylumName" />
            </el-form-item>
            <el-form-item label="纲">
              <el-input v-model="form.className" />
            </el-form-item>
            <el-form-item label="目">
              <el-input v-model="form.orderName" />
            </el-form-item>
            <el-form-item label="科">
              <el-input v-model="form.familyName" />
            </el-form-item>
            <el-form-item label="属">
              <el-input v-model="form.genusName" />
            </el-form-item>
            <el-form-item label="保护等级">
              <el-input v-model="form.protectionLevel" placeholder="如：国家一级保护" />
            </el-form-item>
            <el-form-item label="濒危状态">
              <el-input v-model="form.iucnStatus" placeholder="如：VU / EN / CR" />
            </el-form-item>
            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio :value="1">启用</el-radio>
                <el-radio :value="0">归档</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="分布纬度">
              <el-input-number v-model="form.distributionLat" :precision="6" :step="0.000001" style="width: 100%" />
            </el-form-item>
            <el-form-item label="分布经度">
              <el-input-number v-model="form.distributionLng" :precision="6" :step="0.000001" style="width: 100%" />
            </el-form-item>
          </div>

          <el-form-item label="形态特征">
            <el-input v-model="form.morphology" type="textarea" :rows="3" />
          </el-form-item>
          <el-form-item label="生活习性">
            <el-input v-model="form.habit" type="textarea" :rows="3" />
          </el-form-item>
          <el-form-item label="栖息环境">
            <el-input v-model="form.habitat" type="textarea" :rows="2" />
          </el-form-item>
          <el-form-item label="分布区域描述">
            <el-input v-model="form.distribution" type="textarea" :rows="2" />
          </el-form-item>
          <el-form-item label="地理范围">
            <el-input v-model="form.geoRangeText" type="textarea" :rows="2" placeholder="如：湛江近海、雷州湾北部海域" />
          </el-form-item>
          <el-form-item label="视频链接">
            <el-input v-model="form.videoUrl" placeholder="https://..." />
          </el-form-item>
          <el-form-item label="物种简介">
            <el-input v-model="form.description" type="textarea" :rows="3" />
          </el-form-item>
          <el-form-item label="参考文献">
            <el-input
              v-model="form.referenceText"
              type="textarea"
              :rows="4"
              placeholder="每行一条参考文献或 DOI / 链接"
            />
          </el-form-item>

          <el-form-item v-if="existingImages.length" label="已上传图片">
            <div class="species-image-grid">
              <el-image
                v-for="item in existingImages"
                :key="item.id"
                :src="item.url"
                :preview-src-list="existingImageUrls"
                fit="cover"
                class="species-image-grid__item"
              />
            </div>
          </el-form-item>

          <el-form-item label="图片上传">
            <el-upload
              v-model:file-list="pendingImageFiles"
              action="#"
              :auto-upload="false"
              list-type="picture-card"
              accept="image/*"
              multiple
            >
              <el-icon><Plus /></el-icon>
            </el-upload>
            <div class="field-tip">支持一次选择多张图片，保存物种后会自动上传。</div>
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { Plus } from '@element-plus/icons-vue'
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { CascaderOption, UploadUserFile } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  autocompleteSpeciesProfile,
  identifySpeciesByImage,
  polishSpeciesText,
  translateSpeciesProfile,
} from '@/api/ai'
import { createAiReviewTicket } from '@/api/aiReview'
import {
  createSpecies,
  deleteSpecies,
  fetchSpecies,
  fetchSpeciesDetail,
  fetchTaxa,
  updateSpecies,
  uploadSpeciesImage,
} from '@/api/species'
import { useAuthStore } from '@/stores/auth'
import { listenDataChanged, notifyDataChanged } from '@/utils/dataSync'
import type {
  AiIdentificationCandidate,
  AiIdentifyImageResponse,
  AiRelatedSpeciesRecord,
  AiTranslateSpeciesResponse,
  SpeciesDetailView,
  SpeciesImageView,
  SpeciesView,
  TaxonOption,
} from '@/types/gsmv'

type PolishField = 'description' | 'morphology' | 'habit' | 'habitat' | 'distribution' | 'geoRangeText'

const router = useRouter()
const authStore = useAuthStore()

const loading = ref(false)
const dialogLoading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const identifyDialogVisible = ref(false)
const editingId = ref<number | null>(null)
const rows = ref<SpeciesView[]>([])
const taxa = ref<TaxonOption[]>([])
const existingImages = ref<SpeciesImageView[]>([])
const pendingImageFiles = ref<UploadUserFile[]>([])
const identifyFileList = ref<UploadUserFile[]>([])
const identifyResult = ref<AiIdentifyImageResponse | null>(null)
const autocompleteRelatedSpecies = ref<AiRelatedSpeciesRecord[]>([])
const translationResult = ref<AiTranslateSpeciesResponse | null>(null)
const aiSummary = ref('')
const aiNotes = ref<string[]>([])
const aiIdentifying = ref(false)
const aiAutocompleting = ref(false)
const aiPolishing = ref(false)
const aiTranslating = ref(false)
const aiReviewSubmitting = ref(false)
const polishField = ref<PolishField>('description')
const translationTarget = ref('English')
const manualReviewNote = ref('')
let stopDataSync: (() => void) | undefined
let refreshTimer: number | undefined

const canWrite = computed(() => authStore.authorities.includes('SPECIES_WRITE'))
const existingImageUrls = computed(() => existingImages.value.map((item) => item.url))
const taxonCascaderProps = {
  checkStrictly: true,
  emitPath: false,
  value: 'id',
  label: 'label',
  children: 'children',
}

const query = reactive({
  keyword: '',
  taxonId: undefined as number | undefined,
  protectionLevel: '',
  iucnStatus: '',
  distributionKeyword: '',
  status: undefined as number | undefined,
})

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0,
})

const form = reactive({
  phylumName: '',
  className: '',
  orderName: '',
  familyName: '',
  genusName: '',
  scientificName: '',
  chineseName: '',
  protectionLevel: '',
  iucnStatus: '',
  description: '',
  morphology: '',
  habit: '',
  habitat: '',
  distribution: '',
  distributionLat: null as number | null,
  distributionLng: null as number | null,
  geoRangeText: '',
  videoUrl: '',
  referenceText: '',
  status: 1,
})

const taxonOptions = computed<CascaderOption[]>(() => buildTaxonOptions(taxa.value))

function buildTaxonOptions(source: TaxonOption[]) {
  const nodeMap = new Map<number, CascaderOption & { id: number }>()
  const roots: (CascaderOption & { id: number })[] = []

  source.forEach((item) => {
    nodeMap.set(item.id, {
      id: item.id,
      value: item.id,
      label: `${item.scientificName}${item.chineseName ? ` / ${item.chineseName}` : ''}`,
      children: [],
    })
  })

  source.forEach((item) => {
    const node = nodeMap.get(item.id)
    if (!node) {
      return
    }
    if (item.parentId && nodeMap.has(item.parentId)) {
      ;(nodeMap.get(item.parentId)?.children as CascaderOption[]).push(node)
    } else {
      roots.push(node)
    }
  })

  return roots
}

function resetForm() {
  form.phylumName = ''
  form.className = ''
  form.orderName = ''
  form.familyName = ''
  form.genusName = ''
  form.scientificName = ''
  form.chineseName = ''
  form.protectionLevel = ''
  form.iucnStatus = ''
  form.description = ''
  form.morphology = ''
  form.habit = ''
  form.habitat = ''
  form.distribution = ''
  form.distributionLat = null
  form.distributionLng = null
  form.geoRangeText = ''
  form.videoUrl = ''
  form.referenceText = ''
  form.status = 1
  existingImages.value = []
  pendingImageFiles.value = []
  resetAiState()
}

function resetAiState() {
  identifyResult.value = null
  autocompleteRelatedSpecies.value = []
  translationResult.value = null
  aiSummary.value = ''
  aiNotes.value = []
  manualReviewNote.value = ''
}

function fillForm(detail: SpeciesDetailView) {
  form.phylumName = detail.phylumName || ''
  form.className = detail.className || ''
  form.orderName = detail.orderName || ''
  form.familyName = detail.familyName || ''
  form.genusName = detail.genusName || ''
  form.scientificName = detail.scientificName || ''
  form.chineseName = detail.chineseName || ''
  form.protectionLevel = detail.protectionLevel || ''
  form.iucnStatus = detail.iucnStatus || ''
  form.description = detail.description || ''
  form.morphology = detail.morphology || ''
  form.habit = detail.habit || ''
  form.habitat = detail.habitat || ''
  form.distribution = detail.distribution || ''
  form.distributionLat = detail.distributionLat ?? null
  form.distributionLng = detail.distributionLng ?? null
  form.geoRangeText = detail.geoRangeText || ''
  form.videoUrl = detail.videoUrl || ''
  form.referenceText = detail.referenceText || ''
  form.status = detail.status
  existingImages.value = detail.images || []
  pendingImageFiles.value = []
  resetAiState()
}

async function loadTaxa() {
  taxa.value = await fetchTaxa()
}

async function loadData() {
  if (loading.value) {
    return
  }

  loading.value = true
  try {
    const pageData = await fetchSpecies({
      keyword: query.keyword || undefined,
      taxonId: query.taxonId,
      protectionLevel: query.protectionLevel || undefined,
      iucnStatus: query.iucnStatus || undefined,
      distributionKeyword: query.distributionKeyword || undefined,
      status: query.status,
      page: pagination.page,
      size: pagination.size,
    })
    rows.value = pageData.items
    pagination.total = pageData.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '物种数据加载失败')
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
  query.taxonId = undefined
  query.protectionLevel = ''
  query.iucnStatus = ''
  query.distributionKeyword = ''
  query.status = undefined
  pagination.page = 1
  void loadData()
}

function openCreate() {
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openIdentifyDialog() {
  identifyDialogVisible.value = true
  identifyFileList.value = []
  identifyResult.value = null
  manualReviewNote.value = ''
}

async function runIdentifySpecies() {
  const file = identifyFileList.value[0]?.raw
  if (!file) {
    ElMessage.warning('请先上传需要识别的图片')
    return
  }

  aiIdentifying.value = true
  try {
    identifyResult.value = await identifySpeciesByImage(file)
    ElMessage.success('识图分析已完成')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '图片识别失败')
  } finally {
    aiIdentifying.value = false
  }
}

function applyIdentifyResult() {
  if (!identifyResult.value) {
    return
  }
  editingId.value = null
  resetForm()
  form.chineseName = identifyResult.value.likelyChineseName || ''
  form.scientificName = identifyResult.value.likelyScientificName || ''
  autocompleteRelatedSpecies.value = identifyResult.value.relatedSpeciesRecords || []
  aiSummary.value = identifyResult.value.reasoning || ''
  aiNotes.value = identifyResult.value.needsHumanReview ? ['当前识别建议人工复核'] : []
  identifyDialogVisible.value = false
  dialogVisible.value = true
}

function applyIdentifyCandidate(candidate: AiIdentificationCandidate) {
  editingId.value = null
  resetForm()
  form.chineseName = candidate.chineseName || ''
  form.scientificName = candidate.scientificName || ''
  autocompleteRelatedSpecies.value = identifyResult.value?.relatedSpeciesRecords || []
  aiSummary.value = candidate.reason || identifyResult.value?.reasoning || ''
  aiNotes.value = ['当前识图结果来自候选项，请结合图片与档案信息人工确认']
  identifyDialogVisible.value = false
  dialogVisible.value = true
}

async function submitReviewTicket() {
  const file = identifyFileList.value[0]?.raw
  if (!file || !identifyResult.value) {
    ElMessage.warning('请先完成识图分析后再创建复核工单')
    return
  }

  aiReviewSubmitting.value = true
  try {
    const ticket = await createAiReviewTicket(
      {
        likelyChineseName: identifyResult.value.likelyChineseName,
        likelyScientificName: identifyResult.value.likelyScientificName,
        confidence: identifyResult.value.confidence,
        needsHumanReview: identifyResult.value.needsHumanReview,
        reasoning: identifyResult.value.reasoning,
        candidates: identifyResult.value.candidates,
        relatedSpeciesRecords: identifyResult.value.relatedSpeciesRecords,
        submitNote: manualReviewNote.value || undefined,
      },
      file,
    )
    notifyDataChanged('aiReview')
    identifyDialogVisible.value = false
    manualReviewNote.value = ''
    ElMessage.success(
      authStore.authorities.includes('AI_REVIEW_READ')
        ? `已创建人工复核工单 #${ticket.id}，可前往 AI复核 页面继续处理`
        : `已创建人工复核工单 #${ticket.id}`,
    )
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '人工复核工单创建失败')
  } finally {
    aiReviewSubmitting.value = false
  }
}

async function openEdit(id: number) {
  editingId.value = id
  resetForm()
  dialogVisible.value = true
  dialogLoading.value = true
  try {
    fillForm(await fetchSpeciesDetail(id))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '物种详情加载失败')
    dialogVisible.value = false
  } finally {
    dialogLoading.value = false
  }
}

function openDetail(id: number) {
  router.push(`/species/${id}`)
}

function buildPayload() {
  return {
    phylumName: form.phylumName.trim(),
    className: form.className.trim(),
    orderName: form.orderName.trim(),
    familyName: form.familyName.trim(),
    genusName: form.genusName.trim(),
    scientificName: form.scientificName.trim(),
    chineseName: form.chineseName.trim(),
    protectionLevel: form.protectionLevel.trim() || undefined,
    iucnStatus: form.iucnStatus.trim() || undefined,
    description: form.description.trim() || undefined,
    morphology: form.morphology.trim() || undefined,
    habit: form.habit.trim() || undefined,
    habitat: form.habitat.trim() || undefined,
    distribution: form.distribution.trim() || undefined,
    distributionLat: form.distributionLat ?? undefined,
    distributionLng: form.distributionLng ?? undefined,
    geoRangeText: form.geoRangeText.trim() || undefined,
    videoUrl: form.videoUrl.trim() || undefined,
    referenceText: form.referenceText.trim() || undefined,
    status: form.status,
  }
}

function validateForm() {
  const requiredFields = [
    form.chineseName,
    form.scientificName,
    form.phylumName,
    form.className,
    form.orderName,
    form.familyName,
    form.genusName,
  ]
  return requiredFields.every((value) => value.trim())
}

function currentFieldValue(field: PolishField) {
  return form[field]?.trim?.() || ''
}

function applyPolishedText(field: PolishField, value: string) {
  form[field] = value
}

async function runAutocomplete() {
  if (!form.chineseName.trim() && !form.scientificName.trim()) {
    ElMessage.warning('请先填写中文名或学名')
    return
  }

  aiAutocompleting.value = true
  try {
    const result = await autocompleteSpeciesProfile({
      chineseName: form.chineseName.trim() || undefined,
      scientificName: form.scientificName.trim() || undefined,
      description: form.description.trim() || undefined,
      morphology: form.morphology.trim() || undefined,
      habit: form.habit.trim() || undefined,
      habitat: form.habitat.trim() || undefined,
      distribution: form.distribution.trim() || undefined,
      geoRangeText: form.geoRangeText.trim() || undefined,
    })

    form.chineseName = result.chineseName || form.chineseName
    form.scientificName = result.scientificName || form.scientificName
    form.phylumName = result.phylumName || form.phylumName
    form.className = result.className || form.className
    form.orderName = result.orderName || form.orderName
    form.familyName = result.familyName || form.familyName
    form.genusName = result.genusName || form.genusName
    form.protectionLevel = result.protectionLevel || form.protectionLevel
    form.iucnStatus = result.iucnStatus || form.iucnStatus
    form.description = result.description || form.description
    form.morphology = result.morphology || form.morphology
    form.habit = result.habit || form.habit
    form.habitat = result.habitat || form.habitat
    form.distribution = result.distribution || form.distribution
    form.geoRangeText = result.geoRangeText || form.geoRangeText
    aiSummary.value = result.summary || ''
    aiNotes.value = result.notes || []
    autocompleteRelatedSpecies.value = result.relatedSpeciesRecords || []
    ElMessage.success(`AI 补全完成，当前参考置信度 ${toPercent(result.confidence)}`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'AI 补全失败')
  } finally {
    aiAutocompleting.value = false
  }
}

async function runPolish() {
  const fieldValue = currentFieldValue(polishField.value)
  if (!fieldValue) {
    ElMessage.warning('当前字段还没有可润色的内容')
    return
  }

  aiPolishing.value = true
  try {
    const result = await polishSpeciesText({
      fieldName: polishFieldLabel(polishField.value),
      text: fieldValue,
    })
    applyPolishedText(polishField.value, result.polishedText || fieldValue)
    aiSummary.value = result.summary || aiSummary.value
    aiNotes.value = result.keywords || []
    ElMessage.success('文本润色完成')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '文本润色失败')
  } finally {
    aiPolishing.value = false
  }
}

async function runTranslate() {
  aiTranslating.value = true
  try {
    translationResult.value = await translateSpeciesProfile({
      chineseName: form.chineseName.trim() || undefined,
      scientificName: form.scientificName.trim() || undefined,
      description: form.description.trim() || undefined,
      morphology: form.morphology.trim() || undefined,
      habit: form.habit.trim() || undefined,
      habitat: form.habitat.trim() || undefined,
      distribution: form.distribution.trim() || undefined,
      geoRangeText: form.geoRangeText.trim() || undefined,
      targetLanguage: translationTarget.value,
    })
    ElMessage.success(`已生成${translationTargetLabel(translationTarget.value)}翻译`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '翻译生成失败')
  } finally {
    aiTranslating.value = false
  }
}

function polishFieldLabel(field: PolishField) {
  switch (field) {
    case 'description':
      return '物种简介'
    case 'morphology':
      return '形态特征'
    case 'habit':
      return '生活习性'
    case 'habitat':
      return '栖息环境'
    case 'distribution':
      return '分布区域'
    case 'geoRangeText':
      return '地理范围'
    default:
      return '字段'
  }
}

function translationTargetLabel(value: string) {
  if (value === 'English') return '英文'
  if (value === 'Japanese') return '日文'
  if (value === 'Spanish') return '西班牙文'
  return value
}

function toPercent(value: number) {
  return `${Math.round((value || 0) * 100)}%`
}

async function uploadPendingImages(speciesId: number) {
  const rawFiles = pendingImageFiles.value
    .map((item) => item.raw)
    .filter((file) => Boolean(file)) as File[]

  for (const file of rawFiles) {
    await uploadSpeciesImage(speciesId, file)
  }

  return rawFiles.length
}

async function submit() {
  if (!validateForm()) {
    ElMessage.warning('请完整填写中文名、学名和门纲目科属信息')
    return
  }

  submitting.value = true
  try {
    const payload = buildPayload()
    const saved = editingId.value ? await updateSpecies(editingId.value, payload) : await createSpecies(payload)
    let uploadedCount = 0

    try {
      uploadedCount = await uploadPendingImages(saved.id)
    } catch (uploadError) {
      ElMessage.warning(
        uploadError instanceof Error
          ? `物种已保存，但图片上传失败：${uploadError.message}`
          : '物种已保存，但图片上传失败',
      )
    }

    notifyDataChanged('species')
    dialogVisible.value = false
    await Promise.all([loadTaxa(), loadData()])
    ElMessage.success(uploadedCount > 0 ? `物种已保存，并上传 ${uploadedCount} 张图片` : '物种已保存')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  } finally {
    submitting.value = false
  }
}

async function removeSpecies(id: number) {
  try {
    await ElMessageBox.confirm('删除后将清理该物种的档案和图片，且无法恢复。确认继续吗？', '删除物种', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
    })
    await deleteSpecies(id)
    notifyDataChanged('species')
    ElMessage.success('物种已删除')
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
}

function handleVisibilityChange() {
  if (!document.hidden) {
    void loadData()
  }
}

onMounted(async () => {
  stopDataSync = listenDataChanged((detail) => {
    if (detail.type === 'species') {
      void Promise.all([loadTaxa(), loadData()])
    }
  })
  window.addEventListener('focus', handleFocus)
  document.addEventListener('visibilitychange', handleVisibilityChange)
  refreshTimer = window.setInterval(() => {
    if (!document.hidden) {
      void loadData()
    }
  }, 10000)
  await Promise.all([loadTaxa(), loadData()])
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
.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

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
  border-radius: 24px;
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

.ai-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: flex-end;
}

.ai-card__body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.ai-summary {
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(7, 49, 106, 0.58);
  border: 1px solid rgba(177, 234, 247, 0.14);
}

.ai-summary p {
  margin: 8px 0 0;
  color: var(--gsmv-muted);
  line-height: 1.8;
}

.ai-note-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.translation-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.translation-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.translation-item {
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(7, 49, 106, 0.52);
  border: 1px solid rgba(177, 234, 247, 0.12);
}

.translation-item h3,
.identify-section h3 {
  margin: 0 0 8px;
  font-size: 15px;
}

.translation-item p,
.translation-summary p {
  margin: 0;
  line-height: 1.8;
  color: var(--gsmv-muted);
}

.translation-summary {
  margin-top: 14px;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(7, 49, 106, 0.44);
}

.species-form__grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0 16px;
}

.species-image-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 12px;
  width: 100%;
}

.species-image-grid__item {
  width: 100%;
  height: 120px;
  border-radius: 16px;
  overflow: hidden;
}

.field-tip {
  margin-top: 8px;
  color: var(--gsmv-muted);
  font-size: 13px;
}

.identify-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.identify-panel__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--gsmv-muted);
}

.identify-result {
  display: flex;
  gap: 16px;
  justify-content: space-between;
  align-items: flex-start;
  padding: 18px;
  border-radius: 22px;
  background: rgba(7, 49, 106, 0.58);
  border: 1px solid rgba(177, 234, 247, 0.14);
}

.identify-result__main {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.identify-result__main strong {
  font-size: 20px;
}

.identify-result__main span {
  color: var(--gsmv-primary);
}

.identify-result__main p {
  margin: 0;
  color: var(--gsmv-muted);
  line-height: 1.75;
}

.identify-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.candidate-list,
.related-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
}

.candidate-item,
.related-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px;
  border-radius: 18px;
  border: 1px solid rgba(177, 234, 247, 0.12);
  background: rgba(7, 49, 106, 0.5);
  text-align: left;
}

.candidate-item span,
.candidate-item p,
.related-item span,
.related-item small {
  color: var(--gsmv-muted);
  line-height: 1.7;
}

.candidate-item p {
  margin: 0;
}

.related-item {
  cursor: pointer;
  transition:
    transform 0.18s ease,
    border-color 0.18s ease;
}

.related-item:hover {
  transform: translateY(-1px);
  border-color: rgba(177, 234, 247, 0.24);
}

@media (max-width: 1100px) {
  .ai-card__header,
  .identify-result,
  .identify-panel__actions {
    flex-direction: column;
    align-items: flex-start;
  }
}

@media (max-width: 980px) {
  .species-form__grid,
  .translation-grid {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 640px) {
  .species-form__grid,
  .translation-grid {
    grid-template-columns: 1fr;
  }
}
</style>
