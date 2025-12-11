package com.example.cachesync;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Main Spring Boot Application for Cache Synchronization across Kubernetes pods.
 * 
 * This application demonstrates cache synchronization using:
 * - Spring Cloud Kubernetes Discovery to find all pod instances
 * - Spring Boot Actuator cache endpoints to trigger cache eviction
 * - Caffeine for in-memory caching
 */
@SpringBootApplication
@EnableCaching
@EnableDiscoveryClient
public class CacheSyncApplication {

    public static void main(String[] args) {
        SpringApplication.run(CacheSyncApplication.class, args);
    }
}
