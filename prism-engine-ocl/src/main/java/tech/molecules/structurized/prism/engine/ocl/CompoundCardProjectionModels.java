package tech.molecules.structurized.prism.engine.ocl;

import java.util.List;

public final class CompoundCardProjectionModels {
    private CompoundCardProjectionModels() {
    }

    public record CompoundCardValue(
            CompoundCardPropertySpec specification,
            String formattedValue,
            String formattedDelta,
            Double score
    ) {
    }

    public record CompoundCard(
            String rowId,
            String title,
            boolean reference,
            List<CompoundCardValue> values
    ) {
        public CompoundCard {
            values = values == null ? List.of() : List.copyOf(values);
        }
    }

    public record CompoundCardsModel(
            List<CompoundCard> cards,
            String referenceRowId,
            int totalCompoundCount
    ) {
        public CompoundCardsModel {
            cards = cards == null ? List.of() : List.copyOf(cards);
        }

        public boolean truncated() {
            return cards.size() < totalCompoundCount;
        }
    }
}
