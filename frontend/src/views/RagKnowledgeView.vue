<template>
  <div class="page-shell rag-page">
    <section class="page-hero rag-hero">
      <div>
        <span class="rag-hero__eyebrow">RETRIEVAL AUGMENTED GENERATION</span>
        <h2>RAG 知识中台</h2>
        <p>把系统数据、科研报告、公开资料和上传文档切成可追溯证据，让全系统 AI 先检索、再判断、再组织。</p>
      </div>
      <div class="rag-hero__actions">
        <el-upload
          :auto-upload="false"
          :show-file-list="false"
          accept=".pdf,.docx,.txt,.md"
          :on-change="handleFileChange"
        >
          <el-button type="primary" :loading="uploading">上传资料</el-button>
        </el-upload>
        <input
          ref="multiFileInputRef"
          class="rag-hidden-file-input"
          type="file"
          accept=".pdf,.docx,.txt,.md"
          multiple
          @change="handleMultiFileInput"
        />
        <el-button type="primary" plain :loading="ingesting" @click="openMultiFilePicker">批量上传</el-button>
        <el-button type="success" :loading="rebuilding" @click="rebuildAll">全量重建</el-button>
      </div>
    </section>

    <section class="rag-metrics">
      <article>
        <span>知识文档</span>
        <strong>{{ pagination.total }}</strong>
        <small>系统实体与上传资料统一纳入索引</small>
      </article>
      <article>
        <span>当前页可用</span>
        <strong>{{ readyCount }}</strong>
        <small>READY 状态代表已完成向量化</small>
      </article>
      <article>
        <span>选中文档分块</span>
        <strong>{{ selected?.chunks.length || 0 }}</strong>
        <small>每块保留来源、摘要和可检索正文</small>
      </article>
    </section>

    <section class="knowledge-console">
      <article class="knowledge-console__status">
        <span>Qdrant 向量库</span>
        <strong>{{ qdrantStatus?.status || '检测中' }}</strong>
        <small>
          points {{ qdrantStatus?.pointsCount ?? 0 }} / ready chunks {{ qdrantStatus?.readyChunks ?? 0 }}
        </small>
        <p v-if="qdrantStatus?.errorMessage">{{ qdrantStatus.errorMessage }}</p>
        <div>
          <el-button plain :loading="rebuildingQdrant" @click="rebuildQdrant">重建 Qdrant</el-button>
          <el-button text @click="loadQdrantStatus">刷新状态</el-button>
        </div>
      </article>

      <article class="knowledge-console__ingest">
        <span>本地文件夹导入</span>
        <el-input v-model="folderPath" placeholder="例如 D:\\资料\\海洋生物RAG" clearable />
        <small>适合一次导入很多 PDF、DOCX、TXT、MD。系统会逐个生成任务项，失败不会影响整批。</small>
        <el-button type="primary" :loading="ingesting" @click="runFolderIngest">递归导入</el-button>
      </article>

      <article class="knowledge-console__ingest">
        <span>公开知识采集</span>
        <div class="knowledge-console__inline">
          <el-select v-model="externalSource" style="width: 150px">
            <el-option v-for="item in sources" :key="item.code" :label="item.code" :value="item.code" />
          </el-select>
          <el-input
            v-model="externalQuery"
            :placeholder="externalSource === 'WEB_PDF' ? '一行一个白名单PDF/DOCX/TXT/MD链接' : '学名、中文名或主题关键词'"
            clearable
          />
        </div>
        <el-input
          v-if="externalSource === 'WEB_PDF'"
          v-model="webUrls"
          type="textarea"
          :rows="3"
          placeholder="https://example.org/report.pdf"
        />
        <el-button type="primary" plain :loading="ingesting" @click="runExternalIngest">开始采集</el-button>
      </article>
    </section>

    <section class="rag-grid">
      <el-card class="panel-card rag-list-card" shadow="never">
        <template #header>
          <div class="panel-header">
            <strong>知识文档</strong>
            <el-button text type="primary" :loading="loading" @click="loadDocuments">刷新</el-button>
          </div>
        </template>

        <div class="rag-filters">
          <el-input v-model="filters.keyword" placeholder="标题 / 文件名 / 失败原因" clearable @keyup.enter="loadDocuments" />
          <el-select v-model="filters.sourceType" placeholder="来源类型" clearable>
            <el-option v-for="item in sourceTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="filters.status" placeholder="索引状态" clearable>
            <el-option label="可用" value="READY" />
            <el-option label="等待中" value="PENDING" />
            <el-option label="失败" value="FAILED" />
          </el-select>
          <el-button type="primary" @click="loadDocuments">查询</el-button>
        </div>

        <el-table
          :data="rows"
          v-loading="loading"
          row-key="id"
          stripe
          highlight-current-row
          @row-click="openDocument"
        >
          <el-table-column label="标题" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="rag-title-cell">
                <strong>{{ row.title }}</strong>
                <span>{{ sourceTypeLabel(row.sourceType) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" effect="dark" round>{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="chunkCount" label="分块" width="86" />
          <el-table-column prop="updatedAt" label="更新时间" min-width="170" show-overflow-tooltip />
          <el-table-column label="操作" width="130" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click.stop="openDocument(row)">详情</el-button>
              <el-button
                v-if="canDeleteDocument(row)"
                link
                type="danger"
                @click.stop="deleteDocument(row)"
              >
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="table-footer">
          <el-pagination
            v-model:current-page="pagination.page"
            v-model:page-size="pagination.size"
            layout="total, prev, pager, next"
            :total="pagination.total"
            @current-change="loadDocuments"
          />
        </div>
      </el-card>

      <aside class="rag-side">
        <el-card class="panel-card rag-detail-card" shadow="never">
          <template #header>
            <div class="panel-header">
              <strong>文档详情</strong>
              <el-tag v-if="selected" :type="statusType(selected.document.status)" effect="dark" round>
                {{ statusLabel(selected.document.status) }}
              </el-tag>
            </div>
          </template>

          <template v-if="selected">
            <div class="rag-detail-cover">
              <span>{{ sourceTypeLabel(selected.document.sourceType) }}</span>
              <h3>{{ selected.document.title }}</h3>
              <p v-if="selected.document.errorMessage">{{ selected.document.errorMessage }}</p>
              <p v-else>已生成 {{ selected.document.chunkCount }} 个知识分块，可被 AI 助手、科研报告和物种补全召回。</p>
            </div>

            <div class="rag-chunks">
              <article v-for="chunk in selected.chunks" :key="chunk.id">
                <div>
                  <strong>#{{ chunk.chunkIndex + 1 }} {{ chunk.title }}</strong>
                  <span>{{ chunk.characterCount }} 字</span>
                  <el-button link type="primary" @click="openChunkDetail(chunk)">详情</el-button>
                </div>
                <p>{{ chunk.summary || chunk.content }}</p>
              </article>
              <el-empty v-if="!selected.chunks.length" description="暂无可用分块，查看失败原因或重新构建索引。" />
            </div>
          </template>

          <el-empty v-else description="选择左侧文档查看分块、状态和失败原因。" />
        </el-card>

        <el-card class="panel-card rag-search-card" shadow="never">
          <template #header>
            <div class="panel-header">
              <strong>检索测试</strong>
              <span>{{ searchResults.length }} 条结果</span>
            </div>
          </template>

          <div class="rag-search-box">
            <el-input
              v-model="searchQuery"
              type="textarea"
              :rows="3"
              placeholder="例如：湛江近海高保护等级物种有哪些？"
            />
            <el-button type="primary" :loading="searching" @click="runSearch">测试召回</el-button>
          </div>

          <div class="rag-search-results">
            <article v-for="item in searchResults" :key="item.chunkId">
              <div>
                <strong>{{ item.title }}</strong>
                <el-tag effect="plain" round>{{ sourceTypeLabel(item.sourceType) }}</el-tag>
              </div>
              <p>{{ item.summary || item.content }}</p>
              <footer>
                <span>score {{ formatScore(item.score) }}</span>
                <RouterLink v-if="item.sourcePath" :to="item.sourcePath">打开来源</RouterLink>
              </footer>
            </article>
            <el-empty v-if="searched && !searchResults.length" description="没有召回结果，换个关键词或先重建索引试试。" />
          </div>
        </el-card>

        <el-card class="panel-card rag-jobs-card" shadow="never">
          <template #header>
            <div class="panel-header">
              <strong>索引任务</strong>
              <el-button text type="primary" :loading="jobsLoading" @click="loadJobs">刷新</el-button>
            </div>
          </template>

          <div class="rag-jobs">
            <article v-for="job in jobs" :key="job.id">
              <strong>{{ job.jobType }}</strong>
              <span>{{ job.status }} · 成功 {{ job.successCount }} / 失败 {{ job.failedCount }}</span>
              <small v-if="job.errorMessage">{{ job.errorMessage }}</small>
            </article>
            <el-empty v-if="!jobs.length" description="暂无索引任务" />
          </div>

          <div class="rag-jobs rag-jobs--ingest">
            <h4>导入任务</h4>
            <article v-for="job in ingestJobs" :key="job.id">
              <strong>{{ job.sourceCode || job.jobType }}</strong>
              <span>{{ job.status }} · {{ job.processedItems }}/{{ job.totalItems }}</span>
              <small v-if="job.errorMessage">{{ job.errorMessage }}</small>
              <el-button v-if="job.failedCount" link type="warning" :loading="ingesting" @click="retryIngest(job)">重试失败项</el-button>
            </article>
            <div v-if="ingestItems.length" class="rag-ingest-items">
              <span v-for="item in ingestItems" :key="item.id">
                {{ item.title || item.localPath }} · {{ item.status }}
              </span>
            </div>
          </div>
        </el-card>
      </aside>
    </section>

    <el-dialog
      v-model="chunkDetailVisible"
      class="rag-chunk-dialog"
      width="780px"
      append-to-body
      destroy-on-close
    >
      <template #header>
        <div class="rag-chunk-dialog__header">
          <span>知识分块详情</span>
          <strong v-if="selectedChunk">#{{ selectedChunk.chunkIndex + 1 }} {{ selectedChunk.title }}</strong>
        </div>
      </template>

      <template v-if="selectedChunk">
        <div class="rag-chunk-dialog__meta">
          <span>{{ sourceTypeLabel(selectedChunk.sourceType) }}</span>
          <span>{{ selectedChunk.characterCount }} 字</span>
          <span>{{ selectedChunk.embeddingStatus || 'EMBEDDING_UNKNOWN' }}</span>
          <span>{{ selectedChunk.createdAt }}</span>
        </div>

        <section v-if="selectedChunk.summary" class="rag-chunk-dialog__section">
          <strong>分块摘要</strong>
          <p>{{ selectedChunk.summary }}</p>
        </section>

        <section class="rag-chunk-dialog__section">
          <strong>完整分块知识</strong>
          <pre>{{ selectedChunk.content }}</pre>
        </section>

        <section v-if="selectedChunk.embeddingError" class="rag-chunk-dialog__section rag-chunk-dialog__section--warning">
          <strong>向量化错误</strong>
          <p>{{ selectedChunk.embeddingError }}</p>
        </section>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadFile } from 'element-plus'
import {
  deleteRagDocument,
  fetchQdrantStatus,
  fetchRagDocumentDetail,
  fetchRagDocuments,
  fetchRagIngestItems,
  fetchRagIngestJobs,
  fetchRagJobs,
  fetchRagSources,
  ingestRagExternal,
  ingestRagFiles,
  ingestRagFolder,
  rebuildRagIndex,
  rebuildQdrantIndex,
  retryRagIngestJob,
  testRagSearch,
  uploadRagDocument,
} from '@/api/rag'
import type {
  QdrantStatusView,
  RagChunkView,
  RagDocumentDetailView,
  RagDocumentView,
  RagIndexJobView,
  RagIngestItemView,
  RagIngestJobView,
  RagSourceView,
  RagSearchResultView,
} from '@/types/gsmv'

const loading = ref(false)
const uploading = ref(false)
const rebuilding = ref(false)
const rebuildingQdrant = ref(false)
const searching = ref(false)
const jobsLoading = ref(false)
const ingesting = ref(false)
const searched = ref(false)
const rows = ref<RagDocumentView[]>([])
const selected = ref<RagDocumentDetailView | null>(null)
const selectedChunk = ref<RagChunkView | null>(null)
const chunkDetailVisible = ref(false)
const jobs = ref<RagIndexJobView[]>([])
const ingestJobs = ref<RagIngestJobView[]>([])
const ingestItems = ref<RagIngestItemView[]>([])
const sources = ref<RagSourceView[]>([])
const qdrantStatus = ref<QdrantStatusView | null>(null)
const searchResults = ref<RagSearchResultView[]>([])
const searchQuery = ref('湛江近海有哪些高保护等级物种？')
const folderPath = ref('')
const externalSource = ref('OBIS')
const externalQuery = ref('中华白海豚')
const webUrls = ref('')
const multiFileInputRef = ref<HTMLInputElement | null>(null)

const filters = reactive({
  keyword: '',
  sourceType: '',
  status: '',
})

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0,
})

