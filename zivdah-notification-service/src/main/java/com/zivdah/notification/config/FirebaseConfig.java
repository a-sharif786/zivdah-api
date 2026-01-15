package com.zivdah.notification.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@Component
public class FirebaseConfig {

    @PostConstruct
    public void init() throws IOException {
        InputStream serviceAccount = getClass().getClassLoader()
                .getResourceAsStream("zivdahonlicery-firebase-adminsdk-part.json");

        if (serviceAccount == null) {
            throw new IllegalStateException("Firebase service account file not found in resources!");
        }

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                .build();

        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(options);
            System.out.println("✅ Firebase initialized successfully");
        }
    }
}
//@Component
//public class FirebaseConfig {
//    @PostConstruct
//    public void init() throws IOException {
//
//        FileInputStream serviceAccount = new FileInputStream("src/main/resources/zivdahonlicery-firebase-adminsdk.json");
//
//        FirebaseOptions options = new FirebaseOptions.Builder()
//                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
//                .build();
//
//        FirebaseApp.initializeApp(options);
//    }
//}
