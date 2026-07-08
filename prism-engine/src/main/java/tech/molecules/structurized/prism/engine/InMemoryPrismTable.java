package tech.molecules.structurized.prism.engine;

import tech.molecules.structurized.prism.pack.PrismPack;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class InMemoryPrismTable implements PrismTable {
    private final int rowCount;
    private final List<PrismColumn> columns;
    private final Map<String, PrismColumn> columnsById;

    private InMemoryPrismTable(int rowCount, List<PrismColumn> columns) {
        this.rowCount = rowCount;
        this.columns = List.copyOf(columns);
        LinkedHashMap<String, PrismColumn> byId = new LinkedHashMap<>();
        for (PrismColumn column : columns) {
            PrismColumn previous = byId.putIfAbsent(column.id(), column);
            if (previous != null) {
                throw new IllegalArgumentException("duplicate column id '" + column.id() + "'");
            }
        }
        this.columnsById = Map.copyOf(byId);
    }

    public static InMemoryPrismTable from(PrismPack pack) {
        int rowCount = pack.dataFrame().rows().size();
        ArrayList<PrismColumn> columns = new ArrayList<>();
        for (String header : pack.dataFrame().headers()) {
            PrismPack.Column packColumn = pack.findSchemaColumn(header).orElse(null);
            PrismColumnSchema schema = schemaFor(header, packColumn);
            columns.add(columnFrom(pack.dataFrame(), schema));
        }
        return new InMemoryPrismTable(rowCount, columns);
    }

    private static PrismColumnSchema schemaFor(String header, PrismPack.Column column) {
        if (column == null) {
            return new PrismColumnSchema(header, PrismColumnType.TEXT, header, null, null, null, null, null, null, Map.of());
        }
        PrismColumnType type = PrismColumnType.fromSchema(column.type(), column.semanticType(), column.structureFormat());
        return new PrismColumnSchema(
                column.name(),
                type,
                column.displayName(),
                column.semanticType(),
                column.role(),
                column.unit(),
                column.endpointId(),
                column.direction(),
                column.structureFormat(),
                column.raw()
        );
    }

    private static PrismColumn columnFrom(PrismPack.DataFrame dataFrame, PrismColumnSchema schema) {
        int columnIndex = dataFrame.columnIndex(schema.id());
        int rowCount = dataFrame.rows().size();
        return switch (schema.type()) {
            case NUMERIC, INTEGER -> numericColumn(dataFrame, schema, columnIndex, rowCount);
            case BOOLEAN -> booleanColumn(dataFrame, schema, columnIndex, rowCount);
            case CATEGORICAL -> stringColumn(dataFrame, schema, columnIndex, rowCount, true);
            case TEXT, MOLECULE -> stringColumn(dataFrame, schema, columnIndex, rowCount, false);
        };
    }

    private static PrismColumn numericColumn(PrismPack.DataFrame dataFrame, PrismColumnSchema schema, int columnIndex, int rowCount) {
        double[] values = new double[rowCount];
        BitSet missing = new BitSet(rowCount);
        for (int row = 0; row < rowCount; row++) {
            String text = dataFrame.rows().get(row).get(columnIndex);
            if (isBlank(text)) {
                missing.set(row);
            } else {
                try {
                    values[row] = Double.parseDouble(text.trim());
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("invalid numeric value '" + text + "' in column '" + schema.id()
                            + "' row " + row, exception);
                }
            }
        }
        return new NumericPrismColumn(schema, values, missing);
    }

    private static PrismColumn booleanColumn(PrismPack.DataFrame dataFrame, PrismColumnSchema schema, int columnIndex, int rowCount) {
        Boolean[] values = new Boolean[rowCount];
        BitSet missing = new BitSet(rowCount);
        for (int row = 0; row < rowCount; row++) {
            String text = dataFrame.rows().get(row).get(columnIndex);
            if (isBlank(text)) {
                missing.set(row);
            } else {
                values[row] = Boolean.parseBoolean(text.trim());
            }
        }
        return new BooleanPrismColumn(schema, values, missing);
    }

    private static PrismColumn stringColumn(PrismPack.DataFrame dataFrame,
                                            PrismColumnSchema schema,
                                            int columnIndex,
                                            int rowCount,
                                            boolean categorical) {
        String[] values = new String[rowCount];
        BitSet missing = new BitSet(rowCount);
        for (int row = 0; row < rowCount; row++) {
            String value = dataFrame.rows().get(row).get(columnIndex);
            values[row] = value;
            if (isBlank(value)) {
                missing.set(row);
            }
        }
        return new StringPrismColumn(schema, values, missing, categorical);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Override
    public int rowCount() {
        return rowCount;
    }

    @Override
    public List<PrismColumn> columns() {
        return columns;
    }

    @Override
    public PrismColumn columnAt(int columnIndex) {
        return columns.get(columnIndex);
    }

    @Override
    public Optional<PrismColumn> findColumn(String columnId) {
        return Optional.ofNullable(columnsById.get(columnId));
    }

    @Override
    public int columnIndex(String columnId) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).id().equals(columnId)) {
                return i;
            }
        }
        return -1;
    }

    private abstract static class AbstractPrismColumn implements PrismColumn {
        private final PrismColumnSchema schema;

        private AbstractPrismColumn(PrismColumnSchema schema) {
            this.schema = schema;
        }

        @Override
        public String id() {
            return schema.id();
        }

        @Override
        public PrismColumnType type() {
            return schema.type();
        }

        @Override
        public PrismColumnSchema schema() {
            return schema;
        }
    }

    private static final class NumericPrismColumn extends AbstractPrismColumn {
        private final double[] values;
        private final BitSet missing;

        private NumericPrismColumn(PrismColumnSchema schema, double[] values, BitSet missing) {
            super(schema);
            this.values = values.clone();
            this.missing = (BitSet) missing.clone();
        }

        @Override
        public int rowCount() {
            return values.length;
        }

        @Override
        public boolean isMissing(int physicalRow) {
            return missing.get(physicalRow);
        }

        @Override
        public Object valueAt(int physicalRow) {
            return isMissing(physicalRow) ? null : values[physicalRow];
        }

        @Override
        public String formattedValueAt(int physicalRow) {
            return isMissing(physicalRow) ? "" : Double.toString(values[physicalRow]);
        }

        @Override
        public double doubleValueAt(int physicalRow) {
            return values[physicalRow];
        }

        @Override
        public Set<FilterCapability> filterCapabilities() {
            return Set.of(FilterCapability.NUMERIC_RANGE, FilterCapability.MISSING_VALUE);
        }
    }

    private static final class StringPrismColumn extends AbstractPrismColumn {
        private final String[] values;
        private final BitSet missing;
        private final boolean categorical;

        private StringPrismColumn(PrismColumnSchema schema, String[] values, BitSet missing, boolean categorical) {
            super(schema);
            this.values = values.clone();
            this.missing = (BitSet) missing.clone();
            this.categorical = categorical;
        }

        @Override
        public int rowCount() {
            return values.length;
        }

        @Override
        public boolean isMissing(int physicalRow) {
            return missing.get(physicalRow);
        }

        @Override
        public Object valueAt(int physicalRow) {
            return isMissing(physicalRow) ? null : values[physicalRow];
        }

        @Override
        public String formattedValueAt(int physicalRow) {
            return isMissing(physicalRow) ? "" : values[physicalRow];
        }

        @Override
        public Set<FilterCapability> filterCapabilities() {
            return categorical
                    ? Set.of(FilterCapability.CATEGORY_INCLUDE, FilterCapability.TEXT_CONTAINS, FilterCapability.MISSING_VALUE)
                    : Set.of(FilterCapability.TEXT_CONTAINS, FilterCapability.MISSING_VALUE);
        }
    }

    private static final class BooleanPrismColumn extends AbstractPrismColumn {
        private final Boolean[] values;
        private final BitSet missing;

        private BooleanPrismColumn(PrismColumnSchema schema, Boolean[] values, BitSet missing) {
            super(schema);
            this.values = values.clone();
            this.missing = (BitSet) missing.clone();
        }

        @Override
        public int rowCount() {
            return values.length;
        }

        @Override
        public boolean isMissing(int physicalRow) {
            return missing.get(physicalRow);
        }

        @Override
        public Object valueAt(int physicalRow) {
            return isMissing(physicalRow) ? null : values[physicalRow];
        }

        @Override
        public String formattedValueAt(int physicalRow) {
            return isMissing(physicalRow) ? "" : String.valueOf(values[physicalRow]);
        }

        @Override
        public Set<FilterCapability> filterCapabilities() {
            return Set.of(FilterCapability.MISSING_VALUE);
        }
    }
}
