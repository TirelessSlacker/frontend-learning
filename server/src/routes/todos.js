import { Router } from "express";
import * as persistence from "../services/todoStore.js";

// This layer is where request validation and business rules live - the
// stuff you don't want scattered across the frontend or duplicated into the
// storage layer. todoStore doesn't know what a "blank todo" is; that's a
// decision this app's API makes.

const router = Router();

router.get("/todos", async (req, res, next) => {
  try {
    const todos = await persistence.listTodos();
    res.json(todos);
  } catch (err) {
    next(err);
  }
});

router.post("/todos", async (req, res, next) => {
  try {
    const { text } = req.body;
    if (typeof text !== "string" || !text.trim()) {
      return res.status(400).json({ error: "A non-empty 'text' field is required" });
    }
    const todo = await persistence.createTodo(text.trim());
    res.status(201).json(todo);
  } catch (err) {
    next(err);
  }
});

router.put("/todos/:id", async (req, res, next) => {
  try {
    const { text, completed } = req.body;
    if (text !== undefined && (typeof text !== "string" || !text.trim())) {
      return res.status(400).json({ error: "'text', if provided, must be a non-empty string" });
    }
    const todo = await persistence.updateTodo(req.params.id, { text, completed });
    res.json(todo);
  } catch (err) {
    next(err);
  }
});

router.delete("/todos/:id", async (req, res, next) => {
  try {
    await persistence.deleteTodo(req.params.id);
    res.status(204).end();
  } catch (err) {
    next(err);
  }
});

export default router;
