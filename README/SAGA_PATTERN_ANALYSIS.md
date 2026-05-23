# SAGA Pattern Analizi

## 📋 Genel Bakış

Evet, bu projede **SAGA Pattern** kullanılmaktadır. Ancak tam bir SAGA implementasyonu yerine, **Choreography-based SAGA** yaklaşımı ile event-driven bir mimari kullanılmıştır.

---

## 🎭 SAGA Pattern Nedir?

SAGA Pattern, dağıtık sistemlerde birden fazla mikroservis arasında uzun süreli işlemleri (long-running transactions) yönetmek için kullanılan bir desendir. İki ana yaklaşımı vardır:

1. **Choreography-based SAGA:** Her servis kendi işini yapar ve event'ler üzerinden koordine edilir (decentralized)
2. **Orchestration-based SAGA:** Merkezi bir orchestrator tüm adımları koordine eder (centralized)

---

## 🏗️ Projede Kullanılan SAGA Yaklaşımı

### Choreography-based SAGA (Event-Driven)

Projede **Choreography-based SAGA** kullanılmıştır. Kafka event'leri üzerinden servisler arası koordinasyon sağlanmaktadır.

---

## 🔄 Transfer İşlemi SAGA Akışı

### Adım 1: Transfer Başlatma (LedgerService)

**Servis:** `LedgerService.TransferInitService`

```java
@Transactional
protected void doTransfer(TransferRequest request) {
    // 1. Transfer kaydı oluştur (PENDING status)
    Transfer transfer = Transfer.builder()
        .status("PENDING")
        .description("Waiting fraud check")
        .transferId(request.getTransferId())
        .build();
    transferRepository.save(transfer);
    
    // 2. Fraud kontrolü için Kafka event gönder
    kafkaTemplate.send("fraud-check-events", jsonMessage);
}
```

**SAGA Step:** `INIT_TRANSFER`
- ✅ **Local Transaction:** Transfer kaydı oluşturuldu (PENDING)
- ✅ **Event Published:** `fraud-check-events` topic'ine mesaj gönderildi
- ⚠️ **Compensating Action:** Transfer kaydı silinebilir veya status FRAUD/FAILED yapılabilir

---

### Adım 2: Fraud Kontrolü (FraudService)

**Servis:** `FraudService.FraudConsumer`

```java
@KafkaListener(topics = "fraud-check-events", groupId = "fraud-service-group")
@RetryableTopic(attempts = "5", backoff = @Backoff(delay = 2000, multiplier = 2))
@CircuitBreaker(name = "fraudService", fallbackMethod = "fallbackFraudDetected")
public void consume(String message) {
    TransferRequest transfer = objectMapper.readValue(message, TransferRequest.class);
    
    // Fraud kontrolü yap
    CompletableFuture<Boolean> isFraud = fraudService.checkFraudAsync(transfer);
    boolean fraudResult = isFraud.get();
    
    // Sonucu Kafka'ya gönder
    FraudCheckResponse response = FraudCheckResponse.builder()
        .fraud(fraudResult)
        .transferId(transfer.getTransferId())
        .authToken(transfer.getAuthToken())
        .build();
    
    kafkaTemplate.send("fraud-result-events", resultMessage);
}
```

**SAGA Step:** `CHECK_FRAUD`
- ✅ **Local Transaction:** Fraud kontrolü yapıldı (stateless)
- ✅ **Event Published:** `fraud-result-events` topic'ine sonuç gönderildi
- ⚠️ **Compensating Action:** FraudService stateless olduğu için compensating action yok

---

### Adım 3: Transfer İşlemini Tamamlama veya İptal Etme (LedgerService)

**Servis:** `LedgerService.TransferProcessService`