const sourceTypeOptions = [
  { label: '物种档案', value: 'SPECIES' },
  { label: '观测记录', value: 'OBSERVATION' },
  { label: '生态系统', value: 'ECOSYSTEM' },
  { label: 'AI 科研报告', value: 'AI_REPORT' },
  { label: '上传文档', value: 'UPLOAD' },
  { label: 'OBIS 公开数据', value: 'EXTERNAL_OBIS' },
  { label: 'GBIF 公开数据', value: 'EXTERNAL_GBIF' },
  { label: 'WoRMS 分类数据', value: 'EXTERNAL_WORMS' },
  { label: 'IUCN 保护资料', value: 'EXTERNAL_IUCN' },
  { label: '网页资料', value: 'EXTERNAL_WEB_PDF' },
]

const readyCount = computed(() => rows.value.filter((item) => item.status === 'READY').length)

function sourceTypeLabel(value: string) {
  const matched = sourceTypeOptions.find((item) => item.value === value)?.label
  if (matched) {
    return matched
  }
  if (value?.startsWith('EXTERNAL_')) {
    return `${value.replace('EXTERNAL_', '')} 外部资料`
  }
  return value
}

function statusLabel(value: string) {
  if (value === 'READY') return '可用'
  if (value === 'FAILED') return '失败'
  if (value === 'PENDING') return '等待中'
  return value
}

