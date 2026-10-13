import { useEffect, useRef, useState } from 'react'
import { Link, NavLink, useNavigate } from 'react-router-dom'
import { ChevronDown, LayoutDashboard, LogOut, Package, ShoppingBag, ShoppingCart, User } from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { useCart } from '../context/CartContext'
import { useToast } from '../context/ToastContext'

const linkClass = ({ isActive }) =>
  `flex items-center gap-1.5 text-sm font-medium ${isActive ? 'text-indigo-600' : 'text-slate-600 hover:text-slate-900'}`

export default function Navbar() {
  const { user, isLoggedIn, isAdmin, logout } = useAuth()
  const { cart } = useCart()
  const toast = useToast()
  const navigate = useNavigate()
  const [menuOpen, setMenuOpen] = useState(false)
  const menuRef = useRef(null)

  // Close the user menu when clicking anywhere else.
  useEffect(() => {
    const onClick = (e) => {
      if (menuRef.current && !menuRef.current.contains(e.target)) setMenuOpen(false)
    }
    document.addEventListener('mousedown', onClick)
    return () => document.removeEventListener('mousedown', onClick)
  }, [])

  const handleLogout = () => {
    setMenuOpen(false)
    logout()
    toast.info('You have been logged out.')
    navigate('/')
  }

  return (
    <header className="sticky top-0 z-40 border-b border-slate-200 bg-white/90 backdrop-blur">
      <nav className="mx-auto flex max-w-6xl items-center justify-between px-4 py-3">
        <Link to="/" className="flex items-center gap-2 text-xl font-bold text-indigo-600">
          <ShoppingBag className="h-6 w-6" />
          ShopEasy
        </Link>

        <div className="flex items-center gap-5">
          <NavLink to="/" end className={linkClass}>
            Shop
          </NavLink>

          {/* The admin entry exists in the DOM only for admins. */}
          {isAdmin && (
            <NavLink to="/admin" className={linkClass}>
              <LayoutDashboard className="h-4 w-4" />
              Admin
            </NavLink>
          )}

          {isLoggedIn ? (
            <>
              <NavLink to="/cart" className={linkClass}>
                <ShoppingCart className="h-4 w-4" />
                Cart
                {cart.totalItems > 0 && (
                  <span className="rounded-full bg-indigo-600 px-2 py-0.5 text-xs text-white">{cart.totalItems}</span>
                )}
              </NavLink>

              <div className="relative" ref={menuRef}>
                <button
                  onClick={() => setMenuOpen((open) => !open)}
                  className="flex items-center gap-2 rounded-full border border-slate-200 py-1 pl-1 pr-3 text-sm font-medium hover:bg-slate-50"
                  aria-haspopup="menu"
                  aria-expanded={menuOpen}
                >
                  <span className="flex h-7 w-7 items-center justify-center rounded-full bg-indigo-600 text-xs font-bold text-white">
                    {user.firstName.charAt(0).toUpperCase()}
                  </span>
                  {user.firstName}
                  {isAdmin && <span className="rounded bg-amber-100 px-1.5 text-xs text-amber-800">Admin</span>}
                  <ChevronDown className="h-4 w-4 text-slate-500" />
                </button>

                {menuOpen && (
                  <div role="menu" className="absolute right-0 mt-2 w-48 overflow-hidden rounded-lg border border-slate-200 bg-white py-1 shadow-lg">
                    <MenuLink to="/profile" icon={<User className="h-4 w-4" />} onClick={() => setMenuOpen(false)}>
                      My profile
                    </MenuLink>
                    <MenuLink to="/orders" icon={<Package className="h-4 w-4" />} onClick={() => setMenuOpen(false)}>
                      My orders
                    </MenuLink>
                    <button onClick={handleLogout} role="menuitem"
                      className="flex w-full items-center gap-2 px-4 py-2 text-left text-sm text-red-600 hover:bg-slate-50">
                      <LogOut className="h-4 w-4" />
                      Logout
                    </button>
                  </div>
                )}
              </div>
            </>
          ) : (
            <>
              <NavLink to="/login" className={linkClass}>
                Login
              </NavLink>
              <Link to="/register" className="btn-primary">
                Sign up
              </Link>
            </>
          )}
        </div>
      </nav>
    </header>
  )
}

function MenuLink({ to, icon, onClick, children }) {
  return (
    <Link to={to} role="menuitem" onClick={onClick} className="flex items-center gap-2 px-4 py-2 text-sm text-slate-700 hover:bg-slate-50">
      {icon}
      {children}
    </Link>
  )
}
