import { useEffect } from 'react'
import { Route, Routes, useLocation } from 'react-router-dom'
import { logger } from './utils/logger'
import AdminLayout from './components/admin/AdminLayout'
import AdminRoute from './components/AdminRoute'
import Footer from './components/Footer'
import Navbar from './components/Navbar'
import ProtectedRoute from './components/ProtectedRoute'
import AdminCategoriesPage from './pages/admin/AdminCategoriesPage'
import AdminDashboardPage from './pages/admin/AdminDashboardPage'
import AdminOrderDetailPage from './pages/admin/AdminOrderDetailPage'
import AdminOrdersPage from './pages/admin/AdminOrdersPage'
import AdminPaymentsPage from './pages/admin/AdminPaymentsPage'
import AdminProductFormPage from './pages/admin/AdminProductFormPage'
import AdminProductsPage from './pages/admin/AdminProductsPage'
import CartPage from './pages/CartPage'
import CheckoutPage from './pages/CheckoutPage'
import HomePage from './pages/HomePage'
import LoginPage from './pages/LoginPage'
import NotFoundPage from './pages/NotFoundPage'
import OrderDetailPage from './pages/OrderDetailPage'
import OrdersPage from './pages/OrdersPage'
import ProductDetailPage from './pages/ProductDetailPage'
import ProfilePage from './pages/ProfilePage'
import RegisterPage from './pages/RegisterPage'

export default function App() {
  const location = useLocation()
  useEffect(() => {
    logger.info(`Page: ${location.pathname}${location.search}`)
  }, [location.pathname, location.search])

  return (
    <div className="flex min-h-screen flex-col">
      <Navbar />
      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-6">
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/products/:id" element={<ProductDetailPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />

          {/* Any logged-in user */}
          <Route element={<ProtectedRoute />}>
            <Route path="/cart" element={<CartPage />} />
            <Route path="/checkout" element={<CheckoutPage />} />
            <Route path="/orders" element={<OrdersPage />} />
            <Route path="/orders/:id" element={<OrderDetailPage />} />
            <Route path="/profile" element={<ProfilePage />} />

            {/* Admins only */}
            <Route element={<AdminRoute />}>
              <Route path="/admin" element={<AdminLayout />}>
                <Route index element={<AdminDashboardPage />} />
                <Route path="products" element={<AdminProductsPage />} />
                <Route path="products/new" element={<AdminProductFormPage />} />
                <Route path="products/:id/edit" element={<AdminProductFormPage />} />
                <Route path="categories" element={<AdminCategoriesPage />} />
                <Route path="orders" element={<AdminOrdersPage />} />
                <Route path="orders/:id" element={<AdminOrderDetailPage />} />
                <Route path="payments" element={<AdminPaymentsPage />} />
              </Route>
            </Route>
          </Route>

          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </main>
      <Footer />
    </div>
  )
}
