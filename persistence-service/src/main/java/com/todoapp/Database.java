package com.todoapp;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import java.util.HashMap;
import java.util.Map;

/**
 * Builds the JPA EntityManagerFactory for the "todoPU" persistence unit
 * declared in src/main/resources/META-INF/persistence.xml, overriding its
 * connection settings with DB_URL / DB_USER / DB_PASSWORD environment
 * variables when they're set. TodoRepository just asks for an
 * EntityManagerFactory and doesn't care that these came from env vars, or
 * that Hibernate happens to be the JPA provider underneath.
 */
public final class Database {

    private Database() {}

    public static EntityManagerFactory createEntityManagerFactory() {
        Map<String, String> overrides = new HashMap<>();
        putIfSet(overrides, "jakarta.persistence.jdbc.url", "DB_URL");
        putIfSet(overrides, "jakarta.persistence.jdbc.user", "DB_USER");
        putIfSet(overrides, "jakarta.persistence.jdbc.password", "DB_PASSWORD");

        return Persistence.createEntityManagerFactory("todoPU", overrides);
    }

    private static void putIfSet(Map<String, String> properties, String jpaKey, String envVar) {
        String value = System.getenv(envVar);
        if (value != null && !value.isBlank()) {
            properties.put(jpaKey, value);
        }
    }
}
