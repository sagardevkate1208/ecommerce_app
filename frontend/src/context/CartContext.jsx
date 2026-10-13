import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { cartApi } from '../api/services'
import { useAuth } from './AuthContext'

const CartContext = createContext(null)
const EMPTY = { items: [], totalItems: 0, subtotal: 0 }

/** Keeps the cart in one place so the navbar badge and the cart page always agree. */
export function CartProvider({ children }) {
  const { isLoggedIn } = useAuth()
  const [cart, setCart] = useState(EMPTY)

  const refresh = useCallback(async () => {
    try {
      setCart(await cartApi.get())
    } catch {
      setCart(EMPTY)
    }
  }, [])

  useEffect(() => {
    if (isLoggedIn) refresh()
    else setCart(EMPTY)
  }, [isLoggedIn, refresh])

  // Each mutation returns the new cart from the server, so we simply store it.
  const addItem = useCallback(async (productId, quantity = 1) => setCart(await cartApi.add(productId, quantity)), [])
  const updateItem = useCallback(async (productId, quantity) => setCart(await cartApi.update(productId, quantity)), [])
  const removeItem = useCallback(async (productId) => setCart(await cartApi.remove(productId)), [])
  const clear = useCallback(async () => {
    await cartApi.clear()
    setCart(EMPTY)
  }, [])

  const value = useMemo(
    () => ({ cart, refresh, addItem, updateItem, removeItem, clear }),
    [cart, refresh, addItem, updateItem, removeItem, clear],
  )
  return <CartContext.Provider value={value}>{children}</CartContext.Provider>
}

export const useCart = () => useContext(CartContext)
