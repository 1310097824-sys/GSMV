<template>
  <section v-if="hasTrace" class="agent-trace">
    <header class="agent-trace__header">
      <div>
        <span>{{ eyebrow }}</span>
        <strong>{{ title }}</strong>
      </div>
      <div class="agent-trace__status">
        <el-tag :type="verificationTagType" effect="dark" round>
          {{ verificationLabel }}
        </el-tag>
        <el-tag v-if="confidence !== undefined" effect="plain" round>
          置信度 {{ formatPercent(confidence) }}
        </el-tag>
      </div>
    </header>

    <p v-if="summary" class="agent-trace__summary">{{ summary }}</p>

    <div v-if="finalAnswer || finalEvidenceSnapshot.length || finalActions.length || reviewTicketDraft || reviewTaskDraft" class="agent-final-output">
      <header>
        <span>Final output</span>
        <el-tag effect="plain" round>证据 {{ finalEvidenceSnapshot.length }} 条</el-tag>
      </header>
      <p v-if="finalAnswer">{{ finalAnswer }}</p>
      <div v-if="reviewTicketDraft" class="agent-final-output__draft">
        <strong>{{ reviewDraftTitle }}</strong>
        <small>{{ reviewDraftSubtitle }}</small>
      </div>
      <div v-if="reviewTaskDraft" class="agent-final-output__draft">
        <strong>{{ reviewTaskTitle }}</strong>
        <small>{{ reviewTaskSubtitle }}</small>
      </div>
      <div v-if="finalEvidenceSnapshot.length" class="agent-final-output__evidence">
        <el-tag
          v-for="item in finalEvidenceSnapshot.slice(0, 4)"
          :key="evidenceTitle(item)"
          effect="plain"
          round
        >
          {{ evidenceTitle(item) }}
        </el-tag>
      </div>
      <ul v-if="finalActions.length">
        <li v-for="(item, index) in finalActions.slice(0, 3)" :key="`${actionLabel(item)}-${index}`">
          {{ actionLabel(item) }}
        </li>
      </ul>
    </div>

    <div v-if="needsAttention" class="agent-review-callout" :class="{ 'agent-review-callout--danger': needsManualReview }">
      <div>
        <span>{{ needsManualReview ? 'Human review' : 'Fallback' }}</span>
        <strong>{{ attentionTitle }}</strong>
        <p>{{ attentionMessage }}</p>
      </div>
      <RouterLink v-if="reviewTarget" :to="reviewTarget" class="agent-review-callout__link">
        进入人工复核
      </RouterLink>
    </div>

    <div v-if="replay" class="agent-replay">
      <div class="agent-replay__metrics">
        <article>
          <span>Replay</span>
          <strong>{{ replayStatusLabel }}</strong>
        </article>
        <article>
          <span>Evidence</span>
          <strong>{{ replay.evidenceCount }}</strong>
        </article>
        <article>
          <span>Claims</span>
          <strong>{{ claimChecks.length }}</strong>
        </article>
        <article>
          <span>Findings</span>
          <strong>{{ reviewFindings.length }}</strong>
        </article>
      </div>

      <el-alert
        v-if="replay.consistencyIssues.length"
        type="warning"
        :closable="false"
        show-icon
        title="Replay consistency warnings"
      >
        <template #default>
          <div class="agent-replay__chips">
            <el-tag v-for="issue in replay.consistencyIssues" :key="issue" type="warning" effect="plain" round>
              {{ issue }}
            </el-tag>
          </div>
        </template>
      </el-alert>

      <div v-if="claimChecks.length || reviewFindings.length" class="agent-replay__grid">
        <section v-if="claimChecks.length" class="agent-replay__block">
          <header>
            <span>Claim checks</span>
            <el-tag :type="claimHealthTag" effect="plain" round>{{ claimHealthLabel }}</el-tag>
          </header>
          <ul>
            <li v-for="(claim, index) in claimChecks.slice(0, 4)" :key="`${claimKey(claim)}-${index}`">
              <div>
                <strong>{{ claimLabel(claim) }}</strong>
                <small>{{ claimSupport(claim) }}</small>
              </div>
              <p>{{ claimText(claim) }}</p>
            </li>
          </ul>
        </section>

        <section v-if="reviewFindings.length" class="agent-replay__block">
          <header>
            <span>Review findings</span>
            <el-tag type="warning" effect="plain" round>{{ reviewFindings.length }}</el-tag>
          </header>
          <ul>
            <li v-for="(finding, index) in reviewFindings.slice(0, 4)" :key="`${findingKey(finding)}-${index}`">
              <div>
                <strong>{{ findingLabel(finding) }}</strong>
                <small>{{ findingSeverity(finding) }}</small>
              </div>
              <p>{{ findingMessage(finding) }}</p>
            </li>
          </ul>
        </section>
      </div>
    </div>

    <div class="agent-trace__rail">
      <article
        v-for="step in normalizedSteps"
        :key="step.id || `${step.stepOrder}-${step.agentName}`"
        class="agent-step"
        :class="`agent-step--${step.status?.toLowerCase() || 'success'}`"
      >
        <div class="agent-step__index">{{ padStep(step.stepOrder) }}</div>
        <div class="agent-step__body">
          <div class="agent-step__top">
            <div>
              <strong>{{ step.agentName }}</strong>
              <span>{{ step.agentRole }}</span>
            </div>
            <el-tag :type="stepTagType(step.status)" effect="plain" round>
              {{ stepStatusLabel(step.status) }}
            </el-tag>
          </div>

          <p>{{ step.summary || '该 Agent 已完成当前步骤。' }}</p>

          <div class="agent-step__meta">
            <span v-if="step.confidence !== undefined">置信度 {{ formatPercent(step.confidence) }}</span>
            <span v-if="step.durationMs !== undefined">{{ step.durationMs }} ms</span>
            <span v-if="evidenceCount(step)">证据 {{ evidenceCount(step) }} 条</span>
          </div>

          <div v-if="firstEvidence(step)" class="agent-step__evidence">
            <span>关键证据</span>
            <strong>{{ evidenceTitle(firstEvidence(step)) }}</strong>
            <p>{{ evidenceDescription(firstEvidence(step)) }}</p>
          </div>

          <el-alert
            v-if="step.errorMessage"
            :title="step.errorMessage"
            type="warning"
            :closable="false"
            show-icon
          />
        </div>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { fetchAgentRunReplay } from '@/api/agents'
