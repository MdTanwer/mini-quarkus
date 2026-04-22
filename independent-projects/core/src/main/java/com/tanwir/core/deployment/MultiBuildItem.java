package com.tanwir.core.deployment;

/**
 * A {@link BuildItem} of which any number of instances may be produced per build.
 *
 * <p>Mirrors {@code io.quarkus.builder.item.MultiBuildItem}. Use for build items that
 * represent a collection of results — for example, "a discovered CDI bean" or
 * "a REST route to register". Multiple extensions (build steps) can each contribute
 * instances of the same {@code MultiBuildItem} type.
 *
 * <p>A {@link BuildStep} that consumes {@code MultiBuildItem}s declares a
 * {@link java.util.List} parameter:
 *
 * <pre>{@code
 * @BuildStep
 * public void registerBeans(List<BeanBuildItem> beans, ...) { ... }
 * }</pre>
 */
public abstract class MultiBuildItem extends BuildItem {

    protected MultiBuildItem() {
    }
}
