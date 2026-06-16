<template>
  <div class="page-shell agent-runs-page">
    <section class="page-hero agent-hero">
      <div class="page-hero__content">
        <span class="page-hero__eyebrow">Multi-agent Operations</span>
        <h2>Agent 协作台</h2>
        <p>集中查看 AI 助手、识图复核、观测质检、科研报告和知识库治理的协作轨迹，快速定位证据、置信度和人工复核原因。</p>
      </div>

      <div class="agent-hero__governance">
        <span>知识库治理</span>
        <el-input
          v-model="governancePrompt"
          placeholder="治理主题，如重复知识、分类冲突、低质量来源"
          clearable
          @keyup.enter="runGovernance"
        />
        <el-button type="primary" :icon="Connection" :loading="governanceRunning" @click="runGovernance">
          发起协作
        </el-button>
      </div>
    </section>

    <section class="agent-metrics">
      <article>
        <el-icon><DataLine /></el-icon>
        <span>轨迹总数</span>
        <strong>{{ pagination.total }}</strong>
        <small>当前权限范围内的协作任务</small>
      </article>
      <article>
        <el-icon><CircleCheck /></el-icon>
        <span>本页可确认</span>
        <strong>{{ verifiedCount }}</strong>
        <small>Verifier 判定证据可支撑结论</small>
      </article>
      <article>
        <el-icon><Warning /></el-icon>
        <span>本页需复核</span>
        <strong>{{ reviewCount }}</strong>
        <small>包含证据不足或人工复核建议</small>
      </article>
      <article>
        <el-icon><TrendCharts /></el-icon>
        <span>平均置信度</span>
        <strong>{{ formatPercent(avgConfidence) }}</strong>
        <small>按当前页已完成 run 计算</small>
      </article>
    </section>

    <section class="agent-console">
      <el-card class="panel-card agent-list-card" shadow="never">
        <template #header>
          <div class="panel-header">
            <strong>协作任务</strong>
            <el-button text type="primary" :icon="Refresh" :loading="loading" @click="loadRuns">刷新</el-button>
          </div>
        </template>

        <div class="toolbar toolbar--wrap">
          <el-input
            v-model="filters.keyword"
            placeholder="关键词 / run ID / 发起人 / 主题"
            clearable
            style="max-width: 280px"
            @keyup.enter="handleSearch"
          />
          <el-select v-model="filters.workflowType" placeholder="协作流" clearable style="width: 190px">
            <el-option v-for="item in workflowOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="filters.verificationStatus" placeholder="验证结论" clearable style="width: 170px">
            <el-option v-for="item in verificationOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="filters.status" placeholder="执行状态" clearable style="width: 150px">
            <el-option v-for="item in runStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        </div>

        <el-table
          :data="rows"
          v-loading="loading"
          row-key="id"
          stripe
          highlight-current-row
          @row-click="openRun"
        >
          <el-table-column label="Run" width="86">
            <template #default="{ row }">#{{ row.id }}</template>
          </el-table-column>
          <el-table-column label="协作流" min-width="170">
            <template #default="{ row }">
              <div class="agent-run-cell">
                <strong>{{ workflowLabel(row.workflowType) }}</strong>
                <span>{{ row.workflowType }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="主题" min-width="260" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.summary || row.prompt || '暂无摘要' }}
            </template>
          </el-table-column>
          <el-table-column label="验证" width="140">
            <template #default="{ row }">
              <el-tag :type="verificationType(row.verificationStatus)" effect="dark" round>
                {{ verificationLabel(row.verificationStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="置信度" width="110">
            <template #default="{ row }">
              {{ formatPercent(row.confidence) }}
            </template>
          </el-table-column>
          <el-table-column label="对象" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">
              {{ subjectLabel(row) }}
            </template>
          </el-table-column>
          <el-table-column label="发起人" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.username || '系统' }}
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="时间" min-width="170" show-overflow-tooltip />
          <el-table-column label="操作" width="96" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" :icon="View" @click.stop="openRun(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="table-footer">
          <el-pagination
            v-model:current-page="pagination.page"
            v-model:page-size="pagination.size"
            layout="total, prev, pager, next"
            :total="pagination.total"
            @current-change="loadRuns"
          />
        </div>
      </el-card>

      <aside class="agent-detail">
        <el-card class="panel-card agent-detail-card" shadow="never" v-loading="detailLoading">
          <template #header>
            <div class="panel-header">
              <strong>完整轨迹</strong>
              <el-tag v-if="selectedRun" :type="runStatusType(selectedRun.status)" effect="plain" round>
                {{ runStatusLabel(selectedRun.status) }}
              </el-tag>
            </div>
          </template>

          <template v-if="selectedRun">
            <section class="agent-run-cover">
              <span>{{ workflowLabel(selectedRun.workflowType) }}</span>
              <h3>#{{ selectedRun.id }} {{ selectedRun.summary || selectedRun.prompt || 'Agent 协作任务' }}</h3>
              <div>
                <el-tag :type="verificationType(selectedRun.verificationStatus)" effect="dark" round>
                  {{ verificationLabel(selectedRun.verificationStatus) }}
                </el-tag>
                <el-tag effect="plain" round>{{ formatPercent(selectedRun.confidence) }}</el-tag>
                <el-tag effect="plain" round>{{ selectedRun.steps.length }} 步</el-tag>
              </div>
            </section>

            <AgentTracePanel :run="selectedRun" :replay="selectedReplay" title="Agent 协作轨迹" eyebrow="Trace replay" />

            <section class="agent-run-json">
              <article>
                <span>原始问题</span>
                <p>{{ selectedRun.prompt || '无' }}</p>
              </article>
              <article>
                <span>最终输出</span>
                <pre>{{ formatJson(selectedRun.finalOutput) }}</pre>
              </article>
            </section>
          </template>

          <el-empty v-else description="选择左侧任务查看完整 agent 输入、输出、证据和验证结论。" />
        </el-card>
      </aside>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  CircleCheck,
  Connection,
  DataLine,
  Refresh,
  Search,
  TrendCharts,
  View,
  Warning,
} from '@element-plus/icons-vue'
import AgentTracePanel from '@/components/AgentTracePanel.vue'
import { fetchAgentRunDetail, fetchAgentRunReplay, fetchAgentRuns, runKnowledgeGovernance } from '@/api/agents'
import type { AiAgentRunReplayView, AiAgentRunView } from '@/types/gsmv'

const loading = ref(false)
const detailLoading = ref(false)
const governanceRunning = ref(false)
const rows = ref<AiAgentRunView[]>([])
const selectedRun = ref<AiAgentRunView | null>(null)
const selectedReplay = ref<AiAgentRunReplayView | null>(null)
const governancePrompt = ref('低质量文档、重复知识、分类冲突来源')

const filters = reactive({
  keyword: '',
  workflowType: '',
  status: '',
  verificationStatus: '',
})

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0,
})

