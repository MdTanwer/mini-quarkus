package com.tanwir.arc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CircularDependencyTest {

    @AfterEach
    void tearDown() {
        Arc.shutdown();
    }

    @Test
    void shouldDetectCircularDependency() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            Arc.initialize(CircularBeanA.class, CircularBeanB.class);
        });
        
        assertTrue(exception.getMessage().contains("Circular dependency detected"));
    }

    @Test
    void shouldAllowNonCircularDependencies() {
        assertDoesNotThrow(() -> {
            Arc.initialize(NonCircularBeanA.class, NonCircularBeanB.class, NonCircularBeanC.class);
        });
        
        // Verify beans can be resolved
        NonCircularBeanA beanA = Arc.container().instance(NonCircularBeanA.class).get();
        NonCircularBeanB beanB = Arc.container().instance(NonCircularBeanB.class).get();
        NonCircularBeanC beanC = Arc.container().instance(NonCircularBeanC.class).get();
        
        assertNotNull(beanA);
        assertNotNull(beanB);
        assertNotNull(beanC);
        
        // Verify dependencies are wired correctly
        assertSame(beanA.getBeanB(), beanB);
        assertSame(beanB.getBeanC(), beanC);
    }
}

@Singleton
class CircularBeanA {
    private final CircularBeanB beanB;

    public CircularBeanA(CircularBeanB beanB) {
        this.beanB = beanB;
    }
}

@Singleton
class CircularBeanB {
    private final CircularBeanA beanA;

    public CircularBeanB(CircularBeanA beanA) {
        this.beanA = beanA;
    }
}

@Singleton
class NonCircularBeanA {
    private final NonCircularBeanB beanB;

    public NonCircularBeanA(NonCircularBeanB beanB) {
        this.beanB = beanB;
    }

    public NonCircularBeanB getBeanB() {
        return beanB;
    }
}

@Singleton
class NonCircularBeanB {
    private final NonCircularBeanC beanC;

    public NonCircularBeanB(NonCircularBeanC beanC) {
        this.beanC = beanC;
    }

    public NonCircularBeanC getBeanC() {
        return beanC;
    }
}

@Singleton
class NonCircularBeanC {
    // No dependencies
}
