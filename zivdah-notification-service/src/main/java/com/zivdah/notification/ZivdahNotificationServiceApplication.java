package com.zivdah.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // powers NotificationRetryScheduler
public class ZivdahNotificationServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ZivdahNotificationServiceApplication.class, args);
	}

}
