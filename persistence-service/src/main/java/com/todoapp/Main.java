package com.todoapp;

import com.sun.net.httpserver.HttpServer;
import jakarta.persistence.EntityManagerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * Boots the persistence service.
 *
 * This process's only job is: hold the todos via JPA, and answer simple
 * HTTP requests about them. It knows nothing about React, Express, or HTML -
 * it would happily serve any client that speaks JSON over HTTP.
 */
public final class Main {

    private static final int PORT = 8080;

    public static void main(String[] args) throws IOException {
        // Building the factory triggers Hibernate's startup, including the
        // schema update (see persistence.xml) against whatever DB_URL points at.
        EntityManagerFactory entityManagerFactory = Database.createEntityManagerFactory();
        Runtime.getRuntime().addShutdownHook(new Thread(entityManagerFactory::close));

        TodoRepository repository = new TodoRepository(entityManagerFactory);

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/todos", new TodoHttpHandler(repository));
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        System.out.println("persistence-service listening on http://localhost:" + PORT);
        System.out.println("JPA persistence unit: todoPU (Hibernate + H2, file-based at ./data/tododb)");
    }
}
