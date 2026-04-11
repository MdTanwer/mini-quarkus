package com.tanwir.arc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

class ArcSingletonTest {

    @AfterEach
    void tearDown() {
        Arc.shutdown();
    }

    @Test
    void shouldReturnSameSingletonInstance() {
        Arc.initialize(TestSingletonBean.class);

        TestSingletonBean first = Arc.container().instance(TestSingletonBean.class).get();
        TestSingletonBean second = Arc.container().instance(TestSingletonBean.class).get();

        assertSame(first, second);
    }
}
