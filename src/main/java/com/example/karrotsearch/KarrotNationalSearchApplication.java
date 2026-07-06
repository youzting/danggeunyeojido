package com.example.karrotsearch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class KarrotNationalSearchApplication {

	public static void main(String[] args) {
		SpringApplication.run(KarrotNationalSearchApplication.class, args);
	}

}
