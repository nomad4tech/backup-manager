import { useQuery } from '@tanstack/react-query'
import { discoveryApi } from '@/api/client'

export function useContainers(socketId: number | null) {
  return useQuery({
    queryKey: ['containers', socketId],
    queryFn: () => discoveryApi.containers(socketId!),
    enabled: socketId != null,
  })
}

export function useContainer(socketId: number | null, containerId: string | null) {
  return useQuery({
    queryKey: ['container', socketId, containerId],
    queryFn: () => discoveryApi.container(socketId!, containerId!),
    enabled: socketId != null && containerId != null,
  })
}
