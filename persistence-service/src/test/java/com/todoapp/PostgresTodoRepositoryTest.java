package com.todoapp;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.HashMap;
import java.util.Map;

/**
 * Integration tier: a real, disposable Postgres instance via Testcontainers.
 * The container is created fresh and destroyed after this class runs, so
 * nothing here ever persists across test runs. Requires Docker - tagged so
 * it can be excluded with `-DexcludedGroups=testcontainers` for a fast,
 * Docker-free run.
 */
@Tag("testcontainers")
@Testcontainers
class PostgresTodoRepositoryTest extends TodoRepositoryTestBase {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private static EntityManagerFactory emf;

    @BeforeAll
    static void startDatabase() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("jakarta.persistence.jdbc.driver", "org.postgresql.Driver");
        overrides.put("jakarta.persistence.jdbc.url", POSTGRES.getJdbcUrl());
        overrides.put("jakarta.persistence.jdbc.user", POSTGRES.getUsername());
        overrides.put("jakarta.persistence.jdbc.password", POSTGRES.getPassword());

        emf = Database.createEntityManagerFactory(overrides);
    }

    @AfterAll
    static void closeDatabase() {
        emf.close();
    }

    @Override
    protected EntityManagerFactory emf() {
        return emf;
    }
}
