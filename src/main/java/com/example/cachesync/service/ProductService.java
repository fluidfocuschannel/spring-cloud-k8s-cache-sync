package com.example.cachesync.service;

import com.example.cachesync.config.CacheConfig;
import com.example.cachesync.model.Product;
import com.example.cachesync.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Product service with caching annotations.
 * Demonstrates @Cacheable and @CacheEvict usage.
 */
@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);
    private final ProductRepository productRepository;
    private final CacheSyncService cacheSyncService;

    public ProductService(ProductRepository productRepository, CacheSyncService cacheSyncService) {
        this.productRepository = productRepository;
        this.cacheSyncService = cacheSyncService;
    }

    @Cacheable(value = CacheConfig.PRODUCTS_CACHE, key = "#id")
    public Product getProductById(Long id) {
        log.info("Fetching product from repository (cache miss): {}", id);
        return productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public Product createProduct(Product product) {
        log.info("Creating new product: {}", product.getName());
        return productRepository.save(product);
    }

    @CacheEvict(value = CacheConfig.PRODUCTS_CACHE, key = "#id")
    public Product updateProduct(Long id, Product product) {
        log.info("Updating product: {}", id);
        if (!productRepository.existsById(id)) {
            throw new RuntimeException("Product not found with id: " + id);
        }
        product.setId(id);
        Product updatedProduct = productRepository.save(product);
        
        // Sync cache eviction to all pods
        cacheSyncService.evictCacheOnAllPods(CacheConfig.PRODUCTS_CACHE, id.toString());
        
        return updatedProduct;
    }

    @CacheEvict(value = CacheConfig.PRODUCTS_CACHE, key = "#id")
    public void deleteProduct(Long id) {
        log.info("Deleting product: {}", id);
        productRepository.deleteById(id);
        
        // Sync cache eviction to all pods
        cacheSyncService.evictCacheOnAllPods(CacheConfig.PRODUCTS_CACHE, id.toString());
    }

    @CacheEvict(value = CacheConfig.PRODUCTS_CACHE, allEntries = true)
    public void clearAllProductCache() {
        log.info("Clearing all product cache");
    }
}
