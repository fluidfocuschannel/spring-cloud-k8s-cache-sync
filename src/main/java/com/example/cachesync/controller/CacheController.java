package com.example.cachesync.controller;

import com.example.cachesync.service.CacheSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * REST controller for cache management and synchronization.
 */
@RestController
@RequestMapping("/api/cache")
public class CacheController {

    private static final Logger log = LoggerFactory.getLogger(CacheController.class);
    private final CacheSyncService cacheSyncService;
    private final CacheManager cacheManager;

    public CacheController(CacheSyncService cacheSyncService, CacheManager cacheManager) {
        this.cacheSyncService = cacheSyncService;
        this.cacheManager = cacheManager;
    }

    /**
     * Manually trigger cache sync across all pods for a specific cache.
     */
    @DeleteMapping("/sync/{cacheName}")
    public ResponseEntity<Map<String, Object>> syncCache(@PathVariable String cacheName) {
        log.info("DELETE /api/cache/sync/{} - Triggering cache sync", cacheName);
        
        try {
            cacheSyncService.evictCacheOnAllPods(cacheName, null);
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Cache sync triggered for: " + cacheName);
            response.put("cacheName", cacheName);
            response.put("timestamp", new Date());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to sync cache: {}", cacheName, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Evict all caches on all pods.
     */
    @DeleteMapping("/sync")
    public ResponseEntity<Map<String, Object>> syncAllCaches() {
        log.info("DELETE /api/cache/sync - Triggering sync for all caches");
        
        try {
            cacheSyncService.evictAllCachesOnAllPods();
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "All caches sync triggered");
            response.put("timestamp", new Date());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to sync all caches", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get information about discovered pods and cache info.
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getCacheInfo() {
        log.info("GET /api/cache/info - Fetching cache info");
        
        Map<String, Object> info = new HashMap<>();
        
        // Get discovered instances
        List<ServiceInstance> instances = cacheSyncService.getDiscoveredInstances();
        List<Map<String, String>> instancesInfo = instances.stream()
                .map(instance -> {
                    Map<String, String> instInfo = new HashMap<>();
                    instInfo.put("instanceId", instance.getInstanceId());
                    instInfo.put("host", instance.getHost());
                    instInfo.put("port", String.valueOf(instance.getPort()));
                    instInfo.put("uri", instance.getUri().toString());
                    return instInfo;
                })
                .collect(Collectors.toList());
        
        info.put("discoveredInstances", instancesInfo);
        info.put("instanceCount", instances.size());
        
        // Get cache names
        Collection<String> cacheNames = cacheManager.getCacheNames();
        info.put("cacheNames", cacheNames);
        
        return ResponseEntity.ok(info);
    }

    /**
     * Clear local cache only (for testing purposes).
     */
    @DeleteMapping("/local/{cacheName}")
    public ResponseEntity<Map<String, Object>> clearLocalCache(@PathVariable String cacheName) {
        log.info("DELETE /api/cache/local/{} - Clearing local cache only", cacheName);
        
        var cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Local cache cleared: " + cacheName);
            response.put("cacheName", cacheName);
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
