import { authHttp, catalogHttp, cartHttp, orderHttp, paymentHttp } from './http'

const data = (promise) => promise.then((res) => res.data)

export const authApi = {
  register: (body) => data(authHttp.post('/auth/register', body)),
  login: (body) => data(authHttp.post('/auth/login', body)),
  me: () => data(authHttp.get('/users/me')),
  updateMe: (body) => data(authHttp.put('/users/me', body)),
  addresses: () => data(authHttp.get('/users/me/addresses')),
  addAddress: (body) => data(authHttp.post('/users/me/addresses', body)),
  updateAddress: (id, body) => data(authHttp.put(`/users/me/addresses/${id}`, body)),
  deleteAddress: (id) => data(authHttp.delete(`/users/me/addresses/${id}`)),
}

export const catalogApi = {
  // public
  products: (params) => data(catalogHttp.get('/products', { params })),
  product: (id) => data(catalogHttp.get(`/products/${id}`)),
  categories: () => data(catalogHttp.get('/categories')),
  // admin only (the backend rejects these for customers with 403)
  adminProducts: (params) => data(catalogHttp.get('/admin/products', { params })),
  adminProduct: (id) => data(catalogHttp.get(`/admin/products/${id}`)),
  createProduct: (body) => data(catalogHttp.post('/products', body)),
  updateProduct: (id, body) => data(catalogHttp.put(`/products/${id}`, body)),
  deleteProduct: (id) => data(catalogHttp.delete(`/products/${id}`)),
  createCategory: (body) => data(catalogHttp.post('/categories', body)),
  updateCategory: (id, body) => data(catalogHttp.put(`/categories/${id}`, body)),
  deleteCategory: (id) => data(catalogHttp.delete(`/categories/${id}`)),
}

export const cartApi = {
  get: () => data(cartHttp.get('/cart')),
  add: (productId, quantity) => data(cartHttp.post('/cart/items', { productId, quantity })),
  update: (productId, quantity) => data(cartHttp.put(`/cart/items/${productId}`, { quantity })),
  remove: (productId) => data(cartHttp.delete(`/cart/items/${productId}`)),
  clear: () => data(cartHttp.delete('/cart')),
}

export const orderApi = {
  checkout: (shippingAddress) => data(orderHttp.post('/orders', { shippingAddress })),
  list: () => data(orderHttp.get('/orders')),
  get: (id) => data(orderHttp.get(`/orders/${id}`)),
  cancel: (id) => data(orderHttp.post(`/orders/${id}/cancel`)),
  // admin only
  adminStats: () => data(orderHttp.get('/admin/orders/stats')),
  adminList: (params) => data(orderHttp.get('/admin/orders', { params })),
  adminGet: (id) => data(orderHttp.get(`/admin/orders/${id}`)),
  adminSetStatus: (id, status) => data(orderHttp.put(`/admin/orders/${id}/status`, { status })),
}

export const paymentApi = {
  // Paying is two calls: initiate (register the amount with the provider), then confirm (send the proof of payment).
  config: () => data(paymentHttp.get('/payments/config')),
  initiate: (orderId, provider) => data(paymentHttp.post('/payments/initiate', { orderId, provider })),
  confirm: (paymentId, details) => data(paymentHttp.post(`/payments/${paymentId}/confirm`, { details })),
  list: () => data(paymentHttp.get('/payments')),
  // admin only
  adminList: (params) => data(paymentHttp.get('/admin/payments', { params })),
  refund: (id) => data(paymentHttp.post(`/admin/payments/${id}/refund`)),
  syncOrder: (id) => data(paymentHttp.post(`/admin/payments/${id}/sync-order`)),
}
