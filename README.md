# Spring Cloud Kubernetes Cache Synchronization

A complete Spring Boot application demonstrating cache synchronization across multiple pods in Kubernetes/OpenShift using Spring Cloud Kubernetes Discovery and Spring Boot Actuator.

## Overview

When running multiple pods with in-memory caching (using Spring's `@Cacheable`), cache invalidation needs to propagate to all pod instances. Instead of using Redis or a distributed cache, this solution uses:

- **Spring Cloud Kubernetes Discovery** to find all pod instances in the same namespace
- **Spring Boot Actuator** cache endpoints to trigger cache eviction on each pod
- **Caffeine** for high-performance in-memory caching

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Kubernetes Cluster                        │
│                                                              │
│  ┌──────────────┐      ┌──────────────┐      ┌──────────────┐
│  │   Pod A      │      │   Pod B      │      │   Pod C      │
│  │              │      │              │      │              │
│  │  [Cache]     │      │  [Cache]     │      │  [Cache]     │
│  │  Product:1   │      │  Product:1   │      │  Product:1   │
│  └──────┬───────┘      └──────────────┘      └──────────────┘
│         │                     ▲                      ▲         │
│         │  1. Detect DB       │                      │         │
│         │     change          │                      │         │
│         │                     │                      │         │
│         │  2. Use             │                      │         │
│         │     Discovery       │ 3. Call DELETE       │         │
│         │     Client          │    /actuator/caches  │         │
│         └─────────────────────┴──────────────────────┘         │
│                                                              │
└─────────────────────────────────────────────────────────────┘

Flow:
1. Pod A detects a DB change (update/delete operation)
2. Pod A uses DiscoveryClient to find all pods (A, B, C)
3. Pod A calls DELETE /actuator/caches/{cacheName} on all pods
4. All pods evict their local cache for the affected entry
```

## Features

- ✅ In-memory caching with Caffeine (high performance)
- ✅ Automatic cache synchronization across all pods
- ✅ Spring Cloud Kubernetes service discovery
- ✅ Spring Boot Actuator for cache management
- ✅ RESTful API for product management
- ✅ Manual cache synchronization endpoints
- ✅ RBAC configuration for Kubernetes
- ✅ Health checks and monitoring
- ✅ Production-ready Docker image

## Prerequisites

- **JDK 17+**
- **Maven 3.6+**
- **Docker** (for building images)
- **Kubernetes 1.19+** or **OpenShift 4.x**
- **kubectl** or **oc** CLI

## Project Structure

```
spring-cloud-k8s-cache-sync/
├── pom.xml
├── Dockerfile
├── README.md
├── .gitignore
├── src/
│   └── main/
│       ├── java/com/example/cachesync/
│       │   ├── CacheSyncApplication.java
│       │   ├── config/
│       │   │   ├── CacheConfig.java
│       │   │   └── WebClientConfig.java
│       │   ├── model/
│       │   │   └── Product.java
│       │   ├── repository/
│       │   │   └── ProductRepository.java
│       │   ├── service/
│       │   │   ├── ProductService.java
│       │   │   └── CacheSyncService.java
│       │   └── controller/
│       │       ├── ProductController.java
│       │       └── CacheController.java
│       └── resources/
│           └── application.yml
└── k8s/
    ├── deployment.yaml
    ├── service.yaml
    └── rbac.yaml
```

## Quick Start

### 1. Build the Application

```bash
# Clone the repository
git clone https://github.com/fluidfocuschannel/spring-cloud-k8s-cache-sync.git
cd spring-cloud-k8s-cache-sync

# Build with Maven
mvn clean package
```

### 2. Build Docker Image

```bash
# Build the Docker image
docker build -t cache-sync-service:1.0.0 .

# For Minikube, use Minikube's Docker daemon
eval $(minikube docker-env)
docker build -t cache-sync-service:1.0.0 .
```

### 3. Deploy to Kubernetes

```bash
# Apply RBAC configuration
kubectl apply -f k8s/rbac.yaml

# Deploy the service
kubectl apply -f k8s/service.yaml

# Deploy the application
kubectl apply -f k8s/deployment.yaml

# Wait for pods to be ready
kubectl wait --for=condition=ready pod -l app=cache-sync-service --timeout=120s

# Check pod status
kubectl get pods -l app=cache-sync-service
```

### 4. Access the Application

```bash
# Port forward to access the service locally
kubectl port-forward svc/cache-sync-service 8080:8080

# Or create a NodePort service (Minikube)
kubectl expose deployment cache-sync-service --type=NodePort --name=cache-sync-nodeport
minikube service cache-sync-nodeport --url
```

## API Documentation

### Product Endpoints

#### Get All Products
```bash
curl http://localhost:8080/api/products
```

#### Get Product by ID (Cached)
```bash
curl http://localhost:8080/api/products/1
```

#### Create Product
```bash
curl -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{
    "name": "New Product",
    "price": 99.99,
    "description": "A great product"
  }'
```

#### Update Product (Syncs cache across all pods)
```bash
curl -X PUT http://localhost:8080/api/products/1 \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Updated Product",
    "price": 149.99,
    "description": "An even better product"
  }'
