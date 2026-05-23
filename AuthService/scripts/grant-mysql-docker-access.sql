-- Run as MySQL admin on the Ubuntu host (Docker connects from 172.17.x.x, not localhost):
--
--   sudo mysql < scripts/grant-mysql-docker-access.sql
-- or:
--   mysql -u root -p < scripts/grant-mysql-docker-access.sql
--
-- Error fixed: Host '172.17.0.x' is not allowed to connect to this MySQL server

CREATE USER IF NOT EXISTS 'vincent'@'%' IDENTIFIED BY '1q2w3e4R';
GRANT ALL PRIVILEGES ON commerce_platform.* TO 'vincent'@'%';
FLUSH PRIVILEGES;

SELECT user, host FROM mysql.user WHERE user = 'vincent';
