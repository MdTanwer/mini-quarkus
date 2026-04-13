package com.tanwir.arc;

import com.tanwir.arc.context.RequestContextController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PreDestroyTest {

    @AfterEach
    void tearDown() {
        Arc.shutdown();
    }

    @Test
    void shouldCallPreDestroyOnArcShutdownForSingleton() {
        Arc.initialize(TestSingletonWithPreDestroy.class);

        TestSingletonWithPreDestroy bean = Arc.container().instance(TestSingletonWithPreDestroy.class).get();
        assertFalse(bean.isPreDestroyCalled());

        Arc.shutdown(); // Should trigger @PreDestroy
        
        assertTrue(bean.isPreDestroyCalled());
    }

    @Test
    void shouldCallPreDestroyOnArcShutdownForApplicationScoped() {
        Arc.initialize(TestApplicationScopedWithPreDestroy.class);

        TestApplicationScopedWithPreDestroy bean = Arc.container().instance(TestApplicationScopedWithPreDestroy.class).get();
        assertFalse(bean.isPreDestroyCalled());

        Arc.shutdown(); // Should trigger @PreDestroy
        
        assertTrue(bean.isPreDestroyCalled());
    }

    @Test
    void shouldCallPreDestroyOnRequestDeactivate() {
        Arc.initialize(TestRequestScopedWithPreDestroy.class);
        RequestContextController rcc = Arc.container().requestContextController();

        TestRequestScopedWithPreDestroy bean;
        rcc.activate();
        try {
            bean = Arc.container().instance(TestRequestScopedWithPreDestroy.class).get();
            assertFalse(bean.isPreDestroyCalled());
        } finally {
            rcc.deactivate(); // Should trigger @PreDestroy
        }
        
        assertTrue(bean.isPreDestroyCalled());
    }

    @Test
    void shouldNotCallPreDestroyAutomaticallyForDependent() {
        Arc.initialize(TestDependentWithPreDestroy.class);

        TestDependentWithPreDestroy bean = Arc.container().instance(TestDependentWithPreDestroy.class).get();
        assertFalse(bean.isPreDestroyCalled());

        Arc.shutdown(); // Should NOT trigger @PreDestroy for @Dependent
        
        assertFalse(bean.isPreDestroyCalled());
    }
}

@Singleton
class TestSingletonWithPreDestroy {
    private boolean preDestroyCalled = false;

    @PreDestroy
    public void cleanup() {
        preDestroyCalled = true;
    }

    public boolean isPreDestroyCalled() {
        return preDestroyCalled;
    }
}

@ApplicationScoped
class TestApplicationScopedWithPreDestroy {
    private boolean preDestroyCalled = false;

    @PreDestroy
    public void cleanup() {
        preDestroyCalled = true;
    }

    public boolean isPreDestroyCalled() {
        return preDestroyCalled;
    }
}

@RequestScoped
class TestRequestScopedWithPreDestroy {
    private boolean preDestroyCalled = false;

    @PreDestroy
    public void cleanup() {
        preDestroyCalled = true;
    }

    public boolean isPreDestroyCalled() {
        return preDestroyCalled;
    }
}

@Dependent
class TestDependentWithPreDestroy {
    private boolean preDestroyCalled = false;

    @PreDestroy
    public void cleanup() {
        preDestroyCalled = true;
    }

    public boolean isPreDestroyCalled() {
        return preDestroyCalled;
    }
}
