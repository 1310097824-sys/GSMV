import { http, unwrap } from '@/api/http'
import type { PageResponse, RoleOption, UserProfileView, UserView } from '@/types/gsmv'

export function fetchUsers(params: {
  keyword?: string
  status?: number
  approvalStatus?: string
  page: number
  size: number
}) {
  return unwrap<PageResponse<UserView>>(http.get('/v1/users', { params }))
}

export function fetchRoles() {
  return unwrap<RoleOption[]>(http.get('/v1/users/roles'))
}

export function createUser(payload: Record<string, unknown>) {
  return unwrap<UserView>(http.post('/v1/users', payload))
}

export function updateUser(id: number, payload: Record<string, unknown>) {
  return unwrap<UserView>(http.put(`/v1/users/${id}`, payload))
}

export function reviewUser(id: number, payload: { approvalStatus: 'APPROVED' | 'REJECTED'; approvalRemark?: string }) {
  return unwrap<UserView>(http.post(`/v1/users/${id}/approval`, payload))
}

export function fetchCurrentProfile() {
  return unwrap<UserProfileView>(http.get('/v1/users/profile'))
}

export function updateCurrentProfile(payload: { displayName: string; email?: string; phone?: string; bio?: string }) {
  return unwrap<UserProfileView>(http.put('/v1/users/profile', payload))
}

export async function uploadCurrentAvatar(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return unwrap<UserProfileView>(
    http.post('/v1/users/profile/avatar', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    }),
  )
}
