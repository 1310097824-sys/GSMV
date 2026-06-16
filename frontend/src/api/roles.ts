import { http, unwrap } from '@/api/http'
import type { PermissionOption, RolePermissionView } from '@/types/gsmv'

export function fetchRolePermissions() {
  return unwrap<RolePermissionView[]>(http.get('/v1/roles'))
}

export function fetchPermissions() {
  return unwrap<PermissionOption[]>(http.get('/v1/roles/permissions'))
}

export function createRole(payload: {
  code: string
  name: string
  description?: string
  permissionCodes: string[]
}) {
  return unwrap<RolePermissionView>(http.post('/v1/roles', payload))
}

export function updateRole(
  id: number,
  payload: {
    code: string
    name: string
    description?: string
    permissionCodes: string[]
  },
) {
  return unwrap<RolePermissionView>(http.put(`/v1/roles/${id}`, payload))
}

export function deleteRole(id: number) {
  return unwrap<void>(http.delete(`/v1/roles/${id}`))
}
