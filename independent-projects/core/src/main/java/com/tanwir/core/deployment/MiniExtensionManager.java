package com.tanwir.core.deployment;

import org.jboss.logging.Logger;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Orchestrates the deployment (build-time augmentation) phase for mini-quarkus extensions.
 *
 * <p>This class mirrors the role of {@code io.quarkus.deployment.QuarkusAugmentor} in the real
 * Quarkus framework. It is responsible for:
 * <ol>
 *   <li>Discovering all {@link ExtensionProcessor} implementations via {@link ServiceLoader}</li>
 *   <li>Collecting all {@link BuildStep}-annotated methods from every processor</li>
 *   <li>Executing build steps in dependency order — a step runs once all of its
 *       {@link SimpleBuildItem} dependencies are satisfied</li>
 *   <li>Wiring {@link BuildProducer} parameters so steps can emit {@link MultiBuildItem}s</li>
 *   <li>Instantiating and injecting {@link Recorder}-annotated classes into steps that
 *       carry {@link Record} — exactly how Quarkus recorders bridge deployment and runtime</li>
 * </ol>
 *
 * <h2>How dependency ordering works</h2>
 * <pre>
 *  Step A (no deps)       ──┐
 *  Step B (no deps)       ──┼──► produces BeanBuildItem list
 *  Step C (needs List<BeanBuildItem>) ──► produces BeanContainerBuildItem
 *  Step D (needs BeanContainerBuildItem) ──► registers routes with the container
 * </pre>
 * The manager runs pass-until-stable: in each pass it executes every step whose
 * required {@link SimpleBuildItem}s are already present. This avoids requiring an
 * explicit declaration of inter-step dependencies (real Quarkus infers them automatically
 * from parameter types).
 *
 * <h2>Real Quarkus comparison</h2>
 * <table>
 *   <tr><th>mini-quarkus</th><th>real Quarkus</th></tr>
 *   <tr><td>{@code MiniExtensionManager}</td><td>{@code QuarkusAugmentor}</td></tr>
 *   <tr><td>{@code ExtensionProcessor}</td><td>Quarkus extension processor class (no interface)</td></tr>
 *   <tr><td>ServiceLoader discovery</td><td>Class path scanning + {@code quarkus-extension.yaml}</td></tr>
 *   <tr><td>Runs at JVM startup</td><td>Runs during Maven build</td></tr>
 * </table>
 */
public final class MiniExtensionManager {

    private static final Logger LOG = Logger.getLogger(MiniExtensionManager.class);

    private final BuildContext buildContext = new BuildContext();
    /** Cache of instantiated @Recorder objects — one instance per recorder class. */
    private final Map<Class<?>, Object> recorders = new HashMap<>();

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Factory method for tests — runs build steps using the given processors directly
     * instead of discovering them via {@link ServiceLoader}.
     */
    public static MiniExtensionManager forProcessors(ExtensionProcessor... processors) {
        MiniExtensionManager manager = new MiniExtensionManager();
        List<Object> list = new ArrayList<>(List.of(processors));
        manager.runBuildStepsOn(list);
        return manager;
    }

    /**
     * Runs the full deployment phase.
     *
     * <p>Discovers all {@link ExtensionProcessor} implementations via {@link ServiceLoader},
     * collects their {@link BuildStep} methods, and executes them in dependency-satisfying
     * order. Returns {@code this} for fluent chaining.
     */
    public MiniExtensionManager runBuildSteps() {
        List<Object> processors = discoverProcessors();
        LOG.infof("[deployment] Discovered %d extension processor(s)", processors.size());

        runBuildStepsOn(processors);
        return this;
    }

    private void runBuildStepsOn(List<Object> processors) {
        List<BuildStepMethod> allSteps = collectBuildSteps(processors);
        LOG.infof("[deployment] Found %d @BuildStep method(s) across all processors", allSteps.size());

        executeBuildSteps(allSteps);

        List<String> features = buildContext.getMultiItems(FeatureBuildItem.class)
                .stream().map(FeatureBuildItem::name).toList();
        LOG.infof("[deployment] Deployment complete. Active features: %s", features);
    }

