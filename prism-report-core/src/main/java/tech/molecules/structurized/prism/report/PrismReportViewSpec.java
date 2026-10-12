package tech.molecules.structurized.prism.report;

import tech.molecules.structurized.prism.engine.PrismViewSpec;

import java.util.LinkedHashSet;
import java.util.Set;

public record PrismReportViewSpec(
        String viewId,
        String title,
        PrismReportDocument document
) implements PrismViewSpec {
    public static final String VIEW_TYPE = "report.prism-markdown";

    public PrismReportViewSpec {
        if (viewId == null || viewId.isBlank()) throw new IllegalArgumentException("view id must not be blank");
        if (document == null) throw new IllegalArgumentException("document must not be null");
        viewId = viewId.trim();
        title = title == null || title.isBlank() ? "Prism Report" : title.trim();
    }

    @Override
    public String viewType() {
        return VIEW_TYPE;
    }

    @Override
    public Set<String> referencedRowSetIds() {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (PrismReportBlock block : document.blocks()) {
            if (block instanceof EmbeddedPrismViewReportBlock embedded) {
                result.addAll(embedded.specification().referencedRowSetIds());
            }
        }
        return Set.copyOf(result);
    }

    @Override
    public Set<String> referencedColumnIds() {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (PrismReportBlock block : document.blocks()) {
            if (block instanceof EmbeddedPrismViewReportBlock embedded) {
                result.addAll(embedded.specification().referencedColumnIds());
            }
        }
        return Set.copyOf(result);
    }

    @Override
    public PrismViewSpec copyWithIdentity(String id, String newTitle) {
        return new PrismReportViewSpec(id, newTitle, document);
    }
}