function statusType(value: string) {
  if (value === 'READY') return 'success'
  if (value === 'FAILED') return 'danger'
  if (value === 'PENDING') return 'warning'
  return 'info'
}

function canDeleteDocument(row: RagDocumentView) {
  return row.sourceType === 'UPLOAD' || row.sourceType?.startsWith('EXTERNAL_')
}

function formatScore(value: number) {
  return Number.isFinite(value) ? value.toFixed(2) : '0.00'
}

function openChunkDetail(chunk: RagChunkView) {
  selectedChunk.value = chunk
  chunkDetailVisible.value = true
}

async function loadDocuments() {
  loading.value = true
  try {
    const pageData = await fetchRagDocuments({
      keyword: filters.keyword || undefined,
      sourceType: filters.sourceType || undefined,
      status: filters.status || undefined,
      page: pagination.page,
      size: pagination.size,
    })
    rows.value = pageData.items
    pagination.total = pageData.total
    if (!selected.value && rows.value.length) {
      await openDocument(rows.value[0])
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '知识文档加载失败')
  } finally {
    loading.value = false
  }
}

async function openDocument(row: RagDocumentView) {
  try {
    selected.value = await fetchRagDocumentDetail(row.id)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '文档详情加载失败')
  }
}

async function handleFileChange(uploadFile: UploadFile) {
  const file = uploadFile.raw
  if (!file) return
  uploading.value = true
  try {
    selected.value = await uploadRagDocument(file)
    pagination.page = 1
    await loadDocuments()
    ElMessage.success('知识文档已上传并开始索引')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '知识文档上传失败')
  } finally {
    uploading.value = false
  }
}

