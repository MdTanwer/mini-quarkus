package com.tanwir.it;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackagedJarIT {

    @Test
    void shouldStartPackagedJarAndServeHello() throws Exception {
        Path jar = Path.of("target", "mini-quarkus-main.jar");
        assertTrue(Files.exists(jar), "Packaged jar should exist: " + jar.toAbsolutePath());

        Process process = new ProcessBuilder(
                "java",
                "-Dmini.quarkus.http.port=0",
                "-jar",
                jar.toString())
                .redirectErrorStream(true)
                .start();

        String startupLine = null;
        String deploymentLine = null;
        int port = -1;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            String line;
            while (System.nanoTime() < deadline && (line = readLine(reader, deadline)) != null) {
                // Phase 6: deployment phase complete signal
                if (line.contains("Deployment phase complete") || line.contains("Application:")) {
                    deploymentLine = line;
                }
                // Phase 6: new startup format — "Listening on: http://localhost:<port>"
                if (line.contains("Listening on: http://localhost:")) {
                    startupLine = line;
                    port = extractPort(line);
                    break;
                }
            }

            assertTrue(deploymentLine != null, "Bootstrap runner did not complete the deployment phase");
            assertTrue(startupLine != null, "Packaged jar did not report startup");

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(HttpRequest.newBuilder()
                                    .uri(URI.create("http://localhost:" + port + "/hello"))
                                    .GET()
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertEquals("Hello from dev profile! (count: 5)", response.body());
        } finally {
            process.destroy();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS);
            }
        }
    }

    private static String readLine(BufferedReader reader, long deadlineNanos) throws IOException, InterruptedException {
        while (System.nanoTime() < deadlineNanos) {
            if (reader.ready()) {
                return reader.readLine();
            }
            Thread.sleep(50);
        }
        return null;
    }

    private static int extractPort(String line) {
        int start = line.indexOf("http://localhost:");
        String afterHost = line.substring(start + "http://localhost:".length());
        // Strip any trailing path or whitespace
        int end = afterHost.length();
        for (int i = 0; i < afterHost.length(); i++) {
            char c = afterHost.charAt(i);
            if (!Character.isDigit(c)) { end = i; break; }
        }
        return Integer.parseInt(afterHost.substring(0, end));
    }
}
