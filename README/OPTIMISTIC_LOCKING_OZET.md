# Optimistic Locking Kullanımı - Özet

## 🎯 Kullanım Amacı

**LedgerService**'de bakiye transferi sırasında **concurrent modification** (eşzamanlı değişiklik) problemlerini önlemek için **Optimistic Locking** kullanılmaktadır.

---

## 📍 Kullanıldığı Yer

**Servis:** `LedgerService.TransferProcessService`  
**Entity:** `Ledger` (version field ile)  
**Repository:** `LedgerRepository` (version kontrolü ile update query'leri)

---

## 🔧 Nasıl Çalışıyor?

### 1. Entity'de Version Field

```java
@Entity
public class Ledger {
    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private Long version;  // JPA otomatik olarak version'ı yönetir
}
```

### 2. Version Kontrolü ile Update

**Decrement Balance (Gönderen Hesap):**
```java
@Query("UPDATE Ledger a SET a.balance = a.balance - :amount, a.version = a.version + 1 " +
        "WHERE a.ledgerIbanNumber = :iban AND a.balance >= :amount AND a.version = :version")
int decrementBalance(Long iban, BigDecimal amount, Long version);
```

**Increment Balance (Alıcı Hesap):**
```java
@Query("UPDATE Ledger a SET a.balance = a.balance + :amount, a.version = a.version + 1 " +
        "WHERE a.ledgerIbanNumber = :iban AND a.version = :version")
int incrementBalance(Long iban, BigDecimal amount, Long version);
```

### 3. Transfer İşlemi Akışı

```java
// 1. Version'ları al
Long fromVersion = fromAccount.getVersion();
Long toVersion = toAccount.getVersion();

// 2. Gönderen hesaptan çıkar (version kontrolü ile)
int fromUpdated = accountRepository.decrementBalance(
    fromAccount, amount, fromVersion
);

if (fromUpdated == 0) {
    // Version değişmiş → Optimistic lock failed
    throw new OptimisticLockException("Account modified by another transaction");
}

// 3. Alıcı hesaba ekle (version kontrolü ile)
int toUpdated = accountRepository.incrementBalance(
    toAccount, amount, toVersion
);

if (toUpdated == 0) {
    // Version değişmiş → Rollback yap
    accountRepository.incrementBalance(fromAccount, amount, fromVersion + 1);
    throw new OptimisticLockException("Transfer rolled back");
}
```

---

## 🔄 Akış Diyagramı

```
Transfer İşlemi Başlar
    ↓
Version'ları Al (fromVersion, toVersion)
    ↓
Gönderen Hesaptan Çıkar
    ├─ Version eşleşiyor? → ✅ Bakiye güncellendi, version++
    └─ Version eşleşmiyor? → ❌ OptimisticLockException
    ↓
Alıcı Hesaba Ekle
    ├─ Version eşleşiyor? → ✅ Bakiye güncellendi, version++
    └─ Version eşleşmiyor? → ❌ Rollback + OptimisticLockException
    ↓
Transfer Başarılı ✅
```

---

## ✅ Avantajları

1. **Performans:** Pessimistic locking'e göre daha hızlı (lock beklemez)
2. **Deadlock Önleme:** Lock beklemediği için deadlock riski yok
3. **Concurrent Access:** Aynı anda birden fazla işlem çalışabilir
4. **Atomicity:** Version kontrolü ile atomic update garantisi

---

## ⚠️ Dezavantajları

1. **Retry Gerekli:** Version conflict durumunda işlem tekrar denenmeli
2. **Conflict Detection:** Version değişikliği tespit edilene kadar işlem devam eder

---

## 🛡️ Hata Yönetimi

**OptimisticLockException:** Version conflict durumunda fırlatılır

**Rollback Mekanizması:**
- Gönderen hesaptan çıkarıldı ama alıcı hesaba eklenemedi
- → Gönderen hesaba geri eklenir (compensating action)

---

## 📊 Örnek Senaryo

**Senaryo:** İki transfer aynı anda başlıyor

```
Transfer 1: Account A → Account B (100 TL)
Transfer 2: Account A → Account C (50 TL)

Zaman: T0 → Account A version = 5
Zaman: T1 → Transfer 1: version 5 ile başlar
Zaman: T2 → Transfer 2: version 5 ile başlar
Zaman: T3 → Transfer 1: decrement başarılı, version = 6
Zaman: T4 → Transfer 2: decrement başarısız (version 5 ≠ 6) → OptimisticLockException
Zaman: T5 → Transfer 2: Retry (yeni version ile)
```

---

## 💡 Kısa Özet (Rapor İçin)

**Optimistic Locking:**
- **Kullanım:** LedgerService'de bakiye transferi sırasında concurrent modification önleme
- **Yöntem:** `@Version` annotation ile entity'de version field
- **Mekanizma:** Update query'lerinde version kontrolü (`WHERE version = :version`)
- **Hata Yönetimi:** Version conflict → OptimisticLockException + Rollback
- **Avantaj:** Pessimistic locking'e göre daha performanslı, deadlock riski yok

---

**Kod Lokasyonu:**
- Entity: `LedgerService/src/main/java/com/mimaraslan/model/Ledger.java`
- Repository: `LedgerService/src/main/java/com/mimaraslan/repository/LedgerRepository.java`
- Service: `LedgerService/src/main/java/com/mimaraslan/service/TransferProcessService.java`