const workflowOptions = [
  { label: '智能问答', value: 'ASSISTANT_CHAT' },
  { label: '识图复核', value: 'SPECIES_IDENTIFY' },
  { label: '观测质检', value: 'OBSERVATION_QA' },
  { label: '科研报告', value: 'RESEARCH_REPORT' },
  { label: '知识库治理', value: 'KNOWLEDGE_GOVERNANCE' },
]

const verificationOptions = [
  { label: '可确认', value: 'VERIFIED' },
  { label: '证据不足', value: 'INSUFFICIENT_EVIDENCE' },
  { label: '需要复核', value: 'NEEDS_REVIEW' },
]

const runStatusOptions = [
  { label: '成功', value: 'SUCCESS' },
  { label: '部分完成', value: 'PARTIAL' },
  { label: '执行中', value: 'RUNNING' },
]

const verifiedCount = computed(() => rows.value.filter((row) => row.verificationStatus === 'VERIFIED').length)
const reviewCount = computed(() =>
  rows.value.filter((row) => row.verificationStatus === 'NEEDS_REVIEW' || row.verificationStatus === 'INSUFFICIENT_EVIDENCE').length,
)
const avgConfidence = computed(() => {
  const values = rows.value.map((row) => row.confidence).filter((value): value is number => typeof value === 'number')
  if (!values.length) {
    return undefined
  }
  return values.reduce((sum, value) => sum + value, 0) / values.length
})

