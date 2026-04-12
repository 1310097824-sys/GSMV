import { http, unwrap } from '@/api/http'
import type { LoginResult } from '@/types/gsmv'

export function login(payload: { username: string; password: string }) {
  return unwrap<LoginResult>(http.post('/v1/auth/login', payload))
}

export function register(payload: {
  username: string
  password: string
  displayName: string
  email?: string
  phone?: string
  roleCode: 'STUDENT' | 'PUBLIC'
}) {
  return unwrap<void>(http.post('/v1/auth/register', payload))
}