import type { AiAgentRunReplayView, AiAgentRunView, AiAgentStepView } from '@/types/gsmv'

const props = withDefaults(
  defineProps<{
    run?: AiAgentRunView | null
    runId?: number | null
    replay?: AiAgentRunReplayView | null
    reviewHref?: string | null
    steps?: AiAgentStepView[]
    title?: string
    eyebrow?: string
  }>(),
  {
    run: null,
    runId: null,
    replay: null,
    reviewHref: null,
    steps: () => [],
    title: 'Agent 协作轨迹',
    eyebrow: 'Multi-agent trace',
  },
)

const loadedReplay = ref<AiAgentRunReplayView | null>(null)
let replayRequestSeq = 0
const normalizedSteps = computed(() => props.run?.steps?.length ? props.run.steps : props.steps)
const hasTrace = computed(() => normalizedSteps.value.length > 0 || Boolean(props.run))
const summary = computed(() => props.run?.summary || '')
const confidence = computed(() => props.run?.confidence)
const finalOutput = computed(() => {
  const value = props.run?.finalOutput || replay.value?.run?.finalOutput
  return isRecord(value) ? value : null
})
const finalAnswer = computed(() => stringField(finalOutput.value, 'finalAnswer'))
const finalEvidenceSnapshot = computed(() => objectList(finalOutput.value?.evidenceSnapshot))
const finalActions = computed(() => objectList(finalOutput.value?.actionItems))
const reviewTicketDraft = computed(() => isRecord(finalOutput.value?.reviewTicketDraft) ? finalOutput.value.reviewTicketDraft : null)
const reviewTaskDraft = computed(() => isRecord(finalOutput.value?.reviewTaskDraft) ? finalOutput.value.reviewTaskDraft : null)
const reviewDraftTitle = computed(() => {
  if (!reviewTicketDraft.value) {
    return ''
  }
  return `复核工单草稿：${stringField(reviewTicketDraft.value, 'likelyChineseName') || stringField(reviewTicketDraft.value, 'likelyScientificName') || '待确认物种'}`
})
const reviewDraftSubtitle = computed(() => {
  if (!reviewTicketDraft.value) {
    return ''
  }
  const confidence = numberField(reviewTicketDraft.value, 'confidence')
  const reasonCount = Array.isArray(reviewTicketDraft.value.reviewReasons) ? reviewTicketDraft.value.reviewReasons.length : 0
  const confidenceText = confidence === undefined ? '置信度待确认' : `置信度 ${formatPercent(confidence)}`
  return `${confidenceText}，复核原因 ${reasonCount} 项`
})
const reviewTaskTitle = computed(() => {
  if (!reviewTaskDraft.value) {
    return ''
  }
  return stringField(reviewTaskDraft.value, 'title') || '观测质量审核任务'
})
const reviewTaskSubtitle = computed(() => {
  if (!reviewTaskDraft.value) {
    return ''
  }
  const priority = stringField(reviewTaskDraft.value, 'priority') || 'LOW'
  const summary = stringField(reviewTaskDraft.value, 'summary')
  return `${priority} 优先级${summary ? `，${summary}` : ''}`
})
const verifierStep = computed(() => normalizedSteps.value.find((step) => step.agentName === 'Verifier Agent'))
const verificationStatus = computed(() => props.run?.verificationStatus || stringOutput(verifierStep.value, 'verificationStatus'))
const effectiveRunId = computed(() => props.run?.id || props.runId || null)
const replay = computed(() => props.replay || loadedReplay.value)
const failedSteps = computed(() => normalizedSteps.value.filter((step) => step.status === 'FAILED'))
const claimChecks = computed(() => objectList(replay.value?.claimChecks))
const reviewFindings = computed(() => objectList(replay.value?.reviewFindings))
const unsupportedClaims = computed(() => claimChecks.value.filter((claim) => claimSupport(claim) === 'UNSUPPORTED').length)
const weakClaims = computed(() => claimChecks.value.filter((claim) => claimSupport(claim) === 'WEAK').length)
const hasReplayWarnings = computed(() => Boolean(replay.value?.consistencyIssues?.length))
const hasFallback = computed(() => props.run?.status === 'PARTIAL' || failedSteps.value.length > 0 || hasReplayWarnings.value)
const needsManualReview = computed(() =>
  verificationStatus.value === 'NEEDS_REVIEW'
  || verificationStatus.value === 'INSUFFICIENT_EVIDENCE'
  || unsupportedClaims.value > 0
  || weakClaims.value > 0,
)
const needsAttention = computed(() => needsManualReview.value || hasFallback.value)
const reviewTarget = computed(() => props.reviewHref || inferredReviewTarget())

