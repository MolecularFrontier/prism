package tech.molecules.structurized.prismlite.swing.workspace.views;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class PrismSwingViewRendererRegistry {
    private final Map<String, PrismSwingViewRenderer> renderers = new LinkedHashMap<>();

    public static PrismSwingViewRendererRegistry defaults() {
        PrismSwingViewRendererRegistry registry = new PrismSwingViewRendererRegistry();
        registry.register(new StructureGridViewRenderer());
        registry.register(new ScatterPlotViewRenderer());
        registry.register(new CompoundTableViewRenderer());
        registry.register(new PrismReportViewRenderer());
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
