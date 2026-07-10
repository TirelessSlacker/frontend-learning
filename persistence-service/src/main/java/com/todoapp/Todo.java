package com.todoapp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The core entity this whole sample project is about, now mapped directly
 * onto the "todos" table via JPA annotations instead of being hand-built
 * from a JDBC ResultSet. TodoRepository asks the EntityManager for these
 * by id or query, and JPA constructs and populates them via reflection -
 * that's why there's a no-arg constructor below that nothing else calls
 * directly.
 *
 * Entities must not be `final` (JPA providers may generate proxy subclasses)
 * and their persistent fields must not be `final` either, since JPA sets
 * them outside the constructor.
 */
@Entity
@Table(name = "todos")
public class Todo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(nullable = false)
    private boolean completed;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    /** Required by JPA to instantiate entities loaded from the database. */
    protected Todo() {}

    /** Used by TodoRepository.insert() - id is assigned once persisted. */
    public Todo(String text) {
        this.text = text;
        this.completed = false;
        this.createdAt = System.currentTimeMillis();
    }

    public long getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    /** Converts this Todo into a plain Map so Json.write() can serialize it for an HTTP response. */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("text", text);
        map.put("completed", completed);
        map.put("createdAt", createdAt);
        return map;
    }
}
