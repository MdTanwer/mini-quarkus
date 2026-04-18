package com.tanwir.it;

import com.tanwir.resteasy.reactive.server.MiniResteasyReactiveServer;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
public class UserResourceTest {

    private MiniResteasyReactiveServer server;
    private WebClient webClient;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {
        server = MiniResteasyReactiveServer.start(0);
        webClient = WebClient.create(vertx, new WebClientOptions()
                .setDefaultHost("localhost")
                .setDefaultPort(server.port()));
        testContext.completeNow();
    }

    @AfterEach
    void tearDown(VertxTestContext testContext) {
        server.close();
        testContext.completeNow();
    }

    @Test
    void testGetAllUsers(VertxTestContext testContext) {
        webClient.get("/users")
                .send(testContext.succeeding(response -> {
                    assertEquals(200, response.statusCode());
                    assertTrue(response.getHeader("content-type").contains("application/json"));
                    testContext.completeNow();
                }));
    }

    @Test
    void testGetUserById(VertxTestContext testContext) {
        webClient.get("/users/1")
                .send(testContext.succeeding(response -> {
                    assertEquals(200, response.statusCode());
                    assertTrue(response.getHeader("content-type").contains("application/json"));
                    JsonObject user = response.bodyAsJsonObject();
                    assertEquals(1, user.getLong("id"));
                    assertEquals("John Doe", user.getString("name"));
                    testContext.completeNow();
                }));
    }

    @Test
    void testCreateUser(VertxTestContext testContext) {
        JsonObject newUser = new JsonObject()
                .put("name", "Test User")
                .put("email", "test@example.com");

        webClient.post("/users")
                .sendJsonObject(newUser, testContext.succeeding(response -> {
                    assertEquals(201, response.statusCode());
                    assertTrue(response.getHeader("content-type").contains("application/json"));
                    JsonObject user = response.bodyAsJsonObject();
                    assertNotNull(user.getLong("id"));
                    assertEquals("Test User", user.getString("name"));
                    testContext.completeNow();
                }));
    }

    @Test
    void testUpdateUser(VertxTestContext testContext) {
        JsonObject updatedUser = new JsonObject()
                .put("name", "Updated John")
                .put("email", "updated@example.com");

        webClient.put("/users/1")
                .sendJsonObject(updatedUser, testContext.succeeding(response -> {
                    assertEquals(200, response.statusCode());
                    assertTrue(response.getHeader("content-type").contains("application/json"));
                    JsonObject user = response.bodyAsJsonObject();
                    assertEquals(1, user.getLong("id"));
                    assertEquals("Updated John", user.getString("name"));
                    testContext.completeNow();
                }));
    }

    @Test
    void testDeleteUser(VertxTestContext testContext) {
        webClient.delete("/users/2")
                .send(testContext.succeeding(response -> {
                    assertEquals(204, response.statusCode());
                    testContext.completeNow();
                }));
    }

    @Test
    void testSearchUsers(VertxTestContext testContext) {
        webClient.get("/users/search?name=John")
                .send(testContext.succeeding(response -> {
                    assertEquals(200, response.statusCode());
                    assertTrue(response.getHeader("content-type").contains("application/json"));
                    testContext.completeNow();
                }));
    }

    @Test
    void testPatchUser(VertxTestContext testContext) {
        JsonObject patchData = new JsonObject()
                .put("name", "Patched John");

        webClient.patch("/users/1")
                .sendJsonObject(patchData, testContext.succeeding(response -> {
                    assertEquals(200, response.statusCode());
                    assertTrue(response.getHeader("content-type").contains("application/json"));
                    JsonObject user = response.bodyAsJsonObject();
                    assertEquals("Patched John", user.getString("name"));
                    assertEquals("john@example.com", user.getString("email")); // unchanged
                    testContext.completeNow();
                }));
    }

    @Test
    void testGetNonExistentUser(VertxTestContext testContext) {
        webClient.get("/users/999")
                .send(testContext.succeeding(response -> {
                    assertEquals(204, response.statusCode()); // null returns 204
                    testContext.completeNow();
                }));
    }
}
