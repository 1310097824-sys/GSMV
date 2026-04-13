<template>
  <div class="auth-shell">
    <div class="auth-shell__grid">
      <section class="auth-visual">
        <div class="auth-visual__content">
          <span class="auth-visual__eyebrow">Ocean Archive</span>
          <h1 class="auth-visual__title">走进海底<br />观测与档案中心</h1>
          <p class="auth-visual__desc">
            从近海生态系统到全球观测点位，把物种、生态系统、观测事件与统计分析收束进同一片海域视图。
          </p>
          <div class="auth-visual__chips">
            <span>物种建档</span>
            <span>生态观测</span>
            <span>报表分析</span>
          </div>
        </div>

        <div class="auth-visual__footer">
          <div class="auth-metric">
            <strong>20+</strong>
            <span>海洋物种样例档案已可直接进入管理视图</span>
          </div>
          <div class="auth-metric">
            <strong>23</strong>
            <span>近 30 天观测记录在地图、报表与详情页联动展示</span>
          </div>
          <div class="auth-metric">
            <strong>14</strong>
            <span>生态系统样例覆盖近海、珊瑚礁、海草床等场景</span>
          </div>
        </div>
      </section>

      <el-card class="panel-card auth-card" shadow="never">
        <template #header>
          <div class="auth-card__header">
            <span class="auth-card__eyebrow">GSMV</span>
            <strong>系统登录</strong>
            <p>进入海洋生物多样性平台，继续处理档案建档、生态观测与统计报表工作。</p>
          </div>
        </template>

        <el-form label-position="top" @submit.prevent="handleSubmit">
          <el-form-item label="用户名">
            <el-input v-model="form.username" placeholder="请输入用户名" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" />
          </el-form-item>
          <el-button type="primary" size="large" class="auth-card__button" :loading="authStore.loading" @click="handleSubmit">
            登录
          </el-button>
        </el-form>

        <div class="auth-card__footer">
          <span>学生和公众用户可先申请注册</span>
          <RouterLink to="/register">去注册</RouterLink>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const form = reactive({
  username: '',
  password: '',
})

async function handleSubmit() {
  try {
    await authStore.performLogin(form.username, form.password)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
    router.push(redirect)
    ElMessage.success('登录成功')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '登录失败')
  }
}
</script>

<style scoped>
</style>
