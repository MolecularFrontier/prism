package tech.molecules.structurized.prismlite.swing.report;

import tech.molecules.structurized.prism.engine.PrismSession;
import tech.molecules.structurized.prism.engine.PrismViewRecord;
import tech.molecules.structurized.prism.report.PrismReportDiagnostic;
import tech.molecules.structurized.prism.report.PrismReportDocument;
import tech.molecules.structurized.prism.report.PrismReportParser;
import tech.molecules.structurized.prism.report.PrismReportSeverity;
import tech.molecules.structurized.prism.report.PrismReportValidator;
import tech.molecules.structurized.prism.report.PrismReportViewSpec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PrismReportLoader {
    private PrismReportLoader() {
    }

    public static PrismViewRecord load(Path reportPath, Path datasetPath, PrismSession session)
            throws IOException, PrismReportValidationException {
        String source = Files.readString(reportPath);
        PrismReportDocument parsed = new PrismReportParser().parse(source);
        List<PrismReportDiagnostic> diagnostics = new PrismReportValidator().validate(parsed, session);
        if (diagnostics.stream().anyMatch(item -> item.severity() == PrismReportSeverity.ERROR)) {
            throw new PrismReportValidationException(diagnostics);
        }
        PrismReportDocument validated = new PrismReportDocument(
                parsed.metadata(), parsed.blocks(), parsed.source(), diagnostics);
        String baseId = parsed.metadata().id() == null
                ? filenameId(reportPath)
                : slug(parsed.metadata().id());
        String viewId = uniqueViewId(session, "report:" + baseId);
        PrismReportViewSpec specification = new PrismReportViewSpec(
                viewId, parsed.metadata().title(), validated);
        LinkedHashMap<String, Object> provenance = new LinkedHashMap<>();
        provenance.put("reportSource", reportPath.toAbsolutePath().normalize().toString());
        provenance.put("datasetSource", datasetPath == null
                ? "current dataset"
                : datasetPath.toAbsolutePath().normalize().toString());
        provenance.put("validatedAt", Instant.now().toString());
        return new PrismViewRecord(
                viewId,
                specification.viewType(),
                specification.title(),
                specification,
                Instant.now(),
                Map.copyOf(provenance)
        );
    }

    private static String filenameId(Path path) {
        String name = path.getFileName() == null ? "report" : path.getFileName().toString();
        if (name.toLowerCase().endsWith(".prism.md")) name = name.substring(0, name.length() - 9);
        return slug(name);
    }

    private static String slug(String value) {
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
