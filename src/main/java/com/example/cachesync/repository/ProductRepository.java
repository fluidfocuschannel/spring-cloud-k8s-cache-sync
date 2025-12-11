package com.example.cachesync.repository;

import com.example.cachesync.model.Product;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory repository for storing products using a concurrent map.
 */
@Repository
public class ProductRepository {

    private final Map<Long, Product> products = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public ProductRepository() {
        // Initialize with some sample data
        save(new Product(null, "Laptop", new BigDecimal("999.99"), "High-performance laptop"));
        save(new Product(null, "Mouse", new BigDecimal("29.99"), "Wireless mouse"));
        save(new Product(null, "Keyboard", new BigDecimal("79.99"), "Mechanical keyboard"));
    }

    public Product save(Product product) {
        if (product.getId() == null) {
            product.setId(idGenerator.getAndIncrement());
        }
        products.put(product.getId(), product);
        return product;
    }

    public Optional<Product> findById(Long id) {
        return Optional.ofNullable(products.get(id));
    }

    public List<Product> findAll() {
        return new ArrayList<>(products.values());
    }

    public void deleteById(Long id) {
        products.remove(id);
    }

    public boolean existsById(Long id) {
        return products.containsKey(id);
    }
}
