# ShopEasy frontend

React 18 + Vite 4 + Tailwind CSS 3 + React Router 6 + Axios. Runs on Node 16 or newer.

## Run

Start the backend services first (auth 8081, catalog 8082, cart 8083, order 8084, payment 8085), then:

```powershell
cd frontend
npm install
npm run dev        # http://localhost:5173
```

## How it talks to the backend

The browser only talks to the Vite dev server. `vite.config.js` forwards each prefix to its service,
so no CORS setup is needed in development:

| Browser path     | Service  | Port |
|------------------|----------|------|
| `/auth-api/*`    | auth     | 8081 |
| `/catalog-api/*` | catalog  | 8082 |
| `/cart-api/*`    | cart     | 8083 |
| `/order-api/*`   | order    | 8084 |
| `/payment-api/*` | payment  | 8085 |

When the API Gateway exists, replace these five proxies with one.

## Structure

- `src/api/` axios clients (token added automatically) and one function per endpoint
- `src/context/` login state (`AuthContext`) and cart state (`CartContext`)
- `src/pages/` one file per screen
- `src/components/` shared pieces (navbar, product card, payment form...)

## Notes

- The JWT is kept in `localStorage`. Simple, but readable by any script on the page; fine for development.
- Payments run against the backend's mock gateway: a card ending in `0002` or a UPI id starting with `fail` is declined.
- Role-based UI: the `/admin/*` screens and the "Admin" menu exist only for users whose role is `ADMIN`
  (`isAdmin` in `AuthContext`, `AdminRoute`). This only controls what is shown; the backend independently
  answers 403 to admin calls made with a customer token.
- To make an admin, sign up normally, then run in MySQL:
  `UPDATE ecommerce_auth.users SET role = 'ADMIN' WHERE email = 'you@example.com';` and log in again.
- Admin screens: dashboard, products (add / edit / delete / restore), categories, orders (ship, deliver, cancel,
  refund), payments (refund, sync).
- "Delete" on a product hides it from the shop but keeps it for past orders; "Restore" brings it back.
