package com.tanwir.it;

import com.tanwir.arc.Arc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ArcBuildTimeInjectionTest {

    @AfterEach
    void tearDown() {
        Arc.shutdown();
    }

    @Test
    void shouldResolveBeansUsingGeneratedBootstrap() {
        Arc.initialize();

        MainResource first = Arc.container().instance(MainResource.class).get();
        MainResource second = Arc.container().instance(MainResource.class).get();

        assertEquals("mini-quarkus GET works", first.hello());
        assertSame(first, second);
    }
}
