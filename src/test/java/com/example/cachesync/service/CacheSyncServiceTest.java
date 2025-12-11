package com.example.cachesync.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
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

    @BeforeEach
    void setUp() {
        cacheSyncService = new CacheSyncService(discoveryClient, webClientBuilder);
    }

    @Test
    @DisplayName("Should discover all pods and trigger cache eviction")
    void testEvictCacheOnAllPods() {
        String cacheName = "products";
        
        when(serviceInstance1.getUri()).thenReturn(URI.create("http://pod1:8080"));
        when(serviceInstance1.getInstanceId()).thenReturn("pod1");
        when(serviceInstance2.getUri()).thenReturn(URI.create("http://pod2:8080"));
        when(serviceInstance2.getInstanceId()).thenReturn("pod2");
        
        when(discoveryClient.getInstances(anyString()))
            .thenReturn(Arrays.asList(serviceInstance1, serviceInstance2));

        List<ServiceInstance> instances = cacheSyncService.getDiscoveredInstances();
        
        assertThat(instances).hasSize(2);
        verify(discoveryClient).getInstances(anyString());
    }

    @Test
    @DisplayName("Should handle no pods discovered gracefully")
    void testEvictCacheWithNoPods() {
        when(discoveryClient.getInstances(anyString())).thenReturn(Collections.emptyList());

        List<ServiceInstance> instances = cacheSyncService.getDiscoveredInstances();

        assertThat(instances).isEmpty();
        verify(discoveryClient).getInstances(anyString());
    }

    @Test
    @DisplayName("Should return discovered instances")
    void testGetDiscoveredInstances() {
        when(discoveryClient.getInstances(anyString()))
            .thenReturn(Arrays.asList(serviceInstance1, serviceInstance2));

        List<ServiceInstance> instances = cacheSyncService.getDiscoveredInstances();

        assertThat(instances).hasSize(2);
        verify(discoveryClient).getInstances(anyString());
    }

    @Test
    @DisplayName("Should handle single pod scenario")
    void testSinglePodDiscovery() {
        when(serviceInstance1.getUri()).thenReturn(URI.create("http://pod1:8080"));
        when(serviceInstance1.getInstanceId()).thenReturn("pod1");
        
        when(discoveryClient.getInstances(anyString()))
            .thenReturn(Collections.singletonList(serviceInstance1));

        List<ServiceInstance> instances = cacheSyncService.getDiscoveredInstances();

        assertThat(instances).hasSize(1);
        assertThat(instances.get(0).getInstanceId()).isEqualTo("pod1");
    }
}
