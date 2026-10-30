package tech.molecules.structurized.prism.engine.ocl;

import tech.molecules.structurized.prism.engine.PrismViewSpec;

import java.util.List;
import java.util.Set;

/**
 * A grid of literal structures supplied inline in the report, independent of the dataset.
 *
 * <p>Unlike every other structure-bearing view, this one references no row set and no column: the
 * structures are carried as SMILES in the report source. That makes it usable for compounds which
 * are deliberately absent from the snapshot - generic scaffolds, proposed-but-unmade analogues,
 * reaction intermediates, or registered compounds that carry no measurement and are therefore
 * excluded from an assay-population snapshot.
 */
public record StructureSketchViewSpec(
        String viewId,
        String title,
        List<StructureSketchEntrySpec> structures,
        int gridColumns
) implements PrismViewSpec {
    public static final String VIEW_TYPE = "structure-sketch";
    public static final int DEFAULT_GRID_COLUMNS = 4;
    public static final int MAX_GRID_COLUMNS = 8;
    public static final int HARD_MAX_STRUCTURES = 64;

    public StructureSketchViewSpec {
        if (viewId == null || viewId.isBlank()) {
            throw new IllegalArgumentException("structure-sketch view id must not be blank");
        }
        viewId = viewId.trim();
        title = title == null || title.isBlank() ? null : title.trim();
        if (structures == null || structures.isEmpty()) {
            throw new IllegalArgumentException("structure-sketch requires at least one structure");
        }
        if (structures.size() > HARD_MAX_STRUCTURES) {
            throw new IllegalArgumentException("structure-sketch supports at most "
                    + HARD_MAX_STRUCTURES + " structures");
        }
        structures = List.copyOf(structures);
        if (gridColumns < 1 || gridColumns > MAX_GRID_COLUMNS) {
            throw new IllegalArgumentException("structure-sketch gridColumns must be between 1 and "
                    + MAX_GRID_COLUMNS);
        }
    }

    @Override
    public String viewType() {
        return VIEW_TYPE;
    }

    @Override
    public Set<String> referencedRowSetIds() {
        return Set.of();
    }

    @Override
    public Set<String> referencedColumnIds() {
        return Set.of();
    }

    @Override
    public PrismViewSpec copyWithIdentity(String newViewId, String newTitle) {
        return new StructureSketchViewSpec(newViewId, newTitle, structures, gridColumns);
    }
}
