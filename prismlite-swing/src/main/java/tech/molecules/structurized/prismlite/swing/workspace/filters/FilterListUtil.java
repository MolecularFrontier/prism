package tech.molecules.structurized.prismlite.swing.workspace.filters;

import tech.molecules.structurized.prism.engine.ColumnFilter;
import tech.molecules.structurized.prism.engine.PrismFilter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class FilterListUtil {
    private FilterListUtil() {
    }

    public static PrismFilter activeColumnFilter(List<PrismFilter> filters, String columnId) {
        for (PrismFilter filter : filters) {
            if (isGuiColumnFilter(filter, columnId)) {
                return filter;
            }
        }
        return null;
    }

    public static List<PrismFilter> replaceColumnFilter(List<PrismFilter> filters,
                                                        String columnId,
                                                        PrismFilter replacement) {
        ArrayList<PrismFilter> result = new ArrayList<>();
        for (PrismFilter filter : filters) {
            if (!isGuiColumnFilter(filter, columnId)) {
                result.add(filter);
            }
        }
        if (replacement != null) {
            result.add(replacement);
        }
        return List.copyOf(result);
    }

    public static List<PrismFilter> replaceColumnFilters(List<PrismFilter> filters,
                                                         Collection<String> columnIds,
                                                         Map<String, PrismFilter> replacements) {
        Map<String, PrismFilter> byColumn = new LinkedHashMap<>(replacements);
        ArrayList<PrismFilter> result = new ArrayList<>();
        for (PrismFilter filter : filters) {
            boolean replaced = false;
            for (String columnId : columnIds) {
                if (isGuiColumnFilter(filter, columnId)) {
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                result.add(filter);
            }
        }
        for (String columnId : columnIds) {
            PrismFilter replacement = byColumn.get(columnId);
            if (replacement != null) {
                result.add(replacement);
            }
        }
        return List.copyOf(result);
    }

    public static boolean isGuiColumnFilter(PrismFilter filter, String columnId) {
        Objects.requireNonNull(columnId, "columnId");
        return filter instanceof ColumnFilter columnFilter && columnFilter.columnId().equals(columnId);
    }
}
