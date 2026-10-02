package com.zivdah.notification.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

// Loads the Firebase Admin SDK service-account key from a FILE PATH (firebase.credentials-path /
// FIREBASE_CREDENTIALS_PATH). It used to be read from the classpath — the private key lived in
// src/main/resources, so it was committed to git and baked into every jar and Docker image.
//   dev:  ~/.zivdah/zivdah-secrets.yaml sets FIREBASE_CREDENTIALS_PATH
//   prod: docker-compose.yml mounts the key read-only and sets FIREBASE_CREDENTIALS_PATH
// A configured-but-missing key still fails startup (as before); firebase.enabled=false skips
// initialization entirely (tests / environments without push notifications).
@Component
@Slf4j
public class FirebaseConfig {

    @Value("${firebase.enabled:true}")
    private boolean enabled;

    @Value("${firebase.credentials-path:}")
    private String credentialsPath;

    @PostConstruct
    public void init() throws IOException {
        if (!enabled) {
            log.warn("Firebase disabled (firebase.enabled=false) — push notifications will not be sent");
            return;
        }
        if (credentialsPath == null || credentialsPath.isBlank()) {
            throw new IllegalStateException(
                    "firebase.credentials-path (FIREBASE_CREDENTIALS_PATH) is not set — point it at the "
                            + "Firebase service-account JSON file, or set firebase.enabled=false");
        }
        Path path = Path.of(credentialsPath);
        if (!Files.isReadable(path)) {
            throw new IllegalStateException("Firebase service-account file not readable at " + path);
        }

        try (InputStream serviceAccount = new FileInputStream(path.toFile())) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
                log.info("Firebase initialized successfully");
            }
        }
    }
}
