package com.tanwir.arc;

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class SimpleArcContainer implements ArcContainer {

    private final Map<Class<?>, Object> singletons = new ConcurrentHashMap<>();

    SimpleArcContainer(Class<?>... beanTypes) {
        for (Class<?> beanType : beanTypes) {
            if (!beanType.isAnnotationPresent(Singleton.class)) {
                throw new IllegalArgumentException(beanType.getName() + " must be annotated with @Singleton");
            }
            singletons.put(beanType, create(beanType));
        }
    }

    @Override
    public <T> InstanceHandle<T> instance(Class<T> type) {
        Object bean = singletons.get(type);
        if (bean == null) {
            throw new IllegalArgumentException("No singleton bean registered for " + type.getName());
        }
        return new SimpleInstanceHandle<>(type.cast(bean));
    }

    private static <T> T create(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to instantiate singleton bean " + type.getName(), e);
        }
    }

    private static final class SimpleInstanceHandle<T> implements InstanceHandle<T> {

        private final T instance;

        private SimpleInstanceHandle(T instance) {
            this.instance = instance;
        }

        @Override
        public T get() {
            return instance;
        }
    }
}
