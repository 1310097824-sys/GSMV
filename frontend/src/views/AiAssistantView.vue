<template>
  <div class="page-shell assistant-page">
    <section class="page-hero assistant-hero">
      <div class="page-hero__content">
        <span class="page-hero__eyebrow">Research Copilot</span>
        <h2>把问题说成一句话，剩下的先交给系统里的数据。</h2>
        <p>
          AI 助手会优先解析本地物种、观测与生态系统数据，再组织成适合阅读的回答。适合快速问答、趋势摘要、地点检索与科研整理，也会把本次解析的结构化线索同步展示出来。
        </p>
        <div class="page-hero__actions">
          <el-button type="primary" @click="applyPrompt(quickPrompts[0])">试一个示例问题</el-button>
          <el-button type="primary" plain @click="applyPrompt(quickPrompts[1])">切换到趋势类问题</el-button>
        </div>
      </div>

      <div class="assistant-hero__window">
        <div class="assistant-hero__badge">
          <span>本地数据优先</span>
          <strong>{{ lastResponse?.cacheHit ? '缓存命中，重复问题会更快' : '首次问题会实时生成结构化答案' }}</strong>
        </div>
        <div class="assistant-hero__feature-grid">
          <article class="assistant-hero__feature">
            <span>适合问</span>
            <strong>地点、时间、保护等级与趋势</strong>
          </article>
          <article class="assistant-hero__feature">
            <span>识别能力</span>
            <strong>支持模糊地名和生态系统别名</strong>
          </article>
          <article class="assistant-hero__feature">
            <span>回答方式</span>
            <strong>结构化检索 + 可读摘要 + 证据线索</strong>
          </article>
        </div>
        <div class="assistant-hero__preview">
          <span>建议这样提问</span>
          <strong>{{ quickPrompts[0] }}</strong>
        </div>
      </div>
    </section>

    <section class="assistant-story-grid">
      <article class="assistant-story-card">
        <span>它最擅长的事</span>
        <strong>把零散记录整理成一句能直接用的结论</strong>
        <p>像“最近三年湛江附近观测到哪些濒危物种”这类问题，会优先转成结构化检索，再返回摘要。</p>
      </article>
      <article class="assistant-story-card">
        <span>提问建议</span>
        <strong>地点、时间、对象越清楚，结果越稳定</strong>
        <p>把地名、时间范围、生态系统或保护等级一起说出来，回答通常会更具体，也更快命中缓存。</p>
      </article>
      <article class="assistant-story-card">
        <span>当前状态</span>
        <strong>{{ lastResponse?.cacheHit ? '当前结果来自缓存' : '当前结果为实时生成' }}</strong>
        <p>{{ lastResponse ? '解析结果和证据线索会同步显示在右侧，便于你继续追问。' : '当你提出第一个问题后，这里会显示本次分析的即时状态。' }}</p>
      </article>
    </section>

    <div class="assistant-grid">
      <el-card class="panel-card assistant-workbench" shadow="never">
        <template #header>
          <div class="assistant-header">
            <div>
              <strong>对话区</strong>
              <p>像和研究同伴协作一样提问，让系统先把数据检索出来，再帮你组织表述。</p>
            </div>
            <span>{{ messages.length }} 条消息</span>
          </div>
        </template>

        <div ref="messagesContainerRef" class="assistant-messages">
          <div
            v-for="(item, index) in messages"
            :key="`${item.role}-${index}`"
            class="assistant-message"
            :class="item.role === 'user' ? 'assistant-message--user' : 'assistant-message--assistant'"
          >
            <div class="assistant-message__meta">
              <span>{{ item.role === 'user' ? '你' : 'AI 助手' }}</span>
            </div>
            <div class="assistant-message__content">{{ item.content }}</div>
          </div>

          <div v-if="loading" class="assistant-message assistant-message--assistant">
            <div class="assistant-message__meta">
              <span>AI 助手</span>
            </div>
            <div class="assistant-message__content">正在检索系统数据并整理回答，请稍等...</div>
          </div>
        </div>

        <div class="assistant-composer">
          <el-input
            v-model="input"
            type="textarea"
            :rows="4"
            resize="none"
            placeholder="例如：最近三年在湛江附近观测到的濒危物种有哪些？"
            @keydown.ctrl.enter.prevent="sendMessage()"
          />
          <div class="assistant-composer__actions">
            <span>按 `Ctrl + Enter` 发送</span>
            <el-button type="primary" :loading="loading" @click="sendMessage()">发送提问</el-button>
          </div>
        </div>
      </el-card>

      <div class="assistant-side">
        <el-card class="panel-card" shadow="never">
          <template #header>
            <strong>快捷问题</strong>
          </template>
          <div class="prompt-list">
            <button
              v-for="prompt in quickPrompts"
              :key="prompt"
              class="prompt-chip"
              type="button"
              @click="applyPrompt(prompt)"
            >
              {{ prompt }}
            </button>
          </div>
        </el-card>

        <el-card class="panel-card" shadow="never">
          <template #header>
            <strong>解析结果</strong>
          </template>
          <template v-if="lastResponse">
            <div class="assistant-response-meta">
              <span>本次结果</span>
              <el-tag :type="lastResponse.cacheHit ? 'success' : 'info'" effect="dark" round>
                {{ lastResponse.cacheHit ? '缓存命中' : '实时生成' }}
              </el-tag>
            </div>

            <div class="query-tags">
              <el-tag
                v-for="entry in structuredQueryEntries"
                :key="entry.label"
                effect="plain"
                round
              >
                {{ entry.label }}：{{ entry.value }}
              </el-tag>
            </div>

            <el-divider />

            <div class="assistant-side__section">
              <h3>重点摘要</h3>
              <ul class="assistant-list">
                <li v-for="item in lastResponse.highlights" :key="item">{{ item }}</li>
              </ul>
            </div>

            <div class="assistant-side__section">
              <h3>证据线索</h3>
              <div v-if="lastResponse.evidence.length" class="evidence-list">
                <div v-for="item in lastResponse.evidence" :key="`${item.type}-${item.title}`" class="evidence-item">
                  <strong>{{ item.title || '数据线索' }}</strong>
                  <span>{{ item.description || item.type || '-' }}</span>
                </div>
              </div>
              <el-empty v-else description="这次回答没有返回额外证据线索" />
            </div>
          </template>
          <el-empty v-else description="提问后这里会展示结构化查询与证据摘要" />
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { askAiAssistant } from '@/api/ai'
import type { AiAssistantChatResponse, AiAssistantMessage } from '@/types/gsmv'

