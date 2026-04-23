import { useState, useEffect } from 'react'
import { getDatabaseSize } from '@/api/client'

const TTL_MS = 30 * 60 * 1000

function cacheKey(socketId: number, containerId: string, databaseName: string) {
  return `dbsize:${socketId}:${containerId}:${databaseName}`
}

function readCache(key: string): number | null {
  try {
    const raw = localStorage.getItem(key)
    if (!raw) return null
    const { size, fetchedAt } = JSON.parse(raw) as { size: number; fetchedAt: number }
    if (Date.now() - fetchedAt > TTL_MS) {
      localStorage.removeItem(key)
      return null
    }
    return size
  } catch {
    return null
  }
}

function writeCache(key: string, size: number) {
  try {
    localStorage.setItem(key, JSON.stringify({ size, fetchedAt: Date.now() }))
  } catch {
    // ignore storage errors
  }
}

export type DbSizeState =
  | { status: 'loading' }
  | { status: 'ok'; bytes: number }
  | { status: 'error' }

export function useDatabaseSize(
  socketId: number,
  containerId: string,
  databaseName: string,
): DbSizeState {
  const key = cacheKey(socketId, containerId, databaseName)
  const cached = readCache(key)

  const [state, setState] = useState<DbSizeState>(
    cached !== null ? { status: 'ok', bytes: cached } : { status: 'loading' },
  )

  useEffect(() => {
    if (cached !== null) return
    let cancelled = false
    getDatabaseSize(socketId, containerId, databaseName)
      .then((size) => {
        if (cancelled) return
        if (size < 0) {
          setState({ status: 'error' })
          return
        }
        writeCache(key, size)
        setState({ status: 'ok', bytes: size })
      })
      .catch(() => {
        if (cancelled) return
        setState({ status: 'error' })
      })
    return () => {
      cancelled = true
    }
  }, [socketId, containerId, databaseName])

  return state
}
