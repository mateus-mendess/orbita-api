package com.m2.orbita_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@EnableCaching
@SpringBootApplication
public class OrbitaApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(OrbitaApiApplication.class, args);
	}

}
