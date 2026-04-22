package com.tanwir.core.deployment;

/**
 * Wraps a value that is produced during the deployment phase and consumed at runtime.
 *
 * <p>Mirrors {@code io.quarkus.runtime.RuntimeValue} from real Quarkus. In the real
 * framework, a {@code RuntimeValue<T>} is a reference to an object that will exist at
 * runtime — it is created by a {@link Recorder} method and stored as bytecode so it can
 * be referenced by later recorded calls.
 *
 * <p>In mini-quarkus the object exists immediately (no bytecode deferral), but wrapping
 * it in {@code RuntimeValue} makes the intent explicit: "this object was set up during
 * the deployment phase and is now available for the runtime phase."
 *
 * <pre>{@code
 * @Recorder
 * public class ArcRecorder {
 *     public RuntimeValue<ArcContainer> initContainer(List<String> beanClasses) {
 *         ArcContainer container = Arc.initialize();
 *         return new RuntimeValue<>(container);  // available at runtime
 *     }
 * }
 *
 * @BuildStep
 * @Record(ExecutionTime.STATIC_INIT)
 * public BeanContainerBuildItem build(ArcRecorder recorder, List<BeanBuildItem> beans) {
 *     RuntimeValue<ArcContainer> containerValue = recorder.initContainer(...);
 *     return new BeanContainerBuildItem(containerValue);
 * }
 * }</pre>
 *
 * @param <T> the type of the wrapped runtime value
 */
public final class RuntimeValue<T> {

    private final T value;

    public RuntimeValue(T value) {
        this.value = value;
    }

    /**
     * Returns the wrapped runtime object.
     * In mini-quarkus this is always available immediately.
     * In real Quarkus this would only be valid after bytecode replay.
     */
    public T getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "RuntimeValue[" + value + "]";
    }
}
