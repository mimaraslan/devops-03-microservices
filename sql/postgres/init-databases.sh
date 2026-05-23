#!/bin/bash
# =============================================================================
# Mikroservis PostgreSQL veritabanlarını hazırlar
# =============================================================================
#
# Ne zaman çalışır?
#   docker compose servisi: postgres-init (tek seferlik konteyner)
#
# Ne yapar?
#   1) Ana PostgreSQL konteynerinin (postgres) ayakta olmasını bekler
#   2) Aşağıdaki Micro_* veritabanları yoksa oluşturur (varsa atlar)
#
# İlk kurulum notu:
#   Yeni bir postgres volume ile postgres ilk kez ayağa kalktığında
#   init-databases.sql de /docker-entrypoint-initdb.d üzerinden çalışır.
#   Volume zaten varken ve DB'ler hiç oluşmadıysa bu script devreye girer.
#
# Ortam değişkenleri (docker-compose postgres-init ile uyumlu):
#   PGHOST, PGPORT, PGUSER, PGPASSWORD
# =============================================================================

set -euo pipefail

PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
PGUSER="${PGUSER:-postgres}"
PGPASSWORD="${PGPASSWORD:-${POSTGRES_PASSWORD:-}}"
if [ -z "$PGPASSWORD" ]; then
  echo "Hata: PGPASSWORD veya POSTGRES_PASSWORD tanımlı değil (docker-compose .env)." >&2
  exit 1
fi
export PGPASSWORD

echo "PostgreSQL bekleniyor: ${PGHOST}:${PGPORT} ..."
until pg_isready -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" >/dev/null 2>&1; do
  sleep 2
done
echo "PostgreSQL hazır. Mikroservis veritabanları kontrol ediliyor..."

# Her mikroservisin kendi veritabanı (JPA/Hibernate ddl-auto ile şema güncellenir)
DATABASES=(
  "Micro_AccountServiceDB"       # AccountService
  "Micro_LedgerServiceDB"        # LedgerService
  "Micro_FraudServiceDB"         # FraudService
  "Micro_NotificationServiceDB"  # NotificationService
)

for DB_NAME in "${DATABASES[@]}"; do
  EXISTS=$(psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d postgres -tAc \
    "SELECT 1 FROM pg_database WHERE datname = '${DB_NAME}'")
  if [ "$EXISTS" = "1" ]; then
    echo "  [atlandı] ${DB_NAME} zaten mevcut."
  else
    echo "  [oluşturuluyor] ${DB_NAME} ..."
    psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d postgres -v ON_ERROR_STOP=1 \
      -c "CREATE DATABASE \"${DB_NAME}\";"
    echo "  [tamam] ${DB_NAME} oluşturuldu."
  fi
done

echo "Veritabanı hazırlığı tamamlandı."
