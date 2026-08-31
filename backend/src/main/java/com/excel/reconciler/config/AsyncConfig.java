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
        executor.setCorePoolSize(16);
        executor.setMaxPoolSize(64);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("BarcodeWorker-");
        executor.initialize();
        return executor;
    }
}
