import axios from 'axios'
import { ACCESS_TOKEN_KEY, PROFILE_KEY } from '@/constants/auth'
import type { ApiResponse } from '@/types/gsmv'

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000,
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(ACCESS_TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem(ACCESS_TOKEN_KEY)
      localStorage.removeItem(PROFILE_KEY)
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    }

    const responseMessage = error.response?.data?.message
    if (typeof responseMessage === 'string' && responseMessage.trim()) {
      return Promise.reject(new Error(responseMessage.trim()))
    }

    if (error.code === 'ECONNABORTED') {
      return Promise.reject(new Error('请求超时，请稍后重试'))
    }

    return Promise.reject(error)
  },
)

export async function unwrap<T>(promise: Promise<{ data: ApiResponse<T> }>): Promise<T> {
  const response = await promise
  if (response.data.code !== 'OK') {
    throw new Error(response.data.message)
  }
  return response.data.data
}
