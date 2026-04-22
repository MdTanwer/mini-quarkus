package com.tanwir.core.deployment;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Combined with {@link BuildStep} to indicate that a recorder method should be invoked.
 *
 * <p>Mirrors {@code io.quarkus.deployment.annotations.Record} from real Quarkus.
 * When a {@link BuildStep} method is annotated with {@code @Record}, the
 * {@link MiniExtensionManager} knows to inject a {@link Recorder}-annotated instance
 * as one of the method's parameters and to invoke the recording at the specified
 * {@link ExecutionTime}.
 *
 * <pre>{@code
 * @BuildStep
 * @Record(ExecutionTime.STATIC_INIT)
 * public BeanContainerBuildItem setupArc(
 *         ArcRecorder recorder,
 *         List<BeanBuildItem> discoveredBeans) {
 *     RuntimeValue<ArcContainer> container = recorder.initContainer(
 *         discoveredBeans.stream().map(BeanBuildItem::beanClass).toList());
 *     return new BeanContainerBuildItem(container);
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Record {

    /** When the recorded initialization should run. Defaults to {@link ExecutionTime#STATIC_INIT}. */
    ExecutionTime value() default ExecutionTime.STATIC_INIT;
}