async function loadRuns() {
  loading.value = true
  try {
    const pageData = await fetchAgentRuns({
      keyword: filters.keyword || undefined,
      workflowType: filters.workflowType || undefined,
      status: filters.status || undefined,
      verificationStatus: filters.verificationStatus || undefined,
      page: pagination.page,
      size: pagination.size,
    })
    rows.value = pageData.items
    pagination.total = pageData.total
    if (!selectedRun.value && rows.value.length) {
      await openRun(rows.value[0])
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Agent 协作轨迹加载失败')
  } finally {
    loading.value = false
  }
}

async function openRun(row: AiAgentRunView) {
  detailLoading.value = true
  try {
    const [detail, replay] = await Promise.all([
      fetchAgentRunDetail(row.id),
      fetchAgentRunReplay(row.id),
    ])
    selectedRun.value = detail
    selectedReplay.value = replay
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Agent 协作详情加载失败')
  } finally {
    detailLoading.value = false
  }
}

function handleSearch() {
  pagination.page = 1
  selectedRun.value = null
  selectedReplay.value = null
  void loadRuns()
}

async function runGovernance() {
  governanceRunning.value = true
  try {
    selectedRun.value = await runKnowledgeGovernance({
      prompt: governancePrompt.value.trim() || undefined,
    })
    selectedReplay.value = await fetchAgentRunReplay(selectedRun.value.id)
    filters.workflowType = 'KNOWLEDGE_GOVERNANCE'
    pagination.page = 1
    await loadRuns()
    ElMessage.success('知识库治理协作已完成')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '知识库治理协作失败')
  } finally {
    governanceRunning.value = false
  }
}

function workflowLabel(value?: string) {
  return workflowOptions.find((item) => item.value === value)?.label || value || '未知协作流'
}

function verificationLabel(value?: string) {
  if (value === 'VERIFIED') return '可确认'
  if (value === 'INSUFFICIENT_EVIDENCE') return '证据不足'
  if (value === 'NEEDS_REVIEW') return '需要复核'
  return value || '未验证'
}

function verificationType(value?: string) {
  if (value === 'VERIFIED') return 'success'
  if (value === 'NEEDS_REVIEW') return 'danger'
  if (value === 'INSUFFICIENT_EVIDENCE') return 'warning'
  return 'info'
}

function runStatusLabel(value?: string) {
  if (value === 'SUCCESS') return '成功'
  if (value === 'PARTIAL') return '部分完成'
  if (value === 'RUNNING') return '执行中'
  return value || '未知'
}

function runStatusType(value?: string) {
  if (value === 'SUCCESS') return 'success'
  if (value === 'PARTIAL') return 'warning'
  if (value === 'RUNNING') return 'info'
  return 'info'
}

function subjectLabel(row: AiAgentRunView) {
  if (!row.subjectType && !row.subjectId) {
    return '通用任务'
  }
  return `${row.subjectType || 'SUBJECT'}${row.subjectId ? ` #${row.subjectId}` : ''}`
}

function formatPercent(value?: number) {
  if (value === undefined || !Number.isFinite(value)) {
    return '0%'
  }
  return `${Math.round(value * 100)}%`
}

function formatJson(value: unknown) {
  if (value === undefined || value === null) {
    return '无'
  }
  if (typeof value === 'string') {
    return value
  }
  return JSON.stringify(value, null, 2)
}

onMounted(loadRuns)
</script>

<style scoped>
.agent-hero {
  align-items: stretch;
}

