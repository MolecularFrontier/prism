package tech.molecules.structurized.prism.engine.ocl;

import tech.molecules.structurized.prism.engine.PrismColumn;
import tech.molecules.structurized.prism.engine.PrismColumnType;
import tech.molecules.structurized.prism.engine.PrismRowSet;
import tech.molecules.structurized.prism.engine.PrismSessionSnapshot;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static tech.molecules.structurized.prism.engine.ocl.CompoundCardProjectionModels.CompoundCard;
import static tech.molecules.structurized.prism.engine.ocl.CompoundCardProjectionModels.CompoundCardsModel;
import static tech.molecules.structurized.prism.engine.ocl.CompoundCardProjectionModels.CompoundCardValue;

/** Renderer-independent value, delta, and score projection for compound comparison cards. */
public final class CompoundCardProjectionBuilder {
    private CompoundCardProjectionBuilder() {
    }

    public static CompoundCardsModel build(PrismSessionSnapshot snapshot, CompoundCardsViewSpec specification) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(specification, "specification");
        PrismRowSet rowSet = snapshot.rowSet(specification.rowSetId()).orElseThrow(() ->
                new IllegalArgumentException("unknown row set: " + specification.rowSetId()));
        ArrayList<Row> rows = new ArrayList<>();
        for (String rowId : rowSet.rowIds()) {
            snapshot.rowIdIndex().physicalRow(rowId).ifPresent(row -> rows.add(new Row(rowId, row)));
        }
        if (specification.referenceRowId() != null) {
            rows.sort(Comparator.comparing(row -> !row.rowId().equals(specification.referenceRowId())));
        }
        Integer referencePhysicalRow = specification.referenceRowId() == null ? null
                : snapshot.rowIdIndex().physicalRow(specification.referenceRowId()).stream().boxed()
                .findFirst().orElse(null);
        List<CompoundCard> cards = rows.stream().limit(specification.maxCards())
                .map(row -> card(snapshot, specification, row, referencePhysicalRow)).toList();
        return new CompoundCardsModel(cards, specification.referenceRowId(), rows.size());
    }

    private static CompoundCard card(PrismSessionSnapshot snapshot, CompoundCardsViewSpec specification,
                                     Row row, Integer referencePhysicalRow) {
        PrismColumn titleColumn = snapshot.table().column(specification.titleColumnId());
        String title = titleColumn.isMissing(row.physicalRow()) ? row.rowId()
                : titleColumn.formattedValueAt(row.physicalRow());
        ArrayList<CompoundCardValue> values = new ArrayList<>();
        for (CompoundCardPropertySpec property : specification.properties()) {
            PrismColumn column = snapshot.table().column(property.columnId());
            String formatted = column.isMissing(row.physicalRow()) ? "—"
                    : format(column, row.physicalRow(), property.format());
            String delta = formattedDelta(column, row.physicalRow(), referencePhysicalRow, property);
            Double score = score(snapshot, row.physicalRow(), property.colorColumnId());
            values.add(new CompoundCardValue(property, formatted, delta, score));
        }
        return new CompoundCard(row.rowId(), title,
                row.rowId().equals(specification.referenceRowId()), values);
    }

    private static String format(PrismColumn column, int physicalRow, String pattern) {
        if (pattern == null) return column.formattedValueAt(physicalRow);
        return decimalFormat(pattern).format(column.doubleValueAt(physicalRow));
    }

    private static String formattedDelta(PrismColumn column, int physicalRow, Integer referencePhysicalRow,
                                         CompoundCardPropertySpec property) {
        if (!property.showDelta() || referencePhysicalRow == null || physicalRow == referencePhysicalRow
                || !numeric(column) || column.isMissing(physicalRow) || column.isMissing(referencePhysicalRow)) {
            return null;
        }
        double delta = column.doubleValueAt(physicalRow) - column.doubleValueAt(referencePhysicalRow);
        if (!Double.isFinite(delta)) return null;
        DecimalFormat format = decimalFormat(property.format() == null ? "0.00" : property.format());
        if (delta > 0.0) format.setPositivePrefix("+");
        return format.format(delta);
    }

    private static Double score(PrismSessionSnapshot snapshot, int physicalRow, String scoreColumnId) {
        if (scoreColumnId == null) return null;
        PrismColumn score = snapshot.table().column(scoreColumnId);
        if (score.isMissing(physicalRow)) return null;
        double value = score.doubleValueAt(physicalRow);
        return Double.isFinite(value) ? value : null;
    }

    private static boolean numeric(PrismColumn column) {
        return column.type() == PrismColumnType.NUMERIC || column.type() == PrismColumnType.INTEGER;
    }

    private static DecimalFormat decimalFormat(String pattern) {
        return new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.ROOT));
    }

    private record Row(String rowId, int physicalRow) {
    }
}
