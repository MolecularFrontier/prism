package tech.molecules.structurized.prismlite.swing.operations;

import tech.molecules.structurized.prism.engine.PrismRowSet;
import tech.molecules.structurized.prism.engine.PrismSession;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

public final class PrismOperationLaunchContext {
    private final PrismSession session;
    private final Supplier<Set<Integer>> selectedPhysicalRowsSupplier;

    public PrismOperationLaunchContext(PrismSession session, Supplier<Set<Integer>> selectedPhysicalRowsSupplier) {
        this.session = Objects.requireNonNull(session, "session");
        this.selectedPhysicalRowsSupplier = selectedPhysicalRowsSupplier == null ? Set::of : selectedPhysicalRowsSupplier;
    }

    public Set<Integer> selectedPhysicalRows() {
        return Set.copyOf(selectedPhysicalRowsSupplier.get());
    }

    public PrismRowSet materializeSelectedRows(String label) {
        LinkedHashSet<String> rowIds = new LinkedHashSet<>();
        for (Integer physicalRow : selectedPhysicalRows()) {
            if (physicalRow != null && physicalRow >= 0 && physicalRow < session.totalRowCount()) {
                rowIds.add(session.rowIdForPhysicalRow(physicalRow));
            }
        }
        if (rowIds.isEmpty()) {
            throw new IllegalStateException("No rows are selected.");
        }
        String normalizedLabel = label == null || label.isBlank() ? "Selected rows" : label.trim();
        String rowSetId = uniqueRowSetId("selection:" + slug(normalizedLabel));
        PrismRowSet rowSet = new PrismRowSet(
                rowSetId,
                normalizedLabel,
                "Rows selected in PrismLite",
                rowIds,
                Map.of("source", "prismlite-selection", "createdAt", Instant.now().toString())
        );
        session.addRowSet(rowSet);
        return rowSet;
    }

    private String uniqueRowSetId(String base) {
        String id = base;
        int suffix = 2;
        while (rowSetExists(id)) {
            id = base + "-" + suffix++;
        }
        return id;
    }

    private boolean rowSetExists(String rowSetId) {
        return session.rowSets().stream().anyMatch(rowSet -> rowSet.id().equals(rowSetId));
    }

    private static String slug(String value) {
        String slug = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        return slug.isBlank() ? "rows" : slug;
    }
}