    /**
     * Returns all produced {@link MultiBuildItem}s of the given type.
     * Available after {@link #runBuildSteps()}.
     */
    public <T extends MultiBuildItem> List<T> getMultiItems(Class<T> type) {
        return buildContext.getMultiItems(type);
    }

    /**
     * Returns the single {@link SimpleBuildItem} of the given type.
     * Returns {@code null} if no step produced it.
     */
    public <T extends SimpleBuildItem> T getSimpleItem(Class<T> type) {
        return buildContext.getSimpleItem(type);
    }

    // -------------------------------------------------------------------------
    // Processor discovery
    // -------------------------------------------------------------------------

    private List<Object> discoverProcessors() {
        List<Object> result = new ArrayList<>();
        for (ExtensionProcessor processor : ServiceLoader.load(ExtensionProcessor.class)) {
            result.add(processor);
            LOG.debugf("[deployment] Loaded processor: %s", processor.getClass().getName());
        }
        return result;
    }

    private List<BuildStepMethod> collectBuildSteps(List<Object> processors) {
        List<BuildStepMethod> steps = new ArrayList<>();
        for (Object processor : processors) {
            for (Method method : processor.getClass().getMethods()) {
                if (method.isAnnotationPresent(BuildStep.class)) {
                    steps.add(new BuildStepMethod(processor, method));
                    LOG.debugf("[deployment] Registered @BuildStep: %s#%s",
                            processor.getClass().getSimpleName(), method.getName());
                }
            }
        }
        return steps;
    }

    // -------------------------------------------------------------------------
    // Build step execution — topological sort based on declared types
    // -------------------------------------------------------------------------

    /**
     * Executes build steps in topological order derived from their parameter and return types.
     *
     * <h3>Dependency rules</h3>
     * <ul>
     *   <li>A step <em>produces</em> type T when it: returns T (extends BuildItem) OR has
     *       a {@link BuildProducer}{@code <T>} parameter</li>
     *   <li>A step <em>requires</em> type T when it has a {@code T extends SimpleBuildItem}
     *       parameter OR a {@code List<T extends MultiBuildItem>} parameter</li>
     *   <li>If B requires T and A produces T → B depends on A (B must run after A)</li>
     * </ul>
     *
     * <p>This mirrors the type-based dependency inference done by
     * {@code io.quarkus.deployment.QuarkusAugmentor} in the real framework.
     */
    private void executeBuildSteps(List<BuildStepMethod> allSteps) {
        // Build adjacency: step index → set of step indexes it depends on
        int n = allSteps.size();
        Map<Integer, Set<Integer>> deps = new HashMap<>();
        for (int i = 0; i < n; i++) deps.put(i, new HashSet<>());

        // For each step compute what it produces (by type)
        Map<Class<?>, List<Integer>> producedBy = new HashMap<>(); // type → list of producer step indexes
        for (int i = 0; i < n; i++) {
            for (Class<?> t : producedTypes(allSteps.get(i))) {
                producedBy.computeIfAbsent(t, k -> new ArrayList<>()).add(i);
            }
        }

        // For each step that requires a type, add dependency edges
        for (int i = 0; i < n; i++) {
            for (Class<?> req : requiredTypes(allSteps.get(i))) {
                List<Integer> producers = producedBy.getOrDefault(req, List.of());
                deps.get(i).addAll(producers);
            }
        }

        // Kahn's algorithm — topological sort
        int[] inDegree = new int[n];
        for (int i = 0; i < n; i++) {
            for (int dep : deps.get(i)) {
                if (dep != i) inDegree[i]++;  // count how many steps must precede i
            }
        }
        // Compute: for each producer j, increment in-degree of every consumer i that depends on j
        inDegree = new int[n]; // reset
        for (int i = 0; i < n; i++) {
            for (int dep : deps.get(i)) {
                // dep must run before i → i's inDegree is incremented per dependency
                inDegree[i]++;
            }
        }

        // Queue all steps with no dependencies
        Queue<Integer> ready = new LinkedList<>();
        for (int i = 0; i < n; i++) {
            if (inDegree[i] == 0) ready.add(i);
        }

        int executed = 0;
        while (!ready.isEmpty()) {
            int idx = ready.poll();
            executeStep(allSteps.get(idx));
            executed++;
            // Reduce in-degree of all steps that depend on idx
            for (int i = 0; i < n; i++) {
                if (deps.get(i).contains(idx)) {
                    inDegree[i]--;
                    if (inDegree[i] == 0) ready.add(i);
                }
            }
        }

        if (executed < n) {
            LOG.warnf("[deployment] %d of %d build step(s) could not run "
                    + "(cycle or unsatisfied dependency).", n - executed, n);
        }
    }

