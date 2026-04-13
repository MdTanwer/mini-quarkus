package com.tanwir.arc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PostConstructTest {

    @AfterEach
    void tearDown() {
        Arc.shutdown();
    }

    @Test
    void shouldCallPostConstructExactlyOnceAfterConstruction() {
        Arc.initialize(TestBeanWithPostConstruct.class);

        TestBeanWithPostConstruct bean = Arc.container().instance(TestBeanWithPostConstruct.class).get();
        
        assertEquals(1, bean.getPostConstructCallCount());
    }

    @Test
    void shouldCallPostConstructForSingleton() {
        Arc.initialize(TestSingletonWithPostConstruct.class);

        TestSingletonWithPostConstruct bean = Arc.container().instance(TestSingletonWithPostConstruct.class).get();
        
        assertEquals(1, bean.getPostConstructCallCount());
    }

    @Test
    void shouldCallPostConstructForApplicationScoped() {
        Arc.initialize(TestApplicationScopedWithPostConstruct.class);

        TestApplicationScopedWithPostConstruct bean = Arc.container().instance(TestApplicationScopedWithPostConstruct.class).get();
        
        assertEquals(1, bean.getPostConstructCallCount());
    }

    @Test
    void shouldCallPostConstructForRequestScoped() {
        Arc.initialize(TestRequestScopedWithPostConstruct.class);
        
        Arc.container().requestContextController().activate();
        try {
            TestRequestScopedWithPostConstruct bean = Arc.container().instance(TestRequestScopedWithPostConstruct.class).get();
            
            assertEquals(1, bean.getPostConstructCallCount());
        } finally {
            Arc.container().requestContextController().deactivate();
        }
    }
}

@Singleton
class TestSingletonWithPostConstruct {
    private int postConstructCallCount = 0;

    @PostConstruct
    public void init() {
        postConstructCallCount++;
    }

    public int getPostConstructCallCount() {
        return postConstructCallCount;
    }
}

@ApplicationScoped
class TestApplicationScopedWithPostConstruct {
    private int postConstructCallCount = 0;

    @PostConstruct
    public void init() {
        postConstructCallCount++;
    }

    public int getPostConstructCallCount() {
        return postConstructCallCount;
    }
}

@RequestScoped
class TestRequestScopedWithPostConstruct {
    private int postConstructCallCount = 0;

    @PostConstruct
    public void init() {
        postConstructCallCount++;
    }

    public int getPostConstructCallCount() {
        return postConstructCallCount;
    }
}

@Dependent
class TestBeanWithPostConstruct {
    private int postConstructCallCount = 0;

    @PostConstruct
    public void init() {
        postConstructCallCount++;
    }

    public int getPostConstructCallCount() {
        return postConstructCallCount;
    }
}