const quickPrompts = [
  '最近三年在湛江附近观测到的濒危物种有哪些？',
  '请总结红树林生态系统中物种数量的变化趋势。',
  '近 30 天谁的观测活动最活跃？',
  '帮我概括当前系统里保护等级较高的物种分布特点。',
]

const messages = ref<AiAssistantMessage[]>([
  {
    role: 'assistant',
    content: '可以直接问我物种、观测、生态系统或趋势分析问题，我会先转成结构化查询，再结合系统数据给出回答。',
  },
])
const input = ref('')
const loading = ref(false)
const lastResponse = ref<AiAssistantChatResponse | null>(null)
const messagesContainerRef = ref<HTMLDivElement | null>(null)

const structuredQueryEntries = computed(() => {
  if (!lastResponse.value) {
    return []
  }

  const query = lastResponse.value.structuredQuery
  const entries = [
    { label: '意图', value: query.intent },
    { label: '地点', value: query.locationKeyword || '' },
    { label: '生态系统', value: query.ecosystemKeyword || '' },
    { label: '物种', value: query.speciesKeyword || '' },
    { label: '保护等级', value: query.protectionLevel || '' },
    { label: 'IUCN', value: query.iucnStatus || '' },
    { label: '近年范围', value: query.yearsBack ? `${query.yearsBack} 年` : '' },
    { label: '近天范围', value: query.recentDays ? `${query.recentDays} 天` : '' },
    { label: '趋势分析', value: query.includeTrend ? '是' : '' },
    { label: '风险筛选', value: query.riskOnly ? '是' : '' },
  ]

  return entries.filter((item) => item.value)
})

function applyPrompt(prompt: string) {
  input.value = prompt
}

async function scrollMessagesToBottom(behavior: ScrollBehavior = 'smooth') {
  await nextTick()

  if (messagesContainerRef.value) {
    messagesContainerRef.value.scrollTo({
      top: messagesContainerRef.value.scrollHeight,
      behavior,
    })
  }
}

watch(
  () => messages.value.length,
  (_, previousLength) => {
    void scrollMessagesToBottom(previousLength === undefined ? 'auto' : 'smooth')
  },
)

watch(loading, (value) => {
  if (value) {
    void scrollMessagesToBottom('smooth')
  }
})

onMounted(() => {
  void scrollMessagesToBottom('auto')
})

