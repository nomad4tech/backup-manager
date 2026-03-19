import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { tasksApi } from '@/api/client'
import type { ApiError } from '@/api/client'
import type { CreateBackupTaskRequest, UpdateBackupTaskRequest } from '@/api/types'

export const TASKS_KEY = ['tasks'] as const

export function useTasks() {
  const query = useQuery({
    queryKey: TASKS_KEY,
    queryFn: tasksApi.list,
    select: (data) => data,
    refetchInterval: (query) => {
      const hasRunning = query.state.data?.some((t) => t.status === 'RUNNING')
      return hasRunning ? 10_000 : 30_000
    },
  })
  return query
}

export function useTask(id: number) {
  return useQuery({
    queryKey: [...TASKS_KEY, id],
    queryFn: () => tasksApi.get(id),
  })
}

export function useCreateTask() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: CreateBackupTaskRequest) => tasksApi.create(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: TASKS_KEY }),
  })
}

export function useUpdateTask() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: UpdateBackupTaskRequest }) =>
      tasksApi.update(id, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: TASKS_KEY }),
  })
}

export function useToggleTask() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => tasksApi.toggle(id),
    onSuccess: () => qc.refetchQueries({ queryKey: TASKS_KEY }),
  })
}

export function useDeleteTask() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => tasksApi.delete(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: TASKS_KEY }),
  })
}

export type ForceRunResult = '202' | '409' | '503' | 'error'

export function useForceRunTask() {
  const qc = useQueryClient()
  return useMutation<ForceRunResult, ApiError, number>({
    mutationFn: async (id: number): Promise<ForceRunResult> => {
      const res = await tasksApi.forceRun(id)
      if (res.status === 202) return '202'
      if (res.status === 409) return '409'
      if (res.status === 503) return '503'
      return 'error'
    },
    onSuccess: (result) => {
      if (result === '202') {
        void qc.invalidateQueries({ queryKey: TASKS_KEY })
      }
    },
  })
}