function openMultiFilePicker() {
  multiFileInputRef.value?.click()
}

async function handleMultiFileInput(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  input.value = ''
  if (!files.length) {
    return
  }
  ingesting.value = true
  try {
    await ingestRagFiles(files)
    pagination.page = 1
    await Promise.all([loadDocuments(), loadJobs(), loadQdrantStatus()])
    ElMessage.success(`已导入 ${files.length} 个知识文件`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '批量上传失败')
  } finally {
    ingesting.value = false
  }
}

async function deleteDocument(row: RagDocumentView) {
  try {
    await ElMessageBox.confirm(`确认删除知识文档“${row.title}”？`, '删除确认', { type: 'warning' })
    await deleteRagDocument(row.id)
    if (selected.value?.document.id === row.id) {
      selected.value = null
    }
    await Promise.all([loadDocuments(), loadQdrantStatus()])
    ElMessage.success('知识文档已删除')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error instanceof Error ? error.message : '知识文档删除失败')
    }
  }
}

async function rebuildAll() {
  rebuilding.value = true
  try {
    const job = await rebuildRagIndex()
    await Promise.all([loadDocuments(), loadJobs()])
    ElMessage.success(`重建完成：成功 ${job.successCount}，失败 ${job.failedCount}`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'RAG 索引重建失败')
  } finally {
    rebuilding.value = false
  }
}