async function sendMessage(prefilled?: string) {
  const message = (prefilled ?? input.value).trim()
  if (!message || loading.value) {
    return
  }

  const history = messages.value.filter((item) => item.role === 'user' || item.role === 'assistant').slice(-6)
  messages.value.push({ role: 'user', content: message })
  input.value = ''
  loading.value = true

  try {
    const response = await askAiAssistant({ message, history })
    lastResponse.value = response
    messages.value.push({ role: 'assistant', content: response.answer })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '智能助手暂时不可用')
    messages.value.push({ role: 'assistant', content: '这次回答失败了，请稍后再试，或者换一种问法。' })
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.assistant-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(320px, 430px);
  align-items: stretch;
  gap: 18px;
}

.assistant-hero__window {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 18px;
  border-radius: 28px;
  border: 1px solid rgba(175, 246, 255, 0.18);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.08), rgba(255, 255, 255, 0.03)),
    rgba(5, 24, 57, 0.54);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.05);
}

.assistant-hero__badge,
.assistant-hero__feature,
.assistant-hero__preview {
  padding: 18px;
  border-radius: 22px;
  border: 1px solid rgba(180, 244, 255, 0.14);
  background: rgba(255, 255, 255, 0.05);
}

.assistant-hero__badge span,
.assistant-hero__feature span,
.assistant-story-card span {
  color: var(--gsmv-muted);
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.assistant-hero__badge strong,
.assistant-hero__feature strong,
.assistant-hero__preview strong,
.assistant-story-card strong {
  display: block;
  margin-top: 10px;
  font-size: 18px;
  line-height: 1.35;
}

.assistant-hero__feature-grid,
.assistant-story-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.assistant-hero__preview span {
  color: var(--gsmv-muted);
  font-size: 13px;
}

.assistant-story-card {
  padding: 20px 22px;
  border-radius: 24px;
  border: 1px solid rgba(178, 244, 255, 0.14);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.08), rgba(255, 255, 255, 0.03)),
    rgba(6, 25, 60, 0.54);
  box-shadow: var(--gsmv-shadow-soft);
}

.assistant-story-card p {
  margin: 10px 0 0;
  color: rgba(232, 247, 255, 0.84);
  line-height: 1.72;
}

.assistant-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(320px, 0.8fr);
  gap: 18px;
}

.assistant-workbench :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.assistant-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.assistant-header p {
  margin: 10px 0 0;
  color: var(--gsmv-muted);
  line-height: 1.68;
}

.assistant-header span {
  color: var(--gsmv-muted);
  font-size: 13px;
}

.assistant-messages {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 440px;
  max-height: 680px;
  overflow: auto;
  padding-right: 6px;
}

.assistant-message {
  max-width: 88%;
  padding: 16px 18px;
  border-radius: 24px;
  border: 1px solid rgba(177, 234, 247, 0.18);
  background: rgba(6, 38, 86, 0.72);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.06);
}

.assistant-message--user {
  margin-left: auto;
  background: linear-gradient(135deg, rgba(32, 189, 194, 0.24), rgba(17, 113, 201, 0.26));
}

.assistant-message__meta {
  margin-bottom: 8px;
  color: var(--gsmv-primary);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.08em;
}

.assistant-message__content {
  line-height: 1.8;
  white-space: pre-wrap;
}

.assistant-composer {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.assistant-composer__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--gsmv-muted);
  font-size: 13px;
}

.assistant-side {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.prompt-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.prompt-chip {
  width: 100%;
  padding: 14px 16px;
  border: 1px solid rgba(160, 235, 245, 0.14);
  border-radius: 18px;
  background: rgba(9, 40, 88, 0.64);
  color: var(--gsmv-text);
  text-align: left;
  line-height: 1.6;
  transition:
    transform 0.18s ease,
    border-color 0.18s ease,
    background-color 0.18s ease;
}

.prompt-chip:hover {
  transform: translateY(-1px);
  border-color: rgba(160, 235, 245, 0.24);
  background: rgba(17, 74, 140, 0.46);
}

.query-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.assistant-response-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
  color: var(--gsmv-muted);
  font-size: 13px;
}

.assistant-side__section h3 {
  margin: 0 0 10px;
  font-size: 15px;
}

.assistant-list {
  margin: 0;
  padding-left: 18px;
  line-height: 1.9;
}

.evidence-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.evidence-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid rgba(177, 234, 247, 0.16);
  background: rgba(6, 38, 86, 0.6);
}

.evidence-item span {
  color: var(--gsmv-muted);
  line-height: 1.7;
}

@media (max-width: 1180px) {
  .assistant-hero,
  .assistant-story-grid,
  .assistant-grid {
    grid-template-columns: 1fr;
  }

  .assistant-hero__feature-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .assistant-composer__actions,
  .assistant-header,
  .assistant-response-meta {
    flex-direction: column;
    align-items: flex-start;
  }

  .assistant-message {
    max-width: 100%;
  }
}
</style>
