package com.zivdah.chat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // powers WaitingConversationEscalationScheduler
public class ZivdahChatServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ZivdahChatServiceApplication.class, args);
	}

}