const attentionTitle = computed(() => {
  if (verificationStatus.value === 'NEEDS_REVIEW') {
    return 'Verifier 建议人工复核'
  }
  if (verificationStatus.value === 'INSUFFICIENT_EVIDENCE') {
    return '证据不足，需补证确认'
  }
  if (hasFallback.value) {
    return '协作流程发生降级'
  }
  return '需要关注'
})

const attentionMessage = computed(() => {
  if (unsupportedClaims.value || weakClaims.value) {
    return `存在 ${unsupportedClaims.value} 条未支撑结论、${weakClaims.value} 条弱支撑结论，建议补充证据或交由人工确认。`
  }
  if (failedSteps.value.length) {
    return `${failedSteps.value.length} 个 agent 步骤失败，最终结论已经按降级路径处理。`
  }
  if (hasReplayWarnings.value) {
    return '回放一致性检查发现告警，建议审计步骤输入、输出和最终结论。'
  }
  if (verificationStatus.value === 'INSUFFICIENT_EVIDENCE') {
    return '当前证据无法完整支撑结论，建议补充系统数据、RAG 引用或人工复核说明。'
  }
  return '当前结论需要人工复核后再作为正式依据。'
})

const replayStatusLabel = computed(() => {
  if (!replay.value) {
    return 'Unavailable'
  }
  if (replay.value.replayStatus === 'READY') {
    return 'Ready'
  }
  if (replay.value.replayStatus === 'READY_WITH_WARNINGS') {
    return 'Warnings'
  }
  return 'Incomplete'
})

