<template>
  <div class="page-shell">
    <section class="page-hero">
      <div>
        <h2>审计日志</h2>
        <p>查看系统的关键操作轨迹，支持按模块和执行结果快速过滤，便于问题追踪和留痕检查。</p>
      </div>
      <el-tag type="info" size="large">操作留痕</el-tag>
    </section>

    <el-card class="panel-card" shadow="never">
      <div class="toolbar">
        <el-input v-model="query.module" placeholder="模块名称，如 USER / SPECIES" clearable style="max-width: 240px" />
        <el-select v-model="query.success" placeholder="执行结果" clearable style="width: 160px">
          <el-option label="成功" :value="1" />
          <el-option label="失败" :value="0" />
        </el-select>
        <el-button type="primary" @click="handleSearch">查询</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="createdAt" label="时间" min-width="180" />
        <el-table-column prop="module" label="模块" min-width="120" />
        <el-table-column prop="action" label="动作" min-width="120" />
        <el-table-column label="用户" min-width="150">
          <template #default="{ row }">
            {{ row.displayName || row.username || '系统' }}
          </template>
        </el-table-column>
        <el-table-column label="结果" min-width="100">
          <template #default="{ row }">
            <el-tag :type="row.success === 1 ? 'success' : 'danger'">
              {{ row.success === 1 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="requestId" label="TraceId" min-width="160" />
        <el-table-column prop="detailJson" label="详情" min-width="260" show-overflow-tooltip />
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchAudits } from '@/api/audits'
import type { AuditLogView } from '@/types/gsmv'

const loading = ref(false)
const rows = ref<AuditLogView[]>([])
const query = reactive({
  module: '',
  success: undefined as number | undefined,
})
const pagination = reactive({
  page: 1,
  size: 10,
  total: 0,
})

async function loadData() {
  loading.value = true
  try {
    const pageData = await fetchAudits({
      module: query.module || undefined,
      success: query.success,
      page: pagination.page,
      size: pagination.size,
    })
    rows.value = pageData.items
    pagination.total = pageData.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '审计日志加载失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.page = 1
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
.table-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}
</style>
