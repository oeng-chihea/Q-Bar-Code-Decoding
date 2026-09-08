package com.excel.reconciler.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

    @Bean(name = "imageDecoderExecutor")
    public Executor imageDecoderExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int processors = Runtime.getRuntime().availableProcessors();
        // Dynamic sizing tailored for low-spec cloud hosting (e.g. Render 512MB RAM, shared vCPU)
        // while scaling smoothly on larger multi-core servers.
        // Capping core pool to 2..4 prevents OutOfMemory and severe CPU context switching thrash.
        int corePool = Math.max(2, Math.min(processors, 4));
        int maxPool = Math.max(corePool, Math.min(processors * 2, 8));

        executor.setCorePoolSize(corePool);
        executor.setMaxPoolSize(maxPool);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("BarcodeWorker-");
        executor.initialize();
        return executor;
    }
}
