package tech.molecules.structurized.prism.engine.ocl;

import tech.molecules.structurized.prism.engine.PrismColumn;
import tech.molecules.structurized.prism.engine.PrismColumnType;
import tech.molecules.structurized.prism.engine.PrismRowSet;
import tech.molecules.structurized.prism.engine.PrismSessionSnapshot;
import tech.molecules.structurized.prism.engine.PrismTable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static tech.molecules.structurized.prism.engine.ocl.SarProjectionModels.AggregatedValue;
import static tech.molecules.structurized.prism.engine.ocl.SarProjectionModels.CellKey;
import static tech.molecules.structurized.prism.engine.ocl.SarProjectionModels.Sar1DModel;
import static tech.molecules.structurized.prism.engine.ocl.SarProjectionModels.Sar1DRow;
import static tech.molecules.structurized.prism.engine.ocl.SarProjectionModels.Sar2DCell;
import static tech.molecules.structurized.prism.engine.ocl.SarProjectionModels.Sar2DModel;

/** Renderer-independent grouping and endpoint aggregation for 1D and 2D SAR projections. */
public final class SarProjectionBuilder {
    private SarProjectionBuilder() {
    }

    public static Sar1DModel build1D(PrismSessionSnapshot snapshot, Sar1DViewSpec specification) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(specification, "specification");
        PrismColumn dimension = snapshot.table().column(specification.substituentColumnId());
        List<String> contexts = contextColumns(snapshot.table(), specification.contextColumnIds(),
                Set.of(dimension.id()), dimension);
        LinkedHashMap<String, MutableGroup> groups = new LinkedHashMap<>();
        int excluded = 0;
        for (int physicalRow : physicalRows(snapshot, specification.rowSetId())) {
            SarSubstituent substituent = substituent(dimension, physicalRow);
            if (!substituent.isProjectable()) {
                excluded++;
                continue;
            }
            groups.computeIfAbsent(substituent.identity(), ignored -> new MutableGroup(substituent))
                    .add(snapshot, physicalRow, contexts);
        }
        List<MutableGroup> ranked = groups.values().stream().sorted(groupComparator()).toList();
        List<Sar1DRow> rows = ranked.stream().limit(specification.maxGroups())
                .map(group -> new Sar1DRow(group.substituent,
                        aggregate(snapshot.table(), group.physicalRows, specification.values()),
                        rowIds(snapshot, group.physicalRows), group.contexts.size()))
                .toList();
        return new Sar1DModel(rows, groups.size(), excluded, contexts);
    }

    public static Sar2DModel build2D(PrismSessionSnapshot snapshot, Sar2DViewSpec specification) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(specification, "specification");
        PrismColumn rowDimension = snapshot.table().column(specification.rowSubstituentColumnId());
        PrismColumn columnDimension = snapshot.table().column(specification.columnSubstituentColumnId());
        List<String> contexts = contextColumns(snapshot.table(), specification.contextColumnIds(),
                Set.of(rowDimension.id(), columnDimension.id()), rowDimension, columnDimension);
        LinkedHashMap<String, MutableGroup> rowGroups = new LinkedHashMap<>();
        LinkedHashMap<String, MutableGroup> columnGroups = new LinkedHashMap<>();
        LinkedHashMap<CellKey, MutableCell> cells = new LinkedHashMap<>();
        int excluded = 0;
        for (int physicalRow : physicalRows(snapshot, specification.rowSetId())) {
            SarSubstituent row = substituent(rowDimension, physicalRow);
            SarSubstituent column = substituent(columnDimension, physicalRow);
            if (!row.isProjectable() || !column.isProjectable()) {
                excluded++;
                continue;
            }
            rowGroups.computeIfAbsent(row.identity(), ignored -> new MutableGroup(row)).physicalRows.add(physicalRow);
            columnGroups.computeIfAbsent(column.identity(), ignored -> new MutableGroup(column)).physicalRows.add(physicalRow);
            cells.computeIfAbsent(new CellKey(row.identity(), column.identity()), ignored -> new MutableCell(row, column))
                    .add(snapshot, physicalRow, contexts);
        }
        List<SarSubstituent> rows = rowGroups.values().stream().sorted(groupComparator())
                .limit(specification.maxRowGroups()).map(group -> group.substituent).toList();
        List<SarSubstituent> columns = columnGroups.values().stream().sorted(groupComparator())
                .limit(specification.maxColumnGroups()).map(group -> group.substituent).toList();
        Set<String> rowIdentities = rows.stream().map(SarSubstituent::identity)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> columnIdentities = columns.stream().map(SarSubstituent::identity)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        LinkedHashMap<CellKey, Sar2DCell> visibleCells = new LinkedHashMap<>();
        for (Map.Entry<CellKey, MutableCell> entry : cells.entrySet()) {
            if (!rowIdentities.contains(entry.getKey().rowIdentity())
                    || !columnIdentities.contains(entry.getKey().columnIdentity())) continue;
            MutableCell cell = entry.getValue();
            visibleCells.put(entry.getKey(), new Sar2DCell(cell.row, cell.column,
                    aggregate(snapshot.table(), cell.physicalRows, specification.values()),
                    rowIds(snapshot, cell.physicalRows), cell.contexts.size()));
        }
        return new Sar2DModel(rows, columns, Map.copyOf(visibleCells), rowGroups.size(),
                columnGroups.size(), excluded, contexts);
    }

    public static SarSubstituent substituent(PrismColumn column, int physicalRow) {
        if (column.isMissing(physicalRow)) return SarSubstituentCodec.decode(SarSubstituentCodec.unmatched());
        String value = Objects.toString(column.valueAt(physicalRow), "");
        if (SarSubstituentCodec.SEMANTIC_TYPE.equals(column.schema().semanticType())
                || value.equals("none") || value.equals("multi") || value.equals("ambiguous")
                || value.equals("unmatched") || value.startsWith("sub:")) {
            return SarSubstituentCodec.decode(value);
        }
        if (column.type() == PrismColumnType.MOLECULE) {
            return new SarSubstituent(SarSubstituent.Type.SUBSTITUENT, "mol:" + value,
                    "[substituent]", value);
        }
        return new SarSubstituent(SarSubstituent.Type.LABEL, "label:" + value, value, null);
    }

    private static List<Integer> physicalRows(PrismSessionSnapshot snapshot, String rowSetId) {
        PrismRowSet rowSet = snapshot.rowSet(rowSetId).orElseThrow(() ->
                new IllegalArgumentException("unknown row set: " + rowSetId));
        ArrayList<Integer> result = new ArrayList<>();
        for (String rowId : rowSet.rowIds()) {
            snapshot.rowIdIndex().physicalRow(rowId).ifPresent(result::add);
        }
        return List.copyOf(result);
    }

    private static List<String> contextColumns(PrismTable table, List<String> requested, Set<String> projected,
                                               PrismColumn... dimensions) {
        if (requested != null && !requested.isEmpty()) return requested.stream()
                .filter(id -> !projected.contains(id)).distinct().toList();
        String analysisId = null;
        for (PrismColumn dimension : dimensions) {
            Object value = dimension.schema().raw().get("sarAnalysisId");
            if (!(value instanceof String text) || text.isBlank()) return List.of();
            if (analysisId == null) analysisId = text;
            else if (!analysisId.equals(text)) return List.of();
        }
        if (analysisId == null) return List.of();
        String expected = analysisId;
        return table.columns().stream()
                .filter(column -> SarSubstituentCodec.SEMANTIC_TYPE.equals(column.schema().semanticType()))
                .filter(column -> expected.equals(column.schema().raw().get("sarAnalysisId")))
                .map(PrismColumn::id).filter(id -> !projected.contains(id)).toList();
    }

    private static List<AggregatedValue> aggregate(PrismTable table, List<Integer> physicalRows,
                                                   List<SarValueSpec> specifications) {
        ArrayList<AggregatedValue> result = new ArrayList<>();
        for (SarValueSpec specification : specifications) {
            PrismColumn valueColumn = table.column(specification.columnId());
            PrismColumn scoreColumn = specification.colorColumnId() == null
                    ? null : table.column(specification.colorColumnId());
            ArrayList<Observation> observations = new ArrayList<>();
            for (int physicalRow : physicalRows) {
                if (valueColumn.isMissing(physicalRow)) continue;
                double value = valueColumn.doubleValueAt(physicalRow);
                if (!Double.isFinite(value)) continue;
                Double score = scoreColumn == null || scoreColumn.isMissing(physicalRow)
                        ? null : finite(scoreColumn.doubleValueAt(physicalRow));
                observations.add(new Observation(value, score));
            }
            result.add(aggregate(valueColumn, specification, observations));
        }
        return List.copyOf(result);
    }

    private static AggregatedValue aggregate(PrismColumn column, SarValueSpec specification,
                                               List<Observation> observations) {
        if (observations.isEmpty()) return new AggregatedValue(specification, null, null, 0);
        return switch (specification.aggregation()) {
            case MEAN -> new AggregatedValue(specification,
                    observations.stream().mapToDouble(Observation::value).average().orElseThrow(),
                    meanScore(observations), observations.size());
            case MEDIAN -> new AggregatedValue(specification, median(observations.stream()
                    .map(Observation::value).toList()), medianScore(observations), observations.size());
            case MIN -> selected(specification, observations, Comparator.comparingDouble(Observation::value));
            case MAX -> selected(specification, observations, Comparator.comparingDouble(Observation::value).reversed());
            case BEST -> selected(specification, observations, bestComparator(column));
        };
    }

    private static AggregatedValue selected(SarValueSpec specification, List<Observation> observations,
                                             Comparator<Observation> comparator) {
        Observation selected = observations.stream().min(comparator).orElseThrow();
        return new AggregatedValue(specification, selected.value, selected.score, observations.size());
    }

    private static Comparator<Observation> bestComparator(PrismColumn column) {
        String direction = column.schema().direction() == null ? "" : column.schema().direction()
                .trim().toLowerCase(Locale.ROOT);
        if (Set.of("higher_is_better", "higher", "maximize", "max").contains(direction)) {
            return Comparator.comparingDouble(Observation::value).reversed();
        }
        if (Set.of("lower_is_better", "lower", "minimize", "min").contains(direction)) {
            return Comparator.comparingDouble(Observation::value);
        }
        throw new IllegalArgumentException("BEST aggregation requires endpoint direction for column " + column.id());
    }

    private static Double meanScore(List<Observation> observations) {
        return observations.stream().map(Observation::score).filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue).average().stream().boxed().findFirst().orElse(null);
    }

    private static Double medianScore(List<Observation> observations) {
        return median(observations.stream().map(Observation::score).filter(Objects::nonNull).toList());
    }

    private static Double median(List<Double> values) {
        if (values.isEmpty()) return null;
        List<Double> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
    }

    private static Double finite(double value) { return Double.isFinite(value) ? value : null; }

    private static Set<String> rowIds(PrismSessionSnapshot snapshot, List<Integer> physicalRows) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (int physicalRow : physicalRows) result.add(snapshot.rowIdIndex().rowId(physicalRow));
        return Set.copyOf(result);
    }

    private static Comparator<MutableGroup> groupComparator() {
        return Comparator.<MutableGroup>comparingInt(group -> group.physicalRows.size()).reversed()
                .thenComparing(group -> group.substituent.identity());
    }

    private static String contextTuple(PrismSessionSnapshot snapshot, int physicalRow, List<String> contexts) {
        if (contexts.isEmpty()) return "";
        StringBuilder value = new StringBuilder();
        for (String context : contexts) {
            value.append(substituent(snapshot.table().column(context), physicalRow).identity()).append('\u001f');
        }
        return value.toString();
    }

    private record Observation(double value, Double score) {}

    private static final class MutableGroup {
        private final SarSubstituent substituent;
        private final ArrayList<Integer> physicalRows = new ArrayList<>();
        private final LinkedHashSet<String> contexts = new LinkedHashSet<>();

        private MutableGroup(SarSubstituent substituent) { this.substituent = substituent; }

        private void add(PrismSessionSnapshot snapshot, int physicalRow, List<String> contextColumns) {
            physicalRows.add(physicalRow);
            if (!contextColumns.isEmpty()) contexts.add(contextTuple(snapshot, physicalRow, contextColumns));
        }
    }

    private static final class MutableCell {
        private final SarSubstituent row;
        private final SarSubstituent column;
        private final ArrayList<Integer> physicalRows = new ArrayList<>();
        private final LinkedHashSet<String> contexts = new LinkedHashSet<>();

        private MutableCell(SarSubstituent row, SarSubstituent column) {
            this.row = row;
            this.column = column;
        }

        private void add(PrismSessionSnapshot snapshot, int physicalRow, List<String> contextColumns) {
            physicalRows.add(physicalRow);
            if (!contextColumns.isEmpty()) contexts.add(contextTuple(snapshot, physicalRow, contextColumns));
        }
    }
}
