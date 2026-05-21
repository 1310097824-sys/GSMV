<template>
  <div class="page-shell ai-report-page">
    <section class="page-hero ai-report-hero">
      <div>
        <h2>AI 科研报告</h2>
        <p>把近期观测、重点生态系统、物种保护状态和潜在风险汇总成可归档的科研简报。</p>
      </div>
      <el-space wrap>
        <el-select v-model="reportForm.reportType" class="report-type">
          <el-option label="周报" value="WEEKLY" />
          <el-option label="月报" value="MONTHLY" />
          <el-option label="专题报告" value="CUSTOM" />
        </el-select>
        <el-select v-model="reportForm.days" class="day-filter">
          <el-option label="近 7 天" :value="7" />
          <el-option label="近 30 天" :value="30" />
          <el-option label="近 90 天" :value="90" />
        </el-select>
        <el-button type="primary" :loading="generating" @click="generateReport">生成报告</el-button>
      </el-space>
    </section>

    <section class="ai-report-grid">
      <el-card class="panel-card" shadow="never">
        <template #header>
          <div class="panel-header">
            <strong>报告历史</strong>
            <el-button text type="primary" :loading="loading" @click="loadReports">刷新</el-button>
          </div>
        </template>

        <el-table :data="rows" v-loading="loading" stripe @row-click="openDetail">
          <el-table-column prop="title" label="报告标题" min-width="220" show-overflow-tooltip />
          <el-table-column label="类型" width="110">
            <template #default="{ row }">{{ reportTypeLabel(row.reportType) }}</template>
          </el-table-column>
          <el-table-column label="范围" width="100">
            <template #default="{ row }">近 {{ row.days }} 天</template>
          </el-table-column>
          <el-table-column prop="creatorName" label="生成人" width="120" />
          <el-table-column prop="createdAt" label="生成时间" min-width="180" />
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click.stop="openDetail(row)">查看</el-button>
              <el-button link type="success" @click.stop="exportPdf(row.id)">PDF</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="table-footer">
          <el-pagination
            v-model:current-page="pagination.page"
            v-model:page-size="pagination.size"
            layout="total, prev, pager, next"
            :total="pagination.total"
            @current-change="loadReports"
          />
        </div>
      </el-card>

      <el-card class="panel-card ai-report-detail" shadow="never">
        <template #header>
          <div class="panel-header">
            <strong>报告预览</strong>
            <el-button v-if="detail" text type="success" @click="exportPdf(detail.id)">导出 PDF</el-button>
          </div>
        </template>

        <template v-if="detail">
          <div class="report-cover">
            <span>{{ reportTypeLabel(detail.reportType) }} / 近 {{ detail.days }} 天</span>
            <h3>{{ detail.title }}</h3>
            <p>{{ detail.summary }}</p>
          </div>

          <div class="report-sections">
            <article>
              <h4>重点发现</h4>
              <ul>
                <li v-for="item in detail.highlights" :key="item">{{ item }}</li>
              </ul>
            </article>
            <article>
              <h4>风险提示</h4>
              <ul>
                <li v-for="item in detail.risks" :key="item">{{ item }}</li>
              </ul>
            </article>
            <article>
              <h4>建议行动</h4>
              <ul>
                <li v-for="item in detail.recommendations" :key="item">{{ item }}</li>
              </ul>
            </article>
            <article class="report-evidence-section">
              <h4>RAG / 数据依据</h4>
              <div class="report-evidence-list">
                <div v-for="item in detail.evidence" :key="item" class="report-evidence-card">
                  <span>Evidence</span>
                  <p>{{ item }}</p>
                </div>
              </div>
            </article>
          </div>
        </template>

        <el-empty v-else description="选择一份报告查看详情，或先生成新的科研简报。" />
      </el-card>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  downloadAiReportPdf,
  fetchAiReportDetail,
  fetchAiReports,
  generateAiReport,
} from '@/api/aiReports'
import { triggerBlobDownload } from '@/utils/download'
import type { AiReportDetailView, AiReportView } from '@/types/gsmv'

