import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { authApi } from '@/api/client'

export const AUTH_ME_KEY = ['auth', 'me'] as const

export function useMe() {
  return useQuery({
    queryKey: AUTH_ME_KEY,
    queryFn: authApi.me,
    retry: false,
    staleTime: Infinity,
    refetchOnMount: false,
    refetchOnWindowFocus: false,
  })
}

export function useLogin() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ username, password, rememberMe }: {
      username: string
      password: string
      rememberMe: boolean
    }) => authApi.login(username, password, rememberMe),
    onSuccess: (data) => {
      qc.setQueryData(AUTH_ME_KEY, data)
      qc.invalidateQueries({ queryKey: AUTH_ME_KEY })
    },
  })
}

export function useLogout() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: authApi.logout,
    onSettled: () => {
      qc.clear()
    },
  })
}

export function useChangeCredentials() {
  return useMutation({
    mutationFn: ({ currentPassword, newUsername, newPassword }: {
      currentPassword: string
      newUsername?: string
      newPassword?: string
    }) => authApi.changeCredentials(currentPassword, newUsername, newPassword),
  })
}
