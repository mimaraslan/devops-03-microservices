# Kafka ve Redis Kullanım Raporu

## 📋 İçindekiler
1. [Kafka Kullanımı](#kafka-kullanımı)
2. [Redis Kullanımı](#redis-kullanımı)
3. [Mimari Diyagramlar](#mimari-diyagramlar)

---

## 🚀 Kafka Kullanımı

### Genel Bakış
Projede **Apache Kafka** (Redpanda) asenkron mesajlaşma ve event-driven mimari için kullanılmaktadır. Kafka, mikroservisler arası iletişimi sağlar ve yüksek performanslı, güvenilir bir mesajlaşma katmanı sunar.

### Kafka Konfigürasyonu

**Docker Compose:**
```yaml
redpanda:
  image: redpandadata/redpanda:v25.3.1
  container_name: my-kafka
  ports:
    - "9092:9092"   # Kafka client port
  environment:
    REDPANDA_AUTO_CREATE_TOPICS_ENABLE: "true"
```

**Spring Boot Konfigürasyonu:**
- **Bootstrap Server:** `localhost:9092`
- **Serialization:** String (JSON string olarak gönderiliyor)
- **Consumer Group ID:** Servis bazlı (örn: `fraud-service-group`, `ledger-service-group`)

---

## 📨 Kafka Topics ve Akış Diyagramı

### 1. Topic: `fraud-check-events`
**Producer:** LedgerService  
**Consumer:** FraudService

**Akış:**
```
LedgerService (TransferInitService)
    ↓
[Kafka Producer] → "fraud-check-events" topic
    ↓
[Kafka Consumer] → FraudService (FraudConsumer)
```

**Kod Detayları:**

**Producer (LedgerService):**
```java
// TransferInitService.java
@Transactional
protected void doTransfer(TransferRequest request) {
    // Transfer kaydı oluştur (PENDING status)
    Transfer transfer = Transfer.builder()
        .status("PENDING")
        .transferId(request.getTransferId())
        .build();
    transferRepository.save(transfer);
    
    // Kafka'ya fraud check isteği gönder
    String jsonMessage = objectMapper.writeValueAsString(request);
    kafkaTemplate.send("fraud-check-events", jsonMessage);
}
```

**Consumer (FraudService):**
```java
// FraudConsumer.java
@KafkaListener(topics = "fraud-check-events", groupId = "fraud-service-group")
@RetryableTopic(attempts = "5", backoff = @Backoff(delay = 2000, multiplier = 2))
@CircuitBreaker(name = "fraudService", fallbackMethod = "fallbackFraudDetected")
public void consume(String message) {
    TransferRequest transfer = objectMapper.readValue(message, TransferRequest.class);
    
    // Asenkron fraud kontrolü
    CompletableFuture<Boolean> isFraud = fraudService.checkFraudAsync(transfer);
    boolean fraudResult = isFraud.get();
    
    // Sonucu Kafka'ya gönder
    FraudCheckResponse response = FraudCheckResponse.builder()
        .fraud(fraudResult)
        .transferId(transfer.getTransferId())
        .authToken(transfer.getAuthToken()) // Token'ı taşı
        .build();
    
    kafkaTemplate.send("fraud-result-events", objectMapper.writeValueAsString(response));
}
```

---

### 2. Topic: `fraud-result-events`
**Producer:** FraudService  
**Consumer:** LedgerService

**Akış:**
```
FraudService (FraudConsumer)
    ↓
[Kafka Producer] → "fraud-result-events" topic
    ↓
[Kafka Consumer] → LedgerService (FraudResultConsumerService)
    ↓
TransferProcessService (Bakiye güncelleme)
```

**Kod Detayları:**

**Consumer (LedgerService):**
```java
// FraudResultConsumerService.java
@KafkaListener(topics = "fraud-result-events", groupId = "account-group")
@Transactional
public void handleFraudResult(String message) {
    FraudCheckResponse response = objectMapper.readValue(message, FraudCheckResponse.class);
    
    // Transfer kaydını bul
    Transfer transfer = transferRepository.findByTransferId(response.getTransferId())
        .orElseThrow(() -> new RuntimeException("Transfer not found"));
    
    // TransferRequest oluştur (token Kafka mesajından alınıyor)
    TransferRequest request = TransferRequest.builder()
        .transferId(response.getTransferId())
        .authToken(response.getAuthToken()) // ✅ Token Kafka'dan geliyor
        .build();
    
    // Transfer işlemini tamamla
    transferProcessService.processFraudResult(request, response.isFraud());
}
```

**İşlem Sonucu:**
- Fraud tespit edilirse: Transfer `FRAUD` status ile kaydedilir
- Fraud yoksa: Bakiye güncellenir, transfer `SUCCESS` olur, notification gönderilir

---

### 3. Topic: `notification-transfer-events`
**Producer:** LedgerService  
**Consumer:** NotificationService

**Akış:**
```
LedgerService (TransferProcessService)
    ↓
[Kafka Producer] → "notification-transfer-events" topic
    ↓
[Kafka Consumer] → NotificationService (NotificationConsumer)
    ↓
Email/SMS gönderimi
```

**Kod Detayları:**

**Producer (LedgerService):**
```java
// NotificationProducerService.java
public void sendTransferCompleted(TransferRequest request) {
    kafkaTemplate.send("notification-transfer-events", 
        objectMapper.writeValueAsString(request));
}
```

**Consumer (NotificationService):**
```java
// NotificationConsumer.java
@KafkaListener(topics = "notification-transfer-events", 
               groupId = "notification-service-group")
@RetryableTopic(attempts = "5", backoff = @Backoff(delay = 2000, multiplier = 2))
public void consume(String message) {
    notificationService.sendNotification(message);
}
```

---

## 🔄 Tam Transfer Akışı (End-to-End)

```
1. [API Gateway] → Transfer Request
   ↓
2. [LedgerService] TransferInitService.doTransfer()
   ├─ Transfer kaydı oluştur (PENDING)
   └─ Kafka Producer → "fraud-check-events"
      ↓
3. [FraudService] FraudConsumer.consume()
   ├─ Fraud kontrolü yap
   └─ Kafka Producer → "fraud-result-events"
      ↓
4. [LedgerService] FraudResultConsumerService.handleFraudResult()
   ├─ Fraud yoksa:
   │  ├─ Bakiye güncelle
   │  ├─ Transfer status = SUCCESS
   │  └─ Kafka Producer → "notification-transfer-events"
   └─ Fraud varsa:
      └─ Transfer status = FRAUD
      ↓
5. [NotificationService] NotificationConsumer.consume()
   └─ Email/SMS gönder
```

---

## 🛡️ Kafka Güvenilirlik Özellikleri

### 1. Retry Mekanizması
```java
@RetryableTopic(
    attempts = "5",  // Maksimum 5 deneme
    backoff = @Backoff(
        delay = 2000,      // İlk deneme gecikmesi: 2 saniye
        multiplier = 2     // Her retry'de gecikmeyi 2 kat artır
    )
)
```

**Retry Zamanlaması:**
- 1. deneme: Anında
- 2. deneme: 2 saniye sonra
- 3. deneme: 4 saniye sonra
- 4. deneme: 8 saniye sonra
- 5. deneme: 16 saniye sonra

### 2. Circuit Breaker
```java
@CircuitBreaker(name = "fraudService", fallbackMethod = "fallbackFraudDetected")
```

FraudService'de Circuit Breaker kullanılarak hata durumlarında fallback mekanizması devreye girer.

### 3. Token Taşıma
**Önemli:** Authentication token'lar Kafka mesajlarında taşınır, veritabanına kaydedilmez (stateless mimari).

```java
// TransferRequest içinde token taşınıyor
FraudCheckResponse.builder()
    .authToken(transfer.getAuthToken()) // Token Kafka üzerinden taşınıyor
    .build();
```

---

## 📊 Kafka Kullanım Özeti

| Topic | Producer | Consumer | Amaç |
|-------|----------|----------|------|
| `fraud-check-events` | LedgerService | FraudService | Fraud kontrolü için transfer isteği gönderme |
| `fraud-result-events` | FraudService | LedgerService | Fraud kontrol sonucunu geri gönderme |
| `notification-transfer-events` | LedgerService | NotificationService | Transfer tamamlandığında bildirim gönderme |

---

## 🔴 Redis Kullanımı

### Genel Bakış
Projede **Redis** iki farklı amaç için kullanılmaktadır:
1. **Caching** (AccountService)
2. **Rate Limiting** (API Gateway)

---

## 💾 Redis Caching (AccountService)

### Amaç
Account verilerini cache'leyerek veritabanı yükünü azaltmak ve response time'ı iyileştirmek.

### Konfigürasyon

**Docker Compose:**
```yaml
redis:
  image: redis
  container_name: my-redis
  ports:
    - "6379:6379"
  volumes:
    - redis-volume:/data
```

**Spring Boot Konfigürasyonu:**
```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
  cache:
    type: redis
    redis:
      time-to-live: 600000  # 10 dakika (milisaniye)
```

**Cache Config:**
```java
@Configuration
@EnableCaching
public class CacheConfig {
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10)) // Cache 10 dakika geçerli
            .serializeKeysWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new GenericJackson2JsonRedisSerializer()))
            .disableCachingNullValues(); // Null değerleri cache'leme
        
        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(config)
            .transactionAware()
            .build();
    }
}
```

### Cache Kullanımı

#### 1. Cacheable (Okuma İşlemleri)

**getAllAccounts():**
```java
@Cacheable(value = "accounts", key = "'all'")
public List<AccountResponse> getAllAccounts() {
    log.debug("Fetching all accounts from database");
    List<Account> accounts = accountRepository.findAll();
    return accounts.stream()
        .map(this::mapToAccountResponse)
        .collect(Collectors.toList());
}
```
- **Cache Key:** `accounts::all`
- **İlk çağrı:** Veritabanından okur ve cache'e yazar
- **Sonraki çağrılar:** Redis'ten okur (10 dakika boyunca)

**getAccountById():**
```java
@Cacheable(value = "accounts", key = "#id")
public AccountResponse getAccountById(Long id) {
    log.debug("Fetching account from database: id={}", id);
    Account account = accountRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Account not found"));
    return mapToAccountResponse(account);
}
```
- **Cache Key:** `accounts::1`, `accounts::2`, vb.
- Her account ID için ayrı cache entry

#### 2. CacheEvict (Yazma İşlemleri)

**register():**
```java
@Transactional
@CacheEvict(value = {"accounts"}, allEntries = true)
public LoginResponse register(RegisterRequest request) {
    // Yeni kullanıcı oluştur
    Account account = Account.builder()...build();
    Account savedAccount = accountRepository.save(account);
    // ...
}
```
- Yeni kullanıcı eklendiğinde tüm cache temizlenir

**updateAccount():**
```java
@Transactional
@CacheEvict(value = {"accounts"}, allEntries = true)
public AccountResponse updateAccount(Long userId, UpdateAccountRequest request) {
    // Kullanıcı bilgilerini güncelle
    // ...
}
```
- Kullanıcı güncellendiğinde tüm cache temizlenir

### Cache Stratejisi

**Cache Key Pattern:**
- `accounts::all` → Tüm account listesi
- `accounts::1` → ID=1 olan account
- `accounts::2` → ID=2 olan account

**Cache Invalidation:**
- Her yazma işleminde (`register`, `updateAccount`) tüm cache temizlenir
- Bu sayede tutarsızlık önlenir

**TTL (Time To Live):**
- 10 dakika
- Bu süre sonunda cache otomatik olarak expire olur

---

## 🚦 Redis Rate Limiting (API Gateway)

### Amaç
API Gateway'de IP bazlı rate limiting uygulayarak DDoS saldırılarını ve aşırı yüklenmeyi önlemek.

### Konfigürasyon

**Spring Cloud Gateway Config:**
```yaml
spring:
  cloud:
    gateway:
      default-filters:
        - name: RequestRateLimiter
          args:
            key-resolver: '#{@ipAddressResolver}' # Custom IP Resolver
            redis-rate-limiter.replenishRate: 100 # Saniyede 100 istek
            redis-rate-limiter.burstCapacity: 200 # Anlık 200 istek
```

**IP Address Resolver:**
```java
@Component
public class IpAddressResolver implements KeyResolver {
    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        String ipAddress = "unknown";
        if (exchange.getRequest().getRemoteAddress() != null) {
            ipAddress = exchange.getRequest()
                .getRemoteAddress()
                .getAddress()
                .getHostAddress();
        }
        return Mono.just(ipAddress);
    }
}
```

### Rate Limiting Mantığı

**Token Bucket Algoritması:**
- **replenishRate:** Saniyede 100 token eklenir
- **burstCapacity:** Maksimum 200 token saklanabilir

**Örnek Senaryo:**
```
Zaman: 0.0s → Token: 200 (başlangıç)
Zaman: 0.5s → 50 istek geldi → Token: 150
Zaman: 1.0s → 100 token eklendi → Token: 200 (max)
Zaman: 1.5s → 150 istek geldi → Token: 50
Zaman: 2.0s → 100 token eklendi → Token: 150
```

**Limit Aşımı:**
- Token yoksa → HTTP 429 (Too Many Requests) döner
- Her IP için ayrı token bucket

---

## 📊 Redis Kullanım Özeti

| Servis | Kullanım | Amaç | TTL/Config |
|--------|----------|------|------------|
| **AccountService** | Caching | Account verilerini cache'leme | 10 dakika |
| **API Gateway** | Rate Limiting | IP bazlı istek sınırlama | 100 req/s, burst: 200 |

---

## 🏗️ Mimari Diyagramlar

### Kafka Event Flow
```
┌─────────────┐
│ API Gateway │
└──────┬──────┘
       │
       ▼
┌─────────────────┐
│  LedgerService  │
│  (Producer)      │
└──────┬───────────┘
       │ fraud-check-events
       ▼
┌─────────────────┐
│   Kafka Topic   │
│fraud-check-events│
└──────┬───────────┘
       │
       ▼
┌─────────────────┐
│  FraudService   │
│  (Consumer)      │
└──────┬───────────┘
       │ fraud-result-events
       ▼
┌─────────────────┐
│   Kafka Topic   │
│fraud-result-events│
└──────┬───────────┘
       │
       ▼
┌─────────────────┐
│  LedgerService  │
│  (Consumer)      │
└──────┬───────────┘
       │ notification-transfer-events
       ▼
┌─────────────────┐
│   Kafka Topic   │
│notification-transfer-events│
└──────┬───────────┘
       │
       ▼
┌─────────────────┐
│NotificationService│
│  (Consumer)      │
└──────────────────┘
```

### Redis Caching Flow
```
┌─────────────┐
│   Client    │
└──────┬──────┘
       │ getAccountById(1)
       ▼
┌─────────────────┐
│ AccountService  │
│  @Cacheable     │
└──────┬──────────┘
       │
       ├─ Cache Hit? ──YES──► Redis ──► Response
       │
       └─ Cache Miss? ──NO──► Database ──► Redis ──► Response
```

### Redis Rate Limiting Flow
```
┌─────────────┐
│   Client    │
│ IP: 1.2.3.4 │
└──────┬──────┘
       │ Request
       ▼
┌─────────────────┐
│  API Gateway    │
│  Rate Limiter    │
└──────┬───────────┘
       │ Check Token Bucket
       ▼
┌─────────────────┐
│     Redis       │
│  Token: 150/200 │
└──────┬───────────┘
       │
       ├─ Token Available? ──YES──► Forward Request
       │
       └─ No Token? ──NO──► HTTP 429 (Too Many Requests)
```

---

## 📝 Önemli Notlar

### Kafka
1. **Asenkron İşlem:** Fraud kontrolü asenkron yapılır, transfer işlemi bloklanmaz
2. **Token Taşıma:** Authentication token'lar Kafka mesajlarında taşınır (stateless)
3. **Retry Mekanizması:** Hata durumunda otomatik retry (5 deneme)
4. **Circuit Breaker:** FraudService'de hata durumunda fallback

### Redis
1. **Cache Invalidation:** Her yazma işleminde tüm cache temizlenir
2. **TTL:** 10 dakika sonra cache otomatik expire olur
3. **Rate Limiting:** IP bazlı, her IP için ayrı token bucket
4. **Burst Capacity:** Anlık yük artışlarına karşı tampon

---

## 🔧 Teknik Detaylar

### Kafka Dependencies
```gradle
implementation 'org.springframework.kafka:spring-kafka'
```

### Redis Dependencies
```gradle
// AccountService
implementation 'org.springframework.boot:spring-boot-starter-data-redis'

// API Gateway
implementation 'org.springframework.boot:spring-boot-starter-data-redis-reactive'
```

### Serialization
- **Kafka:** JSON String (ObjectMapper ile serialize/deserialize)
- **Redis Cache:** GenericJackson2JsonRedisSerializer
- **Redis Keys:** StringRedisSerializer

---

## 📈 Performans İyileştirmeleri

### Kafka
- ✅ Asenkron işlem → Yüksek throughput
- ✅ Retry mekanizması → Güvenilirlik
- ✅ Circuit Breaker → Hata toleransı

### Redis
- ✅ Cache → Database yükü azaldı
- ✅ Rate Limiting → DDoS koruması
- ✅ TTL → Otomatik cache temizleme

---

**Rapor Tarihi:** 2025-12-21  
**Proje:** Java Microservices Project  
**Versiyon:** 1.0

