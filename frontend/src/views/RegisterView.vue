<template>
  <div class="auth-shell">
    <div class="auth-shell__grid">
      <section class="auth-visual auth-visual--register">
        <div class="auth-visual__content">
          <span class="auth-visual__eyebrow">Join The Sea</span>
          <h1 class="auth-visual__title">申请加入<br />海洋观测网络</h1>
          <p class="auth-visual__desc">
            面向学生与公众开放注册申请。审核通过后，你可以参与观测记录录入、物种资料查看与海洋生态数据浏览。
          </p>
          <div class="auth-visual__chips">
            <span>学生申请</span>
            <span>公众参与</span>
            <span>审核准入</span>
          </div>
        </div>

        <div class="auth-visual__footer">
          <div class="auth-metric">
            <strong>审核制</strong>
            <span>提交后进入待审核状态，管理员通过后即可登录系统。</span>
          </div>
          <div class="auth-metric">
            <strong>角色化</strong>
            <span>根据学生、公众等身份分配不同可见范围与操作权限。</span>
          </div>
          <div class="auth-metric">
            <strong>可追踪</strong>
            <span>个人资料、活动日志与角色变化会在系统内持续记录。</span>
          </div>
        </div>
      </section>

      <el-card class="panel-card auth-card" shadow="never">
      <template #header>
        <div class="auth-card__header">
          <span class="auth-card__eyebrow">GSMV</span>
          <strong>用户注册申请</strong>
          <p>填写你的基础信息并提交申请，审核通过后即可进入海洋生物多样性平台。</p>
        </div>
      </template>

      <el-form label-position="top" @submit.prevent="handleSubmit">
        <el-form-item label="申请身份">
          <el-radio-group v-model="form.roleCode">
            <el-radio value="STUDENT">学生</el-radio>
            <el-radio value="PUBLIC">公众</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="显示名称">
          <el-input v-model="form.displayName" placeholder="请输入显示名称" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" />
        </el-form-item>
        <el-button type="primary" size="large" class="auth-card__button" :loading="submitting" @click="handleSubmit">
          提交注册申请
        </el-button>
      </el-form>

      <div class="auth-card__footer">
        <span>提交后需要管理员审核通过才能登录</span>
        <RouterLink to="/login">返回登录</RouterLink>
      </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { RouterLink, useRouter } from 'vue-router'
import { register } from '@/api/auth'

const router = useRouter()
const submitting = ref(false)

const form = reactive({
  roleCode: 'STUDENT' as 'STUDENT' | 'PUBLIC',
  username: '',
  displayName: '',
  email: '',
  phone: '',
  password: '',
})

async function handleSubmit() {
  if (!form.username.trim() || !form.displayName.trim() || !form.password.trim()) {
    ElMessage.warning('请填写完整的注册信息')
    return
  }

  submitting.value = true
  try {
    await register({
      roleCode: form.roleCode,
      username: form.username.trim(),
      displayName: form.displayName.trim(),
      email: form.email.trim() || undefined,
      phone: form.phone.trim() || undefined,
      password: form.password,
    })
    ElMessage.success('注册申请已提交，请等待管理员审核')
    router.push('/login')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '注册申请提交失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.auth-visual--register {
  background-position: center 42%;
}
</style>
