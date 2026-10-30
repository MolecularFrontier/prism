package tech.molecules.structurized.prism.report;

import tech.molecules.structurized.prism.engine.PrismSession;
import tech.molecules.structurized.prism.engine.PrismViewRecord;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses, validates and builds a session view from report <em>source text</em>.
 *
 * <p>Source-based rather than path-based on purpose: a report embedded in a PrismPack then travels
 * exactly the same parse/validate/register path as one loaded from disk. A second implementation
 * would be free to drift, and the divergence would surface only as a report behaving differently
 * inside a pack than outside it.
 */
public final class PrismReportRegistrar {
    private PrismReportRegistrar() {
    }

    /**
     * @param source            complete {@code .prism.md} source
     * @param fallbackId        view-id basis used when front matter declares no {@code id}
     * @param sourceDescription human-readable report origin recorded in view provenance
     * @param datasetSource     human-readable dataset origin recorded in view provenance
     * @param datasetIdentity   identity of the dataset being opened, or {@code null} to skip the
     *                          authored-against comparison
     */
    public static PrismViewRecord register(String source,
                                           String fallbackId,
                                           String sourceDescription,
                                           String datasetSource,
                                           PrismSession session,
                                           PrismReportDatasetIdentity datasetIdentity)
            throws PrismReportValidationException {
        PrismReportDocument parsed = new PrismReportParser().parse(source);
        List<PrismReportDiagnostic> diagnostics =
                new PrismReportValidator().validate(parsed, session, datasetIdentity);
        if (diagnostics.stream().anyMatch(item -> item.severity() == PrismReportSeverity.ERROR)) {
            throw new PrismReportValidationException(diagnostics);
        }
        PrismReportDocument validated = new PrismReportDocument(
                parsed.metadata(), parsed.blocks(), parsed.source(), diagnostics);
        String baseId = parsed.metadata().id() == null ? slug(fallbackId) : slug(parsed.metadata().id());
        String viewId = uniqueViewId(session, "report:" + baseId);
        PrismReportViewSpec specification =
                new PrismReportViewSpec(viewId, parsed.metadata().title(), validated);
        LinkedHashMap<String, Object> provenance = new LinkedHashMap<>();
        provenance.put("reportSource", sourceDescription == null ? "inline" : sourceDescription);
        provenance.put("datasetSource", datasetSource == null ? "current dataset" : datasetSource);
        provenance.put("validatedAt", Instant.now().toString());
        return new PrismViewRecord(viewId, specification.viewType(), specification.title(),
                specification, Instant.now(), Map.copyOf(provenance));
    }

    public static String slug(String value) {
        if (value == null) return "report";
        String slug = value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return slug.isBlank() ? "report" : slug;
    }

    private static String uniqueViewId(PrismSession session, String base) {
        String candidate = base;
        int suffix = 2;
        while (containsView(session, candidate)) candidate = base + "-" + suffix++;
        return candidate;
    }

    private static boolean containsView(PrismSession session, String id) {
        return session.views().stream().anyMatch(view -> view.id().equals(id));
    }
}
