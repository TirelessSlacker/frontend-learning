package com.todoapp;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.util.HashMap;
import java.util.Map;

/**
 * Fast tier: in-memory H2, but in Postgres-compatibility mode with
 * PostgreSQLDialect - the same dialect the running app and the Testcontainers
 * tier use, so this stays a meaningful proxy for real Postgres rather than
 * just testing H2's own dialect. No Docker required; runs on every
 * `mvn test`.
 */
class H2FastTodoRepositoryTest extends TodoRepositoryTestBase {

    private static EntityManagerFactory emf;

    @BeforeAll
    static void startDatabase() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("jakarta.persistence.jdbc.driver", "org.h2.Driver");
        // DB_CLOSE_DELAY=-1 keeps the in-memory database alive for the life
        // of the JVM - TodoRepository opens/closes its own EntityManager per
        // call, and without this flag H2 destroys an in-memory database as
        // soon as its last connection closes.
        overrides.put(
                "jakarta.persistence.jdbc.url",
                "jdbc:h2:mem:todo-fast-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        overrides.put("jakarta.persistence.jdbc.user", "sa");
        overrides.put("jakarta.persistence.jdbc.password", "");

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
