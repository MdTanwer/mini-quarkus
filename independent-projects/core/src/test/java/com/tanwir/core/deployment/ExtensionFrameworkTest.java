package com.tanwir.core.deployment;

import com.tanwir.core.deployment.BuildProducer;
import com.tanwir.core.deployment.BuildStep;
import com.tanwir.core.deployment.ExtensionProcessor;
import com.tanwir.core.deployment.MiniExtensionManager;
import com.tanwir.core.deployment.MiniExtensionManager.FeatureBuildItem;
import com.tanwir.core.deployment.MultiBuildItem;
import com.tanwir.core.deployment.SimpleBuildItem;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Phase 6 — Extension Framework (Deployment vs Runtime Split).
 *
 * <p>Verifies that {@link MiniExtensionManager} correctly:
 * <ul>
 *   <li>Executes {@link BuildStep} methods</li>
 *   <li>Injects {@link BuildProducer}, {@link List}, and {@link SimpleBuildItem} parameters</li>
 *   <li>Resolves dependency order (a step requiring a {@link SimpleBuildItem} waits until
 *       another step produces it)</li>
 *   <li>Enforces that each {@link SimpleBuildItem} type is produced exactly once</li>
 * </ul>
 *
 * <p>Uses small self-contained {@link ExtensionProcessor} implementations — the same
 * pattern as the real Quarkus test suite in {@code quarkus-core-deployment}.
 */
class ExtensionFrameworkTest {

    // -------------------------------------------------------------------------
    // Mini build item types used only in these tests
    // -------------------------------------------------------------------------

    static final class MessageBuildItem extends MultiBuildItem {
        final String text;
        MessageBuildItem(String text) { this.text = text; }
    }

    static final class SummaryBuildItem extends SimpleBuildItem {
        final String summary;
        SummaryBuildItem(String summary) { this.summary = summary; }
    }

    // -------------------------------------------------------------------------
    // Mini processors used only in these tests
    // -------------------------------------------------------------------------

    /** Produces two {@link MessageBuildItem}s and a {@link FeatureBuildItem} via BuildProducer. */
    static class ProducerProcessor implements ExtensionProcessor {
        @BuildStep
        public void produceMessages(BuildProducer<MessageBuildItem> messages,
                                    BuildProducer<FeatureBuildItem> features) {
            features.produce(new FeatureBuildItem("test-producer"));
            messages.produce(new MessageBuildItem("hello"));
            messages.produce(new MessageBuildItem("world"));
        }
    }

    /** Consumes all {@link MessageBuildItem}s and returns one {@link SummaryBuildItem}. */
    static class ConsumerProcessor implements ExtensionProcessor {
        @BuildStep
        public SummaryBuildItem summarize(List<MessageBuildItem> messages) {
            String joined = messages.stream()
                    .map(m -> m.text).sorted()
                    .reduce("", (a, b) -> a.isEmpty() ? b : a + " " + b);
            return new SummaryBuildItem(joined);
        }
    }

    /**
     * Requires a {@link SummaryBuildItem} — MUST run after {@link ConsumerProcessor}.
     * The manager defers this step until the dependency is satisfied.
     */
    static class DependentProcessor implements ExtensionProcessor {
        volatile boolean ran = false;
        volatile String receivedSummary = null;

        @BuildStep
        public void useSummary(SummaryBuildItem summary) {
            ran = true;
            receivedSummary = summary.summary;
        }
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    void buildProducerInjectsMultiBuildItems() {
        MiniExtensionManager manager = MiniExtensionManager.forProcessors(new ProducerProcessor());

        List<MessageBuildItem> messages = manager.getMultiItems(MessageBuildItem.class);
        assertEquals(2, messages.size(), "Expected 2 MessageBuildItems");
        assertTrue(messages.stream().anyMatch(m -> "hello".equals(m.text)));
        assertTrue(messages.stream().anyMatch(m -> "world".equals(m.text)));
    }

    @Test
    void featureBuildItemsAreCollected() {
        MiniExtensionManager manager = MiniExtensionManager.forProcessors(new ProducerProcessor());

        List<FeatureBuildItem> features = manager.getMultiItems(FeatureBuildItem.class);
        assertTrue(features.stream().anyMatch(f -> "test-producer".equals(f.name())),
                "Expected 'test-producer' feature");
    }

    @Test
    void dependencyOrderingExecutesProducerBeforeConsumer() {
        // ConsumerProcessor declares List<MessageBuildItem> — runs after ProducerProcessor.
        // The MiniExtensionManager resolves this automatically via pass-until-stable.
        MiniExtensionManager manager = MiniExtensionManager.forProcessors(
                new ConsumerProcessor(),    // declared first but depends on messages
                new ProducerProcessor());   // declared second but has no dependencies

        SummaryBuildItem summary = manager.getSimpleItem(SummaryBuildItem.class);
        assertNotNull(summary, "SummaryBuildItem should have been produced");
        assertEquals("hello world", summary.summary, "Summary should combine sorted messages");
    }

    @Test
    void simpleBuildItemParameterDefersDependentStep() {
        DependentProcessor dependent = new DependentProcessor();
        // All three processors — manager must run them in the right order
        MiniExtensionManager manager = MiniExtensionManager.forProcessors(
                dependent,                  // needs SummaryBuildItem
                new ConsumerProcessor(),    // needs MessageBuildItem list
                new ProducerProcessor());   // no dependencies

        assertTrue(dependent.ran, "DependentProcessor must have run");
        assertEquals("hello world", dependent.receivedSummary, "Must receive correct summary");
    }

    @Test
    void emptyMultiBuildItemListIsInjectedWhenNoneProduced() {
        // ConsumerProcessor alone — no messages → gets empty list → produces empty summary
        MiniExtensionManager manager = MiniExtensionManager.forProcessors(new ConsumerProcessor());

        SummaryBuildItem summary = manager.getSimpleItem(SummaryBuildItem.class);
        assertNotNull(summary, "SummaryBuildItem should still be produced with empty input");
        assertEquals("", summary.summary, "Summary should be empty when no messages were produced");
    }

    @Test
    void simpleBuildItemUniquenessIsEnforced() {
        ExtensionProcessor first = new ExtensionProcessor() {
            @BuildStep public SummaryBuildItem produce() { return new SummaryBuildItem("first"); }
        };
        ExtensionProcessor second = new ExtensionProcessor() {
            @BuildStep public SummaryBuildItem produce() { return new SummaryBuildItem("second"); }
        };

        assertThrows(RuntimeException.class,
                () -> MiniExtensionManager.forProcessors(first, second),
                "Should throw when two steps produce the same SimpleBuildItem type");
    }

    @Test
    void nullReturnFromVoidBuildStepIsIgnored() {
        // void @BuildStep methods return null — the manager must not try to produce null
        ExtensionProcessor voidStep = new ExtensionProcessor() {
            @BuildStep
            public void doNothing(BuildProducer<FeatureBuildItem> features) {
                features.produce(new FeatureBuildItem("void-step"));
            }
        };

        MiniExtensionManager manager = MiniExtensionManager.forProcessors(voidStep);
        List<FeatureBuildItem> features = manager.getMultiItems(FeatureBuildItem.class);
        assertEquals(1, features.size());
        assertEquals("void-step", features.get(0).name());
    }
}