const claimHealthLabel = computed(() => {
  if (unsupportedClaims.value) {
    return `${unsupportedClaims.value} unsupported`
  }
  if (weakClaims.value) {
    return `${weakClaims.value} weak`
  }
  return 'supported'
})

const claimHealthTag = computed(() => {
  if (unsupportedClaims.value) {
    return 'danger'
  }
  if (weakClaims.value) {
    return 'warning'
  }
  return 'success'
})

watch(
  () => [props.replay, effectiveRunId.value] as const,
  async ([providedReplay, runId]) => {
    const requestSeq = ++replayRequestSeq
    if (providedReplay || !runId) {
      loadedReplay.value = null
      return
    }
    loadedReplay.value = null
    try {
      const nextReplay = await fetchAgentRunReplay(runId)
      if (requestSeq === replayRequestSeq) {
        loadedReplay.value = nextReplay
      }
    } catch {
      if (requestSeq === replayRequestSeq) {
        loadedReplay.value = null
      }
    }
  },
  { immediate: true },
)

const verificationLabel = computed(() => {
  switch (verificationStatus.value) {
    case 'VERIFIED':
      return '可确认'
    case 'INSUFFICIENT_EVIDENCE':
      return '证据不足'
    case 'NEEDS_REVIEW':
      return '需要复核'
    default:
      return '已记录'
  }
})

const verificationTagType = computed(() => {
  switch (verificationStatus.value) {
    case 'VERIFIED':
      return 'success'
    case 'INSUFFICIENT_EVIDENCE':
      return 'warning'
    case 'NEEDS_REVIEW':
      return 'danger'
    default:
      return 'info'
  }
})

function stepStatusLabel(status?: string) {
  switch (status) {
    case 'FAILED':
      return '失败'
    case 'SKIPPED':
      return '跳过'
    default:
      return '完成'
  }
}

function stepTagType(status?: string) {
  switch (status) {
    case 'FAILED':
      return 'danger'
    case 'SKIPPED':
      return 'warning'
    default:
      return 'success'
  }
}

function padStep(value?: number) {
  return String(value || 0).padStart(2, '0')
}

function formatPercent(value?: number) {
  if (value === undefined || !Number.isFinite(value)) {
    return '0%'
  }
  return `${Math.round(value * 100)}%`
}

function evidenceList(step: AiAgentStepView) {
  return Array.isArray(step.evidence) ? step.evidence as Array<Record<string, unknown>> : []
}

function evidenceCount(step: AiAgentStepView) {
  return evidenceList(step).length
}

function firstEvidence(step: AiAgentStepView) {
  return evidenceList(step)[0]
}

function stringOutput(step: AiAgentStepView | undefined, key: string) {
  if (!step || !step.output || typeof step.output !== 'object' || Array.isArray(step.output)) {
    return undefined
  }
  const value = (step.output as Record<string, unknown>)[key]
  return typeof value === 'string' ? value : undefined
}

function evidenceTitle(value?: Record<string, unknown>) {
  return String(value?.title || value?.sourceType || value?.type || '证据片段')
}

function evidenceDescription(value?: Record<string, unknown>) {
  return String(value?.description || value?.summary || value?.contentSnippet || value?.sourcePath || '暂无摘要')
}
function inferredReviewTarget() {
  if (props.run?.subjectType === 'AI_REVIEW_TICKET') {
    return '/ai-reviews'
  }
  return null
}

