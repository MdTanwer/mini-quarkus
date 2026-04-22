package com.tanwir.arc.deployment;

import com.tanwir.core.deployment.MultiBuildItem;

import java.util.function.Supplier;

/**
 * Registers a pre-created (synthetic) object as a CDI bean.
 *
 * <p>Mirrors {@code io.quarkus.arc.deployment.SyntheticBeanBuildItem} from real Quarkus.
 * Unlike {@link BeanBuildItem} (which refers to a class to be instantiated by ARC),
 * a synthetic bean carries a {@link Supplier} that produces the instance directly.
 *
 * <p>Use this to make framework-managed objects (e.g. the Vert.x EventBus, data sources,
 * HTTP clients) injectable into application beans without requiring them to carry a
 * scope annotation.
 *
 * <pre>{@code
 * @BuildStep
 * @Record(ExecutionTime.STATIC_INIT)
 * public SyntheticBeanBuildItem exposeEventBus(MutinyRecorder recorder,
 *                                              EventBusBuildItem eventBus) {
 *     return SyntheticBeanBuildItem.configure(MiniEventBus.class)
 *             .supplier(() -> eventBus.eventBus())
 *             .build();
 * }
 * }</pre>
 */
public final class SyntheticBeanBuildItem extends MultiBuildItem {

    private final Class<?> beanType;
    private final Supplier<?> supplier;

    private SyntheticBeanBuildItem(Class<?> beanType, Supplier<?> supplier) {
        this.beanType = beanType;
        this.supplier = supplier;
    }

    public Class<?> beanType() {
        return beanType;
    }

    @SuppressWarnings("unchecked")
    public <T> Supplier<T> supplier() {
        return (Supplier<T>) supplier;
    }

    /** Entry point for the builder. */
    public static Builder configure(Class<?> beanType) {
        return new Builder(beanType);
    }

    public static final class Builder {
        private final Class<?> beanType;
        private Supplier<?> supplier;

        private Builder(Class<?> beanType) {
            this.beanType = beanType;
        }

        public Builder supplier(Supplier<?> supplier) {
            this.supplier = supplier;
            return this;
        }

        public SyntheticBeanBuildItem build() {
            return new SyntheticBeanBuildItem(beanType, supplier);
        }
    }
}
