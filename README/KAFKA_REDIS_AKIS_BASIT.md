# Kafka ve Redis Akışı - Basit Özet

## 🚀 Kafka Event Flow (Transfer İşlemi)

```
LedgerService: TransferStartedEvent publish 
    ↓
FraudService dinler → fraud kontrolü yapar 
    ↓
FraudService: FraudCheckedEvent publish 
    ↓
LedgerService dinler → bakiye güncelle 
    ↓
LedgerService: TransferCompletedEvent publish 
    ↓
NotificationService dinler → mail/sms gönderir
```

---

## 📨 Kafka Topics ve Event'ler

### 1. `fraud-check-events` (TransferStartedEvent)
- **Producer:** LedgerService
- **Consumer:** FraudService
- **İçerik:** TransferRequest (transferId, fromAccount, toAccount, amount, authToken)

### 2. `fraud-result-events` (FraudCheckedEvent)
- **Producer:** FraudService
- **Consumer:** LedgerService
- **İçerik:** FraudCheckResponse (transferId, fraud, authToken)

### 3. `notification-transfer-events` (TransferCompletedEvent)
- **Producer:** LedgerService
- **Consumer:** NotificationService
- **İçerik:** TransferRequest (transferId, amount, account bilgileri)

---

## 🔴 Redis Kullanımı

### 1. Caching (AccountService)
- **Amaç:** Account verilerini cache'leme
- **TTL:** 10 dakika
- **Kullanım:**
  - `@Cacheable` → Account okuma işlemleri cache'lenir
  - `@CacheEvict` → Account yazma işlemlerinde cache temizlenir

### 2. Rate Limiting (API Gateway)
- **Amaç:** IP bazlı istek sınırlama
- **Limit:** 100 req/s, burst: 200
- **Kullanım:** DDoS koruması ve aşırı yüklenmeyi önleme

---

## 📊 Basit Akış Diyagramı

```
┌──────────────┐
│ LedgerService│
│  Transfer    │
│   Başlat     │
└──────┬───────┘
       │ fraud-check-events
       ▼
┌──────────────┐
│ FraudService │
│ Fraud Kontrol│
└──────┬───────┘
       │ fraud-result-events
       ▼
┌──────────────┐
│ LedgerService│
│ Bakiye Güncelle│
└──────┬───────┘
       │ notification-transfer-events
       ▼
┌──────────────┐
│NotificationService│
│ Mail/SMS Gönder│
└──────────────┘
```

---

## 🔄 Detaylı Adımlar

### Adım 1: Transfer Başlatma
**LedgerService** → Transfer kaydı oluştur (PENDING) → Kafka'ya `fraud-check-events` gönder

### Adım 2: Fraud Kontrolü
**FraudService** → Kafka'dan mesaj al → Fraud kontrolü yap → Kafka'ya `fraud-result-events` gönder

### Adım 3: Transfer Tamamlama
**LedgerService** → Kafka'dan sonuç al → Fraud yoksa bakiye güncelle (SUCCESS) → Kafka'ya `notification-transfer-events` gönder

### Adım 4: Bildirim
**NotificationService** → Kafka'dan mesaj al → Email/SMS gönder

---

## 💡 Önemli Notlar

- **Asenkron İşlem:** Fraud kontrolü asenkron yapılır, transfer işlemi bloklanmaz
- **Token Taşıma:** Authentication token'lar Kafka mesajlarında taşınır (stateless)
- **Retry:** Hata durumunda otomatik 5 deneme (exponential backoff)
- **Idempotency:** Transfer ID ile duplicate kontrolü

---

**Kısa Versiyon (Mesaj İçin):**

```
LedgerService → fraud-check-events → FraudService → fraud-result-events → LedgerService → notification-transfer-events → NotificationService
```

**Redis:**
- AccountService: Account caching (10 dk TTL)
- API Gateway: Rate limiting (100 req/s)