    /** Returns the {@link BuildItem} types that a step produces (return type + BuildProducer params). */
    private Set<Class<?>> producedTypes(BuildStepMethod step) {
        Set<Class<?>> types = new HashSet<>();
        Class<?> ret = step.method.getReturnType();
        if (ret != null && BuildItem.class.isAssignableFrom(ret) && ret != BuildItem.class) {
            types.add(ret);
        }
        for (Parameter param : step.method.getParameters()) {
            if (BuildProducer.class.isAssignableFrom(param.getType())) {
                Class<?> t = extractGenericArg(param.getParameterizedType());
                if (t != null) types.add(t);
            }
        }
        return types;
    }

    /** Returns the {@link BuildItem} types that a step requires (SimpleBuildItem + List<MultiBuildItem>). */
    private Set<Class<?>> requiredTypes(BuildStepMethod step) {
        Set<Class<?>> types = new HashSet<>();
        for (Parameter param : step.method.getParameters()) {
            Class<?> type = param.getType();
            if (SimpleBuildItem.class.isAssignableFrom(type)) {
                types.add(type);
            } else if (List.class.isAssignableFrom(type)) {
                Class<?> elem = extractGenericArg(param.getParameterizedType());
                if (elem != null && MultiBuildItem.class.isAssignableFrom(elem)) {
                    types.add(elem);
                }
            }
        }
        return types;
    }

    private void executeStep(BuildStepMethod step) {
        LOG.infof("[deployment] Running @BuildStep %s#%s",
                step.processor.getClass().getSimpleName(), step.method.getName());
        try {
            Object[] args = resolveParameters(step);
            Object result = step.method.invoke(step.processor, args);
            if (result instanceof BuildItem item) {
                buildContext.produce(item);
            }
        } catch (Exception e) {
            throw new RuntimeException("[deployment] @BuildStep " + step.method.getName() + " failed", e);
        }
    }

    // -------------------------------------------------------------------------
    // Parameter resolution — the heart of the injection mechanism
    // -------------------------------------------------------------------------

