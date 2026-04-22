package com.tanwir.core.deployment;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method in an {@link ExtensionProcessor} as a build step.
 *
 * <p>Mirrors {@code io.quarkus.deployment.annotations.BuildStep} from real Quarkus.
 * A build step is a unit of work that runs during the <em>deployment phase</em>
 * (build-time augmentation in real Quarkus; early-startup in mini-quarkus).
 *
 * <h2>Method signature rules</h2>
 * A {@code @BuildStep} method may declare:
 * <ul>
 *   <li><b>Return type</b>: {@link BuildItem} or {@code void} — the return value is
 *       automatically produced into the build context</li>
 *   <li><b>{@link BuildProducer}{@code <T>} parameter</b>: injected to produce multiple
 *       items of type {@code T}</li>
 *   <li><b>{@code T extends SimpleBuildItem} parameter</b>: the single item of type
 *       {@code T} produced by an earlier step (the step is deferred until it is ready)</li>
 *   <li><b>{@code List<T extends MultiBuildItem>} parameter</b>: all items of type
 *       {@code T} produced so far (may be empty)</li>
 *   <li><b>{@link RuntimeValue}{@code <T>} parameter</b>: a value made available by a
 *       {@link Recorder}-annotated class</li>
 * </ul>
 *
 * <h2>Real Quarkus example</h2>
 * <pre>{@code
 * // In arc-deployment
 * @BuildStep
 * @Record(ExecutionTime.STATIC_INIT)
 * public BeanContainerBuildItem build(
 *         ArcRecorder recorder,
 *         List<BeanDefiningAnnotationBuildItem> beanDefiningAnnotations,
 *         BuildProducer<FeatureBuildItem> feature) {
 *     feature.produce(new FeatureBuildItem(Feature.CDI));
 *     RuntimeValue<ArcContainer> container = recorder.initContainer(...);
 *     return new BeanContainerBuildItem(container);
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface BuildStep {
}
