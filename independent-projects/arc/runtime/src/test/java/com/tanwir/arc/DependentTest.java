package com.tanwir.arc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DependentTest {

    @AfterEach
    void tearDown() {
        Arc.shutdown();
    }

    @Test
    void shouldReturnDifferentInstancesOnEachCall() {
        Arc.initialize(TestDependentScopedBean.class);

        TestDependentScopedBean first = Arc.container().instance(TestDependentScopedBean.class).get();
        TestDependentScopedBean second = Arc.container().instance(TestDependentScopedBean.class).get();

        assertNotSame(first, second);
    }

    @Test
    void closeShouldTriggerPreDestroy() {
        Arc.initialize(TestDependentBeanWithLifecycle.class);

        TestDependentBeanWithLifecycle bean = Arc.container().instance(TestDependentBeanWithLifecycle.class).get();
        assertFalse(bean.isPreDestroyCalled());

        bean.close(); // Should trigger @PreDestroy
        
        assertTrue(bean.isPreDestroyCalled());
    }

    @Test
    void shouldCallPostConstructOnCreation() {
        Arc.initialize(TestDependentBeanWithLifecycle.class);

        TestDependentBeanWithLifecycle bean = Arc.container().instance(TestDependentBeanWithLifecycle.class).get();
        
        assertTrue(bean.isPostConstructCalled());
    }
}

@Dependent
class TestDependentScopedBean {
}


@Dependent
class TestDependentBeanWithLifecycle implements AutoCloseable {
    private boolean postConstructCalled = false;
    private boolean preDestroyCalled = false;

    @PostConstruct
    public void init() {
        postConstructCalled = true;
    }

    @PreDestroy
    public void cleanup() {
        preDestroyCalled = true;
    }

    public boolean isPostConstructCalled() {
        return postConstructCalled;
    }

    public boolean isPreDestroyCalled() {
        return preDestroyCalled;
    }

    @Override
    public void close() {
        cleanup();
    }
}
