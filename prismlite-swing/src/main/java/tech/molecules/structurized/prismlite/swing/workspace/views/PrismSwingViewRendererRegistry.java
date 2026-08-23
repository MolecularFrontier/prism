package tech.molecules.structurized.prismlite.swing.workspace.views;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class PrismSwingViewRendererRegistry {
    private final Map<String, PrismSwingViewRenderer> renderers = new LinkedHashMap<>();

    public static PrismSwingViewRendererRegistry defaults() {
        PrismSwingViewRendererRegistry registry = embeddedDefaults();
        registry.register(new PrismReportViewRenderer(registry));
        return registry;
    }

    public static PrismSwingViewRendererRegistry embeddedDefaults() {
        PrismSwingViewRendererRegistry registry = new PrismSwingViewRendererRegistry();
        registry.register(new StructureGridViewRenderer());
        registry.register(new ScatterPlotViewRenderer());
        registry.register(new CompoundTableViewRenderer());
        registry.register(new ColumnSummaryViewRenderer());
        registry.register(new Sar1DViewRenderer());
        registry.register(new Sar2DViewRenderer());
        return registry;
    }

    public void register(PrismSwingViewRenderer renderer) {
        if (renderer == null) {
            return;
        }
        renderers.put(renderer.viewType(), renderer);
    }

    public Optional<PrismSwingViewRenderer> find(String viewType) {
        return Optional.ofNullable(renderers.get(viewType));
    }
}
