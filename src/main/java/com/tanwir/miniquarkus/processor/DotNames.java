package com.tanwir.miniquarkus.processor;

import java.io.Serializable;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.CompletionStage;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.context.NormalScope;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Decorated;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Intercepted;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.Specializes;
import jakarta.enterprise.inject.Stereotype;
import jakarta.enterprise.inject.TransientReference;
import jakarta.enterprise.inject.Typed;
import jakarta.enterprise.inject.Vetoed;
import jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanContainer;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.EventMetadata;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.util.Nonbinding;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Provider;
import jakarta.inject.Qualifier;

import org.jboss.jandex.DotName;

/**
 * DotNames utility class following Quarkus ARC patterns.
 * Contains commonly used DotName constants for CDI annotations and classes.
 */
public class DotNames {

    // Core CDI annotations
    public static final DotName NAMED = create(Named.class);
    public static final DotName INJECT = create(Inject.class);
    public static final DotName ANY = create(Any.class);
    public static final DotName DEFAULT = create(Default.class);
    public static final DotName ALTERNATIVE = create(Alternative.class);
    public static final DotName PRIORITY = create(Priority.class);
    public static final DotName QUALIFIER = create(Qualifier.class);
    public static final DotName STEREOTYPE = create(Stereotype.class);
    public static final DotName SPECIALIZES = create(Specializes.class);
    public static final DotName PRODUCES = create(Produces.class);
    public static final DotName DISPOSES = create(Disposes.class);
    public static final DotName DECORATED = create(Decorated.class);
    public static final DotName INTERCEPTED = create(Intercepted.class);
    public static final DotName TYPED = create(Typed.class);
    public static final DotName TRANSIENT_REFERENCE = create(TransientReference.class);
    public static final DotName VETOED = create(Vetoed.class);
    public static final DotName INSTANCE = create(Instance.class);
    public static final DotName PROVIDER = create(Provider.class);
    public static final DotName DELEGATE = create(Delegate.class);
    public static final DotName DECORATOR = create(Decorator.class);

    // Lifecycle annotations
    public static final DotName POST_CONSTRUCT = create(PostConstruct.class);
    public static final DotName PRE_DESTROY = create(PreDestroy.class);

    // Context annotations
    public static final DotName APPLICATION_SCOPED = create(ApplicationScoped.class);
    public static final DotName NORMAL_SCOPED = create(NormalScope.class);
    public static final DotName INITIALIZED = create(Initialized.class);
    public static final DotName ACTIVATE_REQUEST_CONTEXT = create(ActivateRequestContext.class);

    // Event annotations
    public static final DotName OBSERVES = create(Observes.class);
    public static final DotName OBSERVES_ASYNC = create(ObservesAsync.class);
    public static final DotName TRANSACTION_PHASE = create(TransactionPhase.class);
    public static final DotName EVENT = create(Event.class);

    // SPI interfaces
    public static final DotName BEAN = create(Bean.class);
    public static final DotName BEAN_MANAGER = create(BeanManager.class);
    public static final DotName BEAN_CONTAINER = create(BeanContainer.class);
    public static final DotName EXTENSION = create(Extension.class);
    public static final DotName INJECTION_POINT = create(InjectionPoint.class);
    public static final DotName EVENT_METADATA = create(EventMetadata.class);
    public static final DotName NONBINDING = create(Nonbinding.class);
    public static final DotName BUILD_COMPATIBLE_EXTENSION = create(BuildCompatibleExtension.class);

    // Java core classes
    public static final DotName OBJECT = create(Object.class);
    public static final DotName STRING = create(String.class);
    public static final DotName CLASS = create(Class.class);
    public static final DotName SERIALIZABLE = create(Serializable.class);
    public static final DotName INHERITED = create(Inherited.class);
    public static final DotName REPEATABLE = create(Repeatable.class);

    // Optional types
    public static final DotName OPTIONAL = create(Optional.class);
    public static final DotName OPTIONAL_INT = create(OptionalInt.class);
    public static final DotName OPTIONAL_LONG = create(OptionalLong.class);
    public static final DotName OPTIONAL_DOUBLE = create(OptionalDouble.class);

    // Concurrent types
    public static final DotName COMPLETION_STAGE = create(CompletionStage.class);

    // Reflection classes
    public static final DotName FIELD = create(java.lang.reflect.Field.class);
    public static final DotName METHOD = create(java.lang.reflect.Method.class);
    public static final DotName CONSTRUCTOR = create(java.lang.reflect.Constructor.class);
    public static final DotName MEMBER = create(java.lang.reflect.Member.class);
    public static final DotName MODIFIER = create(java.lang.reflect.Modifier.class);

    // Exception classes
    public static final DotName EXCEPTION = create(Exception.class);
    public static final DotName RUNTIME_EXCEPTION = create(RuntimeException.class);
    public static final DotName THROWABLE = create(Throwable.class);

    // Collection classes
    public static final DotName LIST = create(List.class);
    public static final DotName SET = create(java.util.Set.class);
    public static final DotName MAP = create(java.util.Map.class);

    // Function classes
    public static final DotName SUPPLIER = create(java.util.function.Supplier.class);
    public static final DotName FUNCTION = create(java.util.function.Function.class);
    public static final DotName CONSUMER = create(java.util.function.Consumer.class);
    public static final DotName BIFUNCTION = create(java.util.function.BiFunction.class);
    public static final DotName PREDICATE = create(java.util.function.Predicate.class);

    // Utility methods
    public static DotName create(Class<?> clazz) {
        return DotName.createSimple(clazz.getName());
    }

    public static DotName createSimple(String name) {
        return DotName.createSimple(name);
    }

    public static String packagePrefix(DotName name) {
        String className = name.toString();
        int lastDot = className.lastIndexOf('.');
        return lastDot > 0 ? className.substring(0, lastDot) : "";
    }

    public static String simpleName(DotName name) {
        String className = name.toString();
        int lastDot = className.lastIndexOf('.');
        return lastDot > 0 ? className.substring(lastDot + 1) : className;
    }

    // Prevent instantiation
    private DotNames() {
        throw new UnsupportedOperationException("Utility class");
    }
}
