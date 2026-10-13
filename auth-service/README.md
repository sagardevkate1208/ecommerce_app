# auth-service

Authentication and user profile service (port 8081).

## Database (MySQL 8+)

```sql
CREATE DATABASE IF NOT EXISTS ecommerce_auth CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Tables are created automatically (`ddl-auto=update`). Defaults: `root` / `root` on `localhost:3306`.
Override with environment variables `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `JWT_SECRET`.

## Run

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-23"   # any JDK 17+
$env:DB_PASSWORD = "your-mysql-password"
.\mvnw.cmd -s ..\maven-central-settings.xml spring-boot:run   # drop -s when on the corporate network
```

## Test (uses in-memory H2, no MySQL needed)

```powershell
.\mvnw.cmd -s ..\maven-central-settings.xml test
```