const loading = ref(false)
const generating = ref(false)
const rows = ref<AiReportView[]>([])
const detail = ref<AiReportDetailView | null>(null)

const reportForm = reactive({
  reportType: 'MONTHLY',
  days: 30,
})

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0,
})

function reportTypeLabel(value: string) {
  switch (value) {
    case 'WEEKLY':
      return '周报'
    case 'CUSTOM':
      return '专题报告'
    default:
      return '月报'
  }
}

async function loadReports() {
  loading.value = true
  try {
    const pageData = await fetchAiReports({ page: pagination.page, size: pagination.size })
    rows.value = pageData.items
    pagination.total = pageData.total
    if (!detail.value && rows.value.length) {
      await openDetail(rows.value[0])
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'AI 报告加载失败')
  } finally {
    loading.value = false
  }
}

async function generateReport() {
  generating.value = true
  try {
    detail.value = await generateAiReport({ reportType: reportForm.reportType, days: reportForm.days })
    pagination.page = 1
    await loadReports()
    ElMessage.success('AI 科研报告已生成')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'AI 报告生成失败')
  } finally {
    generating.value = false
  }
}

async function openDetail(row: AiReportView) {
  try {
    detail.value = await fetchAiReportDetail(row.id)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '报告详情加载失败')
  }
}

async function exportPdf(id: number) {
  try {
    const { blob, fileName } = await downloadAiReportPdf(id)
    triggerBlobDownload(blob, fileName)
    ElMessage.success('AI 报告 PDF 已开始下载')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'AI 报告导出失败')
  }
}

onMounted(() => {
  void loadReports()
})
</script>

<style scoped>
.ai-report-hero {
  align-items: center;
}

.report-type,
.day-filter {
  width: 140px;
}

.ai-report-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(360px, 0.92fr);
  gap: 18px;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.report-cover {
  padding: 24px;
  border-radius: 24px;
  border: 1px solid rgba(173, 239, 255, 0.16);
  background:
    linear-gradient(145deg, rgba(72, 219, 251, 0.16), rgba(13, 66, 132, 0.36)),
    rgba(5, 28, 70, 0.72);
}

.report-cover span {
  color: var(--gsmv-primary);
  font-size: 13px;
  font-weight: 700;
}

.report-cover h3 {
  margin: 12px 0;
  font-size: 24px;
}

.report-cover p {
  margin: 0;
  color: var(--gsmv-muted);
  line-height: 1.8;
}

.report-sections {
  display: grid;
  gap: 14px;
  margin-top: 18px;
}

.report-sections article {
  padding: 18px;
  border-radius: 20px;
  border: 1px solid rgba(173, 239, 255, 0.12);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.06), rgba(255, 255, 255, 0.02)),
    rgba(4, 23, 60, 0.68);
}

.report-sections h4 {
  margin: 0 0 10px;
}

.report-sections ul {
  margin: 0;
  padding-left: 18px;
  color: var(--gsmv-muted);
  line-height: 1.8;
}

.report-evidence-section {
  background:
    linear-gradient(135deg, rgba(76, 214, 255, 0.12), rgba(7, 25, 70, 0.72)),
    rgba(8, 33, 78, 0.54);
}

.report-evidence-list {
  display: grid;
  gap: 10px;
}

.report-evidence-card {
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid rgba(125, 211, 252, 0.16);
  background: rgba(4, 18, 52, 0.52);
}

.report-evidence-card span {
  color: #70f3ff;
  font-size: 11px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.report-evidence-card p {
  margin: 6px 0 0;
  color: var(--gsmv-muted);
  line-height: 1.75;
}

@media (max-width: 1180px) {
  .ai-report-grid {
    grid-template-columns: 1fr;
  }
}
</style>
