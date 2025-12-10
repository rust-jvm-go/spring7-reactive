package initiative.java.spring7plus.spring7reactive.config;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    public DataSource dataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:postgresql://localhost:5433/yugabyte");
        ds.setUsername("yugabyte");
        ds.setPassword(""); // set if you add a password later
        return ds;
    }

    @Bean
    public SpringLiquibase liquibase(DataSource dataSource) {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.setShouldRun(true);
        return liquibase;
    }
}
