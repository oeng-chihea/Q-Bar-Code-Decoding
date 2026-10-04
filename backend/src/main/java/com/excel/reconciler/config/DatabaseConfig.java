package com.excel.reconciler.config;

import com.excel.reconciler.service.JdbcWaybillTrackingStore;
import com.excel.reconciler.service.WaybillTrackingStore;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
@ConditionalOnExpression("!'${app.database.url:}'.isBlank()")
public class DatabaseConfig {

    @Bean(destroyMethod = "close")
    public HikariDataSource dataSource(@Value("${app.database.url}") String url,
                                       @Value("${app.database.username:}") String username,
                                       @Value("${app.database.password:}") String password,
                                       @Value("${app.database.max-pool-size:5}") int maxPoolSize) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("ReconcilerMySQL");
        config.setJdbcUrl(url.trim());
        config.setUsername(username);
        config.setPassword(password);
        // Small pool for low-spec hosting (e.g. Render 512MB RAM)
        config.setMaximumPoolSize(Math.max(1, maxPoolSize));
        config.setMinimumIdle(1);
        config.setConnectionTimeout(5_000);
        // Do not fail startup when MySQL is unreachable; reconciliation keeps working without it
        config.setInitializationFailTimeout(-1);
        return new HikariDataSource(config);
    }

    @Bean
    public WaybillTrackingStore waybillTrackingStore(DataSource dataSource) {
        return new JdbcWaybillTrackingStore(new JdbcTemplate(dataSource));
    }
}
