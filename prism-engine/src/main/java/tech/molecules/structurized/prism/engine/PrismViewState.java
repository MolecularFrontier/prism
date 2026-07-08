package tech.molecules.structurized.prism.engine;

import tech.molecules.structurized.prism.pack.PrismPack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PrismViewState {
    private List<String> visibleColumns;
    private List<PrismFilter> activeFilters;
    private List<SortKey> sortKeys;
    private final RowSelectionModel selectionModel;
    private final RowFlagModel flagModel;

    public PrismViewState(List<String> visibleColumns) {
        this.visibleColumns = visibleColumns == null ? List.of() : List.copyOf(visibleColumns);
        this.activeFilters = List.of();
        this.sortKeys = List.of();
        this.selectionModel = new RowSelectionModel();
        this.flagModel = new RowFlagModel();
    }

    public static PrismViewState defaultFor(PrismTable table) {
        return new PrismViewState(table.columns().stream().map(PrismColumn::id).toList());
    }

    public static PrismViewState fromPack(PrismPack pack, PrismTable table) {
        PrismViewState state = defaultFor(table);
        if (pack.tableView() == null) {
            return state;
        }
        if (!pack.tableView().columns().isEmpty()) {
            ArrayList<String> visible = new ArrayList<>();
            for (String column : pack.tableView().columns()) {
                if (table.findColumn(column).isPresent()) {
                    visible.add(column);
                }
            }
            if (!visible.isEmpty()) {
                state.setVisibleColumns(visible);
            }
        } else if (!pack.tableView().hiddenColumns().isEmpty()) {
            ArrayList<String> visible = new ArrayList<>();
            for (PrismColumn column : table.columns()) {
                if (!pack.tableView().hiddenColumns().contains(column.id())) {
                    visible.add(column.id());
                }
            }
            state.setVisibleColumns(visible);
        }
        ArrayList<SortKey> sorts = new ArrayList<>();
        for (PrismPack.Sort sort : pack.tableView().sort()) {
            if (sort.column() != null && table.findColumn(sort.column()).isPresent()) {
                SortDirection direction = "descending".equals(normalize(sort.direction())) || "desc".equals(normalize(sort.direction()))
                        ? SortDirection.DESCENDING
                        : SortDirection.ASCENDING;
                sorts.add(new SortKey(sort.column(), direction, MissingValueOrder.LAST));
            }
        }
        state.setSortKeys(sorts);
        return state;
    }

    public List<String> visibleColumns() {
        return visibleColumns;
    }

    public void setVisibleColumns(List<String> visibleColumns) {
        this.visibleColumns = visibleColumns == null ? List.of() : List.copyOf(visibleColumns);
    }

    public List<PrismFilter> activeFilters() {
        return activeFilters;
    }

    public void setActiveFilters(List<PrismFilter> activeFilters) {
        this.activeFilters = activeFilters == null ? List.of() : List.copyOf(activeFilters);
    }

    public void addFilter(PrismFilter filter) {
        ArrayList<PrismFilter> next = new ArrayList<>(activeFilters);
        next.add(filter);
        activeFilters = List.copyOf(next);
    }

    public void clearFilters() {
        activeFilters = List.of();
    }

    public List<SortKey> sortKeys() {
        return sortKeys;
    }

    public void setSortKeys(List<SortKey> sortKeys) {
        this.sortKeys = sortKeys == null ? List.of() : List.copyOf(sortKeys);
    }

    public RowSelectionModel selectionModel() {
        return selectionModel;
    }

    public RowFlagModel flagModel() {
        return flagModel;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
