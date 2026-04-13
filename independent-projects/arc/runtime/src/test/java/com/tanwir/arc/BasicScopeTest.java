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
        // Note: This test demonstrates the limitation of Arc.initialize(Class<?>...)
        // It only supports @Singleton beans via registerReflectiveSingleton
        // For full scope support (@ApplicationScoped, @RequestScoped, @Dependent),
        // we need the annotation processor to generate bean descriptors
        assertThrows(IllegalArgumentException.class, () -> {
            Arc.initialize(TestDependentBean.class);
        });
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
