package com.mimaraslan.service;

import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import com.mimaraslan.dto.response.KeycloakPasswordGrantResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakService {

    private final Keycloak keycloak;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.server-url}")
    private String serverUrl;

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${keycloak.client-secret}")
    private String clientSecret;

    /**
     * Resource Owner Password Credentials ile kullanıcı access token alır.
     * Keycloak'ta ilgili client için "Direct access grants" açık olmalıdır.
     */
    public KeycloakPasswordGrantResponse obtainPasswordGrantToken(String username, String password) {
        return obtainPasswordGrantToken(username, password, true);
    }

    /**
     * @param logFailureAsWarn false ise başarısızlık DEBUG (ör. email ile tekrar denenecek username denemesi)
     */
    public KeycloakPasswordGrantResponse obtainPasswordGrantToken(String username, String password, boolean logFailureAsWarn) {
        String base = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        String tokenUrl = base + "/realms/" + realm + "/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", clientId);
        if (clientSecret != null && !clientSecret.isBlank()) {
            form.add("client_secret", clientSecret);
        }
        form.add("username", username);
        form.add("password", password);

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);
        try {
            ResponseEntity<KeycloakPasswordGrantResponse> response =
                    restTemplate.exchange(tokenUrl, HttpMethod.POST, entity, KeycloakPasswordGrantResponse.class);
            KeycloakPasswordGrantResponse body = response.getBody();
            if (body == null || body.getAccessToken() == null || body.getAccessToken().isBlank()) {
                throw new RuntimeException("Keycloak token response was empty");
            }
            return body;
        } catch (HttpStatusCodeException e) {
            if (logFailureAsWarn) {
                log.warn("Keycloak password grant failed: {} — {}", e.getStatusCode(), e.getResponseBodyAsString());
            } else {
                log.debug("Keycloak password grant failed for username '{}': {} — {}", username, e.getStatusCode(),
                        e.getResponseBodyAsString());
            }
            throw new RuntimeException("Could not obtain token from Keycloak (check client Direct access grants and credentials)", e);
        }
    }

    /**
     * Keycloak'a yeni kullanıcı ekler
     */
    public String createUser(String email, String password, String firstName, String lastName) {
        return createUser(email, null, password, firstName, lastName);
    }
    
    /**
     * Keycloak'a yeni kullanıcı ekler (username ile)
     */
    public String createUser(String email, String username, String password, String firstName, String lastName) {
        try {
            RealmResource realmResource = keycloak.realm(realm);
            UsersResource usersResource = realmResource.users();

            // Kullanıcı zaten var mı kontrol et
            List<UserRepresentation> existingUsers = usersResource.search(email, true);
            if (!existingUsers.isEmpty()) {
                log.warn("User already exists in Keycloak with email: {}", email);
                return existingUsers.get(0).getId();
            }

            // Yeni kullanıcı oluştur
            UserRepresentation user = new UserRepresentation();
            // Username'i email'den farklı yap (eğer verilmişse)
            String usernameToUse = (username != null && !username.isEmpty()) ? username : email;
            user.setUsername(usernameToUse);
            user.setEmail(email);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setEnabled(true);
            user.setEmailVerified(true);
            
            log.info("Creating user in Keycloak - Email: {}, Username: {}", email, usernameToUse);

            // Kullanıcıyı oluştur
            Response response = usersResource.create(user);

            if (response.getStatus() == 201) {
                // Kullanıcı ID'sini al
                String userId = getCreatedId(response);
                log.info("User created in Keycloak with ID: {}", userId);

                // Şifreyi ayarla
                setUserPassword(userId, password);

                return userId;
            } else {
                log.error("Failed to create user in Keycloak. Status: {}", response.getStatus());
                throw new RuntimeException("Failed to create user in Keycloak");
            }
        } catch (Exception e) {
            log.error("Error creating user in Keycloak: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create user in Keycloak: " + e.getMessage(), e);
        }
    }

    /**
     * Keycloak'ta kullanıcı şifresini ayarlar
     */
    private void setUserPassword(String userId, String password) {
        try {
            RealmResource realmResource = keycloak.realm(realm);
            UserResource userResource = realmResource.users().get(userId);

            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(password);
            credential.setTemporary(false);

            userResource.resetPassword(credential);
            log.info("Password set for user: {}", userId);
        } catch (Exception e) {
            log.error("Error setting password for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to set password for user: " + e.getMessage(), e);
        }
    }

    /**
     * Keycloak'ta kullanıcı bilgilerini günceller (ID hariç tüm bilgiler)
     */
    public void updateUser(String oldEmail, String newEmail, String username, String password, String firstName, String lastName, String phoneNumber, String address) {
        try {
            RealmResource realmResource = keycloak.realm(realm);
            UsersResource usersResource = realmResource.users();

            // Kullanıcıyı eski email ile bul
            List<UserRepresentation> users = usersResource.search(oldEmail, true);
            
            // Eğer email ile bulunamazsa, username ile dene
            if (users.isEmpty() && username != null && !username.isEmpty()) {
                log.warn("User not found with email: {}, trying to search with username: {}", oldEmail, username);
                users = usersResource.search(username, true);
            }
            
            // Hala bulunamazsa, exact email match dene
            if (users.isEmpty()) {
                users = usersResource.search(null, 0, 1000); // Tüm kullanıcıları al
                users = users.stream()
                        .filter(u -> oldEmail.equals(u.getEmail()))
                        .toList();
            }
            
            if (users.isEmpty()) {
                log.error("User not found in Keycloak with email: {}. Available users count: {}. Cannot update user.", 
                        oldEmail, usersResource.count());
                throw new RuntimeException("User not found in Keycloak with email: " + oldEmail);
            }

            UserRepresentation user = users.get(0);
            String userId = user.getId(); // ID'yi koruyoruz, değiştirmiyoruz
            
            log.info("Found user in Keycloak - ID: {}, Email: {}, Username: {}", userId, user.getEmail(), user.getUsername());
            
            // Mevcut değerleri al (log için)
            String currentEmail = user.getEmail();
            String currentUsername = user.getUsername();

            // ÖNEMLİ: Username'i ÖNCE set et (email'den bağımsız)
            // Keycloak'ta username güncellemesi için username'in email'den önce set edilmesi önemli
            if (username != null && !username.isEmpty() && !username.equals(currentUsername)) {
                // Keycloak'ta username unique olmalı, kontrol et
                List<UserRepresentation> existingUsernameUsers = usersResource.search(username, true);
                boolean usernameExists = existingUsernameUsers.stream()
                        .anyMatch(u -> u.getUsername().equals(username) && !u.getId().equals(userId));
                
                if (usernameExists) {
                    log.warn("Username already exists in Keycloak: {}", username);
                    throw new RuntimeException("Username already exists in Keycloak: " + username);
                }
                
                // Username'i email'den BAĞIMSIZ olarak set et
                user.setUsername(username);
                log.info("Setting username FIRST in Keycloak: {} -> {} (Email will be: {})", 
                        currentUsername, username, newEmail != null ? newEmail : currentEmail);
            } else if (username != null && !username.isEmpty() && username.equals(currentUsername)) {
                log.info("Username unchanged in Keycloak: {}", username);
            } else if (username == null || username.isEmpty()) {
                // Username verilmemişse, mevcut username'i koru (email ile aynı olmamalı)
                log.info("Username not provided, keeping current username: {}", currentUsername);
            }
            
            // Email güncellemesi (username'den SONRA, bağımsız olarak)
            if (newEmail != null && !newEmail.isEmpty()) {
                user.setEmail(newEmail);
                if (!newEmail.equals(currentEmail)) {
                    log.info("Email updated in Keycloak: {} -> {} (Username is: {})", 
                            currentEmail, newEmail, username != null ? username : currentUsername);
                }
            }

            // İsim bilgilerini güncelle (null ise mevcut değeri koru)
            if (firstName != null && !firstName.isEmpty()) {
                user.setFirstName(firstName);
            }
            if (lastName != null && !lastName.isEmpty()) {
                user.setLastName(lastName);
            }

            // Attributes olarak phoneNumber ve address ekle/güncelle
            Map<String, List<String>> attributes = user.getAttributes();
            if (attributes == null) {
                attributes = new HashMap<>();
            }
            
            // Sadece null olmayan değerleri güncelle (null ise mevcut değeri koru)
            if (phoneNumber != null && !phoneNumber.isEmpty()) {
                attributes.put("phoneNumber", Collections.singletonList(phoneNumber));
            }
            if (address != null && !address.isEmpty()) {
                attributes.put("address", Collections.singletonList(address));
            }
            
            // Attributes'ı güncelle (boş bile olsa set et)
            user.setAttributes(attributes);

            UserResource userResource = usersResource.get(userId);
            
            // Güncelleme öncesi log - gönderilecek değerleri göster
            log.info("Updating user in Keycloak - ID: {}, Email: {} -> {}, Username: {} -> {}, FirstName: {}, LastName: {}", 
                    userId, currentEmail, newEmail != null ? newEmail : currentEmail, 
                    currentUsername, username != null ? username : currentUsername,
                    firstName, lastName);
            
            // Keycloak'ta kullanıcıyı güncelle
            // ÖNEMLİ: Username'i email'den bağımsız olarak set et
            // Keycloak'ta username güncellemesi için username'in açıkça set edilmesi gerekir
            try {
                // Username ve email'i ayrı ayrı set ettiğimizden emin ol
                if (username != null && !username.isEmpty()) {
                    user.setUsername(username);
                    log.info("Username explicitly set in user object: {}", username);
                }
                
                // Email'i set et (username'den bağımsız)
                if (newEmail != null && !newEmail.isEmpty()) {
                    user.setEmail(newEmail);
                    log.info("Email explicitly set in user object: {}", newEmail);
                }
                
                // Tüm güncellemeleri bir seferde yap
                userResource.update(user);
                log.info("Update call completed successfully");
            } catch (Exception updateException) {
                log.error("Error during update call: {}", updateException.getMessage(), updateException);
                throw updateException;
            }
            
            // Güncelleme sonrası kontrol - kullanıcıyı tekrar oku ve doğrula
            UserRepresentation updatedUser = userResource.toRepresentation();
            log.info("User after update in Keycloak - ID: {}, Email: {}, Username: {}, FirstName: {}, LastName: {}", 
                    updatedUser.getId(), updatedUser.getEmail(), updatedUser.getUsername(),
                    updatedUser.getFirstName(), updatedUser.getLastName());
            
            // Değerlerin gerçekten güncellenip güncellenmediğini kontrol et
            boolean emailUpdated = newEmail != null && !newEmail.isEmpty() && newEmail.equals(updatedUser.getEmail());
            boolean usernameUpdated = username != null && !username.isEmpty() && username.equals(updatedUser.getUsername());
            boolean firstNameUpdated = firstName != null && !firstName.isEmpty() && firstName.equals(updatedUser.getFirstName());
            boolean lastNameUpdated = lastName != null && !lastName.isEmpty() && lastName.equals(updatedUser.getLastName());
            
            if (!emailUpdated && newEmail != null && !newEmail.isEmpty()) {
                log.warn("Email was not updated in Keycloak. Expected: {}, Actual: {}", newEmail, updatedUser.getEmail());
            }
            if (!usernameUpdated && username != null && !username.isEmpty()) {
                log.warn("Username was not updated in Keycloak. Expected: {}, Actual: {}", username, updatedUser.getUsername());
            }
            if (!firstNameUpdated && firstName != null && !firstName.isEmpty()) {
                log.warn("FirstName was not updated in Keycloak. Expected: {}, Actual: {}", firstName, updatedUser.getFirstName());
            }
            if (!lastNameUpdated && lastName != null && !lastName.isEmpty()) {
                log.warn("LastName was not updated in Keycloak. Expected: {}, Actual: {}", lastName, updatedUser.getLastName());
            }
            
            // Password güncellemesi (varsa)
            if (password != null && !password.isEmpty()) {
                setUserPassword(userId, password);
                log.info("Password updated in Keycloak for user: {}", userId);
            }
            
            log.info("User successfully updated in Keycloak. Old email: {}, New email: {}", oldEmail, newEmail != null ? newEmail : oldEmail);
        } catch (Exception e) {
            log.error("Error updating user in Keycloak: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to update user in Keycloak: " + e.getMessage(), e);
        }
    }

    /**
     * Keycloak'ta kullanıcının tüm session'larını sonlandırır (logout)
     */
    public void logoutUser(String email) {
        try {
            RealmResource realmResource = keycloak.realm(realm);
            UsersResource usersResource = realmResource.users();

            // Kullanıcıyı bul
            List<UserRepresentation> users = usersResource.search(email, true);
            if (users.isEmpty()) {
                log.warn("User not found in Keycloak with email: {}", email);
                return;
            }

            String userId = users.get(0).getId();
            UserResource userResource = usersResource.get(userId);
            
            // Kullanıcının tüm session'larını sonlandır (logout)
            userResource.logout();
            
            log.info("User logged out from Keycloak: {}", email);
        } catch (Exception e) {
            log.error("Error logging out user from Keycloak: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to logout user from Keycloak: " + e.getMessage(), e);
        }
    }

    /**
     * Keycloak'tan kullanıcıyı siler
     */
    public void deleteUser(String email) {
        try {
            RealmResource realmResource = keycloak.realm(realm);
            UsersResource usersResource = realmResource.users();

            // Kullanıcıyı bul
            List<UserRepresentation> users = usersResource.search(email, true);
            if (users.isEmpty()) {
                log.warn("User not found in Keycloak with email: {}", email);
                return;
            }

            UserResource userResource = usersResource.get(users.get(0).getId());
            userResource.remove();
            log.info("User deleted from Keycloak: {}", email);
        } catch (Exception e) {
            log.error("Error deleting user from Keycloak: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to delete user from Keycloak: " + e.getMessage(), e);
        }
    }

    /**
     * Response'dan oluşturulan kaynağın ID'sini alır
     */
    private String getCreatedId(Response response) {
        String location = response.getLocation().getPath();
        return location.substring(location.lastIndexOf('/') + 1);
    }
}

