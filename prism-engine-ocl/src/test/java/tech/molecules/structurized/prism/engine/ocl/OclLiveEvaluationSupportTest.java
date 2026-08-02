package tech.molecules.structurized.prism.engine.ocl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.molecules.structurized.prism.engine.PrismMoleculeDocumentMode;
import tech.molecules.structurized.prism.engine.PrismMoleculeWorkspace;
import tech.molecules.structurized.prism.engine.live.PrismLiveContext;
import tech.molecules.structurized.prism.engine.live.PrismLiveEvaluationStatus;
import tech.molecules.structurized.prism.engine.live.PrismLiveExecutionEnvironment;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OclLiveEvaluationSupportTest {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService computations = Executors.newFixedThreadPool(2);
    private PrismLiveContext context;

    @AfterEach
    void shutdown() {
        if (context != null) context.close();
        scheduler.shutdownNow();
        computations.shutdownNow();
    }

    @Test
    void publishesIndependentPropertyAndStructureResultsForOneDocument() throws Exception {
        PrismMoleculeWorkspace molecules = new PrismMoleculeWorkspace();
        context = new PrismLiveContext(
                molecules,
                new PrismLiveExecutionEnvironment(scheduler, computations, 32));
        OclLiveEvaluationSupport.registerDefaults(context);

        OclMoleculeDocumentCodec.EncodedMolecule ethanol =
                new OclMoleculeDocumentCodec().parse("CCO", PrismMoleculeDocumentMode.MOLECULE);
        molecules.addDocument(PrismMoleculeWorkspace.SCRATCHPAD_ID, "ethanol", "Ethanol",
                PrismMoleculeDocumentMode.MOLECULE, ethanol.idcode(), ethanol.coordinates());

        await(() -> context.evaluationsFor("ethanol").stream()
                .filter(evaluation -> evaluation.status() == PrismLiveEvaluationStatus.SUCCEEDED)
                .count() == 2);

        var propertyResult = context.findEvaluation(
                        OclLiveEvaluationSupport.BASIC_PROPERTIES_BINDING_ID, "ethanol")
                .orElseThrow().lastSuccessful().result();
        var summaryResult = context.findEvaluation(
                        OclLiveEvaluationSupport.STRUCTURE_SUMMARY_BINDING_ID, "ethanol")
                .orElseThrow().lastSuccessful().result();

        assertEquals("chemistry.ocl.basic_properties.v1", propertyResult.schemaId());
        assertEquals(46.069, (double) propertyResult.values().get("molecular_weight"), 0.01);
        assertEquals(1, summaryResult.values().get("hetero_atoms"));
        assertEquals(0, summaryResult.values().get("rings"));
        assertFalse(OclLiveCapabilities.DECODED_MOLECULE.publishable());
        assertTrue(OclLiveCapabilities.BASIC_PROPERTIES.publishable());
    }

    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("condition was not satisfied before timeout");
            }
            Thread.sleep(10);
        }
    }
}
