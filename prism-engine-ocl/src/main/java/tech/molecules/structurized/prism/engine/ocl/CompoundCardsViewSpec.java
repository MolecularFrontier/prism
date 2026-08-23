package tech.molecules.structurized.prism.engine.ocl;

import tech.molecules.structurized.prism.engine.PrismViewSpec;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record CompoundCardsViewSpec(
        String viewId,
        String title,
        String rowSetId,
        String structureColumnId,
        String titleColumnId,
        String referenceRowId,
        List<CompoundCardPropertySpec> properties,
        boolean linkSelection,
        int maxCards
) implements PrismViewSpec {
    public static final String VIEW_TYPE = "chemistry.compound-cards";
    public static final int DEFAULT_MAX_CARDS = 6;
    public static final int HARD_MAX_CARDS = 8;
    public static final int HARD_MAX_PROPERTIES = 8;

    public CompoundCardsViewSpec {
        if (viewId == null || viewId.isBlank()) throw new IllegalArgumentException("view id must not be blank");
        if (rowSetId == null || rowSetId.isBlank()) throw new IllegalArgumentException("row set id must not be blank");
        if (structureColumnId == null || structureColumnId.isBlank()) {
            throw new IllegalArgumentException("structure column id must not be blank");
        }
        if (titleColumnId == null || titleColumnId.isBlank()) {
            throw new IllegalArgumentException("title column id must not be blank");
        }
        viewId = viewId.trim();
        title = title == null || title.isBlank() ? "Compound Comparison" : title.trim();
        rowSetId = rowSetId.trim();
        structureColumnId = structureColumnId.trim();
        titleColumnId = titleColumnId.trim();
        referenceRowId = referenceRowId == null || referenceRowId.isBlank() ? null : referenceRowId.trim();
        properties = properties == null ? List.of() : List.copyOf(properties);
        if (properties.isEmpty() || properties.size() > HARD_MAX_PROPERTIES) {
            throw new IllegalArgumentException("compound cards require between 1 and "
                    + HARD_MAX_PROPERTIES + " properties");
        }
        maxCards = maxCards < 1 ? DEFAULT_MAX_CARDS : Math.min(maxCards, HARD_MAX_CARDS);
    }

    @Override
    public String viewType() {
        return VIEW_TYPE;
    }

    @Override
    public Set<String> referencedRowSetIds() {
        return Set.of(rowSetId);
    }

    @Override
    public Set<String> referencedColumnIds() {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        result.add(structureColumnId);
        result.add(titleColumnId);
        properties.forEach(property -> {
            result.add(property.columnId());
            if (property.colorColumnId() != null) result.add(property.colorColumnId());
        });
        return Set.copyOf(result);
    }

    @Override
    public PrismViewSpec copyWithIdentity(String id, String newTitle) {
        return new CompoundCardsViewSpec(id, newTitle, rowSetId, structureColumnId, titleColumnId,
                referenceRowId, properties, linkSelection, maxCards);
    }
}
