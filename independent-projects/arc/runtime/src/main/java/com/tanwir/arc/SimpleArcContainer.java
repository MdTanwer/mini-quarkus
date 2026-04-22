package com.tanwir.arc;

import com.tanwir.arc.context.ApplicationContext;
import com.tanwir.arc.context.DependentContext;
import com.tanwir.arc.context.RequestContext;
import com.tanwir.arc.context.RequestContextController;
import com.tanwir.arc.context.SingletonContext;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class SimpleArcContainer implements ArcContainer, BeanRegistrar {

    // One map: type → descriptor (replaces both factories + singletons maps)
    private final Map<Class<?>, BeanDescriptor<?>> descriptors = new ConcurrentHashMap<>();
    
    // One context per scope
    private final SingletonContext singletonContext = new SingletonContext();
    private final ApplicationContext applicationContext = new ApplicationContext();
    private final RequestContext requestContext = new RequestContext();
    private final DependentContext dependentContext = new DependentContext();
    
    // Qualifier-aware lookup: Map<Class<?>, List<BeanDescriptor<?>>>
    // for when multiple beans of same type exist, differentiated by qualifier
    private final Map<Class<?>, List<BeanDescriptor<?>>> byType = new ConcurrentHashMap<>();
    
    private final ThreadLocal<Deque<Class<?>>> creationStack = ThreadLocal.withInitial(ArrayDeque::new);
    private final RequestContextController requestContextController = new RequestContextController(requestContext);

    <T> void registerReflectiveSingleton(Class<T> beanType) {
        registerReflectiveBean(beanType);
    }

    <T> void registerReflectiveBean(Class<T> beanType) {
        Scope scope = determineScope(beanType);
        Constructor<T> constructor = selectConstructor(beanType);
        Method postConstructMethod = findLifecycleMethod(beanType, PostConstruct.class);
        Method preDestroyMethod = findLifecycleMethod(beanType, PreDestroy.class);
        Set<Class<?>> qualifiers = findQualifiers(beanType);

        BeanDescriptor<T> descriptor = new BeanDescriptor<>(
                beanType,
                scope,
                container -> create(beanType, constructor, container),
                postConstructMethod != null ? postConstructMethod.getName() : null,
                preDestroyMethod != null ? preDestroyMethod.getName() : null,
                qualifiers);
        register(descriptor);
    }

    private static <T> Scope determineScope(Class<T> beanType) {
        List<Scope> scopes = new ArrayList<>();
        if (beanType.isAnnotationPresent(Singleton.class)) {
            scopes.add(Scope.SINGLETON);
        }
        if (beanType.isAnnotationPresent(ApplicationScoped.class)) {
            scopes.add(Scope.APPLICATION);
        }
        if (beanType.isAnnotationPresent(RequestScoped.class)) {
            scopes.add(Scope.REQUEST);
        }
        if (beanType.isAnnotationPresent(Dependent.class)) {
            scopes.add(Scope.DEPENDENT);
        }
        if (scopes.isEmpty()) {
            throw new IllegalArgumentException(beanType.getName() + " must declare one bean scope");
        }
        if (scopes.size() > 1) {
            throw new IllegalArgumentException(beanType.getName() + " declares multiple bean scopes");
        }
        return scopes.get(0);
    }

    @Override
    public <T> void register(BeanDescriptor<T> descriptor) {
        BeanDescriptor<?> previous = descriptors.putIfAbsent(descriptor.beanClass(), descriptor);
        if (previous != null) {
            throw new IllegalStateException("A bean descriptor is already registered for " + descriptor.beanClass().getName());
        }

        for (Class<?> exposedType : beanTypes(descriptor.beanClass())) {
            byType.computeIfAbsent(exposedType, k -> new ArrayList<>()).add(descriptor);
        }
    }

    @Override
    public <T> void registerBean(BeanDescriptor<T> descriptor) {
        register(descriptor);
    }

    @Override
    public boolean isRegistered(Class<?> type) {
        return byType.containsKey(type);
    }

    @Override
    public <T> InstanceHandle<T> instance(Class<T> type) {
        return instance(type, null);
    }

    public <T> InstanceHandle<T> instance(Class<T> type, Class<? extends Annotation> qualifier) {
        BeanDescriptor<T> descriptor = findDescriptor(type, qualifier);
        if (descriptor == null) {
            throw new IllegalStateException("No bean registered for " + type.getName() + 
                (qualifier != null ? " with qualifier " + qualifier.getName() : ""));
        }
        
        T instance = getOrCreate(descriptor);
        return new SimpleInstanceHandle<>(instance, descriptor);
    }

    public RequestContextController requestContextController() {
        return requestContextController;
    }

    @SuppressWarnings("unchecked")
    private <T> T getOrCreate(BeanDescriptor<T> descriptor) {
        Deque<Class<?>> stack = creationStack.get();
        if (stack.contains(descriptor.beanClass())) {
            throw new IllegalStateException("Circular dependency detected: " + formatCircularDependency(stack, descriptor.beanClass()));
        }
        stack.push(descriptor.beanClass());
        try {
            return switch (descriptor.scope()) {
                case SINGLETON -> singletonContext.getOrCreate(descriptor, this);
                case APPLICATION -> applicationContext.getOrCreate(descriptor, this);
                case REQUEST -> requestContext.getOrCreate(descriptor, this);
                case DEPENDENT -> dependentContext.getOrCreate(descriptor, this);
            };
        } finally {
            stack.pop();
            if (stack.isEmpty()) {
                creationStack.remove();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> BeanDescriptor<T> findDescriptor(Class<T> type, Class<? extends Annotation> qualifier) {
        List<BeanDescriptor<?>> candidates = byType.get(type);
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        if (qualifier != null) {
            List<BeanDescriptor<?>> qualifiedCandidates = candidates.stream()
                    .filter(candidate -> candidate.qualifiers().contains(qualifier))
                    .toList();
            if (qualifiedCandidates.size() == 1) {
                return (BeanDescriptor<T>) qualifiedCandidates.get(0);
            }
            if (qualifiedCandidates.size() > 1) {
                throw new IllegalStateException("Multiple beans registered for " + type.getName()
                        + " with qualifier " + qualifier.getName());
            }
            return null;
        }

        if (candidates.size() == 1) {
            return (BeanDescriptor<T>) candidates.get(0);
        }

        List<BeanDescriptor<?>> unqualifiedCandidates = candidates.stream()
                .filter(candidate -> candidate.qualifiers().isEmpty())
                .toList();
        if (unqualifiedCandidates.size() == 1) {
            return (BeanDescriptor<T>) unqualifiedCandidates.get(0);
        }

        for (BeanDescriptor<?> candidate : candidates) {
            if (candidate.beanClass().equals(type)) {
                return (BeanDescriptor<T>) candidate;
            }
        }

        throw new IllegalStateException("Multiple beans registered for " + type.getName()
                + "; use a qualifier to disambiguate");
    }

    private static Set<Class<?>> beanTypes(Class<?> beanClass) {
        Set<Class<?>> types = new LinkedHashSet<>();
        Class<?> current = beanClass;
        while (current != null && current != Object.class) {
            types.add(current);
            for (Class<?> iface : current.getInterfaces()) {
                types.add(iface);
                collectParentInterfaces(iface, types);
            }
            current = current.getSuperclass();
        }
        return types;
    }

    private static void collectParentInterfaces(Class<?> iface, Set<Class<?>> types) {
        for (Class<?> parent : iface.getInterfaces()) {
            if (types.add(parent)) {
                collectParentInterfaces(parent, types);
            }
        }
    }

    private static <T> Constructor<T> selectConstructor(Class<T> beanType) {
        @SuppressWarnings("unchecked")
        Constructor<T>[] constructors = (Constructor<T>[]) beanType.getDeclaredConstructors();
        if (constructors.length == 0) {
            throw new IllegalStateException("No constructor declared for " + beanType.getName());
        }

        List<Constructor<T>> injectConstructors = new ArrayList<>();
        for (Constructor<T> constructor : constructors) {
            if (constructor.isAnnotationPresent(Inject.class)) {
                injectConstructors.add(constructor);
            }
        }
        if (injectConstructors.size() > 1) {
            throw new IllegalStateException("Only one constructor can be annotated with @Inject on " + beanType.getName());
        }
        if (injectConstructors.size() == 1) {
            return injectConstructors.get(0);
        }
        if (constructors.length == 1) {
            return constructors[0];
        }

        try {
            return beanType.getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Bean must declare exactly one constructor or mark one with @Inject: "
                    + beanType.getName(), e);
        }
    }

    private static Method findLifecycleMethod(Class<?> beanType, Class<? extends Annotation> annotationType) {
        Method found = null;
        for (Method method : beanType.getMethods()) {
            if (!method.isAnnotationPresent(annotationType)) {
                continue;
            }
            if (found != null) {
                throw new IllegalStateException("Only one method can be annotated with @"
                        + annotationType.getSimpleName() + " on " + beanType.getName());
            }
            if (method.getParameterCount() != 0 || method.getReturnType() != void.class) {
                throw new IllegalStateException("@" + annotationType.getSimpleName()
                        + " method must have signature void methodName() on " + beanType.getName());
            }
            found = method;
        }
        return found;
    }

    private static Set<Class<?>> findQualifiers(Class<?> beanType) {
        Set<Class<?>> qualifiers = new LinkedHashSet<>();
        for (Annotation annotation : beanType.getAnnotations()) {
            if (annotation.annotationType().isAnnotationPresent(Qualifier.class)) {
                qualifiers.add(annotation.annotationType());
            }
        }
        return qualifiers;
    }

    private static Class<? extends Annotation> findQualifier(Parameter parameter) {
        for (Annotation annotation : parameter.getAnnotations()) {
            if (annotation.annotationType().isAnnotationPresent(Qualifier.class)) {
                return annotation.annotationType();
            }
        }
        return null;
    }

    private static <T> T create(Class<T> type, Constructor<T> constructor, ArcContainer container) {
        try {
            constructor.setAccessible(true);
            if (constructor.getParameterCount() == 0) {
                return constructor.newInstance();
            }

            Object[] arguments = new Object[constructor.getParameterCount()];
            Parameter[] parameters = constructor.getParameters();
            for (int i = 0; i < parameters.length; i++) {
                Class<? extends Annotation> qualifier = findQualifier(parameters[i]);
                arguments[i] = qualifier == null
                        ? container.instance(parameters[i].getType()).get()
                        : container.instance(parameters[i].getType(), qualifier).get();
            }
            return constructor.newInstance(arguments);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to instantiate bean " + type.getName(), e);
        }
    }

    private static void invokePreDestroy(Object instance, BeanDescriptor<?> descriptor) {
        if (descriptor.preDestroyMethod() == null) {
            return;
        }
        try {
            Method method = instance.getClass().getMethod(descriptor.preDestroyMethod());
            method.setAccessible(true);
            method.invoke(instance);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to invoke @PreDestroy method", e);
        }
    }

    public void destroyAll() {
        singletonContext.destroy();
        applicationContext.destroy();
        requestContext.destroy();
        dependentContext.destroy();
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

    private static final class SimpleInstanceHandle<T> implements InstanceHandle<T> {

        private final T instance;
        private final BeanDescriptor<T> descriptor;

        private SimpleInstanceHandle(T instance, BeanDescriptor<T> descriptor) {
            this.instance = instance;
            this.descriptor = descriptor;
        }

        @Override
        public T get() {
            return instance;
        }

        @Override
        public void close() {
            if (descriptor.scope() == Scope.DEPENDENT) {
                invokePreDestroy(instance, descriptor);
            }
        }
    }
}
