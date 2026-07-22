import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from "vitest";
import request from "supertest";
import { startStubPersistenceServer } from "./support/stubPersistenceServer.js";

let stub;
let app;

beforeAll(async () => {
  process.env.NODE_ENV = "test";
  stub = await startStubPersistenceServer();
  process.env.PERSISTENCE_URL = stub.url;
  ({ default: app } = await import("../src/app.js"));
});

afterAll(async () => {
  await stub.close();
});

beforeEach(() => {
  stub.reset();
  process.env.PERSISTENCE_URL = stub.url;
});

describe("GET /api/health", () => {
  it("returns 200 without calling persistence-service", async () => {
    const res = await request(app).get("/api/health");

    expect(res.status).toBe(200);
    expect(res.body).toEqual({ status: "ok" });
  });
});

describe("GET /api/todos", () => {
  it("returns 200 with an empty array initially", async () => {
    const res = await request(app).get("/api/todos");

    expect(res.status).toBe(200);
    expect(res.body).toEqual([]);
  });

  it("returns 200 with todos created via the stub", async () => {
    await request(app).post("/api/todos").send({ text: "seeded" });

    const res = await request(app).get("/api/todos");

    expect(res.status).toBe(200);
    expect(res.body).toHaveLength(1);
    expect(res.body[0]).toMatchObject({ text: "seeded" });
  });

  it("returns 502 when persistence-service is unreachable", async () => {
    await stub.close();

    const res = await request(app).get("/api/todos");

    expect(res.status).toBe(502);
    expect(res.body).toHaveProperty("error");

    // restore for subsequent tests
    stub = await startStubPersistenceServer();
    process.env.PERSISTENCE_URL = stub.url;
  });

  it("passes through a 500 from persistence-service", async () => {
    stub.setFailure(500, { error: "boom" });

    const res = await request(app).get("/api/todos");

    expect(res.status).toBe(500);
    expect(res.body).toEqual({ error: "boom" });
  });
});

describe("POST /api/todos", () => {
  it("returns 201 and the created todo on success", async () => {
    const res = await request(app).post("/api/todos").send({ text: "buy milk" });

    expect(res.status).toBe(201);
    expect(res.body).toMatchObject({ text: "buy milk", completed: false });
  });

  it("returns 400 without calling persistence-service when text is missing", async () => {
    const res = await request(app).post("/api/todos").send({});

    expect(res.status).toBe(400);
    expect(res.body).toEqual({ error: "A non-empty 'text' field is required" });
  });

  it("returns 400 without calling persistence-service when text is blank", async () => {
    const res = await request(app).post("/api/todos").send({ text: "   " });

    expect(res.status).toBe(400);
  });

  it("returns 502 when persistence-service is unreachable", async () => {
    await stub.close();

    const res = await request(app).post("/api/todos").send({ text: "buy milk" });

    expect(res.status).toBe(502);

    stub = await startStubPersistenceServer();
    process.env.PERSISTENCE_URL = stub.url;
  });
});

describe("PUT /api/todos/:id", () => {
  it("returns 200 with the updated todo on success", async () => {
    const created = await request(app).post("/api/todos").send({ text: "original" });

    const res = await request(app)
      .put(`/api/todos/${created.body.id}`)
      .send({ text: "changed", completed: true });

    expect(res.status).toBe(200);
    expect(res.body).toMatchObject({ text: "changed", completed: true });
  });

  it("returns 400 without calling persistence-service when text is blank", async () => {
    const res = await request(app).put("/api/todos/1").send({ text: "   " });

    expect(res.status).toBe(400);
    expect(res.body).toEqual({ error: "'text', if provided, must be a non-empty string" });
  });

  it("passes through a 404 when persistence-service doesn't find the todo", async () => {
    const res = await request(app).put("/api/todos/999999").send({ completed: true });

    expect(res.status).toBe(404);
    expect(res.body).toEqual({ error: "Todo not found" });
  });

  it("returns 502 when persistence-service is unreachable", async () => {
    await stub.close();

    const res = await request(app).put("/api/todos/1").send({ completed: true });

    expect(res.status).toBe(502);

    stub = await startStubPersistenceServer();
    process.env.PERSISTENCE_URL = stub.url;
  });
});

describe("DELETE /api/todos/:id", () => {
  it("returns 204 on success", async () => {
    const created = await request(app).post("/api/todos").send({ text: "to delete" });

    const res = await request(app).delete(`/api/todos/${created.body.id}`);

    expect(res.status).toBe(204);
    expect(res.body).toEqual({});
  });

  it("passes through a 404 when persistence-service doesn't find the todo", async () => {
    const res = await request(app).delete("/api/todos/999999");

    expect(res.status).toBe(404);
    expect(res.body).toEqual({ error: "Todo not found" });
  });

  it("returns 502 when persistence-service is unreachable", async () => {
    await stub.close();

    const res = await request(app).delete("/api/todos/1");

    expect(res.status).toBe(502);

    stub = await startStubPersistenceServer();
    process.env.PERSISTENCE_URL = stub.url;
  });
});

describe("unmatched /api/* routes", () => {
  it("returns 404 without calling persistence-service", async () => {
    const res = await request(app).get("/api/unknown-path");

    expect(res.status).toBe(404);
    expect(res.body).toEqual({ error: "Not found" });
  });
});