```java
@Transactional
public void processFraudResult(TransferRequest request, boolean isFraud) {
    Transfer transferLog = transferRepository.findByTransferId(request.getTransferId())
        .orElseThrow(() -> new RuntimeException("Transfer record not found"));
    
    if (isFraud) {
        // COMPENSATING ACTION: Transfer'i iptal et
        transferLog.setStatus("FRAUD");
        transferLog.setDescription("Fraud detected");
        transferRepository.save(transferLog);
        return; // SAGA sonlandı (başarısız)
    }
    
    // SUCCESS PATH: Bakiye transferi yap
    // Optimistic locking ile atomic transfer
    accountRepository.decrementBalance(fromAccount, amount, fromVersion);
    accountRepository.incrementBalance(toAccount, amount, toVersion);
    
    transferLog.setStatus("SUCCESS");
    transferLog.setDescription("Money transferred successfully");
    transferRepository.save(transferLog);
    
    // Notification event gönder
    notificationProducerService.sendTransferCompleted(request);
}
```

**SAGA Step:** `COMPLETE_TRANSFER` veya `COMPENSATE_TRANSFER`

**Success Path (isFraud = false):**
- ✅ **Local Transaction:** Bakiye transferi yapıldı
- ✅ **Local Transaction:** Transfer status = SUCCESS
- ✅ **Event Published:** `notification-transfer-events` topic'ine mesaj gönderildi

**Compensating Path (isFraud = true):**
- ✅ **Compensating Action:** Transfer status = FRAUD (iptal edildi)
- ✅ **No Balance Change:** Bakiye değişikliği yapılmadı (zaten PENDING aşamasında yapılmamıştı)

---

### Adım 4: Bildirim Gönderme (NotificationService)

**Servis:** `NotificationService.NotificationConsumer`

```java
@KafkaListener(topics = "notification-transfer-events", 
               groupId = "notification-service-group")
@RetryableTopic(attempts = "5", backoff = @Backoff(delay = 2000, multiplier = 2))
public void consume(String message) {
    notificationService.sendNotification(message);
}
```

**SAGA Step:** `SEND_NOTIFICATION`
- ✅ **Local Transaction:** Email/SMS gönderildi
- ⚠️ **Compensating Action:** Bildirim geri alınamaz (idempotent operation)

---

## 📊 SAGA State Machine

```
┌─────────────────┐
│  INIT_TRANSFER  │
│  (PENDING)      │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  CHECK_FRAUD    │
│  (Processing)   │
└────────┬────────┘
         │
    ┌────┴────┐
    │         │
    ▼         ▼
┌─────────┐ ┌──────────────┐
│ SUCCESS │ │ COMPENSATE   │
│         │ │ (FRAUD)      │
└────┬────┘ └──────────────┘
     │
     ▼
┌─────────────────┐
│ SEND_NOTIFY     │
│ (Final Step)    │
└─────────────────┘
```

---

## ✅ SAGA Pattern Özellikleri

### 1. Event-Driven Coordination
- ✅ Kafka event'leri üzerinden servisler arası koordinasyon
- ✅ Her servis kendi işini yapar ve sonucu event olarak yayınlar

### 2. Local Transactions
- ✅ Her adımda sadece bir servisin local transaction'ı var
- ✅ ACID garantisi sadece local transaction seviyesinde

### 3. Compensating Actions
- ⚠️ **Kısmi:** Fraud durumunda transfer iptal ediliyor
- ⚠️ **Eksik:** Tam bir rollback mekanizması yok (örneğin notification gönderildikten sonra geri alınamaz)

### 4. Idempotency
- ✅ Transfer ID ile duplicate kontrolü yapılıyor
- ✅ Aynı transfer ID ile tekrar işlem yapılamıyor

### 5. Retry Mechanism
- ✅ `@RetryableTopic` ile otomatik retry (5 deneme)
- ✅ Exponential backoff stratejisi

---

## 🔍 SAGA Pattern'in Eksik Yönleri

### 1. Tam Compensating Transaction Yok
**Mevcut Durum:**
- Fraud durumunda transfer iptal ediliyor ✅
- Ancak notification gönderildikten sonra geri alınamaz ⚠️

**İyileştirme Önerisi:**
```java
// Notification gönderildikten sonra hata olursa
// Compensating action: Notification'ı iptal et veya düzeltme gönder
```

