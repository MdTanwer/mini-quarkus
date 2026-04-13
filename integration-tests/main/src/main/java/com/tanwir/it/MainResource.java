package com.tanwir.it;

import com.tanwir.arc.Inject;
import com.tanwir.arc.Singleton;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Singleton
@Path("/hello")
public final class MainResource {

    private final GreetingService greetingService;

    @Inject
    public MainResource(GreetingService greetingService) {
        this.greetingService = greetingService;
    }

    @GET
    public String hello() {
        return greetingService.message();
    }
}
