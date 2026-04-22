package com.tanwir.mutiny.deployment;

import com.tanwir.core.deployment.Recorder;

/**
 * Recorder for the Mutiny extension.
 *
 * <p>In real Quarkus, {@code @Recorder} classes record initialization code at build time
 * that is replayed at startup. In the current mini-quarkus Mutiny implementation, the
 * EventBus setup is done directly in the {@link MutinyDeploymentProcessor} build step
 * using a lazy supplier — no recorded bytecode is needed.
 *
 * <p>This class is retained as a placeholder that illustrates the recorder pattern.
 * In a full mini-quarkus implementation it would record Mutiny thread pool configuration,
 * uncaught exception handler setup, and EventBus consumer registrations.
 */
@Recorder
public class MutinyRecorder {
    // Intentionally empty — Mutiny deployment setup is done via lazy SyntheticBeanBuildItem supplier.
}
