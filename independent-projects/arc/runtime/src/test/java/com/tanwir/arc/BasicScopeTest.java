package com.tanwir.arc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BasicScopeTest {

    @AfterEach
    void tearDown() {
        Arc.shutdown();
    }

    @Test
    void shouldSupportSingletonScope() {
        Arc.initialize(TestSingletonBean.class);

        TestSingletonBean first = Arc.container().instance(TestSingletonBean.class).get();
        TestSingletonBean second = Arc.container().instance(TestSingletonBean.class).get();

        assertSame(first, second);
        assertEquals("singleton-message", first.getMessage());
    }

    @Test
    void shouldSupportDependentScope() {
        Arc.initialize(TestDependentBean.class);

        TestDependentBean first = Arc.container().instance(TestDependentBean.class).get();
        TestDependentBean second = Arc.container().instance(TestDependentBean.class).get();

        assertNotSame(first, second);
        assertEquals("dependent-message", first.getMessage());
    }
}

@Singleton
class TestSingletonBean {
    public String getMessage() {
        return "singleton-message";
    }
}

@Dependent
class TestDependentBean {
    public String getMessage() {
        return "dependent-message";
    }
}
