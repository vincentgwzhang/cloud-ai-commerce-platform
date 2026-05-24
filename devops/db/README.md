# Database scripts (MySQL)

SQL files for the **host OS MySQL** instance (`commerce_platform` database).  
**Not executed** by `devops/script/install.sh` — apply manually when setting up or resetting data.

| File | Purpose |
|------|---------|
| [init.sql](init.sql) | `users`, `refresh_tokens` tables + seed users (password `123456`) |
| [grant-mysql-docker-access.sql](grant-mysql-docker-access.sql) | `vincent@%` for Docker / Minikube / remote clients |
| [update-user-passwords.sql](update-user-passwords.sql) | Re-hash sample user passwords |

```bash
mysql -u vincent -p commerce_platform < devops/db/init.sql
sudo mysql < devops/db/grant-mysql-docker-access.sql
```

Product catalog schema/data: Flyway in ProductService (`src/main/resources/db/migration/`).
