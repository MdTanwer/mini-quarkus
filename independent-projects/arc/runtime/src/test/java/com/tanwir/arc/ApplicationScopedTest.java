package com.tanwir.arc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApplicationScopedTest {

    @AfterEach
    void tearDown() {
        Arc.shutdown();
    }

    @Test
    void shouldReturnSameProxyInstanceTwice() {
        Arc.initialize(TestApplicationScopedBean.class);

        TestApplicationScopedBean first = Arc.container().instance(TestApplicationScopedBean.class).get();
        TestApplicationScopedBean second = Arc.container().instance(TestApplicationScopedBean.class).get();

        assertSame(first, second);
    }

    @Test
    void shouldReturnProxyInstance() {
        Arc.initialize(TestApplicationScopedBean.class);

        TestApplicationScopedBean bean = Arc.container().instance(TestApplicationScopedBean.class).get();
        
        // Should be a proxy class, not the real class
        assertNotEquals(TestApplicationScopedBean.class, bean.getClass());
        assertTrue(bean.getClass().getSimpleName().contains("_Proxy"));
    }

    @Test
    void proxyShouldDelegateToRealBean() {
        Arc.initialize(TestApplicationScopedBean.class);

        TestApplicationScopedBean proxy = Arc.container().instance(TestApplicationScopedBean.class).get();
        
        assertEquals("application-scoped-message", proxy.getMessage());
    }
}

@ApplicationScoped
class TestApplicationScopedBean {
    public String getMessage() {
        return "application-scoped-message";
    }
}
