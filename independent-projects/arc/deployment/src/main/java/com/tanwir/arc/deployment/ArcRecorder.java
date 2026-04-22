package com.tanwir.arc.deployment;

import com.tanwir.arc.Arc;
import com.tanwir.arc.ArcContainer;
import com.tanwir.arc.BeanDescriptor;
import com.tanwir.arc.Scope;
import com.tanwir.core.deployment.Recorder;
import com.tanwir.core.deployment.RuntimeValue;

import org.jboss.logging.Logger;

import java.util.List;
import java.util.Set;

/**
 * Recorder that initializes the ARC CDI container at deployment time.
 *
 * <p>Mirrors {@code io.quarkus.arc.runtime.ArcRecorder} from real Quarkus. In the real
 * framework, calls to recorder methods are encoded as bytecode at build time and
 * replayed at startup. In mini-quarkus, the methods run directly during the
 * deployment phase that precedes the HTTP server start.
 *
 * <p>The recorder is injected into {@link ArcDeploymentProcessor}'s {@code @BuildStep}
 * methods that carry {@code @Record} — the mini-quarkus analogue of:
 * <pre>{@code
 * // Real Quarkus
 * @BuildStep
 * @Record(ExecutionTime.STATIC_INIT)
 * public BeanContainerBuildItem build(ArcRecorder recorder, ...) { ... }
 * }</pre>
 */
@Recorder
public class ArcRecorder {

    private static final Logger LOG = Logger.getLogger(ArcRecorder.class);

    /**
     * Initializes the ARC container.
     *
     * <p>In real Quarkus, the generated bean registrar classes (produced by
     * {@code ArcBeanProcessor}) are discovered via {@link java.util.ServiceLoader} at
     * this point. In mini-quarkus the same ServiceLoader mechanism is used — the
     * annotation processor already generated and registered those classes.
     *
     * @param syntheticBeans additional framework-managed beans to register
     * @return a {@link RuntimeValue} wrapping the initialized container
     */
    public RuntimeValue<ArcContainer> initContainer(List<SyntheticBeanBuildItem> syntheticBeans) {
        LOG.info("[deployment] ArcRecorder: initializing ARC container");
        ArcContainer container = Arc.initialize();

        // Register synthetic beans (framework objects like MiniEventBus, Vertx, etc.)
        for (SyntheticBeanBuildItem synthetic : syntheticBeans) {
            registerSynthetic(container, synthetic);
        }

        LOG.infof("[deployment] ARC container ready with %d synthetic bean(s)", syntheticBeans.size());
        return new RuntimeValue<>(container);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void registerSynthetic(ArcContainer container, SyntheticBeanBuildItem synthetic) {
        Class beanType = synthetic.beanType();
        container.registerBean(new BeanDescriptor<>(
                beanType, Scope.SINGLETON, c -> synthetic.supplier().get(), null, null, Set.of()));
        LOG.debugf("[deployment] Registered synthetic bean: %s", beanType.getName());
    }
}
