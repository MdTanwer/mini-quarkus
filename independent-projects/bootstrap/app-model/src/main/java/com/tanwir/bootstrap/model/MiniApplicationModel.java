package com.tanwir.bootstrap.model;

import java.util.List;

public final class MiniApplicationModel {

    private final String applicationName;
    private final List<RouteModel> getRoutes;

    public MiniApplicationModel(String applicationName, List<RouteModel> getRoutes) {
        this.applicationName = applicationName;
        this.getRoutes = List.copyOf(getRoutes);
    }

    public String applicationName() {
        return applicationName;
    }

    public List<RouteModel> getRoutes() {
        return getRoutes;
    }

    public record RouteModel(String path, String operationId) {
    }
}
