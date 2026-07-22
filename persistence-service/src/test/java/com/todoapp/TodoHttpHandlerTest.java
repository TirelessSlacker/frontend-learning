package com.todoapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Full HTTP-level coverage of TodoHttpHandler's route table, run against a
 * real HttpServer on an ephemeral port. Fast tier only (H2, Postgres
 * dialect) - the HTTP/JSON layer is database-agnostic, so there's no need to
 * repeat this against the Testcontainers Postgres tier too.
 */
class TodoHttpHandlerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static EntityManagerFactory emf;
    private static HttpServer server;
    private static HttpClient client;
    private static String baseUrl;

    @BeforeAll
    static void startServer() throws IOException {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("jakarta.persistence.jdbc.driver", "org.h2.Driver");
        overrides.put(
                "jakarta.persistence.jdbc.url",
                "jdbc:h2:mem:todo-http-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        overrides.put("jakarta.persistence.jdbc.user", "sa");
        overrides.put("jakarta.persistence.jdbc.password", "");
        emf = Database.createEntityManagerFactory(overrides);

        TodoRepository repository = new TodoRepository(emf);

        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/todos", new TodoHttpHandler(repository));
        server.start();

        baseUrl = "http://localhost:" + server.getAddress().getPort();
        client = HttpClient.newHttpClient();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
        emf.close();
    }

    @BeforeEach
    void clearTable() {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            em.createQuery("DELETE FROM Todo").executeUpdate();
            em.getTransaction().commit();
        } finally {
            em.close();
        }
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .method(method, publisher);
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void getTodos_returnsEmptyArray_whenNoneExist() throws Exception {
        HttpResponse<String> res = send("GET", "/todos", null);

        assertEquals(200, res.statusCode());
        assertEquals("[]", res.body());
    }

    @Test
    void getTodos_returnsInsertedTodos() throws Exception {
        send("POST", "/todos", "{\"text\":\"first\"}");

        HttpResponse<String> res = send("GET", "/todos", null);

        assertEquals(200, res.statusCode());
        JsonNode body = MAPPER.readTree(res.body());
        assertEquals(1, body.size());
        assertEquals("first", body.get(0).get("text").asText());
    }

    @Test
    void postTodos_createsTodo_returns201() throws Exception {
        HttpResponse<String> res = send("POST", "/todos", "{\"text\":\"buy milk\"}");

        assertEquals(201, res.statusCode());
        JsonNode body = MAPPER.readTree(res.body());
        assertEquals("buy milk", body.get("text").asText());
        assertFalse(body.get("completed").asBoolean());
        assertTrue(body.get("id").isIntegralNumber());
    }

    @Test
    void postTodos_blankText_returns400() throws Exception {
        HttpResponse<String> res = send("POST", "/todos", "{\"text\":\"   \"}");

        assertEquals(400, res.statusCode());
        assertEquals("Field 'text' is required", MAPPER.readTree(res.body()).get("error").asText());
    }

    @Test
    void postTodos_missingText_returns400() throws Exception {
        HttpResponse<String> res = send("POST", "/todos", "{}");

        assertEquals(400, res.statusCode());
    }

    @Test
    void putTodos_updatesExistingTodo_returns200() throws Exception {
        HttpResponse<String> created = send("POST", "/todos", "{\"text\":\"original\"}");
        long id = MAPPER.readTree(created.body()).get("id").asLong();

        HttpResponse<String> res = send("PUT", "/todos/" + id, "{\"text\":\"changed\",\"completed\":true}");

        assertEquals(200, res.statusCode());
        JsonNode body = MAPPER.readTree(res.body());
        assertEquals("changed", body.get("text").asText());
        assertTrue(body.get("completed").asBoolean());
    }

    @Test
    void putTodos_unknownId_returns404() throws Exception {
        HttpResponse<String> res = send("PUT", "/todos/999999", "{\"text\":\"changed\"}");

        assertEquals(404, res.statusCode());
        assertEquals("Todo not found", MAPPER.readTree(res.body()).get("error").asText());
    }

    @Test
    void putTodos_nonNumericId_returns400() throws Exception {
        HttpResponse<String> res = send("PUT", "/todos/not-a-number", "{\"text\":\"changed\"}");

        assertEquals(400, res.statusCode());
        assertEquals("Invalid id", MAPPER.readTree(res.body()).get("error").asText());
    }

    @Test
    void deleteTodos_removesExistingTodo_returns204() throws Exception {
        HttpResponse<String> created = send("POST", "/todos", "{\"text\":\"to delete\"}");
        long id = MAPPER.readTree(created.body()).get("id").asLong();

        HttpResponse<String> res = send("DELETE", "/todos/" + id, null);

        assertEquals(204, res.statusCode());
        assertTrue(res.body().isEmpty());
    }

    @Test
    void deleteTodos_unknownId_returns404() throws Exception {
        HttpResponse<String> res = send("DELETE", "/todos/999999", null);

        assertEquals(404, res.statusCode());
    }

    @Test
    void unmatchedPathUnderTodosContext_returns404() throws Exception {
        // "/nope" wouldn't even reach TodoHttpHandler - the JDK HttpServer
        // only routes paths starting with "/todos" to it. A path with extra
        // segments under that prefix does reach it, and exercises its own
        // "Not found" branch instead of the JDK's default error page.
        HttpResponse<String> res = send("GET", "/todos/1/extra", null);

        assertEquals(404, res.statusCode());
        assertEquals("Not found", MAPPER.readTree(res.body()).get("error").asText());
    }

    @Test
    void unsupportedMethodOnTodos_returns405() throws Exception {
        HttpResponse<String> res = send("PATCH", "/todos", null);

        assertEquals(405, res.statusCode());
    }

    @Test
    void optionsRequest_returns204WithCorsHeaders() throws Exception {
        HttpResponse<String> res = send("OPTIONS", "/todos", null);

        assertEquals(204, res.statusCode());
        assertEquals("*", res.headers().firstValue("Access-Control-Allow-Origin").orElse(null));
    }
}
