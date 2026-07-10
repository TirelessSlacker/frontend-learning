package com.todoapp;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * The persistence layer, now backed by JPA (via Hibernate) instead of raw
 * JDBC. Note what did NOT need to change to get here, again: Todo's public
 * shape, TodoHttpHandler.java, and everything in the Express server and
 * React app are untouched. They only ever depended on this class's public
 * method signatures (findAll / insert / update / delete), never on whether
 * it spoke SQL directly or went through an ORM - so this migration, too, is
 * contained entirely to this one file.
 */
public final class TodoRepository {

    private final EntityManagerFactory emf;

    public TodoRepository(EntityManagerFactory emf) {
        this.emf = emf;
    }

    public List<Todo> findAll() {
        EntityManager em = emf.createEntityManager();
        try {
            return em.createQuery("SELECT t FROM Todo t ORDER BY t.id", Todo.class).getResultList();
        } finally {
            em.close();
        }
    }

    public Optional<Todo> findById(long id) {
        EntityManager em = emf.createEntityManager();
        try {
            return Optional.ofNullable(em.find(Todo.class, id));
        } finally {
            em.close();
        }
    }

    public Todo insert(String text) {
        return inTransaction(em -> {
            Todo todo = new Todo(text);
            em.persist(todo);
            return todo;
        });
    }

    public Optional<Todo> update(long id, String text, Boolean completed) {
        return inTransaction(em -> {
            Todo todo = em.find(Todo.class, id);
            if (todo == null) {
                return Optional.empty();
            }
            // Just setting fields is enough - JPA's dirty checking notices
            // the change and writes it back when the transaction commits.
            // There's no explicit "save" call, unlike the JDBC version's
            // explicit UPDATE statement.
            if (text != null) todo.setText(text);
            if (completed != null) todo.setCompleted(completed);
            return Optional.of(todo);
        });
    }

    public boolean delete(long id) {
        return inTransaction(em -> {
            Todo todo = em.find(Todo.class, id);
            if (todo == null) {
                return false;
            }
            em.remove(todo);
            return true;
        });
    }

    /**
     * Wraps the begin/commit/rollback/close boilerplate every write
     * operation needs. Without a framework managing transactions, this is
     * the one piece of plumbing worth factoring out by hand.
     */
    private <T> T inTransaction(Function<EntityManager, T> action) {
        EntityManager em = emf.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            T result = action.apply(em);
            tx.commit();
            return result;
        } catch (RuntimeException e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }
}
