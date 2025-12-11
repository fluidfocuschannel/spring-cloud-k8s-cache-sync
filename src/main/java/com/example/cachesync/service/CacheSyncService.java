package com.example.cachesync.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Service for synchronizing cache eviction across all pod instances.
 * Uses Spring Cloud Kubernetes DiscoveryClient to find all pods and
 * Spring Boot Actuator's cache endpoints to trigger eviction.
 */
@Service
public class CacheSyncService {

    private static final Logger log = LoggerFactory.getLogger(CacheSyncService.class);
    
    private final DiscoveryClient discoveryClient;
    private final WebClient.Builder webClientBuilder;
    
    @Value("${spring.application.name}")
    private String applicationName;
    
    @Value("${cache.sync.timeout:5}")
    private int timeoutSeconds;

    public CacheSyncService(DiscoveryClient discoveryClient, WebClient.Builder webClientBuilder) {
        this.discoveryClient = discoveryClient;
        this.webClientBuilder = webClientBuilder;
    }

    /**
     * Evict a specific cache entry on all pods.
     * 
     * @param cacheName the name of the cache
     * @param cacheKey the cache key to evict (optional, if null evicts entire cache)
     */
    public void evictCacheOnAllPods(String cacheName, String cacheKey) {
        List<ServiceInstance> instances = discoveryClient.getInstances(applicationName);
        
        log.info("Found {} instances of service: {}", instances.size(), applicationName);
        
        for (ServiceInstance instance : instances) {
            String podName = resolvePodName(instance);
            String labels = describeMetadata(instance);
            try {
                String url = buildCacheUrl(instance, cacheName, cacheKey);
                
                webClientBuilder.build()
                    .delete()
                    .uri(url)
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .onErrorResume(e -> {
                        log.error("Failed to evict cache on pod: {} - {}", podName, e.getMessage());
                        return Mono.empty();
                    })
                    .block();
                    
                log.info("Cache {} evicted on pod: {} (uri={}, metadata={})", 
                        cacheName, podName, instance.getUri(), labels);
            } catch (Exception e) {
                log.error("Failed to evict cache on pod: {}", podName, e);
            }
        }
    }

    /**
     * Evict all caches on all pods.
     */
    public void evictAllCachesOnAllPods() {
        List<ServiceInstance> instances = discoveryClient.getInstances(applicationName);
        
        log.info("Evicting all caches on {} instances", instances.size());
        
        for (ServiceInstance instance : instances) {
            String podName = resolvePodName(instance);
            String labels = describeMetadata(instance);
            try {
                String url = instance.getUri() + "/actuator/caches";
                
                webClientBuilder.build()
                    .delete()
                    .uri(url)
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .onErrorResume(e -> {
                        log.error("Failed to evict all caches on pod: {} - {}", podName, e.getMessage());
                        return Mono.empty();
                    })
                    .block();
                    
                log.info("All caches evicted on pod: {} (uri={}, metadata={})", 
                        podName, instance.getUri(), labels);
            } catch (Exception e) {
                log.error("Failed to evict all caches on pod: {}", podName, e);
            }
        }
    }

    /**
     * Get information about all discovered service instances.
     */
    public List<ServiceInstance> getDiscoveredInstances() {
        return discoveryClient.getInstances(applicationName);
    }

    private String buildCacheUrl(ServiceInstance instance, String cacheName, String cacheKey) {
        String baseUrl = instance.getUri() + "/actuator/caches/" + cacheName;
        if (cacheKey != null && !cacheKey.isEmpty()) {
            baseUrl += "?key=" + cacheKey;
        }
        return baseUrl;
    }

    private String resolvePodName(ServiceInstance instance) {
        Map<String, String> metadata = instance.getMetadata();
        if (metadata != null) {
            String podName = metadata.get("pod.name");
            if (podName != null && !podName.isBlank()) {
                return podName;
            }
        }
        if (instance.getInstanceId() != null && !instance.getInstanceId().isBlank()) {
            return instance.getInstanceId();
        }
        return instance.getHost();
    }

    private String describeMetadata(ServiceInstance instance) {
        Map<String, String> metadata = instance.getMetadata();
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        return metadata.toString();
    }
}
