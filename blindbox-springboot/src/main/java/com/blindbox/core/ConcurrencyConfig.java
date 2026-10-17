package com.blindbox.core;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.*;

@Configuration
public class ConcurrencyConfig {
    @Bean(name = "catalogInvalidationExecutor", destroyMethod = "shutdown")
    public ExecutorService catalogInvalidationExecutor() {
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1),
            task -> {
                Thread thread = new Thread(task, "catalog-invalidation");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    }
}
