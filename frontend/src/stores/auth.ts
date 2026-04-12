import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { ACCESS_TOKEN_KEY, PROFILE_KEY } from '@/constants/auth'
import { login } from '@/api/auth'
import type { AuthProfile } from '@/types/gsmv'

function readProfile() {
  const raw = localStorage.getItem(PROFILE_KEY)
  return raw ? (JSON.parse(raw) as AuthProfile) : null
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem(ACCESS_TOKEN_KEY) ?? '')
  const profile = ref<AuthProfile | null>(readProfile())
  const loading = ref(false)

  const isAuthenticated = computed(() => Boolean(token.value))
  const authorities = computed(() => profile.value?.authorities ?? [])

  function persistProfile(nextProfile: AuthProfile | null) {
    profile.value = nextProfile
    if (nextProfile) {
      localStorage.setItem(PROFILE_KEY, JSON.stringify(nextProfile))
    } else {
      localStorage.removeItem(PROFILE_KEY)
    }
  }

  async function performLogin(username: string, password: string) {
    loading.value = true
    try {
      const payload = await login({ username, password })
      token.value = payload.accessToken
      persistProfile(payload.user)
      localStorage.setItem(ACCESS_TOKEN_KEY, payload.accessToken)
    } finally {
      loading.value = false
    }
  }

  function patchProfile(patch: Partial<AuthProfile>) {
    if (!profile.value) {
      return
    }
    persistProfile({
      ...profile.value,
      ...patch,
    })
  }

  function logout() {
    token.value = ''
    persistProfile(null)
    localStorage.removeItem(ACCESS_TOKEN_KEY)
  }

  return {
    token,
    profile,
    loading,
    isAuthenticated,
    authorities,
    performLogin,
    patchProfile,
    logout,
  }
})
