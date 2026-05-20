package com.ctrip.garfield.spring;

import com.ctrip.garfield.common.config.ConfigLoader;
import com.ctrip.garfield.common.config.DefaultStorageTypeRegistry;
import com.ctrip.garfield.common.config.RetryConfig;
import com.ctrip.garfield.common.config.StorageTypeRegistry;
import com.ctrip.garfield.common.lock.LockEntity;
import com.ctrip.garfield.common.spi.BackoffStrategy;
import com.ctrip.garfield.common.spi.CircuitBreaker;
import com.ctrip.garfield.common.spi.CompensationChannel;
import com.ctrip.garfield.common.spi.CompensationExhaustionHandler;
import com.ctrip.garfield.common.spi.DistributedLock;
import com.ctrip.garfield.common.spi.GarfieldSerializer;
import com.ctrip.garfield.common.spi.MetricsReporter;
import com.ctrip.garfield.common.spi.RateLimiter;
import com.ctrip.garfield.common.spi.FollowerExecutorProvider;
import com.ctrip.garfield.common.spi.StorageEngineFactory;
import com.ctrip.garfield.common.spi.defaults.ExponentialBackoffStrategy;
import com.ctrip.garfield.common.spi.defaults.JacksonSerializer;
import com.ctrip.garfield.common.spi.defaults.LoggingExhaustionHandler;
import com.ctrip.garfield.common.spi.defaults.NoOpCompensationChannel;
import com.ctrip.garfield.common.spi.defaults.NoOpRateLimiter;
import com.ctrip.garfield.common.spi.defaults.Slf4jMetricsReporter;
import com.ctrip.garfield.process.compensation.CompensationHandler;
import com.ctrip.garfield.process.compensation.CompensationOrchestrator;
import com.ctrip.garfield.process.compensation.CompensationPublisher;
import com.ctrip.garfield.process.orchestration.LockOrchestrator;
import com.ctrip.garfield.process.orchestration.ReadOrchestrator;
import com.ctrip.garfield.process.orchestration.WriteOrchestrator;
import com.ctrip.garfield.process.route.StorageEngineRegistry;
import com.ctrip.garfield.process.route.StorageRouteFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Spring Boot auto-configuration for Garfield.
 *
 * <p>Registers all framework beans with {@code @ConditionalOnMissingBean},
 * so users can override any default by declaring their own bean. Conditional
 * configurations for Resilience4j and Redisson activate only when their
 * classes are on the classpath.
 *
 * @author Trip.com Group
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(StorageRouteFactory.class)
@EnableConfigurationProperties(GarfieldProperties.class)
public class GarfieldAutoConfiguration {