### 2. Orchestrator Yok
**Mevcut Durum:**
- Choreography-based yaklaşım (decentralized) ✅
- Merkezi bir orchestrator yok

**Avantajlar:**
- Servisler arası bağımlılık az
- Scalable ve flexible

**Dezavantajlar:**
- Debugging zor
- State tracking zor

### 3. Saga State Tracking Yok
**Mevcut Durum:**
- Transfer status ile state tracking yapılıyor ✅
- Ancak merkezi bir saga state store yok

**İyileştirme Önerisi:**
```java
// Saga state entity
@Entity
public class SagaState {
    private String sagaId;
    private String currentStep;
    private String status; // IN_PROGRESS, COMPLETED, COMPENSATING, FAILED
    private String transferId;
    // ...
}
```

---

## 🎯 SAGA Pattern Kullanım Senaryoları

### Senaryo 1: Başarılı Transfer
```
1. INIT_TRANSFER → PENDING ✅
2. CHECK_FRAUD → No Fraud ✅
3. COMPLETE_TRANSFER → SUCCESS ✅
4. SEND_NOTIFICATION → Email Sent ✅
```

### Senaryo 2: Fraud Tespit Edildi
```
1. INIT_TRANSFER → PENDING ✅
2. CHECK_FRAUD → Fraud Detected ✅
3. COMPENSATE_TRANSFER → FRAUD ✅
4. SEND_NOTIFICATION → ❌ (Gönderilmedi)
```

### Senaryo 3: Optimistic Lock Başarısız
```
1. INIT_TRANSFER → PENDING ✅
2. CHECK_FRAUD → No Fraud ✅
3. COMPLETE_TRANSFER → Optimistic Lock Failed ✅
4. COMPENSATE_TRANSFER → FAILED ✅
5. Rollback: Bakiye değişikliği geri alındı ✅
```

---

## 📈 SAGA Pattern Avantajları

### 1. Distributed Transaction Yönetimi
- ✅ Birden fazla mikroservis arasında işlem koordinasyonu
- ✅ Her servis kendi veritabanına sahip (database per service)

### 2. Scalability
- ✅ Servisler bağımsız scale edilebilir
- ✅ Event-driven mimari yüksek throughput sağlar

### 3. Resilience
- ✅ Retry mekanizması ile hata toleransı
- ✅ Circuit Breaker ile servis koruması

### 4. Loose Coupling
- ✅ Servisler birbirini doğrudan çağırmaz
- ✅ Kafka event'leri üzerinden iletişim

---

## 🔧 Teknik Detaylar

### Event Topics
| Topic | Purpose | Producer | Consumer |
|-------|---------|----------|----------|
| `fraud-check-events` | Fraud kontrolü başlatma | LedgerService | FraudService |
| `fraud-result-events` | Fraud kontrol sonucu | FraudService | LedgerService |
| `notification-transfer-events` | Bildirim gönderme | LedgerService | NotificationService |

### Transfer Status Flow
```
PENDING → (Fraud Check) → SUCCESS / FRAUD / FAILED
```

### Compensating Actions
| Step | Compensating Action |
|------|---------------------|
| INIT_TRANSFER | Transfer kaydını sil veya FRAUD/FAILED yap |
| CHECK_FRAUD | N/A (stateless) |
| COMPLETE_TRANSFER | Bakiye değişikliğini geri al (rollback) |
| SEND_NOTIFICATION | N/A (idempotent) |

---

## 📝 Sonuç

**Evet, projede SAGA Pattern kullanılmaktadır!**

**Yaklaşım:** Choreography-based SAGA (Event-Driven)

**Özellikler:**
- ✅ Event-driven coordination
- ✅ Local transactions
- ✅ Kısmi compensating actions
- ✅ Idempotency
- ✅ Retry mechanism

**İyileştirme Alanları:**
- ⚠️ Tam compensating transaction mekanizması
- ⚠️ Saga state tracking
- ⚠️ Orchestrator (opsiyonel)

---

**Rapor Tarihi:** 2025-12-21  
**Proje:** Java Microservices Project  
**Pattern:** Choreography-based SAGA

