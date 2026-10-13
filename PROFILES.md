# Environment profiles

Every backend service has three Spring profiles. Settings that differ per environment live in
`src/main/resources/application-<profile>.properties`; shared settings stay in `application.properties`.

| Profile | When | Database | Secrets and URLs | Logging |
|---------|------|----------|------------------|---------|
| `dev`   | Your machine. **Used when nothing is set.** | MySQL on localhost | Defaults in the file, overridable by env vars | DEBUG for `com.ecommerce`, SQL printed |
| `test`  | Automated tests (selected by `@ActiveProfiles("test")`) | In-memory H2, no MySQL needed | Fixed test values | default |
| `prod`  | Real deployment | MySQL given by env vars, schema must already exist (`ddl-auto=validate`) | **All from env vars, no defaults** | WARN, app code INFO, no stack traces in responses |

## Choosing a profile

```powershell
# dev (default): nothing to set
.\mvnw.cmd -s ..\maven-central-settings.xml spring-boot:run

# prod
$env:SPRING_PROFILES_ACTIVE = "prod"      # or:  -Dspring-boot.run.profiles=prod
```

## prod: required environment variables

A service refuses to start if one of its variables is missing (that is intentional).

| Variable | auth | catalog | cart | order | payment | Notes |
|----------|:----:|:-------:|:----:|:-----:|:-------:|-------|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | x | x | x | x | x | Use a separate database per service |
| `JWT_SECRET` | x | x | x | x | x | **Same value everywhere**, at least 32 characters |
| `INTERNAL_API_KEY` |  |  |  | x | x | Same value in order and payment. Long and random |
| `CATALOG_URL` |  |  | x | x |  | Base URL of catalog-service |
| `CART_URL` |  |  |  | x |  | Base URL of cart-service |
| `ORDER_URL` |  |  |  |  | x | Base URL of order-service |
| `JWT_EXPIRATION_MS` | optional |  |  |  |  | Defaults to 3600000 (1 hour) |

## Things to know

- **Payment gateways.** payment-service has two `PaymentGateway` implementations:
  `RazorpayPaymentGateway` (`@Primary`) and `MockPaymentGateway` (`@Qualifier("mock")`).
  - **Razorpay is switched off by default** (`razorpay.enabled=false`), so in `dev` only the mock exists and is used
    automatically. No Razorpay keys are needed. To try Razorpay in dev, set `RAZORPAY_ENABLED=true`,
    `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET` (test-mode keys) and, for webhooks, `RAZORPAY_WEBHOOK_SECRET`.
  - **The mock never exists in `prod`.** There, Razorpay must be enabled with live keys, so payments can never be
    approved without being charged. Without them the service refuses to start.
  - Razorpay webhooks need a public URL (`POST /api/payments/webhook`); on a laptop use a tunnel such as ngrok.
- **Existing dev database:** MySQL `ENUM` columns made by the first version of the `payments` table do not accept the
  new values. Run once: `ALTER TABLE ecommerce_payment.payments MODIFY status VARCHAR(20) NOT NULL,
  MODIFY method VARCHAR(50) NOT NULL;` (or drop the table and let it be recreated).
- **`prod` does not create tables.** Run the schema once (or add a migration tool such as Flyway), then Hibernate only
  validates it. You can generate the schema from a `dev` database.
- **The dev password is still in `application-dev.properties`.** Fine for your machine, but keep these files out of any
  shared repository, or move the value to the `DB_PASSWORD` environment variable.
- **Never expose `/internal/**`** of order-service through a gateway.
- The React app is not covered by profiles yet; in production it needs the API Gateway in front of the services.
