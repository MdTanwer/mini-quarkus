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
public class SimpleUserResourceTest {

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
        webClient.get("/simple-users")
                .send(testContext.succeeding(response -> {
                    assertEquals(200, response.statusCode());
                    assertTrue(response.getHeader("content-type").contains("application/json"));
                    testContext.completeNow();
                }));
    }

    @Test
    void testGetUser(VertxTestContext testContext) {
        webClient.get("/simple-users/1")
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

        webClient.post("/simple-users")
                .sendJsonObject(newUser, testContext.succeeding(response -> {
                    assertEquals(201, response.statusCode());
                    assertTrue(response.getHeader("content-type").contains("application/json"));
                    JsonObject user = response.bodyAsJsonObject();
                    assertNotNull(user.getLong("id"));
                    assertEquals("New User", user.getString("name"));
                    testContext.completeNow();
                }));
    }

    @Test
    void testUpdateUser(VertxTestContext testContext) {
        JsonObject updatedUser = new JsonObject()
                .put("name", "Updated User")
                .put("email", "updated@example.com");

        webClient.put("/simple-users/1")
                .sendJsonObject(updatedUser, testContext.succeeding(response -> {
                    assertEquals(200, response.statusCode());
                    assertTrue(response.getHeader("content-type").contains("application/json"));
                    testContext.completeNow();
                }));
    }

    @Test
    void testDeleteUser(VertxTestContext testContext) {
        webClient.delete("/simple-users/1")
                .send(testContext.succeeding(response -> {
                    assertEquals(204, response.statusCode());
                    testContext.completeNow();
                }));
    }

    @Test
    void testSearchUsers(VertxTestContext testContext) {
        webClient.get("/simple-users/search")
                .send(testContext.succeeding(response -> {
                    assertEquals(200, response.statusCode());
                    assertTrue(response.getHeader("content-type").contains("application/json"));
                    testContext.completeNow();
                }));
    }

    @Test
    void testPatchUser(VertxTestContext testContext) {
        JsonObject patchData = new JsonObject()
                .put("name", "Patched User");

        webClient.patch("/simple-users/1")
                .sendJsonObject(patchData, testContext.succeeding(response -> {
                    assertEquals(200, response.statusCode());
                    assertTrue(response.getHeader("content-type").contains("application/json"));
                    testContext.completeNow();
                }));
    }
}
