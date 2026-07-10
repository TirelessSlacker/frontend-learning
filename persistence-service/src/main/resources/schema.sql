-- Reference schema for the todos table.
--
-- You don't need to run this by hand - Hibernate generates and applies the
-- equivalent DDL automatically on startup (hibernate.hbm2ddl.auto=update in
-- persistence.xml), derived from the @Entity annotations on Todo.java. This
-- file exists so the schema is visible in one place without having to read
-- annotations to reconstruct it, and so you can run it manually against the
-- H2 console if you'd rather manage migrations yourself (in which case, set
-- hibernate.hbm2ddl.auto to "validate" or "none" instead of "update").

CREATE TABLE IF NOT EXISTS todos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    text TEXT NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at BIGINT NOT NULL
);