async function runSearch() {
  if (!searchQuery.value.trim()) {
    ElMessage.warning('请输入检索问题')
    return
  }
  searching.value = true
  searched.value = true
  try {
    searchResults.value = await testRagSearch({ query: searchQuery.value.trim(), limit: 8 })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'RAG 检索失败')
  } finally {
    searching.value = false
  }
}

async function loadJobs() {
  jobsLoading.value = true
  try {
    const pageData = await fetchRagJobs({ page: 1, size: 5 })
    jobs.value = pageData.items
    const ingestPage = await fetchRagIngestJobs({ page: 1, size: 5 })
    ingestJobs.value = ingestPage.items
    if (ingestJobs.value[0]) {
      ingestItems.value = await fetchRagIngestItems(ingestJobs.value[0].id)
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '索引任务加载失败')
  } finally {
    jobsLoading.value = false
  }
}

async function loadSources() {
  try {
    sources.value = await fetchRagSources()
  } catch {
    sources.value = [
      { id: 0, code: 'OBIS', name: 'OBIS', sourceType: 'EXTERNAL_API', enabled: true },
      { id: 1, code: 'GBIF', name: 'GBIF', sourceType: 'EXTERNAL_API', enabled: true },
      { id: 2, code: 'WORMS', name: 'WoRMS', sourceType: 'EXTERNAL_API', enabled: true },
      { id: 3, code: 'WEB_PDF', name: 'Web documents', sourceType: 'WEB_DOCUMENT', enabled: true },
    ]
  }
}

async function loadQdrantStatus() {
  try {
    qdrantStatus.value = await fetchQdrantStatus()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Qdrant 状态读取失败')
  }
}

async function rebuildQdrant() {
  rebuildingQdrant.value = true
  try {
    const status = await rebuildQdrantIndex()
    qdrantStatus.value = status
    if (!status.available) {
      ElMessage.warning(status.errorMessage || 'Qdrant 当前不可用，未写入向量数据')
    } else if (status.pointsCount === 0 && status.readyChunks > 0) {
      ElMessage.warning('Qdrant 已连接，但没有写入向量点。请确认分块已完成 embedding。')
    } else {
      ElMessage.success(`Qdrant 向量索引已重建：${status.pointsCount} / ${status.readyChunks}`)
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Qdrant 重建失败')
  } finally {
    rebuildingQdrant.value = false
  }
}

async function runFolderIngest() {
  if (!folderPath.value.trim()) {
    ElMessage.warning('请输入本地文件夹路径')
    return
  }
  ingesting.value = true
  try {
    await ingestRagFolder({ path: folderPath.value.trim(), recursive: true })
    await Promise.all([loadDocuments(), loadJobs(), loadQdrantStatus()])
    ElMessage.success('文件夹知识导入完成')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '文件夹导入失败')
  } finally {
    ingesting.value = false
  }
}

async function runExternalIngest() {
  ingesting.value = true
  try {
    const urls = webUrls.value
      .split('\n')
      .map((item) => item.trim())
      .filter(Boolean)
    await ingestRagExternal({
      sourceCode: externalSource.value,
      query: externalSource.value === 'WEB_PDF' ? undefined : externalQuery.value.trim(),
      urls: externalSource.value === 'WEB_PDF' ? urls : undefined,
      limit: 10,
    })
    await Promise.all([loadDocuments(), loadJobs(), loadQdrantStatus()])
    ElMessage.success('外部知识采集完成')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '外部知识采集失败')
  } finally {
    ingesting.value = false
  }
}

async function retryIngest(job: RagIngestJobView) {
  ingesting.value = true
  try {
    await retryRagIngestJob(job.id)
    await Promise.all([loadDocuments(), loadJobs(), loadQdrantStatus()])
    ElMessage.success('失败项已重试')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '重试失败')
  } finally {
    ingesting.value = false
  }
}