    private Object[] resolveParameters(BuildStepMethod step) throws ReflectiveOperationException {
        Parameter[] params = step.method.getParameters();
        Object[] args = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            args[i] = resolveParameter(params[i]);
        }
        return args;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object resolveParameter(Parameter param) {
        Class<?> type = param.getType();

        // BuildProducer<T> — inject a producer that adds to the build context
        if (BuildProducer.class.isAssignableFrom(type)) {
            Class<? extends BuildItem> itemType = extractGenericArg(param.getParameterizedType());
            return (BuildProducer) item -> buildContext.produce(item);
        }

        // List<T extends MultiBuildItem> — inject all produced items of that type
        if (List.class.isAssignableFrom(type)) {
            Class<?> elementType = extractGenericArg(param.getParameterizedType());
            if (elementType != null && MultiBuildItem.class.isAssignableFrom(elementType)) {
                return buildContext.getMultiItems(elementType.asSubclass(MultiBuildItem.class));
            }
            return new ArrayList<>();
        }

        // SimpleBuildItem subtype — inject the single produced item (guaranteed by canExecute)
        if (SimpleBuildItem.class.isAssignableFrom(type)) {
            return buildContext.getSimpleItem(type.asSubclass(SimpleBuildItem.class));
        }

        // @Recorder-annotated class — instantiate once and cache (same instance per recorder type)
        if (type.isAnnotationPresent(Recorder.class)) {
            return recorders.computeIfAbsent(type, t -> {
                try {
                    return t.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    throw new RuntimeException("Cannot instantiate @Recorder " + t.getName(), e);
                }
            });
        }

        throw new IllegalArgumentException(
                "Cannot inject @BuildStep parameter of type " + type.getName()
                + ". Supported: BuildProducer<T>, List<T extends MultiBuildItem>,"
                + " T extends SimpleBuildItem, @Recorder class.");
    }

    /** Extracts the first type argument from a generic type (e.g. {@code List<T>} → {@code T}). */
    @SuppressWarnings("unchecked")
    private static <T> Class<T> extractGenericArg(Type genericType) {
        if (genericType instanceof ParameterizedType pt) {
            Type[] args = pt.getActualTypeArguments();
            if (args.length > 0 && args[0] instanceof Class<?> cls) {
                return (Class<T>) cls;
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Inner types
    // -------------------------------------------------------------------------

    private record BuildStepMethod(Object processor, Method method) {}

    // -------------------------------------------------------------------------
    // BuildContext — central store for all produced BuildItems
    // -------------------------------------------------------------------------

    /**
     * Central store for all {@link BuildItem}s produced during the deployment phase.
     *
     * <p>Mirrors the internal build context used by {@code QuarkusAugmentor}. Each
     * {@link SimpleBuildItem} type may have at most one instance; each
     * {@link MultiBuildItem} type accumulates a list.
     */
    public static final class BuildContext {

        private final Map<Class<?>, SimpleBuildItem> simpleItems = new HashMap<>();
        private final Map<Class<?>, List<MultiBuildItem>> multiItems = new HashMap<>();

        @SuppressWarnings("unchecked")
        public <T extends BuildItem> void produce(T item) {
            if (item instanceof SimpleBuildItem simple) {
                Class<?> type = simple.getClass();
                if (simpleItems.containsKey(type)) {
                    throw new IllegalStateException(
                            "SimpleBuildItem of type " + type.getName() + " was produced more than once."
                            + " Only one instance is allowed.");
                }
                simpleItems.put(type, simple);
            } else if (item instanceof MultiBuildItem multi) {
                multiItems.computeIfAbsent(multi.getClass(), k -> new ArrayList<>()).add(multi);
            }
        }

        @SuppressWarnings("unchecked")
        public <T extends SimpleBuildItem> T getSimpleItem(Class<T> type) {
            return (T) simpleItems.get(type);
        }

        @SuppressWarnings("unchecked")
        public <T extends MultiBuildItem> List<T> getMultiItems(Class<T> type) {
            List<MultiBuildItem> items = multiItems.get(type);
            if (items == null) return new ArrayList<>();
            return (List<T>) new ArrayList<>(items);
        }
    }

    // -------------------------------------------------------------------------
    // FeatureBuildItem — built into core so all modules can produce it
    // -------------------------------------------------------------------------

    /**
     * Signals that an extension is active.
     *
     * <p>Mirrors {@code io.quarkus.deployment.builditem.FeatureBuildItem}. Every
     * extension deployment processor should produce one of these to announce itself.
     * The list of active features is logged at the end of the deployment phase.
     */
    public static final class FeatureBuildItem extends MultiBuildItem {

        private final String name;

        public FeatureBuildItem(String name) {
            this.name = name;
        }

        public String name() {
            return name;
        }
    }
}
