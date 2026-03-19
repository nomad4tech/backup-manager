import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { historyApi } from '@/api/client'
import type { HistoryFilters } from '@/api/types'

export const HISTORY_KEY = ['history'] as const

export function useHistory(filters: HistoryFilters = {}) {
  return useQuery({
    queryKey: [...HISTORY_KEY, filters],
    queryFn: () => historyApi.list(filters),
    refetchInterval: (query) => {
      const hasRunning = query.state.data?.content.some((r) => r.status === 'RUNNING')
      return hasRunning ? 5_000 : 15_000
    },
  })
}

export function useHistoryRecord(id: number) {
  return useQuery({
    queryKey: [...HISTORY_KEY, id],
    queryFn: () => historyApi.get(id),
  })
}

export function useDeleteHistoryRecord() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => historyApi.delete(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: HISTORY_KEY }),
  })
}
