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
import tech.molecules.structurized.prism.engine.ocl.OclSimilarityFilter;
import tech.molecules.structurized.prism.engine.ocl.OclSubstructureFilter;

public final class DefaultPrismFilterLabelProvider implements PrismFilterLabelProvider {
    @Override
    public boolean supports(PrismFilter filter) {
        return true;
    }

    @Override
    public String label(PrismFilter filter, PrismTable table) {
        PrismFilter unwrapped = FilterListUtil.unwrap(filter);
        String prefix = FilterListUtil.isInverted(filter) ? "not " : "";
        if (unwrapped instanceof NumericRangeFilter numeric) {
            return prefix + columnName(table, numeric.columnId()) + ": " + range(numeric.min(), numeric.max());
        }
        if (unwrapped instanceof TextPatternFilter text) {
            return prefix + columnName(table, text.columnId()) + ": " + text.patternText();
        }
        if (unwrapped instanceof CategoryIncludeFilter category) {
            return prefix + columnName(table, category.columnId()) + ": categories";
        }
        if (unwrapped instanceof MissingValueFilter missing) {
            return prefix + columnName(table, missing.columnId()) + ": missing/value";
        }
        if (unwrapped instanceof OclSubstructureFilter substructure) {
            return prefix + columnName(table, substructure.columnId()) + ": substructure";
        }
        if (unwrapped instanceof OclSimilarityFilter similarity) {
            return prefix + columnName(table, similarity.columnId()) + ": similarity >= "
                    + trim(similarity.minimumSimilarity());
        }
        if (unwrapped instanceof RowSetFilter rowSet) {
            return "Row set: " + rowSet.rowSetId();
        }
        if (unwrapped instanceof ColumnFilter columnFilter) {
            return prefix + columnName(table, columnFilter.columnId()) + ": filter";
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
