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
    void shouldReturnSameApplicationScopedInstanceTwice() {
        Arc.initialize(TestApplicationScopedBean.class);

        TestApplicationScopedBean first = Arc.container().instance(TestApplicationScopedBean.class).get();
        TestApplicationScopedBean second = Arc.container().instance(TestApplicationScopedBean.class).get();

        assertSame(first, second);
    }

    @Test
    void shouldResolveApplicationScopedBean() {
        Arc.initialize(TestApplicationScopedBean.class);

        TestApplicationScopedBean bean = Arc.container().instance(TestApplicationScopedBean.class).get();

        assertNotNull(bean);
        assertEquals("application-scoped-message", bean.getMessage());
    }
}

@ApplicationScoped
class TestApplicationScopedBean {
    public String getMessage() {
        return "application-scoped-message";
    }
}