    private static final int DEFAULT_FOLLOWER_EXECUTOR_QUEUE_CAPACITY = 1024;
    private static final int LOCK_EXECUTOR_QUEUE_CAPACITY = 256;

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.github.resilience4j.circuitbreaker.CircuitBreaker")
    static class Resilience4jCircuitBreakerConfiguration {
        @Bean
        @ConditionalOnMissingBean(CircuitBreaker.class)
        public CircuitBreaker resilience4jCircuitBreaker() {
            return new com.ctrip.garfield.common.spi.defaults.Resilience4jCircuitBreaker();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.redisson.api.RedissonClient")
    @ConditionalOnBean(type = "org.redisson.api.RedissonClient")
    static class RedissonDistributedLockConfiguration {
        @Bean
        @ConditionalOnMissingBean(DistributedLock.class)
        public DistributedLock redissonDistributedLock(org.redisson.api.RedissonClient redissonClient) {
            return new com.ctrip.garfield.common.spi.defaults.RedissonDistributedLock(redissonClient);
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public StorageTypeRegistry storageTypeRegistry(List<StorageEngineFactory> factories) {
        return new DefaultStorageTypeRegistry(factories);
    }

    /** No-op circuit breaker fallback when Resilience4j is not on the classpath. */
    @Bean
    @ConditionalOnMissingBean
    public CircuitBreaker circuitBreaker() {
        return new CircuitBreaker() {
            @Override
            public <T> T execute(String commandKey, Supplier<T> action, Function<Throwable, T> fallback) {
                try {
                    return action.get();
                } catch (Exception e) {
                    return fallback.apply(e);
                }
            }
        };
    }

    /** No-op distributed lock fallback when Redisson is not on the classpath. */
    @Bean
    @ConditionalOnMissingBean
    public DistributedLock distributedLock() {
        return new DistributedLock() {
            @Override
            public boolean tryLock(String lockType, LockEntity entity) {
                return true;
            }

            @Override
            public boolean checkLock(String lockType, String key, String expectedToken) {
                return true;
            }

            @Override
            public List<String> batchGetOwners(String lockType, String... keys) {
                return Collections.emptyList();
            }

            @Override
            public void unlock(String lockType, LockEntity entity) {
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public MetricsReporter metricsReporter() {
        return new Slf4jMetricsReporter();
    }

    @Bean
    @ConditionalOnMissingBean
    public CompensationChannel compensationChannel() {
        return new NoOpCompensationChannel();
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimiter rateLimiter() {
        return new NoOpRateLimiter();
    }

    @Bean
    @ConditionalOnMissingBean
    public StorageEngineRegistry storageEngineRegistry(List<StorageEngineFactory> factories) {
        StorageEngineRegistry registry = new StorageEngineRegistry();
        factories.forEach(registry::register);
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public StorageRouteFactory storageRouteFactory(StorageEngineRegistry registry,
                                                   ConfigLoader configLoader,
                                                   ApplicationContext applicationContext,
                                                   RateLimiter rateLimiter) {
        StorageRouteFactory factory = new StorageRouteFactory(registry, configLoader,
                applicationContext::getBean, rateLimiter);
        factory.init();
        return factory;
    }

    @Bean
    @ConditionalOnMissingBean
    public GarfieldSerializer garfieldSerializer() {
        return new JacksonSerializer();
    }

    @Bean
    @ConditionalOnMissingBean
    public CompensationPublisher compensationPublisher(CompensationChannel compensationChannel,
                                                       GarfieldSerializer serializer,
                                                       MetricsReporter metricsReporter) {
        return new CompensationPublisher(compensationChannel, serializer, metricsReporter);
    }

    @Bean
    @ConditionalOnMissingBean
    public RetryConfig retryConfig() {
        return new RetryConfig();
    }

    @Bean
    @ConditionalOnMissingBean
    public LockOrchestrator lockOrchestrator(DistributedLock distributedLock) {
        int lockPoolSize = Runtime.getRuntime().availableProcessors();
        AtomicInteger lockCounter = new AtomicInteger();
        ExecutorService lockExecutor = new ThreadPoolExecutor(
                lockPoolSize, lockPoolSize,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(LOCK_EXECUTOR_QUEUE_CAPACITY),
                r -> {
                    Thread t = new Thread(r, "garfield-lock-worker-" + lockCounter.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
        return new LockOrchestrator(distributedLock, lockExecutor);
    }

    @Bean
    @ConditionalOnMissingBean
    public FollowerExecutorProvider followerExecutorProvider() {
        int poolSize = Runtime.getRuntime().availableProcessors();
        AtomicInteger counter = new AtomicInteger();
        ExecutorService shared = new ThreadPoolExecutor(
                poolSize, poolSize,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(DEFAULT_FOLLOWER_EXECUTOR_QUEUE_CAPACITY),
                r -> {
                    Thread t = new Thread(r, "garfield-follower-writer-" + counter.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.AbortPolicy());
        return new FollowerExecutorProvider() {
            @Override
            public ExecutorService getExecutor(String storageId) {
                return shared;
            }

            @Override
            public void shutdown() {
                shared.shutdown();
            }
        };
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public WriteOrchestrator writeOrchestrator(StorageRouteFactory routeFactory,
                                               LockOrchestrator lockOrchestrator,
                                               CompensationPublisher compensationPublisher,
                                               MetricsReporter metricsReporter,
                                               RetryConfig retryConfig,
                                               FollowerExecutorProvider followerExecutorProvider,
                                               GarfieldProperties properties) {
        return new WriteOrchestrator(routeFactory, lockOrchestrator, compensationPublisher,
                metricsReporter, retryConfig, followerExecutorProvider,
                properties.getFollower().getTimeoutMs());
    }

    @Bean
    @ConditionalOnMissingBean
    public ReadOrchestrator readOrchestrator(StorageRouteFactory routeFactory,
                                             LockOrchestrator lockOrchestrator,
                                             MetricsReporter metricsReporter) {
        return new ReadOrchestrator(routeFactory, lockOrchestrator, metricsReporter);
    }

    @Bean
    @ConditionalOnMissingBean
    public BackoffStrategy backoffStrategy() {
        return new ExponentialBackoffStrategy();
    }

    @Bean
    @ConditionalOnMissingBean
    public CompensationExhaustionHandler compensationExhaustionHandler() {
        return new LoggingExhaustionHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    public CompensationOrchestrator compensationOrchestrator(
            WriteOrchestrator writeOrchestrator,
            BackoffStrategy backoffStrategy,
            CompensationExhaustionHandler exhaustionHandler,
            MetricsReporter metricsReporter,
            ApplicationContext applicationContext) {
        CompensationOrchestrator orchestrator = new CompensationOrchestrator(
                writeOrchestrator, backoffStrategy, exhaustionHandler, metricsReporter);
        applicationContext.getBeansOfType(CompensationHandler.class)
                .forEach((beanName, handler) -> orchestrator.registerHandler(handler.reqClassName(), handler));
        return orchestrator;
    }
}
