<template>
  <el-card class="panel-card version-card" shadow="never">
    <template #header>
      <div class="version-card__header">
        <div>
          <strong>{{ title }}</strong>
          <p>{{ description }}</p>
        </div>
        <el-tag effect="plain" round>{{ versions.length }} 条版本</el-tag>
      </div>
    </template>

    <el-empty v-if="!loading && !versions.length" :description="emptyText" />

    <div v-else v-loading="loading" class="version-list">
      <div
        v-for="(item, index) in versions"
        :key="item.id"
        class="version-item"
        :class="{ 'version-item--latest': index === 0 }"
      >
        <div class="version-item__top">
          <div>
            <div class="version-item__title">
              <strong>V{{ item.versionNo }}</strong>
              <el-tag :type="actionTagType(item.action)" effect="dark" size="small">
                {{ actionLabel(item) }}
              </el-tag>
              <el-tag v-if="index === 0" effect="plain" size="small" round>当前版本</el-tag>
            </div>
            <p>{{ item.changedByName || '未知用户' }} · {{ item.createdAt }}</p>
          </div>
          <el-button
            v-if="canRollback && index !== 0"
            plain
            size="small"
            :loading="rollbackingVersionId === item.id"
            @click="$emit('rollback', item)"
          >
            回滚到此版本
          </el-button>
        </div>

        <div v-if="item.changes.length" class="version-item__changes">
          <div v-for="change in item.changes" :key="`${item.id}-${change.fieldKey}`" class="version-change">
            <span class="version-change__label">{{ change.fieldLabel }}</span>
            <div class="version-change__values">
              <div class="version-change__value">
                <small>变更前</small>
                <p>{{ displayValue(change.oldValue) }}</p>
              </div>
              <div class="version-change__arrow">→</div>
              <div class="version-change__value">
                <small>变更后</small>
                <p>{{ displayValue(change.newValue) }}</p>
              </div>
            </div>
          </div>
        </div>
        <div v-else class="version-item__empty">这一版没有记录到字段差异，通常说明是同值回滚或仅做了状态对齐。</div>
      </div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import type { EntityVersionView } from '@/types/gsmv'

defineProps<{
  title?: string
  description?: string
  emptyText?: string
  versions: EntityVersionView[]
  loading?: boolean
  canRollback?: boolean
  rollbackingVersionId?: number | null
}>()

defineEmits<{
  rollback: [version: EntityVersionView]
}>()

function actionTagType(action: string) {
  if (action === 'CREATE') return 'success'
  if (action === 'DELETE') return 'danger'
  if (action === 'ROLLBACK') return 'warning'
  return 'info'
}

function actionLabel(item: EntityVersionView) {
  if (item.action === 'ROLLBACK' && item.rollbackSourceVersionNo) {
    return `回滚自 V${item.rollbackSourceVersionNo}`
  }
  if (item.action === 'CREATE') return '创建'
  if (item.action === 'UPDATE') return '更新'
  if (item.action === 'DELETE') return '删除'
  if (item.action === 'ROLLBACK') return '回滚'
  return item.action
}

function displayValue(value?: string) {
  return value && value.trim() ? value : '—'
}
</script>

<style scoped>
.version-card__header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.version-card__header p {
  margin: 8px 0 0;
  color: var(--gsmv-muted);
}

.version-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.version-item {
  padding: 18px;
  border-radius: 20px;
  border: 1px solid rgba(177, 234, 247, 0.12);
  background: rgba(7, 49, 106, 0.46);
}

.version-item--latest {
  border-color: rgba(104, 205, 255, 0.28);
  box-shadow: 0 16px 38px rgba(8, 26, 58, 0.16);
}

.version-item__top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.version-item__title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.version-item__top p {
  margin: 8px 0 0;
  color: var(--gsmv-muted);
}

.version-item__changes {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 14px;
}

.version-change {
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(177, 234, 247, 0.08);
}

.version-change__label {
  display: inline-flex;
  margin-bottom: 8px;
  color: var(--gsmv-primary);
  font-weight: 600;
}

.version-change__values {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  gap: 10px;
  align-items: center;
}

.version-change__value small {
  display: block;
  margin-bottom: 6px;
  color: var(--gsmv-muted);
}

.version-change__value p {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.7;
}

.version-change__arrow {
  color: rgba(164, 230, 255, 0.88);
  font-size: 18px;
  font-weight: 700;
}

.version-item__empty {
  margin-top: 14px;
  color: var(--gsmv-muted);
}

@media (max-width: 760px) {
  .version-card__header,
  .version-item__top,
  .version-change__values {
    display: flex;
    flex-direction: column;
  }
}
</style>
