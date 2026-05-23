-- Mikroservis veritabanları (PostgreSQL ilk kurulumda /docker-entrypoint-initdb.d ile çalışır)
-- Mevcut volume'da bu script tekrar çalışmaz; o durumda postgres-init servisi devreye girer.
-- Dosya: sql/postgres/init-databases.sql

CREATE DATABASE "Micro_AccountServiceDB";
CREATE DATABASE "Micro_LedgerServiceDB";
CREATE DATABASE "Micro_FraudServiceDB";
CREATE DATABASE "Micro_NotificationServiceDB";

-- keycloak veritabanı POSTGRES_DB ortam değişkeni ile oluşturulur