.agent-hero__governance {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(240px, 340px) auto;
  gap: 12px;
  align-content: center;
  min-width: min(100%, 520px);
  padding: 18px;
  border: 1px solid rgba(147, 241, 255, 0.18);
  border-radius: 22px;
  background: rgba(5, 25, 64, 0.56);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.05);
}

.agent-hero__governance span {
  grid-column: 1 / -1;
  color: var(--gsmv-primary);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.agent-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.agent-metrics article {
  position: relative;
  min-height: 146px;
  padding: 20px;
  border: 1px solid rgba(125, 211, 252, 0.2);
  border-radius: 24px;
  background:
    linear-gradient(135deg, rgba(21, 63, 121, 0.78), rgba(6, 20, 56, 0.94)),
    radial-gradient(circle at 84% 10%, rgba(99, 235, 255, 0.18), transparent 34%);
  box-shadow: 0 18px 50px rgba(0, 9, 36, 0.24);
  overflow: hidden;
}

.agent-metrics .el-icon {
  position: absolute;
  right: 18px;
  top: 18px;
  width: 42px;
  height: 42px;
  border-radius: 16px;
  color: #f4fdff;
  background: linear-gradient(135deg, rgba(97, 228, 255, 0.82), rgba(22, 143, 218, 0.78));
  box-shadow: 0 16px 28px rgba(0, 12, 38, 0.18);
}

.agent-metrics span,
.agent-metrics small {
  display: block;
  color: rgba(220, 244, 255, 0.72);
}

.agent-metrics span {
  font-size: 12px;
  letter-spacing: 0.12em;
}

.agent-metrics strong {
  display: block;
  margin: 18px 0 10px;
  color: #f7fcff;
  font-size: 38px;
  line-height: 1;
}

.agent-console {
  display: grid;
  grid-template-columns: minmax(0, 1.12fr) minmax(390px, 0.88fr);
  gap: 18px;
  align-items: start;
}

.panel-header,
.toolbar--wrap {
  display: flex;
  align-items: center;
  gap: 12px;
}

.panel-header {
  justify-content: space-between;
}

.toolbar--wrap {
  flex-wrap: wrap;
}

.agent-run-cell {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.agent-run-cell strong {
  color: #f7fcff;
}

.agent-run-cell span {
  color: rgba(205, 235, 255, 0.58);
  font-size: 12px;
}

.table-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}

.agent-detail {
  position: sticky;
  top: 22px;
}

.agent-detail-card {
  min-height: 640px;
}

.agent-run-cover {
  display: grid;
  gap: 12px;
  margin-bottom: 14px;
  padding: 18px;
  border: 1px solid rgba(111, 217, 255, 0.2);
  border-radius: 20px;
  background: linear-gradient(135deg, rgba(25, 96, 150, 0.58), rgba(15, 37, 91, 0.76));
}

.agent-run-cover span {
  color: #62e8ff;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.agent-run-cover h3 {
  margin: 0;
  color: #f7fcff;
  font-size: 20px;
  line-height: 1.35;
}

.agent-run-cover div {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.agent-run-json {
  display: grid;
  gap: 12px;
  margin-top: 14px;
}

.agent-run-json article {
  padding: 14px;
  border: 1px solid rgba(125, 211, 252, 0.16);
  border-radius: 16px;
  background: rgba(5, 18, 54, 0.58);
}

.agent-run-json span {
  color: #62e8ff;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.1em;
}

.agent-run-json p,
.agent-run-json pre {
  margin: 10px 0 0;
  color: rgba(224, 242, 255, 0.78);
  line-height: 1.72;
}

.agent-run-json pre {
  max-height: 260px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
}

@media (max-width: 1240px) {
  .agent-console,
  .agent-metrics {
    grid-template-columns: 1fr;
  }

  .agent-detail {
    position: static;
  }
}

@media (max-width: 720px) {
  .agent-hero__governance,
  .toolbar--wrap {
    grid-template-columns: 1fr;
    align-items: stretch;
  }
}
</style>
