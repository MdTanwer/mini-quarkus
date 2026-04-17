package com.tanwir.arc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.junit.jupiter.api.Assertions.*;

class QualifierTest {

    @AfterEach
    void tearDown() {
        Arc.shutdown();
    }

    @Test
    void shouldResolveBeanWithQualifier() {
        Arc.initialize(TestBeanWithQualifier.class, TestConsumerBean.class);

        TestInterface bean = Arc.container().instance(TestInterface.class, MyQualifier.class).get();
        
        assertNotNull(bean);
        assertEquals("qualified-bean", bean.getMessage());
        assertEquals("qualified-bean", Arc.container().instance(TestConsumerBean.class).get().message());
    }

    @Test
    void shouldResolveBeanWithoutQualifier() {
        Arc.initialize(TestBeanWithoutQualifier.class);

        TestInterface bean = Arc.container().instance(TestInterface.class).get();
        
        assertNotNull(bean);
        assertEquals("unqualified-bean", bean.getMessage());
    }

    @Test
    void shouldThrowWhenNoBeanFoundWithQualifier() {
        Arc.initialize(TestBeanWithoutQualifier.class);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            Arc.container().instance(TestInterface.class, MyQualifier.class).get();
        });
        
        assertTrue(exception.getMessage().contains("No bean registered for"));
        assertTrue(exception.getMessage().contains("with qualifier"));
    }
}

@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.PARAMETER })
@interface MyQualifier {}

interface TestInterface {
    String getMessage();
}

@Singleton
@MyQualifier
class TestBeanWithQualifier implements TestInterface {
    @Override
    public String getMessage() {
        return "qualified-bean";
    }
}

@Singleton
class TestBeanWithoutQualifier implements TestInterface {
    @Override
    public String getMessage() {
        return "unqualified-bean";
    }
}

@Singleton
class TestConsumerBean {
    private final TestInterface bean;

    TestConsumerBean(@MyQualifier TestInterface bean) {
        this.bean = bean;
    }

    String message() {
        return bean.getMessage();
    }
}
