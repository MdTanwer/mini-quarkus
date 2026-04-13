package com.tanwir.bootstrap.model;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public final class MiniApplicationModelLoader {

    private MiniApplicationModelLoader() {
    }

    public static MiniApplicationModel load() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = MiniApplicationModelLoader.class.getClassLoader();
        }
        return load(classLoader);
    }

    public static MiniApplicationModel load(ClassLoader classLoader) {
        try (InputStream stream = classLoader.getResourceAsStream(MiniApplicationModelConstants.RESOURCE_PATH)) {
            if (stream == null) {
                throw new IllegalStateException(
                        "Application model not found: " + MiniApplicationModelConstants.RESOURCE_PATH);
            }
            Properties properties = new Properties();
            properties.load(stream);
            String applicationName = properties.getProperty("application.name", "mini-quarkus-application");
            int routeCount = Integer.parseInt(properties.getProperty("route.count", "0"));
            List<MiniApplicationModel.RouteModel> routes = new ArrayList<>(routeCount);
            for (int i = 0; i < routeCount; i++) {
                String path = require(properties, "route." + i + ".path");
                String operationId = require(properties, "route." + i + ".operation-id");
                routes.add(new MiniApplicationModel.RouteModel(path, operationId));
            }
            return new MiniApplicationModel(applicationName, routes);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load application model", e);
        }
    }

    private static String require(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null) {
            throw new IllegalStateException("Missing application model property: " + key);
        }
        return value;
    }
}
