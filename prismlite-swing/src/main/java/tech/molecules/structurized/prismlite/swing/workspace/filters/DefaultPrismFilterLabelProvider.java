package tech.molecules.structurized.prismlite.swing.workspace.filters;

import tech.molecules.structurized.prism.engine.CategoryIncludeFilter;
import tech.molecules.structurized.prism.engine.ColumnFilter;
import tech.molecules.structurized.prism.engine.MissingValueFilter;
import tech.molecules.structurized.prism.engine.NumericRangeFilter;
import tech.molecules.structurized.prism.engine.PrismColumn;
import tech.molecules.structurized.prism.engine.PrismFilter;
import tech.molecules.structurized.prism.engine.PrismTable;
import tech.molecules.structurized.prism.engine.RowSetFilter;
import tech.molecules.structurized.prism.engine.TextPatternFilter;

public final class DefaultPrismFilterLabelProvider implements PrismFilterLabelProvider {
    @Override
    public boolean supports(PrismFilter filter) {
        return true;
    }

    @Override
    public String label(PrismFilter filter, PrismTable table) {
        if (filter instanceof NumericRangeFilter numeric) {
            return columnName(table, numeric.columnId()) + ": " + range(numeric.min(), numeric.max());
        }
        if (filter instanceof TextPatternFilter text) {
            return columnName(table, text.columnId()) + ": " + text.patternText();
        }
        if (filter instanceof CategoryIncludeFilter category) {
            return columnName(table, category.columnId()) + ": categories";
        }
        if (filter instanceof MissingValueFilter missing) {
            return columnName(table, missing.columnId()) + ": missing/value";
        }
        if (filter instanceof RowSetFilter rowSet) {
            return "Row set: " + rowSet.rowSetId();
        }
        if (filter instanceof ColumnFilter columnFilter) {
            return columnName(table, columnFilter.columnId()) + ": filter";
        }
        return "Filter";
    }

    private static String columnName(PrismTable table, String columnId) {
        PrismColumn column = table.column(columnId);
        return column.schema().displayName();
    }

    private static String range(Double min, Double max) {
        String left = min == null ? "-inf" : trim(min);
        String right = max == null ? "+inf" : trim(max);
        return left + ".." + right;
    }

    private static String trim(Double value) {
        if (value == null) {
            return "";
        }
        if (Math.rint(value) == value) {
            return Long.toString(value.longValue());
        }
        return Double.toString(value);
    }
}
