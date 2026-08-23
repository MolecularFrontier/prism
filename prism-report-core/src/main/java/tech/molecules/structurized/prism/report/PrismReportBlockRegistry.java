package tech.molecules.structurized.prism.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import tech.molecules.structurized.prism.engine.PrismSession;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class PrismReportBlockRegistry {
    private final Map<String, PrismReportBlockProvider> providers = new LinkedHashMap<>();

    public static PrismReportBlockRegistry defaults() {
        PrismReportBlockRegistry registry = new PrismReportBlockRegistry();
        BuiltInPrismReportBlockProviders.registerDefaults(registry);
        return registry;
    }

    public void register(PrismReportBlockProvider provider) {
        if (provider == null || provider.type() == null || provider.type().isBlank()) {
            throw new IllegalArgumentException("report block provider type must not be blank");
        }
        String type = provider.type().trim();
        if (providers.putIfAbsent(type, provider) != null) {
            throw new IllegalArgumentException("report block provider already registered: " + type);
        }
    }

    public Optional<PrismReportBlockProvider> find(String type) {
        return Optional.ofNullable(providers.get(type));
    }

    public List<String> blockTypes() {
        return List.copyOf(providers.keySet());
    }

    PrismReportBlock parse(ObjectMapper mapper, String source, int ordinal, int sourceLine,
                           List<PrismReportDiagnostic> diagnostics) {
        JsonNode json;
        try {
            json = mapper.readTree(source);
        } catch (JsonProcessingException exception) {
            int relativeLine = exception.getLocation() == null ? 1 : Math.max(1, exception.getLocation().getLineNr());
            int column = exception.getLocation() == null ? 1 : Math.max(1, exception.getLocation().getColumnNr());
            diagnostics.add(new PrismReportDiagnostic(PrismReportSeverity.ERROR, "INVALID_BLOCK_JSON",
                    exception.getOriginalMessage(), null, sourceLine + relativeLine, column));
            return null;
        }
        if (json == null || !json.isObject()) {
            diagnostics.add(new PrismReportDiagnostic(PrismReportSeverity.ERROR, "INVALID_BLOCK_JSON",
                    "A prism block must contain one JSON object.", null, sourceLine + 1, 1));
            return null;
        }
        String type = json.path("type").isTextual() ? json.path("type").textValue().trim() : null;
        String blockId = json.path("id").isTextual() ? json.path("id").textValue().trim() : null;
        if (blockId == null || blockId.isBlank()) blockId = "block-" + ordinal;
        PrismReportBlockProvider provider = providers.get(type);
        if (provider == null) {
            diagnostics.add(new PrismReportDiagnostic(PrismReportSeverity.ERROR, "UNKNOWN_BLOCK_TYPE",
                    type == null || type.isBlank() ? "Prism block is missing required field 'type'."
                            : "Unknown prism block type '" + type + "'.",
                    blockId, sourceLine + 1, 1));
            return null;
        }
        return provider.parse(json, new PrismReportParseContext(blockId, sourceLine, diagnostics));
    }

    void validate(PrismReportBlock block, PrismSession session, List<PrismReportDiagnostic> diagnostics) {
        if (!(block instanceof EmbeddedPrismViewReportBlock embedded)) return;
        PrismReportBlockProvider provider = providers.get(embedded.blockType());
        if (provider == null) {
            diagnostics.add(new PrismReportDiagnostic(PrismReportSeverity.ERROR, "UNKNOWN_BLOCK_TYPE",
                    "No provider is registered for report block type '" + embedded.blockType() + "'.",
                    embedded.blockId(), embedded.sourceLine(), 1));
            return;
        }
        provider.validate(block, session, diagnostics);
    }
}