```

#### Delete Product (Syncs cache across all pods)
```bash
curl -X DELETE http://localhost:8080/api/products/1
```

### Cache Management Endpoints

#### Get Cache Info and Discovered Pods
```bash
curl http://localhost:8080/api/cache/info
```

#### Manually Sync Specific Cache
```bash
curl -X DELETE http://localhost:8080/api/cache/sync/products
```

#### Sync All Caches Across All Pods
```bash
curl -X DELETE http://localhost:8080/api/cache/sync
```

#### Clear Local Cache Only (Testing)
```bash
curl -X DELETE http://localhost:8080/api/cache/local/products
```

### Actuator Endpoints

#### Health Check
```bash
curl http://localhost:8080/actuator/health
```

#### View All Caches
```bash
curl http://localhost:8080/actuator/caches
```

#### Evict Specific Cache
```bash
curl -X DELETE http://localhost:8080/actuator/caches/products
```

## Testing Cache Synchronization

### Scenario: Verify Cache Sync Across Pods

1. **Get the pod names:**
```bash
kubectl get pods -l app=cache-sync-service
```

2. **Access product on Pod 1 (creates cache entry):**
```bash
POD1=$(kubectl get pods -l app=cache-sync-service -o jsonpath='{.items[0].metadata.name}')
kubectl exec -it $POD1 -- wget -qO- http://localhost:8080/api/products/1
```

3. **Check logs to confirm cache miss:**
```bash
kubectl logs $POD1 | grep "cache miss"
```

4. **Access same product again (should be cached):**
```bash
kubectl exec -it $POD1 -- wget -qO- http://localhost:8080/api/products/1
kubectl logs $POD1 | tail -5
# Should NOT see "cache miss" this time
```

5. **Update product (triggers cache sync):**
```bash
kubectl exec -it $POD1 -- wget -qO- --method=PUT \
  --header="Content-Type: application/json" \
  --body-data='{"name":"Updated","price":199.99,"description":"Updated"}' \
  http://localhost:8080/api/products/1
```

6. **Verify cache evicted on all pods:**
```bash
# Check Pod 2
POD2=$(kubectl get pods -l app=cache-sync-service -o jsonpath='{.items[1].metadata.name}')
kubectl logs $POD2 | grep "Cache products evicted"

# Check Pod 3
POD3=$(kubectl get pods -l app=cache-sync-service -o jsonpath='{.items[2].metadata.name}')
kubectl logs $POD3 | grep "Cache products evicted"
```

7. **Access product again (should be cache miss on all pods):**
```bash
kubectl exec -it $POD2 -- wget -qO- http://localhost:8080/api/products/1
kubectl logs $POD2 | grep "cache miss"
```

### Scenario: Test Discovery

1. **Check discovered instances:**
```bash
curl http://localhost:8080/api/cache/info | jq '.discoveredInstances'
```

2. **Scale deployment:**
```bash
kubectl scale deployment cache-sync-service --replicas=5
kubectl wait --for=condition=ready pod -l app=cache-sync-service --timeout=120s
```

3. **Verify new pods are discovered:**
```bash
curl http://localhost:8080/api/cache/info | jq '.instanceCount'
# Should show 5
```

## Configuration

### Application Properties

Key configuration in `application.yml`:

```yaml
spring:
  application:
    name: cache-sync-service  # Used by DiscoveryClient
  cloud:
    kubernetes:
      discovery:
        enabled: true
        all-namespaces: false  # Same namespace only

management:
  endpoints:
    web:
      exposure:
        include: health,info,caches

cache:
  sync:
    timeout: 5  # Timeout for cache sync operations
