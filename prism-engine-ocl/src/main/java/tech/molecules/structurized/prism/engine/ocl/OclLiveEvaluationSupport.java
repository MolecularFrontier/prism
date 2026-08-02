package tech.molecules.structurized.prism.engine.ocl;

import tech.molecules.structurized.prism.engine.live.PrismLiveBinding;
import tech.molecules.structurized.prism.engine.live.PrismLiveContext;
import tech.molecules.structurized.prism.engine.live.PrismLiveExecutionMode;

import java.time.Duration;
import java.util.Map;

public final class OclLiveEvaluationSupport {
    public static final String BASIC_PROPERTIES_BINDING_ID = "ocl.basic_properties";
    public static final String STRUCTURE_SUMMARY_BINDING_ID = "ocl.structure_summary";
    public static final Duration DEFAULT_QUIET_PERIOD = Duration.ofMillis(500);

    private OclLiveEvaluationSupport() {
    }

    public static void registerCapabilities(PrismLiveContext context) {
        context.registerProvider(new OclDecodedMoleculeLiveProvider());
        context.registerProvider(new OclBasicPropertiesLiveProvider());
        context.registerProvider(new OclStructureSummaryLiveProvider());
    }

    public static void registerDefaultBindings(PrismLiveContext context) {
        context.configureBinding(new PrismLiveBinding(
                BASIC_PROPERTIES_BINDING_ID, OclLiveCapabilities.BASIC_PROPERTIES.id(),
                PrismLiveExecutionMode.AUTO, DEFAULT_QUIET_PERIOD, Map.of()));
        context.configureBinding(new PrismLiveBinding(
                STRUCTURE_SUMMARY_BINDING_ID, OclLiveCapabilities.STRUCTURE_SUMMARY.id(),
                PrismLiveExecutionMode.AUTO, DEFAULT_QUIET_PERIOD, Map.of()));
    }

    public static void registerDefaults(PrismLiveContext context) {
        registerCapabilities(context);
        registerDefaultBindings(context);
    }
}