function objectList(value: unknown) {
  return Array.isArray(value) ? value.filter(isRecord) : []
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function claimKey(value: Record<string, unknown>) {
  return String(value.claim || value.claimType || value.source || 'claim')
}

function claimLabel(value: Record<string, unknown>) {
  return String(value.claimType || value.verdict || 'Claim')
}

function claimSupport(value: Record<string, unknown>) {
  return String(value.supportLevel || value.verdict || 'UNKNOWN')
}

function claimText(value: Record<string, unknown>) {
  return String(value.claim || 'No claim text recorded.')
}

function findingKey(value: Record<string, unknown>) {
  return String(value.code || value.message || 'finding')
}

function findingLabel(value: Record<string, unknown>) {
  return String(value.code || value.severity || 'Finding')
}

function findingSeverity(value: Record<string, unknown>) {
  return String(value.severity || 'INFO')
}

function findingMessage(value: Record<string, unknown>) {
  return String(value.message || value.description || 'No finding message recorded.')
}

function stringField(value: Record<string, unknown> | null | undefined, key: string) {
  const field = value?.[key]
  return typeof field === 'string' ? field : ''
}

function numberField(value: Record<string, unknown> | null | undefined, key: string) {
  const field = value?.[key]
  return typeof field === 'number' && Number.isFinite(field) ? field : undefined
}

function actionLabel(value: Record<string, unknown>) {
  return String(value.action || value.title || value.message || value.code || '建议动作')
}
</script>

<style scoped>
.agent-trace {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 16px;
  border: 1px solid rgba(157, 235, 255, 0.16);
  border-radius: 18px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.055), rgba(255, 255, 255, 0.02)),
    rgba(5, 25, 64, 0.58);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
}

.agent-trace__header,
.agent-step__top,
.agent-trace__status,
.agent-step__meta {
  display: flex;
  align-items: center;
  gap: 10px;
}

.agent-trace__header {
  justify-content: space-between;
  align-items: flex-start;
}

.agent-trace__header span,
.agent-step__top span,
.agent-step__meta,
.agent-step__evidence span,
.agent-trace__summary {
  color: var(--gsmv-muted);
}

