<template>
  <div class="page-shell">
    <section class="page-hero">
      <div>
        <h2>智能问答与科研助手</h2>
        <p>支持自然语言提问、观测数据总结、物种信息检索和趋势分析，让系统数据更容易被直接使用。</p>
      </div>
      <el-button type="primary" plain @click="applyPrompt(quickPrompts[0])">试一个示例问题</el-button>
    </section>

    <div class="assistant-grid">
      <el-card class="panel-card" shadow="never">
        <template #header>
          <div class="assistant-header">
            <strong>对话区</strong>
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
            <div class="assistant-message__content">正在检索系统数据并生成回答...</div>
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
  '近30天谁的观测活动最活跃？',
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
.assistant-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(320px, 0.8fr);
  gap: 18px;
}

.assistant-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.assistant-header span {
  color: var(--gsmv-muted);
  font-size: 13px;
}

.assistant-messages {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 420px;
  max-height: 640px;
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
  margin-top: 18px;
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

@media (max-width: 1100px) {
  .assistant-grid {
    grid-template-columns: 1fr;
  }

  .assistant-message {
    max-width: 100%;
  }
}

@media (max-width: 720px) {
  .assistant-composer__actions {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
