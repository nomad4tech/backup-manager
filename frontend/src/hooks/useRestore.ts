import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { restoreApi } from '@/api/client'
import type { CreateRestoreRequest } from '@/api/types'

export const RESTORE_KEY = ['restore'] as const

export function useRestoreRecords(page = 0, size = 20) {
  return useQuery({
    queryKey: [...RESTORE_KEY, page, size],
    queryFn: () => restoreApi.list(page, size),
    refetchInterval: (query) => {
      const hasActive = query.state.data?.content?.some(
        (r) => r.status === 'PENDING' || r.status === 'RUNNING',
      )
      return hasActive ? 5_000 : 30_000
    },
  })
}

export function useRestoreRecord(id: number) {
  return useQuery({
    queryKey: [...RESTORE_KEY, id],
    queryFn: () => restoreApi.get(id),
    refetchInterval: (query) => {
      const status = query.state.data?.status
      return status === 'PENDING' || status === 'RUNNING' ? 3_000 : false
    },
  })
}

export function useAvailableBackups() {
  return useQuery({
    queryKey: ['restore-backups'],
    queryFn: restoreApi.availableBackups,
  })
}

export function useCompatibleContainers(socketId: number | null, dbType: string | null) {
  return useQuery({
    queryKey: ['restore-containers', socketId, dbType],
    queryFn: () => restoreApi.compatibleContainers(socketId!, dbType!),
    enabled: socketId != null && dbType != null,
  })
}

export function useCreateRestore() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: CreateRestoreRequest) => restoreApi.create(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: RESTORE_KEY }),
  })
}

export function useCancelRestore() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => restoreApi.cancel(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: RESTORE_KEY }),
  })
}