.agent-trace__header span,
.agent-step__evidence span {
  display: block;
  font-size: 11px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.agent-trace__header strong {
  display: block;
  margin-top: 6px;
  font-size: 16px;
}

.agent-trace__status {
  justify-content: flex-end;
  flex-wrap: wrap;
}

.agent-trace__summary {
  margin: 0;
  line-height: 1.75;
}

.agent-final-output {
  display: grid;
  gap: 10px;
  padding: 12px 14px;
  border: 1px solid rgba(124, 245, 220, 0.16);
  border-radius: 14px;
  background: rgba(4, 22, 58, 0.54);
}

.agent-final-output header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.agent-final-output header span {
  color: var(--gsmv-muted);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.agent-final-output p {
  margin: 0;
  color: rgba(235, 249, 255, 0.88);
  line-height: 1.7;
}

.agent-final-output__evidence {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.agent-final-output__draft {
  display: grid;
  gap: 4px;
  padding: 10px 12px;
  border: 1px solid rgba(255, 196, 87, 0.18);
  border-radius: 12px;
  background: rgba(84, 53, 14, 0.28);
}

.agent-final-output__draft strong {
  color: #fff8e5;
  font-size: 13px;
}

.agent-final-output__draft small {
  color: rgba(255, 239, 197, 0.76);
  line-height: 1.55;
}

.agent-final-output ul {
  display: grid;
  gap: 7px;
  margin: 0;
  padding-left: 18px;
  color: rgba(224, 242, 255, 0.78);
  line-height: 1.65;
}

.agent-review-callout {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 12px 14px;
  border: 1px solid rgba(255, 196, 87, 0.26);
  border-radius: 14px;
  background: linear-gradient(135deg, rgba(85, 55, 14, 0.5), rgba(8, 24, 58, 0.6));
}

.agent-review-callout--danger {
  border-color: rgba(255, 130, 130, 0.32);
  background: linear-gradient(135deg, rgba(95, 28, 38, 0.52), rgba(8, 24, 58, 0.62));
}

.agent-review-callout span {
  display: block;
  color: #ffd27a;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.agent-review-callout--danger span {
  color: #ffaaa7;
}

.agent-review-callout strong {
  display: block;
  margin-top: 4px;
  color: #fffaf1;
  font-size: 14px;
}

.agent-review-callout p {
  margin: 4px 0 0;
  color: rgba(244, 250, 255, 0.76);
  line-height: 1.65;
}

.agent-review-callout__link {
  flex: 0 0 auto;
  padding: 8px 12px;
  border: 1px solid rgba(255, 255, 255, 0.24);
  border-radius: 10px;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  text-decoration: none;
  background: rgba(255, 255, 255, 0.08);
}

.agent-review-callout__link:hover {
  border-color: rgba(255, 255, 255, 0.42);
  background: rgba(255, 255, 255, 0.14);
}

.agent-replay {
  display: grid;
  gap: 12px;
  padding: 12px;
  border: 1px solid rgba(124, 245, 220, 0.14);
  border-radius: 14px;
  background: rgba(3, 17, 46, 0.46);
}

.agent-replay__metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.agent-replay__metrics article {
  min-width: 0;
  padding: 10px;
  border: 1px solid rgba(157, 235, 255, 0.12);
  border-radius: 10px;
  background: rgba(9, 34, 78, 0.62);
}

.agent-replay__metrics span,
.agent-replay__block header span,
.agent-replay__block small {
  color: var(--gsmv-muted);
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.agent-replay__metrics strong {
  display: block;
  margin-top: 5px;
  color: #f7fcff;
  font-size: 17px;
}

.agent-replay__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.agent-replay__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.agent-replay__block {
  min-width: 0;
  padding: 12px;
  border: 1px solid rgba(157, 235, 255, 0.12);
  border-radius: 12px;
  background: rgba(5, 28, 70, 0.58);
}

.agent-replay__block header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.agent-replay__block ul {
  display: grid;
  gap: 9px;
  margin: 10px 0 0;
  padding: 0;
  list-style: none;
}

.agent-replay__block li {
  display: grid;
  gap: 5px;
  padding-top: 9px;
  border-top: 1px solid rgba(157, 235, 255, 0.1);
}

.agent-replay__block li:first-child {
  border-top: 0;
  padding-top: 0;
}

.agent-replay__block li > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.agent-replay__block strong {
  color: #f7fcff;
  font-size: 13px;
}

.agent-replay__block p {
  margin: 0;
  color: rgba(224, 242, 255, 0.78);
  font-size: 12px;
  line-height: 1.65;
}

.agent-trace__rail {
  display: grid;
  gap: 10px;
}

.agent-step {
  position: relative;
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  gap: 12px;
  padding: 12px;
  border: 1px solid rgba(157, 235, 255, 0.12);
  border-radius: 16px;
  background: rgba(5, 30, 75, 0.58);
  overflow: hidden;
}

.agent-step::before {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 3px;
  background: linear-gradient(180deg, #61e4ff, #7cf5dc);
  opacity: 0.78;
}

.agent-step--failed::before {
  background: linear-gradient(180deg, #ff8a8a, #ffbd7a);
}

.agent-step__index {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 12px;
  background: rgba(97, 228, 255, 0.12);
  color: var(--gsmv-primary);
  font-weight: 800;
}

.agent-step__body {
  min-width: 0;
}

.agent-step__top {
  justify-content: space-between;
  align-items: flex-start;
}

.agent-step__top strong {
  display: block;
  font-size: 15px;
}

.agent-step p {
  margin: 8px 0 0;
  color: rgba(232, 247, 255, 0.86);
  line-height: 1.72;
}

.agent-step__meta {
  flex-wrap: wrap;
  margin-top: 10px;
  font-size: 12px;
}

.agent-step__evidence {
  margin-top: 10px;
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid rgba(124, 245, 220, 0.12);
  background: rgba(4, 20, 52, 0.52);
}

.agent-step__evidence strong {
  display: block;
  margin-top: 4px;
}

.agent-step__evidence p {
  color: var(--gsmv-muted);
}

@media (max-width: 720px) {
  .agent-trace__header,
  .agent-step__top {
    flex-direction: column;
  }

  .agent-replay__metrics,
  .agent-replay__grid,
  .agent-review-callout {
    grid-template-columns: 1fr;
    flex-direction: column;
    align-items: flex-start;
  }

  .agent-trace__status {
    justify-content: flex-start;
  }
}
</style>
