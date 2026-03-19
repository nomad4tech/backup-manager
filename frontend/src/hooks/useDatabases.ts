import { useQuery } from '@tanstack/react-query'
import { discoveryApi } from '@/api/client'

export function useDatabases(socketId: number | null, containerId: string | null) {
  return useQuery({
    queryKey: ['databases', socketId, containerId],
    queryFn: () => discoveryApi.databases(socketId!, containerId!),
    enabled: socketId != null && containerId != null,
  })
}