onMounted(() => {
  void Promise.all([loadDocuments(), loadJobs(), loadSources(), loadQdrantStatus()])
})
</script>

<style scoped>
.rag-hero {
  align-items: center;
  overflow: hidden;
}

.rag-hero::after {
  content: '';
  position: absolute;
  right: -80px;
  bottom: -120px;
  width: 360px;
  height: 360px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(84, 218, 255, 0.28), transparent 66%);
}

.rag-hero__eyebrow {
  display: inline-flex;
  margin-bottom: 12px;
  color: #70f3ff;
  font-size: 12px;
  letter-spacing: 0.18em;
}

.rag-hero__actions,
.panel-header,
.rag-filters,
.rag-search-box,
.rag-search-results article footer {
  display: flex;
  align-items: center;
  gap: 12px;
}

.rag-hidden-file-input {
  display: none;
}

.rag-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.rag-metrics article {
  position: relative;
  padding: 22px;
  min-height: 132px;
  border: 1px solid rgba(119, 221, 255, 0.22);
  border-radius: 28px;
  background:
    linear-gradient(135deg, rgba(29, 92, 139, 0.62), rgba(7, 20, 58, 0.86)),
    radial-gradient(circle at 100% 0%, rgba(89, 226, 255, 0.22), transparent 42%);
  box-shadow: 0 20px 60px rgba(0, 10, 38, 0.28);
  overflow: hidden;
}

.rag-metrics article::after {
  content: '';
  position: absolute;
  top: 18px;
  right: 18px;
  width: 42px;
  height: 42px;
  border-radius: 16px;
  background: linear-gradient(135deg, rgba(93, 224, 255, 0.9), rgba(42, 119, 230, 0.65));
  box-shadow: 0 0 28px rgba(93, 224, 255, 0.4);
}

.rag-metrics span,
.rag-metrics small {
  display: block;
  color: rgba(220, 244, 255, 0.72);
}

.rag-metrics strong {
  display: block;
  margin: 12px 0 8px;
  color: #f7fcff;
  font-size: 42px;
  line-height: 1;
}

.knowledge-console {
  display: grid;
  grid-template-columns: 1fr 1.15fr 1.4fr;
  gap: 16px;
}

.knowledge-console article {
  min-height: 170px;
  padding: 20px;
  border-radius: 28px;
  border: 1px solid rgba(125, 211, 252, 0.2);
  background:
    linear-gradient(135deg, rgba(21, 63, 121, 0.78), rgba(6, 20, 56, 0.94)),
    radial-gradient(circle at 86% 8%, rgba(95, 230, 255, 0.18), transparent 34%);
  box-shadow: 0 18px 60px rgba(0, 9, 36, 0.24);
}

.knowledge-console article,
.knowledge-console__status,
.knowledge-console__ingest {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.knowledge-console span {
  color: #7af0ff;
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.knowledge-console strong {
  color: #f7fcff;
  font-size: 28px;
}

.knowledge-console small,
.knowledge-console p {
  color: rgba(224, 242, 255, 0.72);
  line-height: 1.6;
}

.knowledge-console__inline {
  display: flex;
  gap: 10px;
}

.rag-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.06fr) minmax(400px, 0.94fr);
  gap: 18px;
}

.rag-list-card,
.rag-detail-card,
.rag-search-card,
.rag-jobs-card {
  background:
    linear-gradient(180deg, rgba(17, 47, 99, 0.92), rgba(5, 18, 54, 0.96)),
    radial-gradient(circle at 12% 0%, rgba(69, 212, 255, 0.16), transparent 38%);
}

.panel-header {
  justify-content: space-between;
}

.rag-filters {
  margin-bottom: 16px;
}

.rag-filters .el-input {
  min-width: 220px;
}

