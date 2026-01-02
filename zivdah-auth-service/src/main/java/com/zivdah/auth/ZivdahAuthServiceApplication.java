package com.zivdah.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class ZivdahAuthServiceApplication {
	public static void main(String[] args) {
		SpringApplication.run(ZivdahAuthServiceApplication.class, args);
	}
}
