package com.tanwir.it;

import com.tanwir.resteasy.reactive.server.MiniResteasyReactiveServer;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpGetRouteTest {

    private MiniResteasyReactiveServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void shouldServeGeneratedGetRoute() throws IOException, InterruptedException {
        server = MiniResteasyReactiveServer.start(0);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + server.port() + "/hello"))
                .GET()
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("mini-quarkus GET works", response.body());
    }
}
