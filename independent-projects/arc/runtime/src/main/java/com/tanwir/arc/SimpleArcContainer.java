package com.tanwir.arc;

import java.lang.reflect.Constructor;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class SimpleArcContainer implements ArcContainer, BeanRegistrar {

    private final Map<Class<?>, BeanFactory<?>> factories = new ConcurrentHashMap<>();
    private final Map<Class<?>, Object> singletons = new ConcurrentHashMap<>();
    private final ThreadLocal<Deque<Class<?>>> creationStack = ThreadLocal.withInitial(ArrayDeque::new);

    <T> void registerReflectiveSingleton(Class<T> beanType) {
        if (!beanType.isAnnotationPresent(Singleton.class)) {
            throw new IllegalArgumentException(beanType.getName() + " must be annotated with @Singleton");
        }
        register(beanType, container -> create(beanType));
    }

    @Override
    public <T> void register(Class<T> type, BeanFactory<T> factory) {
        BeanFactory<?> previous = factories.putIfAbsent(type, factory);
        if (previous != null) {
            throw new IllegalStateException("A bean factory is already registered for " + type.getName());
        }
    }

    @Override
    public <T> InstanceHandle<T> instance(Class<T> type) {
        Object bean = singleton(type);
        if (bean == null) {
            throw new IllegalStateException("No bean registered for " + type.getName());
        }
        return new SimpleInstanceHandle<>(type.cast(bean));
    }

    private Object singleton(Class<?> type) {
        Object existing = singletons.get(type);
        if (existing != null) {
            return existing;
        }
        synchronized (singletons) {
            Object current = singletons.get(type);
            if (current != null) {
                return current;
            }
            BeanFactory<?> factory = factories.get(type);
            if (factory == null) {
                return null;
            }
            Deque<Class<?>> stack = creationStack.get();
            if (stack.contains(type)) {
                throw new IllegalStateException("Circular dependency detected: " + formatCircularDependency(stack, type));
            }
            stack.push(type);
            try {
                Object created = factory.create(this);
                singletons.put(type, created);
                return created;
            } finally {
                stack.pop();
                if (stack.isEmpty()) {
                    creationStack.remove();
                }
            }
        }
    }

    private static String formatCircularDependency(Deque<Class<?>> stack, Class<?> repeatedType) {
        StringBuilder message = new StringBuilder();
        for (Class<?> type : stack) {
            if (!message.isEmpty()) {
                message.append(" -> ");
            }
            message.append(type.getName());
        }
        if (!message.isEmpty()) {
            message.append(" -> ");
        }
        return message.append(repeatedType.getName()).toString();
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
