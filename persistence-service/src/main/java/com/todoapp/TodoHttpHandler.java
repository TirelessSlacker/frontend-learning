package com.todoapp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Translates HTTP requests into TodoRepository calls and back into JSON
 * responses. This is the only class that knows about HTTP - everything below
 * it (TodoRepository, Todo) has no idea it's being used over a network.
 *
 * Routes:
 *   GET    /todos          -> list all todos
 *   POST   /todos          -> create a todo, body: {"text": "..."}
 *   PUT    /todos/{id}     -> update a todo, body: {"text": "...", "completed": true}
 *   DELETE /todos/{id}     -> delete a todo
 */
public final class TodoHttpHandler implements HttpHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TodoRepository repository;

    public TodoHttpHandler(TodoRepository repository) {
        this.repository = repository;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            // Always allow the server layer to call this from a browser too,
            // in case someone wants to hit it directly while learning.
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equals(method)) {
                sendResponse(exchange, 204, "");
                return;
            }

            String[] segments = path.replaceAll("^/+", "").split("/");
            // segments[0] is "todos"; segments[1], if present, is the id.

            if (segments.length == 1 && "todos".equals(segments[0])) {
                if ("GET".equals(method)) {
                    handleList(exchange);
                } else if ("POST".equals(method)) {
                    handleCreate(exchange);
                } else {
                    sendResponse(exchange, 405, error("Method not allowed"));
                }
                return;
            }

            if (segments.length == 2 && "todos".equals(segments[0])) {
                long id;
                try {
                    id = Long.parseLong(segments[1]);
                } catch (NumberFormatException e) {
                    sendResponse(exchange, 400, error("Invalid id"));
                    return;
                }
                if ("PUT".equals(method)) {
                    handleUpdate(exchange, id);
                } else if ("DELETE".equals(method)) {
                    handleDelete(exchange, id);
                } else {
                    sendResponse(exchange, 405, error("Method not allowed"));
                }
                return;
            }

            sendResponse(exchange, 404, error("Not found"));
        } catch (Exception e) {
            e.printStackTrace();
            sendResponse(exchange, 500, error("Internal server error"));
        }
    }

    private void handleList(HttpExchange exchange) throws IOException {
        List<Todo> body = repository.findAll();
        sendResponse(exchange, 200, MAPPER.writeValueAsString(body));
    }

    private void handleCreate(HttpExchange exchange) throws IOException {
        Map<String, Object> input = readJsonBody(exchange);
        Object textObj = input.get("text");
        if (!(textObj instanceof String text) || text.isBlank()) {
            sendResponse(exchange, 400, error("Field 'text' is required"));
            return;
        }
        Todo created = repository.insert(text.strip());
        sendResponse(exchange, 201, MAPPER.writeValueAsString(created));
    }

    private void handleUpdate(HttpExchange exchange, long id) throws IOException {
        Map<String, Object> input = readJsonBody(exchange);
        String text = input.get("text") instanceof String s ? s : null;
        Boolean completed = input.get("completed") instanceof Boolean b ? b : null;

        Optional<Todo> updated = repository.update(id, text, completed);
        if (updated.isEmpty()) {
            sendResponse(exchange, 404, error("Todo not found"));
            return;
        }
        sendResponse(exchange, 200, MAPPER.writeValueAsString(updated.get()));
    }

    private void handleDelete(HttpExchange exchange, long id) throws IOException {
        boolean deleted = repository.delete(id);
        if (!deleted) {
            sendResponse(exchange, 404, error("Todo not found"));
            return;
        }
        sendResponse(exchange, 204, "");
    }

    private Map<String, Object> readJsonBody(HttpExchange exchange) throws IOException {
        byte[] raw = exchange.getRequestBody().readAllBytes();
        String content = new String(raw, StandardCharsets.UTF_8);
        if (content.isBlank()) return Map.of();
        return MAPPER.readValue(content, new TypeReference<Map<String, Object>>() {});
    }

    private String error(String message) throws IOException {
        return MAPPER.writeValueAsString(Map.of("error", message));
    }

    private void sendResponse(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length == 0 && status == 204 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } else {
            exchange.getResponseBody().close();
        }
    }
}