```

### Environment Variables

You can override configuration using environment variables:

- `SPRING_APPLICATION_NAME` - Application name
- `SPRING_CLOUD_KUBERNETES_CLIENT_NAMESPACE` - Kubernetes namespace
- `CACHE_SYNC_TIMEOUT` - Cache sync timeout in seconds

## Deployment Options

### Minikube

```bash
minikube start
eval $(minikube docker-env)
docker build -t cache-sync-service:1.0.0 .
kubectl apply -f k8s/
```

### OpenShift

```bash
# Login to OpenShift
oc login

# Create new project
oc new-project cache-sync

# Build and deploy
oc apply -f k8s/rbac.yaml
oc apply -f k8s/service.yaml
oc apply -f k8s/deployment.yaml

# Create route
oc expose svc/cache-sync-service
```

### Production Kubernetes

```bash
# Update image registry in deployment.yaml
# Push image to your registry
docker tag cache-sync-service:1.0.0 your-registry/cache-sync-service:1.0.0
docker push your-registry/cache-sync-service:1.0.0

# Update k8s/deployment.yaml with correct image
# Apply configurations
kubectl apply -f k8s/
```

## Monitoring and Troubleshooting

### Check Application Logs

```bash
# All pods
kubectl logs -l app=cache-sync-service --tail=100 -f

# Specific pod
kubectl logs cache-sync-service-xxx-yyy -f
```

### Check Discovery

```bash
# Verify service account
kubectl get serviceaccount cache-sync-service

# Verify RBAC
kubectl get role cache-sync-service-role
kubectl get rolebinding cache-sync-service-rolebinding

# Test service discovery from inside a pod
POD=$(kubectl get pods -l app=cache-sync-service -o jsonpath='{.items[0].metadata.name}')
kubectl exec -it $POD -- env | grep KUBERNETES
```

### Common Issues

#### 1. Pods Not Discovered

**Symptom:** Cache sync doesn't reach all pods
**Solution:**
- Check RBAC permissions: `kubectl get role,rolebinding`
- Verify service account: `kubectl describe sa cache-sync-service`
- Check logs: `kubectl logs <pod> | grep "discovery"`

#### 2. Cache Not Evicting

**Symptom:** Cache doesn't clear on remote pods
**Solution:**
- Verify Actuator is enabled: `curl http://<pod-ip>:8080/actuator/caches`
- Check network connectivity between pods
- Verify timeout settings in application.yml

#### 3. Application Won't Start

**Symptom:** Pods in CrashLoopBackOff
**Solution:**
- Check logs: `kubectl logs <pod>`
- Verify image exists: `docker images | grep cache-sync`
- Check resource limits in deployment.yaml

#### 4. Discovery Client Not Working

**Symptom:** `discoveredInstances` shows 0 instances
**Solution:**
- Ensure running in Kubernetes (not locally)
- Verify namespace matches: `kubectl get pods -n <namespace>`
- Check service labels match deployment labels

## Advanced Configuration

### Custom Cache TTL

Modify `CacheConfig.java`:

```java
return Caffeine.newBuilder()
    .expireAfterWrite(30, TimeUnit.MINUTES)  // Custom TTL
    .maximumSize(5000)  // Custom max size
    .recordStats();
```

### Multiple Caches

Add more caches in `CacheConfig.java`:

```java
cacheManager.setCacheNames(Arrays.asList("products", "users", "orders"));
```

### Circuit Breaker for Cache Sync

Add Resilience4j for fault tolerance:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-circuitbreaker-resilience4j</artifactId>
</dependency>
```

## Performance Considerations

- **Cache Size:** Default is 1000 entries. Adjust based on memory.
- **TTL:** Default is 10 minutes. Set based on data staleness tolerance.
- **Sync Timeout:** Default is 5 seconds. Increase for slower networks.
- **Pod Count:** Works well with 3-10 pods. For larger deployments, consider Redis.

## Security Considerations

- RBAC limits discovery to same namespace
- Non-root user in Docker container
- Health endpoints exposed, sensitive endpoints require authentication
- No credentials in code or logs

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## Support

For issues and questions, please open an issue on GitHub.

## References

- [Spring Cloud Kubernetes](https://spring.io/projects/spring-cloud-kubernetes)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Caffeine Cache](https://github.com/ben-manes/caffeine)
- [Kubernetes RBAC](https://kubernetes.io/docs/reference/access-authn-authz/rbac/)
