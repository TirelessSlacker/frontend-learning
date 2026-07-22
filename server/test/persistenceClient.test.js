import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from "vitest";
import { startStubPersistenceServer } from "./support/stubPersistenceServer.js";
import * as persistence from "../src/services/persistenceClient.js";

let stub;

beforeAll(async () => {
  stub = await startStubPersistenceServer();
});

afterAll(async () => {
  await stub.close();
});

beforeEach(() => {
  stub.reset();
  process.env.PERSISTENCE_URL = stub.url;
});

describe("listTodos / createTodo / updateTodo / deleteTodo", () => {
  it("createTodo returns the created todo", async () => {
    const todo = await persistence.createTodo("buy milk");

    expect(todo).toMatchObject({ text: "buy milk", completed: false });
  });

  it("listTodos returns todos created via createTodo", async () => {
    await persistence.createTodo("buy milk");

    const todos = await persistence.listTodos();

    expect(todos).toHaveLength(1);
    expect(todos[0]).toMatchObject({ text: "buy milk" });
  });

  it("updateTodo returns the updated todo", async () => {
    const created = await persistence.createTodo("original");

    const updated = await persistence.updateTodo(created.id, { text: "changed", completed: true });

    expect(updated).toMatchObject({ text: "changed", completed: true });
  });

  it("deleteTodo resolves null on a 204 response", async () => {
    const created = await persistence.createTodo("to delete");

    const result = await persistence.deleteTodo(created.id);

    expect(result).toBeNull();
  });
});

describe("error handling", () => {
  it("throws an Error with status and message from a non-2xx response", async () => {
    await expect(persistence.updateTodo(999999, { completed: true })).rejects.toMatchObject({
      message: "Todo not found",
      status: 404,
    });
  });

  it("throws when persistence-service is unreachable", async () => {
    await stub.close();

    await expect(persistence.listTodos()).rejects.toThrow();

    stub = await startStubPersistenceServer();
    process.env.PERSISTENCE_URL = stub.url;
  });
});
