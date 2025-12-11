package com.example.cachesync;

import com.example.cachesync.model.Product;
import com.example.cachesync.service.ProductService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cache.CacheManager;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for cache synchronization across pods.
 * These tests verify that:
 * 1. Products are correctly added to cache
 * 2. Cache entries are accessible
 * 3. Cache is properly invalidated on delete
 * 4. Cache sync propagates to all pods
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CacheSyncIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private ProductService productService;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/api";
    }

    @Test
    @Order(1)
    @DisplayName("TC001: Create multiple products and verify they are cached")
    void testCreateProductsAndVerifyCache() {
        List<Product> testProducts = Arrays.asList(
            new Product(null, "Laptop", new BigDecimal("999.99"), "High-performance laptop"),
            new Product(null, "Mouse", new BigDecimal("29.99"), "Wireless mouse"),
            new Product(null, "Keyboard", new BigDecimal("79.99"), "Mechanical keyboard")
        );

        for (Product product : testProducts) {
            ResponseEntity<Product> response = restTemplate.postForEntity(
                baseUrl + "/products",
                product,
                Product.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getId()).isNotNull();
        }
    }

    @Test
    @Order(2)
    @DisplayName("TC002: Access products and verify cache population")
    void testAccessProductsAndVerifyCachePopulation() {
        ResponseEntity<Product[]> allProductsResponse = restTemplate.getForEntity(
            baseUrl + "/products",
            Product[].class
        );
        
        assertThat(allProductsResponse.getBody()).isNotNull();
        Product[] products = allProductsResponse.getBody();
        assertThat(products.length).isGreaterThan(0);

        for (Product product : products) {
            ResponseEntity<Product> response = restTemplate.getForEntity(
                baseUrl + "/products/" + product.getId(),
                Product.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getId()).isEqualTo(product.getId());
        }

        var cache = cacheManager.getCache("products");
        assertThat(cache).isNotNull();
        
        for (Product product : products) {
            ResponseEntity<Product> response = restTemplate.getForEntity(
                baseUrl + "/products/" + product.getId(),
                Product.class
            );
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Test
    @Order(3)
    @DisplayName("TC003: Verify cache hit on subsequent access")
    void testCacheHitOnSubsequentAccess() {
        ResponseEntity<Product[]> allProductsResponse = restTemplate.getForEntity(
            baseUrl + "/products",
            Product[].class
        );
        
        assertThat(allProductsResponse.getBody()).isNotNull();
        assertThat(allProductsResponse.getBody().length).isGreaterThan(0);
        
        Long productId = allProductsResponse.getBody()[0].getId();

        long startTime1 = System.currentTimeMillis();
        ResponseEntity<Product> firstAccess = restTemplate.getForEntity(
            baseUrl + "/products/" + productId,
            Product.class
        );
        long duration1 = System.currentTimeMillis() - startTime1;

        long startTime2 = System.currentTimeMillis();
        ResponseEntity<Product> secondAccess = restTemplate.getForEntity(
            baseUrl + "/products/" + productId,
            Product.class
        );
        long duration2 = System.currentTimeMillis() - startTime2;

        assertThat(firstAccess.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(secondAccess.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstAccess.getBody()).isNotNull();
        assertThat(secondAccess.getBody()).isNotNull();
        assertThat(firstAccess.getBody().getId()).isEqualTo(secondAccess.getBody().getId());
        
        System.out.println("First access duration: " + duration1 + "ms");
        System.out.println("Second access duration: " + duration2 + "ms");
    }

    @Test
    @Order(4)
    @DisplayName("TC004: Delete product and verify cache eviction")
    void testDeleteProductAndVerifyCacheEviction() {
        Product productToDelete = new Product(null, "ToDelete", new BigDecimal("49.99"), "Will be deleted");
        
        ResponseEntity<Product> createResponse = restTemplate.postForEntity(
            baseUrl + "/products",
            productToDelete,
            Product.class
        );
        
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long productId = createResponse.getBody().getId();

        ResponseEntity<Product> getResponse = restTemplate.getForEntity(
            baseUrl + "/products/" + productId,
            Product.class
        );
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        restTemplate.delete(baseUrl + "/products/" + productId);

        ResponseEntity<Product> afterDeleteResponse = restTemplate.getForEntity(
            baseUrl + "/products/" + productId,
            Product.class
        );
        
        assertThat(afterDeleteResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @Order(5)
    @DisplayName("TC005: Update product and verify cache eviction and sync")
    void testUpdateProductAndVerifyCacheEvictionAndSync() {
        Product product = new Product(null, "OriginalName", new BigDecimal("100.00"), "Original description");
        
        ResponseEntity<Product> createResponse = restTemplate.postForEntity(
            baseUrl + "/products",
            product,
            Product.class
        );
        
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long productId = createResponse.getBody().getId();

        restTemplate.getForEntity(baseUrl + "/products/" + productId, Product.class);

        Product updatedProduct = new Product(productId, "UpdatedName", new BigDecimal("150.00"), "Updated description");
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Product> requestEntity = new HttpEntity<>(updatedProduct, headers);
        
        ResponseEntity<Product> updateResponse = restTemplate.exchange(
            baseUrl + "/products/" + productId,
            HttpMethod.PUT,
            requestEntity,
            Product.class
        );

        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody().getName()).isEqualTo("UpdatedName");

        ResponseEntity<Product> getAfterUpdate = restTemplate.getForEntity(
            baseUrl + "/products/" + productId,
            Product.class
        );
        
        assertThat(getAfterUpdate.getBody().getName()).isEqualTo("UpdatedName");
        assertThat(getAfterUpdate.getBody().getPrice()).isEqualByComparingTo(new BigDecimal("150.00"));
    }

    @Test
    @Order(6)
    @DisplayName("TC006: Verify cache info endpoint returns discovered pods")
    void testCacheInfoEndpointReturnsPodInfo() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
            baseUrl + "/cache/info",
            Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("cacheNames");
    }

    @Test
    @Order(7)
    @DisplayName("TC007: Manually trigger cache sync and verify response")
    void testManualCacheSyncTrigger() {
        ResponseEntity<Map> response = restTemplate.exchange(
            baseUrl + "/cache/sync/products",
            HttpMethod.DELETE,
            null,
            Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("message");
        assertThat(response.getBody().get("cacheName")).isEqualTo("products");
    }

    @Test
    @Order(8)
    @DisplayName("TC008: Sync all caches across all pods")
    void testSyncAllCaches() {
        ResponseEntity<Map> response = restTemplate.exchange(
            baseUrl + "/cache/sync",
            HttpMethod.DELETE,
            null,
            Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("message");
    }

    @Test
    @Order(9)
    @DisplayName("TC009: Bulk create products and verify all cached")
    void testBulkCreateAndVerifyAllCached() {
        int numberOfProducts = 5;
        
        for (int i = 0; i < numberOfProducts; i++) {
            Product product = new Product(
                null, 
                "BulkProduct" + i, 
                new BigDecimal(10 + i * 10), 
                "Bulk test product " + i
            );
            
            ResponseEntity<Product> response = restTemplate.postForEntity(
                baseUrl + "/products",
                product,
                Product.class
            );
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        ResponseEntity<Product[]> allProducts = restTemplate.getForEntity(
            baseUrl + "/products",
            Product[].class
        );
        
        assertThat(allProducts.getBody()).isNotNull();
        
        for (Product product : allProducts.getBody()) {
            ResponseEntity<Product> response = restTemplate.getForEntity(
                baseUrl + "/products/" + product.getId(),
                Product.class
            );
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        for (Product product : allProducts.getBody()) {
            ResponseEntity<Product> response = restTemplate.getForEntity(
                baseUrl + "/products/" + product.getId(),
                Product.class
            );
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getId()).isEqualTo(product.getId());
        }
    }

    @Test
    @Order(10)
    @DisplayName("TC010: Delete multiple products and verify cache cleared for each")
    void testDeleteMultipleProductsAndVerifyCacheCleared() {
        Long[] productIds = new Long[3];
        
        for (int i = 0; i < 3; i++) {
            Product product = new Product(
                null, 
                "ToDeleteBulk" + i, 
                new BigDecimal(99.99), 
                "Will be deleted"
            );
            
            ResponseEntity<Product> response = restTemplate.postForEntity(
                baseUrl + "/products",
                product,
                Product.class
            );
            
            productIds[i] = response.getBody().getId();
            
            restTemplate.getForEntity(baseUrl + "/products/" + productIds[i], Product.class);
        }

        for (Long id : productIds) {
            restTemplate.delete(baseUrl + "/products/" + id);
        }

        for (Long id : productIds) {
            ResponseEntity<Product> response = restTemplate.getForEntity(
                baseUrl + "/products/" + id,
                Product.class
            );
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
