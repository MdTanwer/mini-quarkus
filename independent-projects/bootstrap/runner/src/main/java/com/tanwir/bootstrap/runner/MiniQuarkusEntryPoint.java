package com.tanwir.bootstrap.runner;

import com.tanwir.bootstrap.model.MiniApplicationModel;
import com.tanwir.bootstrap.model.MiniApplicationModelLoader;
import com.tanwir.resteasy.reactive.server.MiniResteasyReactiveServer;

import java.util.concurrent.CountDownLatch;

import org.jboss.logging.Logger;

public final class MiniQuarkusEntryPoint {

    private static final Logger LOG = Logger.getLogger(MiniQuarkusEntryPoint.class);
    private static final String PORT_PROPERTY = "mini.quarkus.http.port";

    private MiniQuarkusEntryPoint() {
    }

    public static void main(String[] args) throws InterruptedException {
        MiniApplicationModel applicationModel = MiniApplicationModelLoader.load();
        LOG.infof("Loaded application model for %s with %d GET route(s)",
                applicationModel.applicationName(), applicationModel.getRoutes().size());

        int port = Integer.getInteger(PORT_PROPERTY, 8080);
        MiniResteasyReactiveServer server = MiniResteasyReactiveServer.start(applicationModel, port);
        CountDownLatch shutdown = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(shutdown::countDown, "mini-quarkus-shutdown"));
        System.out.println("mini-quarkus listening on http://localhost:" + server.port() + "/hello");
        try {
            shutdown.await();
        } finally {
            server.close();
        }
    }
}
