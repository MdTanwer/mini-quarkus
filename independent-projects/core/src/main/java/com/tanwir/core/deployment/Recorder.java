package com.tanwir.core.deployment;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a runtime recorder.
 *
 * <p>Mirrors {@code io.quarkus.deployment.annotations.Recorder} from real Quarkus.
 * A recorder class bridges the deployment phase and the runtime: its methods are
 * called during the deployment phase to configure how the runtime should behave,
 * and the results are wrapped in {@link RuntimeValue}s to be used by later build steps.
 *
 * <h2>Real Quarkus behaviour</h2>
 * In the real framework, recorder method calls are <em>recorded as bytecode</em> at
 * build time and then <em>replayed</em> at startup. This is how Quarkus achieves
 * near-zero startup work — everything complex happens at build time.
 *
 * <p>In mini-quarkus we simplify: recorder methods are called directly during the
 * deployment phase that runs at the very start of the JVM. The result is the same:
 * the runtime is configured before any request is processed.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @Recorder
 * public class ArcRecorder {
 *
 *     public RuntimeValue<ArcContainer> initContainer(List<String> beanClasses) {
 *         ArcContainer container = Arc.initialize();
 *         return new RuntimeValue<>(container);
 *     }
 * }
 * }</pre>
 *
 * A {@link BuildStep} method receives a recorder instance by declaring it as a parameter:
 * <pre>{@code
 * @BuildStep
 * @Record(ExecutionTime.STATIC_INIT)
 * public BeanContainerBuildItem build(ArcRecorder recorder, ...) { ... }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Recorder {
}
