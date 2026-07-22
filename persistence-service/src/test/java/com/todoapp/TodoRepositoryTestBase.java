package com.todoapp;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The CRUD contract TodoRepository must satisfy regardless of which
 * database backs it. Subclasses supply an EntityManagerFactory pointed at a
 * specific backend (in-memory H2, or a real Testcontainers Postgres) - the
 * assertions here run unchanged against both, which is the point: proving
 * the fast H2-with-Postgres-dialect tier actually behaves like the real
 * thing.
 */
abstract class TodoRepositoryTestBase {

    protected abstract EntityManagerFactory emf();

    private TodoRepository repository() {
        return new TodoRepository(emf());
    }

    @BeforeEach
    void clearTable() {
        EntityManager em = emf().createEntityManager();
        try {
            em.getTransaction().begin();
            em.createQuery("DELETE FROM Todo").executeUpdate();
            em.getTransaction().commit();
        } finally {
            em.close();
        }
    }

    @Test
    void findAll_returnsEmptyList_whenNoTodosExist() {
        assertTrue(repository().findAll().isEmpty());
    }

    @Test
    void insert_persistsAndReturnsGeneratedIdAndTimestamp() {
        Todo created = repository().insert("write tests");

        assertNotNull(created.getId());
        assertEquals("write tests", created.getText());
        assertFalse(created.isCompleted());
        assertTrue(created.getCreatedAt() > 0);
    }

    @Test
    void findAll_returnsInsertedTodosOrderedById() {
        TodoRepository repo = repository();
        Todo first = repo.insert("first");
        Todo second = repo.insert("second");

        List<Todo> all = repo.findAll();

        assertEquals(2, all.size());
        assertEquals(first.getId(), all.get(0).getId());
        assertEquals(second.getId(), all.get(1).getId());
    }

    @Test
    void update_changesTextOnly_whenCompletedNotProvided() {
        TodoRepository repo = repository();
        Todo created = repo.insert("original");

        Optional<Todo> updated = repo.update(created.getId(), "changed", null);

        assertTrue(updated.isPresent());
        assertEquals("changed", updated.get().getText());
        assertFalse(updated.get().isCompleted());
    }

    @Test
    void update_changesCompletedOnly_whenTextNotProvided() {
        TodoRepository repo = repository();
        Todo created = repo.insert("original");

        Optional<Todo> updated = repo.update(created.getId(), null, true);

        assertTrue(updated.isPresent());
        assertEquals("original", updated.get().getText());
        assertTrue(updated.get().isCompleted());
    }

    @Test
    void update_returnsEmpty_whenIdDoesNotExist() {
        assertTrue(repository().update(999_999L, "text", true).isEmpty());
    }

    @Test
    void delete_removesRowAndReturnsTrue() {
        TodoRepository repo = repository();
        Todo created = repo.insert("to delete");

        assertTrue(repo.delete(created.getId()));
        assertTrue(repo.findById(created.getId()).isEmpty());
    }

    @Test
    void delete_returnsFalse_whenIdDoesNotExist() {
        assertFalse(repository().delete(999_999L));
    }

    @Test
    void insert_allowsTextContainingSqlKeywordsAndQuotes() {
        String tricky = "SELECT * FROM todos; DROP TABLE todos; -- 'quoted' \"text\"";
        TodoRepository repo = repository();

        Todo created = repo.insert(tricky);
        Optional<Todo> found = repo.findById(created.getId());

        assertTrue(found.isPresent());
        assertEquals(tricky, found.get().getText());
    }
}
