import { Navigate, Outlet } from 'react-router-dom'
import { useMe } from '@/hooks/useAuth'

export function ProtectedRoute() {
  const { data, isLoading, isError } = useMe()

  if (isLoading) return null
  if (isError) return <Navigate to="/login" replace />
  if (!data) return null

  return <Outlet />
}