.rag-title-cell {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.rag-title-cell span {
  color: rgba(205, 235, 255, 0.58);
  font-size: 12px;
}

.rag-side {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.rag-detail-cover {
  padding: 20px;
  border: 1px solid rgba(111, 217, 255, 0.2);
  border-radius: 24px;
  background: linear-gradient(135deg, rgba(25, 96, 150, 0.58), rgba(15, 37, 91, 0.76));
}

.rag-detail-cover span {
  color: #62e8ff;
  font-size: 12px;
  letter-spacing: 0.14em;
}

.rag-detail-cover h3 {
  margin: 8px 0;
  color: #fff;
  font-size: 24px;
}

.rag-detail-cover p,
.rag-chunks p,
.rag-search-results p,
.rag-jobs span,
.rag-jobs small {
  color: rgba(224, 242, 255, 0.72);
  line-height: 1.7;
}

.rag-chunks {
  display: grid;
  gap: 12px;
  max-height: 360px;
  margin-top: 14px;
  overflow: auto;
  padding-right: 4px;
}

.rag-chunks article,
.rag-search-results article,
.rag-jobs article {
  padding: 15px;
  border: 1px solid rgba(125, 211, 252, 0.18);
  border-radius: 20px;
  background: rgba(7, 24, 62, 0.66);
}

.rag-chunks article div,
.rag-search-results article div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.rag-chunks strong,
.rag-search-results strong,
.rag-jobs strong {
  color: #f5fbff;
}

.rag-chunks span,
.rag-search-results footer span {
  color: #62e8ff;
  font-size: 12px;
}

.rag-chunks article > div {
  align-items: flex-start;
}

.rag-chunks article .el-button {
  flex: 0 0 auto;
}

:deep(.rag-chunk-dialog) {
  border: 1px solid rgba(116, 220, 255, 0.24);
  border-radius: 20px;
  background:
    linear-gradient(180deg, rgba(17, 47, 99, 0.98), rgba(5, 18, 54, 0.98)),
    radial-gradient(circle at 88% 0%, rgba(84, 218, 255, 0.18), transparent 36%);
  box-shadow: 0 28px 90px rgba(0, 7, 34, 0.48);
}

.rag-chunk-dialog__header {
  display: grid;
  gap: 6px;
  padding-right: 32px;
}

.rag-chunk-dialog__header span {
  color: #6deaff;
  font-size: 12px;
  letter-spacing: 0.12em;
}

.rag-chunk-dialog__header strong {
  color: #f7fcff;
  font-size: 18px;
  line-height: 1.4;
}

.rag-chunk-dialog__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;
}

.rag-chunk-dialog__meta span {
  padding: 5px 9px;
  border: 1px solid rgba(109, 234, 255, 0.22);
  border-radius: 8px;
  color: rgba(224, 242, 255, 0.8);
  background: rgba(15, 45, 92, 0.62);
  font-size: 12px;
}

.rag-chunk-dialog__section {
  padding: 14px;
  border: 1px solid rgba(125, 211, 252, 0.18);
  border-radius: 8px;
  background: rgba(5, 18, 54, 0.68);
}

.rag-chunk-dialog__section + .rag-chunk-dialog__section {
  margin-top: 12px;
}

.rag-chunk-dialog__section strong {
  color: #f7fcff;
}

.rag-chunk-dialog__section p,
.rag-chunk-dialog__section pre {
  margin: 10px 0 0;
  color: rgba(224, 242, 255, 0.78);
  line-height: 1.75;
}

.rag-chunk-dialog__section pre {
  max-height: 420px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 13px;
}

.rag-chunk-dialog__section--warning {
  border-color: rgba(255, 188, 87, 0.36);
}

.rag-search-box {
  align-items: stretch;
  margin-bottom: 14px;
}

.rag-search-box .el-button {
  min-width: 104px;
}

.rag-search-results,
.rag-jobs {
  display: grid;
  gap: 12px;
}

.rag-search-results article footer {
  justify-content: space-between;
  margin-top: 10px;
}

.rag-search-results a {
  color: #6deaff;
  text-decoration: none;
}

.rag-jobs article {
  display: grid;
  gap: 4px;
}

.rag-jobs--ingest {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid rgba(125, 211, 252, 0.14);
}

.rag-jobs--ingest h4 {
  margin: 0;
  color: #f5fbff;
}

.rag-ingest-items {
  display: grid;
  gap: 6px;
  max-height: 130px;
  overflow: auto;
}

.rag-ingest-items span {
  color: rgba(224, 242, 255, 0.72);
  font-size: 12px;
}

@media (max-width: 1120px) {
  .rag-grid,
  .rag-metrics,
  .knowledge-console {
    grid-template-columns: 1fr;
  }

  .rag-filters,
  .rag-search-box,
  .rag-hero__actions,
  .knowledge-console__inline {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
