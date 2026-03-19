import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { socketsApi } from '@/api/client'
import type { CreateSocketRequest, UpdateSocketRequest } from '@/api/types'

export const SOCKETS_KEY = ['sockets'] as const

export function useSockets() {
  return useQuery({
    queryKey: SOCKETS_KEY,
    queryFn: socketsApi.list,
    refetchInterval: 30_000,
  })
}

export function useSocket(id: number) {
  const { data: sockets } = useSockets()
  return sockets?.find((s) => s.id === id)
}

export function useCreateSocket() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: CreateSocketRequest) => socketsApi.create(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: SOCKETS_KEY }),
  })
}

export function useUpdateSocket() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: UpdateSocketRequest }) =>
      socketsApi.update(id, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: SOCKETS_KEY }),
  })
}

export function useDeleteSocket() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => socketsApi.delete(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: SOCKETS_KEY }),
  })
}

export function useConnectSocket() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => socketsApi.connect(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: SOCKETS_KEY }),
  })
}

export function useDisconnectSocket() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => socketsApi.disconnect(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: SOCKETS_KEY }),
  })
}
