package initiative.java.spring7plus.spring7reactive.config;

import javax.sql.DataSource;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

import com.zaxxer.hikari.HikariDataSource;

import liquibase.integration.spring.SpringLiquibase;

/**
 * Explicit Liquibase configuration for this WebFlux + R2DBC application.
 * <p>
 * The main data access stack uses R2DBC (reactive, non-JDBC), but Liquibase
 * only works with a JDBC {@link javax.sql.DataSource}. To bridge that gap,
 * this configuration defines a dedicated {@link HikariDataSource} pointing at
 * the same Yugabyte YSQL instance and wires it into {@link SpringLiquibase}
 * so schema migrations can run at application startup.
 */
@Configuration
public class LiquibaseConfig {

    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource dataSource(
            @Value("${spring.datasource.url}") String jdbcUrl,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password) {
        // Single source of truth: bind JDBC connection settings from application.yaml.
        // @ConfigurationProperties allows Hikari pool tuning (timeouts, max size, etc.) via YAML.
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(jdbcUrl);
        ds.setUsername(username);
        ds.setPassword(password);
        return ds;
    }

    @Bean
    public SpringLiquibase liquibase(
            DataSource dataSource,
            @Value("${spring.liquibase.change-log}") String changeLog,
            @Value("${spring.liquibase.enabled:true}") boolean enabled) {
        // Liquibase needs JDBC even though the application uses R2DBC for reactive data access.
        // This DataSource is for migrations only; application queries should use the R2DBC connection.
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog(changeLog);
        liquibase.setShouldRun(enabled);
        return liquibase;
    }
}
