#!/bin/bash
# =============================================================================
# Özel PostgreSQL giriş noktası (isteğe bağlı)
# =============================================================================
#
# Amaç:
#   Resmi postgres imajının entrypoint'ini sarmalayarak sunucuyu başlatır,
#   hazır olduktan sonra ek init script çalıştırır.
#
# Not — docker-compose.yml ile ilişki:
#   Şu an compose'ta varsayılan postgres entrypoint kullanılıyor;
#   ilk kurulum: init-databases.sql (/docker-entrypoint-initdb.d)
#   sonraki durumlar: postgres-init + init-databases.sh
#
# Bu dosyayı kullanmak için postgres servisine örnek:
#   entrypoint: ["/bin/bash", "/scripts/entrypoint.sh"]
#   command: ["postgres"]
#   volumes:
#     - ./sql/postgres/entrypoint.sh:/scripts/entrypoint.sh:ro
#     - ./sql/postgres/init-databases.sh:/docker-entrypoint-initdb.d/01-init-databases.sh:ro
# =============================================================================

set -e

# PostgreSQL'in resmi entrypoint'ini arka planda başlat
/usr/local/bin/docker-entrypoint.sh "$@" &
POSTGRES_PID=$!

# Sunucu bağlantı kabul edene kadar bekle (en fazla ~60 saniye)
echo "PostgreSQL başlatılıyor, hazır olması bekleniyor..."
for i in {1..60}; do
    if pg_isready -U postgres >/dev/null 2>&1; then
        echo "PostgreSQL hazır."
        sleep 2
        break
    fi
    sleep 1
done

# Init script varsa çalıştır (Micro_* veritabanları — init-databases.sh)
if [ -f /docker-entrypoint-initdb.d/01-init-databases.sh ]; then
    echo "Veritabanı init script'i çalıştırılıyor..."
    bash /docker-entrypoint-initdb.d/01-init-databases.sh || echo "Init script uyarılarla tamamlandı."
fi

# PostgreSQL sürecini ön planda tut (konteyner ayakta kalsın)
wait $POSTGRES_PID
