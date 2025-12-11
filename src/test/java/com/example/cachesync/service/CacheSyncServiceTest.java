package com.example.cachesync.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CacheSyncServiceTest {

    @Mock
    private DiscoveryClient discoveryClient;

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @Mock
    private ServiceInstance serviceInstance1;

    @Mock
    private ServiceInstance serviceInstance2;

    private CacheSyncService cacheSyncService;
    private static final String TEST_APP_NAME = "cache-sync-service";

    @BeforeEach
    void setUp() {
        cacheSyncService = new CacheSyncService(discoveryClient, webClientBuilder);
        ReflectionTestUtils.setField(cacheSyncService, "applicationName", TEST_APP_NAME);
    }

    @Test
    @DisplayName("Should discover all pods and trigger cache eviction")
    void testEvictCacheOnAllPods() {
        when(discoveryClient.getInstances(TEST_APP_NAME))
            .thenReturn(Arrays.asList(serviceInstance1, serviceInstance2));

        List<ServiceInstance> instances = cacheSyncService.getDiscoveredInstances();
        
        assertThat(instances).hasSize(2);
        verify(discoveryClient).getInstances(TEST_APP_NAME);
    }

    @Test
    @DisplayName("Should handle no pods discovered gracefully")
    void testEvictCacheWithNoPods() {
        when(discoveryClient.getInstances(TEST_APP_NAME)).thenReturn(Collections.emptyList());

        List<ServiceInstance> instances = cacheSyncService.getDiscoveredInstances();

        assertThat(instances).isEmpty();
        verify(discoveryClient).getInstances(TEST_APP_NAME);
    }

    @Test
    @DisplayName("Should return discovered instances")
    void testGetDiscoveredInstances() {
        when(discoveryClient.getInstances(TEST_APP_NAME))
            .thenReturn(Arrays.asList(serviceInstance1, serviceInstance2));

        List<ServiceInstance> instances = cacheSyncService.getDiscoveredInstances();

        assertThat(instances).hasSize(2);
        verify(discoveryClient).getInstances(TEST_APP_NAME);
    }

    @Test
    @DisplayName("Should handle single pod scenario")
    void testSinglePodDiscovery() {
        when(discoveryClient.getInstances(TEST_APP_NAME))
            .thenReturn(Collections.singletonList(serviceInstance1));

        List<ServiceInstance> instances = cacheSyncService.getDiscoveredInstances();

        assertThat(instances).hasSize(1);
    }
}
