package com.assethub.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.net.URI;
import java.sql.Connection;

@Configuration
public class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    @Value("${database.url:}")
    private String databaseUrl;

    @Value("${spring.datasource.url:}")
    private String springDatasourceUrl;

    @Value("${spring.datasource.username:}")
    private String springDatasourceUsername;

    @Value("${spring.datasource.password:}")
    private String springDatasourcePassword;

    @Value("classpath:schema.sql")
    private Resource schemaSql;

    @Value("classpath:seed.sql")
    private Resource seedSql;

    @Bean
    @Primary
    public DataSource dataSource() {
        if (databaseUrl != null && !databaseUrl.isBlank()) {
            return buildFromDatabaseUrl(databaseUrl.trim());
        }

        if (springDatasourceUrl != null && !springDatasourceUrl.isBlank()) {
            log.info("Connecting to database using spring.datasource.url: {}", springDatasourceUrl);
            return DataSourceBuilder.create()
                    .url(springDatasourceUrl)
                    .username(springDatasourceUsername)
                    .password(springDatasourcePassword)
                    .build();
        }

        log.info("No external database configured. Starting in-memory H2 database with PostgreSQL compatibility.");
        return DataSourceBuilder.create()
                .driverClassName("org.h2.Driver")
                .url("jdbc:h2:mem:assethub;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
                .username("sa")
                .password("")
                .build();
    }

    private DataSource buildFromDatabaseUrl(String rawUrl) {
        try {
            log.info("Parsing DATABASE_URL for PostgreSQL connection...");
            String clean = rawUrl;
            if (clean.startsWith("postgres://")) {
                clean = "postgresql://" + clean.substring("postgres://".length());
            }

            URI uri = new URI(clean);
            String userInfo = uri.getUserInfo();
            String username = "";
            String password = "";
            if (userInfo != null) {
                String[] parts = userInfo.split(":", 2);
                username = parts[0];
                if (parts.length > 1) {
                    password = parts[1];
                }
            }

            String host = uri.getHost();
            int port = uri.getPort() == -1 ? 5432 : uri.getPort();
            String path = uri.getPath();
            if (path.startsWith("/")) {
                path = path.substring(1);
            }

            String query = uri.getQuery();
            String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s", host, port, path);
            if (query != null && !query.isEmpty()) {
                jdbcUrl += "?" + query + "&stringtype=unspecified";
            } else if (!clean.contains("localhost")) {
                jdbcUrl += "?sslmode=require&stringtype=unspecified";
            } else {
                jdbcUrl += "?stringtype=unspecified";
            }

            log.info("PostgreSQL JDBC URL: jdbc:postgresql://{}:{}/{}", host, port, path);
            return DataSourceBuilder.create()
                    .driverClassName("org.postgresql.Driver")
                    .url(jdbcUrl)
                    .username(username)
                    .password(password)
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse DATABASE_URL: {}. Falling back to default DataSource.", rawUrl, e);
            throw new RuntimeException("Invalid DATABASE_URL", e);
        }
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.setResultsMapCaseInsensitive(true);
        initializeDatabase(dataSource, jdbcTemplate);
        return jdbcTemplate;
    }

    @Bean
    public NamedParameterJdbcTemplate namedParameterJdbcTemplate(JdbcTemplate jdbcTemplate) {
        return new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    private void initializeDatabase(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        try {
            log.info("Initializing database schema and seed data...");
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.setContinueOnError(true);
            populator.setIgnoreFailedDrops(true);
            populator.addScript(schemaSql);
            populator.addScript(seedSql);
            populator.execute(dataSource);

            // Ensure seeded users have valid BCrypt hashes
            org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
            int userCount = jdbcTemplate.queryForObject("SELECT count(*) FROM users", Integer.class);
            log.info("USERS COUNT IN DB: {}", userCount);
            jdbcTemplate.update("UPDATE users SET password_hash = ? WHERE LOWER(email) = 'admin@assetflow.com'", encoder.encode("admin123"));
            jdbcTemplate.update("UPDATE users SET password_hash = ? WHERE LOWER(email) != 'admin@assetflow.com'", encoder.encode("password123"));

            // If PostgreSQL, resync sequences
            try (Connection conn = dataSource.getConnection()) {
                String productName = conn.getMetaData().getDatabaseProductName();
                if (productName != null && productName.toLowerCase().contains("postgres")) {
                    resyncPostgresSequences(jdbcTemplate);
                }
            }

            log.info("Database schema and seed scripts executed successfully.");
        } catch (Exception e) {
            log.warn("Database initialization warning: {}", e.getMessage());
        }
    }

    private void resyncPostgresSequences(JdbcTemplate jdbcTemplate) {
        try {
            jdbcTemplate.execute("SELECT setval('departments_id_seq', (SELECT COALESCE(MAX(id), 1) FROM departments))");
            jdbcTemplate.execute("SELECT setval('users_id_seq', (SELECT COALESCE(MAX(id), 1) FROM users))");
            jdbcTemplate.execute("SELECT setval('categories_id_seq', (SELECT COALESCE(MAX(id), 1) FROM categories))");
            jdbcTemplate.execute("SELECT setval('assets_id_seq', (SELECT COALESCE(MAX(id), 1) FROM assets))");
            jdbcTemplate.execute("SELECT setval('allocations_id_seq', (SELECT COALESCE(MAX(id), 1) FROM allocations))");
        } catch (Exception e) {
            log.debug("Sequence resync not needed or already aligned: {}", e.getMessage());
        }
    }
}
