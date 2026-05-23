package com.mimaraslan.service;

import com.mimaraslan.dto.request.LoginRequest;
import com.mimaraslan.dto.request.RegisterRequest;
import com.mimaraslan.dto.request.UpdateAccountRequest;
import com.mimaraslan.dto.response.KeycloakPasswordGrantResponse;
import com.mimaraslan.dto.response.LoginResponse;
import com.mimaraslan.dto.response.AccountResponse;
import com.mimaraslan.model.Account;
import com.mimaraslan.repository.AccountRepository;
import com.mimaraslan.service.KeycloakService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final KeycloakService keycloakService;

    public AccountService(AccountRepository accountRepository, KeycloakService keycloakService) {
        this.accountRepository = accountRepository;
        this.keycloakService = keycloakService;
    }

    @Transactional
    @CacheEvict(value = {"accounts"}, allEntries = true)
    public LoginResponse register(RegisterRequest request) {
        // Email kontrolü
        if (accountRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Account already exists with email: " + request.getEmail());
        }

        // Yeni kullanıcı oluştur (şifre düz metin; güvenlik eklenirse encode edilmeli)
        Account account = Account.builder()
                .email(request.getEmail())
                .password(request.getPassword())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .username(request.getUsername())
                .phoneNumber(request.getPhoneNumber())
                .address(request.getAddress())
                .createAt(System.currentTimeMillis())
                .updateAt(System.currentTimeMillis())
                .state(true)
                .build();

        Account savedAccount = accountRepository.save(account);

        // Keycloak'a kullanıcı ekle
        try {
            String keycloakUserId = keycloakService.createUser(
                    request.getEmail(),
                    request.getUsername() != null ? request.getUsername() : request.getEmail(), // Username yoksa email kullan
                    request.getPassword(),
                    request.getFirstName(),
                    request.getLastName() != null ? request.getLastName() : ""
            );
            // Keycloak user ID'sini kaydetmek isterseniz Account entity'sine field ekleyebilirsiniz
        } catch (Exception e) {
            // Keycloak hatası durumunda veritabanındaki kaydı geri almak isteyebilirsiniz
            // accountRepository.delete(savedAccount);
            throw new RuntimeException("Account created but failed to add to Keycloak: " + e.getMessage(), e);
        }

        return LoginResponse.builder()
                .userId(savedAccount.getId())
                .email(savedAccount.getEmail())
                .message("Account registered successfully")
                .build();
        }

    public LoginResponse login(LoginRequest request) {
        // Kullanıcıyı email ile bul (mevcut mu kontrolü)
        Account account = accountRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Account not found with email: " + request.getEmail()));

        // Basit şifre kontrolü (encode edilmeden kaydedildiği varsayımıyla)
        if (!account.getPassword().equals(request.getPassword())) {
            throw new RuntimeException("Invalid email or password");
        }

        String primary = (account.getUsername() != null && !account.getUsername().isBlank())
                ? account.getUsername()
                : account.getEmail();

        KeycloakPasswordGrantResponse token = obtainKeycloakToken(primary, account.getEmail(), request.getPassword());

        return LoginResponse.builder()
                .userId(account.getId())
                .email(account.getEmail())
                .message("Login successful")
                .accessToken(token.getAccessToken())
                .refreshToken(token.getRefreshToken())
                .expiresIn(token.getExpiresIn())
                .tokenType(token.getTokenType() != null ? token.getTokenType() : "Bearer")
                .build();
    }

    /**
     * Kullanıcıyı logout yapar - Keycloak'ta session'ları sonlandırır
     */
    public void logout(String email) {
        // Kullanıcının veritabanında olup olmadığını kontrol et
        if (!accountRepository.existsByEmail(email)) {
            throw new RuntimeException("Account not found with email: " + email);
        }

        // Keycloak'ta logout yap (tüm session'ları sonlandır)
        try {
            keycloakService.logoutUser(email);
        } catch (Exception e) {
            log.error("Error during logout: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to logout: " + e.getMessage(), e);
        }
    }

    @Cacheable(value = "accounts", key = "'all'")
    public List<AccountResponse> getAllAccounts() {
        log.debug("Fetching all accounts from database");
        List<Account> accounts = accountRepository.findAll();
        return accounts.stream()
                .map(this::mapToAccountResponse)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "accounts", key = "#id")
    public AccountResponse getAccountById(Long id) {
        log.debug("Fetching account from database: id={}", id);
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Account not found with id: " + id));
        return mapToAccountResponse(account);
    }

    @Transactional
    @CacheEvict(value = {"accounts"}, allEntries = true)
    public AccountResponse updateAccount(Long userId, UpdateAccountRequest request) {
        Account existing = accountRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Account not found with id: " + userId));

        String oldEmail = existing.getEmail();
        String newEmail = request.getEmail();
        
        // Email güncellemesi - request'te email null ise mevcut email'i kullan
        if (newEmail != null && !newEmail.isEmpty() && !newEmail.equals(oldEmail)) {
            if (accountRepository.existsByEmail(newEmail)) {
                throw new RuntimeException("Email already exists: " + newEmail);
            }
            existing.setEmail(newEmail);
        }
        // Email güncellenmemişse, mevcut email'i kullan (Keycloak'a göndermek için)
        if (newEmail == null || newEmail.isEmpty()) {
            newEmail = oldEmail;
        }

        // Username güncellemesi
        if (request.getUsername() != null && !request.getUsername().isEmpty()) {
            existing.setUsername(request.getUsername());
        }

        // Password güncellemesi
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            existing.setPassword(request.getPassword());
        }

        // Boş olmayan alanları güncelle
        if (request.getFirstName() != null && !request.getFirstName().isEmpty()) {
            existing.setFirstName(request.getFirstName());
        }

        if (request.getLastName() != null && !request.getLastName().isEmpty()) {
            existing.setLastName(request.getLastName());
        }

        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isEmpty()) {
            existing.setPhoneNumber(request.getPhoneNumber());
        }

        if (request.getAddress() != null && !request.getAddress().isEmpty()) {
            existing.setAddress(request.getAddress());
        }

        // BaseEntity -> updateAt otomatik güncellenmesi için
        existing.setUpdateAt(System.currentTimeMillis());

        Account updatedAccount = accountRepository.save(existing);

        // Keycloak'ta kullanıcı bilgilerini güncelle (ID hariç tüm bilgiler)
        try {
            String usernameToUpdate = updatedAccount.getUsername() != null ? updatedAccount.getUsername() : request.getUsername();
            log.info("Attempting to update user in Keycloak - Old Email: {}, New Email: {}, Username: {}", 
                    oldEmail, updatedAccount.getEmail(), usernameToUpdate);
            
            keycloakService.updateUser(
                    oldEmail, // Keycloak'ta arama için eski email kullan
                    updatedAccount.getEmail(), // Yeni email (güncellenmiş veya mevcut)
                    usernameToUpdate, // Username (Keycloak'ta güncellenecek)
                    request.getPassword() != null ? request.getPassword() : null, // Password (sadece güncellenmişse)
                    updatedAccount.getFirstName(),
                    updatedAccount.getLastName(),
                    updatedAccount.getPhoneNumber(),
                    updatedAccount.getAddress()
            );
            log.info("User updated successfully in Keycloak for email: {}", oldEmail);
        } catch (Exception e) {
            // Keycloak güncelleme hatası - detaylı log
            log.error("Failed to update user in Keycloak. Old Email: {}, Error: {}", oldEmail, e.getMessage(), e);
            // Exception'ı fırlatmıyoruz çünkü veritabanı güncellemesi başarılı oldu
            // Ancak kullanıcıya bilgi vermek için log'u detaylı tutuyoruz
        }

        return mapToAccountResponse(updatedAccount);
    }

    private KeycloakPasswordGrantResponse obtainKeycloakToken(String primaryUsername, String email, String password) {
        boolean willRetryWithEmail = email != null && !email.equals(primaryUsername);
        try {
            // Username Keycloak'ta login adı olmayabilir; email ile tekrar denenecekse ilk hatayı WARN yapma
            return keycloakService.obtainPasswordGrantToken(primaryUsername, password, !willRetryWithEmail);
        } catch (Exception first) {
            if (willRetryWithEmail) {
                try {
                    return keycloakService.obtainPasswordGrantToken(email, password, true);
                } catch (Exception second) {
                    log.debug("Keycloak token retry with email also failed: {}", second.getMessage());
                }
            }
            log.error("Keycloak token after DB login failed for {}: {}", primaryUsername, first.getMessage());
            throw new RuntimeException("Login ok but SSO token failed: " + first.getMessage(), first);
        }
    }

    private AccountResponse mapToAccountResponse(Account account) {
        return AccountResponse.builder()
                .id(account.getId())
                .email(account.getEmail())
                .username(account.getUsername())
                .firstName(account.getFirstName())
                .lastName(account.getLastName())
                .phoneNumber(account.getPhoneNumber())
                .address(account.getAddress())
                .build();
    }
}

