package org.fintech.config;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import net.devh.boot.grpc.server.serverfactory.GrpcServerConfigurer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcServerConfig {

    @Bean(destroyMethod = "shutdown")
    ExecutorService grpcExecutor(
        @Value("${grpc.server.executor.core-threads:0}") int coreThreads,
        @Value("${grpc.server.executor.max-threads:0}") int maxThreads,
        @Value("${grpc.server.executor.keep-alive-seconds:60}") long keepAliveSeconds,
        @Value("${grpc.server.executor.queue-type:array}") String queueType,
        @Value("${grpc.server.executor.queue-capacity:0}") int queueCapacity,
        @Value("${grpc.server.executor.allow-core-timeout:false}") boolean allowCoreTimeout
    ) {
        int fallbackSize = Math.max(2, Runtime.getRuntime().availableProcessors());
        int resolvedCore = coreThreads > 0 ? coreThreads : fallbackSize;
        int resolvedMax = maxThreads > 0 ? maxThreads : resolvedCore;
        if (resolvedMax < resolvedCore) {
            resolvedMax = resolvedCore;
        }

        BlockingQueue<Runnable> queue = createQueue(queueType, queueCapacity);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            resolvedCore,
            resolvedMax,
            keepAliveSeconds,
            TimeUnit.SECONDS,
            queue,
            namedThreadFactory("grpc-exec-")
        );
        executor.allowCoreThreadTimeOut(allowCoreTimeout);
        return executor;
    }

    @Bean
    GrpcServerConfigurer grpcServerConfigurer(ExecutorService grpcExecutor) {
        return serverBuilder -> serverBuilder.executor(grpcExecutor);
    }

    private static ThreadFactory namedThreadFactory(String prefix) {
        AtomicInteger index = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName(prefix + index.getAndIncrement());
            return thread;
        };
    }

    private static BlockingQueue<Runnable> createQueue(String queueType, int queueCapacity) {
        String normalized = queueType == null ? "" : queueType.trim().toLowerCase();
        switch (normalized) {
            case "synchronous":
                return new SynchronousQueue<>();
            case "array":
            default:
                int resolvedCapacity = queueCapacity > 0 ? queueCapacity : 1024;
                return new ArrayBlockingQueue<>(resolvedCapacity);
            case "linked":
                if (queueCapacity > 0) {
                    return new LinkedBlockingQueue<>(queueCapacity);
                }
                return new LinkedBlockingQueue<>();
        }
    }
}
