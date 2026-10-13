import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { authApi } from '../api/services'
import { setUnauthorizedHandler, tokenStore } from '../api/http'
import { logger } from '../utils/logger'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  // True while we check a stored token on first load, so protected pages do not flash the login screen.
  const [loading, setLoading] = useState(Boolean(tokenStore.get()))

  const logout = useCallback(() => {
    tokenStore.clear()
    setUser(null)
    logger.info('Logged out')
  }, [])

  useEffect(() => {
    setUnauthorizedHandler(logout)
  }, [logout])

  useEffect(() => {
    if (!tokenStore.get()) return
    authApi
      .me()
      .then(setUser)
      .catch(() => tokenStore.clear())
      .finally(() => setLoading(false))
  }, [])

  // Stores the token, loads the profile, and returns it so callers can route by role.
  const completeAuth = useCallback(async (authResponse) => {
    tokenStore.set(authResponse.token)
    const me = await authApi.me()
    setUser(me)
    logger.info(`Logged in: user ${me.id}, role ${me.role}`)
    return me
  }, [])

  const login = useCallback(async (email, password) => completeAuth(await authApi.login({ email, password })), [completeAuth])
  const register = useCallback(async (form) => completeAuth(await authApi.register(form)), [completeAuth])

  const value = useMemo(
    () => ({
      user,
      loading,
      isLoggedIn: Boolean(user),
      // Only used to decide what to SHOW. The backend independently rejects admin calls from customers.
      isAdmin: user?.role === 'ADMIN',
      login,
      register,
      logout,
      setUser,
    }),
    [user, loading, login, register, logout],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export const useAuth = () => useContext(AuthContext)
