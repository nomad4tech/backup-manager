import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { settingsApi } from '@/api/client'
import type { AppSettingsRequest } from '@/api/types'

export const SETTINGS_KEY = ['settings'] as const

export function useSettings() {
  return useQuery({
    queryKey: SETTINGS_KEY,
    queryFn: settingsApi.get,
  })
}

export function useUpdateSettings() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: AppSettingsRequest) => settingsApi.update(data),
    onSuccess: (data) => {
      qc.setQueryData(SETTINGS_KEY, data)
    },
  })
}
