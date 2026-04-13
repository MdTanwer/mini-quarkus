package com.tanwir.arc;

import com.tanwir.arc.context.RequestContextController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RequestScopedTest {

    private RequestContextController requestContextController;

    @BeforeEach
    void setUp() {
        Arc.initialize(TestRequestScopedBean.class);
        requestContextController = Arc.container().requestContextController();
    }

    @AfterEach
    void tearDown() {
        Arc.shutdown();
    }

    @Test
    void shouldReturnSameInstanceWithinSameRequest() {
        requestContextController.activate();
        try {
            TestRequestScopedBean first = Arc.container().instance(TestRequestScopedBean.class).get();
            TestRequestScopedBean second = Arc.container().instance(TestRequestScopedBean.class).get();
            
            assertSame(first, second);
        } finally {
            requestContextController.deactivate();
        }
    }

    @Test
    void shouldReturnDifferentInstancesAcrossDifferentRequests() {
        // First request
        requestContextController.activate();
        TestRequestScopedBean first;
        try {
            first = Arc.container().instance(TestRequestScopedBean.class).get();
        } finally {
            requestContextController.deactivate();
        }

        // Second request
        requestContextController.activate();
        TestRequestScopedBean second;
        try {
            second = Arc.container().instance(TestRequestScopedBean.class).get();
        } finally {
            requestContextController.deactivate();
        }

        assertNotSame(first, second);
    }

    @Test
    void shouldThrowWhenRequestContextNotActive() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            Arc.container().instance(TestRequestScopedBean.class).get();
        });
        
        assertTrue(exception.getMessage().contains("Request context is not active"));
    }
}

@RequestScoped
class TestRequestScopedBean {
    public String getMessage() {
        return "request-scoped-message";
    }
}
