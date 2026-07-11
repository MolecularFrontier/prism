package tech.molecules.structurized.prismlite.swing.workspace;

import tech.molecules.structurized.prism.engine.PrismFilter;
import tech.molecules.structurized.prism.engine.PrismSession;
import tech.molecules.structurized.prism.engine.PrismTable;
import tech.molecules.structurized.prismlite.swing.workspace.filters.FilterDraftState;
import tech.molecules.structurized.prismlite.swing.workspace.filters.FilterListUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;

public final class PrismLiteWorkspaceModel {
    public enum WorkspaceChange {
        ROW_FOCUS,
        COLUMN_FOCUS,
        FILTER_STATE,
        PRESENTATION,
        STRUCTURE
    }

    private final PrismSession session;
    private final Map<String, FilterDraftState> appliedFilterStatesByColumn = new LinkedHashMap<>();
    private final Map<String, FilterDraftState> draftFilterStatesByColumn = new LinkedHashMap<>();
    private final Set<String> dirtyFilterColumns = new LinkedHashSet<>();
    private final Set<String> pinnedColumns = new LinkedHashSet<>();
    private final Map<String, Integer> preferredWidths = new LinkedHashMap<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private final List<Consumer<WorkspaceChange>> changeListeners = new ArrayList<>();
    private String focusedColumnId;
    private Integer focusedPhysicalRow;
    private String focusedRowId;
    private int rowHeight = 24;

    public PrismLiteWorkspaceModel(PrismSession session) {
        this.session = Objects.requireNonNull(session, "session");
        if (!session.table().columns().isEmpty()) {
            focusedColumnId = session.table().columns().getFirst().id();
        }
        if (session.visibleRowCount() > 0) {
            setFocusedVisibleRowSilently(0);
        }
        syncAppliedColumnFiltersFromSession();
    }

    public PrismSession session() {
        return session;
    }

    public PrismTable table() {
        return session.table();
    }

    public String focusedColumnId() {
        return focusedColumnId;
    }

    public void setFocusedColumn(String columnId) {
        if (columnId != null) {
            table().column(columnId);
        }
        if (Objects.equals(focusedColumnId, columnId)) {
            return;
        }
        focusedColumnId = columnId;
        fireChanged(WorkspaceChange.COLUMN_FOCUS);
    }

    public Integer focusedPhysicalRow() {
        return focusedPhysicalRow;
    }

    public String focusedRowId() {
        return focusedRowId;
    }

    public void setFocusedVisibleRow(Integer visibleRow) {
        Integer oldPhysical = focusedPhysicalRow;
        if (visibleRow == null || visibleRow < 0 || visibleRow >= session.visibleRowCount()) {
            focusedPhysicalRow = null;
            focusedRowId = null;
        } else {
            setFocusedVisibleRowSilently(visibleRow);
        }
        if (!Objects.equals(oldPhysical, focusedPhysicalRow)) {
            fireChanged(WorkspaceChange.ROW_FOCUS);
        }
    }

    public void setFocusedPhysicalRow(Integer physicalRow) {
        Integer oldPhysical = focusedPhysicalRow;
        if (physicalRow == null || physicalRow < 0 || physicalRow >= table().rowCount()) {
            focusedPhysicalRow = null;
            focusedRowId = null;
        } else {
            focusedPhysicalRow = physicalRow;
            focusedRowId = session.rowIdForPhysicalRow(physicalRow);
        }
        if (!Objects.equals(oldPhysical, focusedPhysicalRow)) {
            fireChanged(WorkspaceChange.ROW_FOCUS);
        }
    }

    public OptionalInt focusedVisibleRow() {
        if (focusedPhysicalRow == null) {
            return OptionalInt.empty();
        }
        for (int visible = 0; visible < session.visibleRowCount(); visible++) {
            if (session.physicalRowAtVisibleIndex(visible) == focusedPhysicalRow) {
                return OptionalInt.of(visible);
            }
        }
        return OptionalInt.empty();
    }

    public boolean isVisible(String columnId) {
        return session.viewState().visibleColumns().contains(columnId);
    }

    public boolean isPinned(String columnId) {
        return pinnedColumns.contains(columnId);
    }

    public void setPinned(String columnId, boolean pinned) {
        if (pinned) {
            pinnedColumns.add(columnId);
        } else {
            pinnedColumns.remove(columnId);
        }
        fireChanged(WorkspaceChange.PRESENTATION);
    }

    public Map<String, Integer> preferredWidths() {
        return preferredWidths;
    }

    public void setPreferredWidth(String columnId, int width) {
        table().column(columnId);
        int normalized = Math.max(24, width);
        if (Objects.equals(preferredWidths.get(columnId), normalized)) {
            return;
        }
        preferredWidths.put(columnId, normalized);
    }

    public int rowHeight() {
        return rowHeight;
    }

    public void setRowHeight(int rowHeight) {
        int normalized = Math.max(18, rowHeight);
        if (this.rowHeight == normalized) {
            return;
        }
        this.rowHeight = normalized;
        fireChanged(WorkspaceChange.PRESENTATION);
    }

    public boolean hasAppliedColumnFilter(String columnId) {
        FilterDraftState state = appliedColumnFilterState(columnId);
        return state != null && state.filter() != null && state.enabled();
    }

    public FilterDraftState appliedColumnFilterState(String columnId) {
        syncAppliedColumnFiltersFromSession();
        return appliedFilterStatesByColumn.get(columnId);
    }

    public Map<String, FilterDraftState> appliedColumnFilterStates() {
        syncAppliedColumnFiltersFromSession();
        return Map.copyOf(appliedFilterStatesByColumn);
    }

    public PrismFilter activeColumnFilter(String columnId) {
        FilterDraftState state = appliedColumnFilterState(columnId);
        return state == null ? null : state.filter();
    }

    public PrismFilter draftFilter(String columnId) {
        FilterDraftState state = draftFilterState(columnId);
        return state == null ? null : state.filter();
    }

    public FilterDraftState draftFilterState(String columnId) {
        if (dirtyFilterColumns.contains(columnId)) {
            return draftFilterStatesByColumn.get(columnId);
        }
        return appliedColumnFilterState(columnId);
    }

    public boolean isDirty(String columnId) {
        return dirtyFilterColumns.contains(columnId);
    }

    public Set<String> dirtyFilterColumns() {
        return Set.copyOf(dirtyFilterColumns);
    }

    public void setDraftFilter(String columnId, PrismFilter filter) {
        table().column(columnId);
        if (filter != null && !filter.referencedColumnIds().contains(columnId)) {
            throw new IllegalArgumentException("draft filter does not reference column '" + columnId + "'");
        }
        FilterDraftState current = draftFilterState(columnId);
        boolean enabled = current == null || current.enabled();
        boolean inverted = current != null && current.inverted();
        setDraftFilterState(columnId, filter == null ? null : new FilterDraftState(filter, enabled, inverted));
    }

    public void setDraftFilterEnabled(String columnId, boolean enabled) {
        FilterDraftState current = draftFilterState(columnId);
        if (current == null || current.filter() == null) {
            return;
        }
        setDraftFilterState(columnId, current.withEnabled(enabled));
    }

    public void setDraftFilterInverted(String columnId, boolean inverted) {
        FilterDraftState current = draftFilterState(columnId);
        if (current == null || current.filter() == null) {
            return;
        }
        setDraftFilterState(columnId, current.withInverted(inverted));
    }

    public void setAppliedFilterEnabled(String columnId, boolean enabled) {
        FilterDraftState current = appliedColumnFilterState(columnId);
        if (current == null || current.filter() == null) {
            return;
        }
        appliedFilterStatesByColumn.put(columnId, current.withEnabled(enabled));
        reapplyFilterStates();
        fireChanged(WorkspaceChange.FILTER_STATE);
    }

    public void setAppliedFilterInverted(String columnId, boolean inverted) {
        FilterDraftState current = appliedColumnFilterState(columnId);
        if (current == null || current.filter() == null) {
            return;
        }
        appliedFilterStatesByColumn.put(columnId, current.withInverted(inverted));
        reapplyFilterStates();
        fireChanged(WorkspaceChange.FILTER_STATE);
    }

    public void discardDraft(String columnId) {
        draftFilterStatesByColumn.remove(columnId);
        dirtyFilterColumns.remove(columnId);
        fireChanged(WorkspaceChange.FILTER_STATE);
    }

    public void discardAllDrafts() {
        draftFilterStatesByColumn.clear();
        dirtyFilterColumns.clear();
        fireChanged(WorkspaceChange.FILTER_STATE);
    }

    public void applyDraft(String columnId) {
        if (!dirtyFilterColumns.contains(columnId)) {
            return;
        }
        FilterDraftState draft = draftFilterStatesByColumn.get(columnId);
        if (draft == null || draft.filter() == null) {
            appliedFilterStatesByColumn.remove(columnId);
        } else {
            appliedFilterStatesByColumn.put(columnId, draft);
        }
        draftFilterStatesByColumn.remove(columnId);
        dirtyFilterColumns.remove(columnId);
        reapplyFilterStates();
        fireChanged(WorkspaceChange.FILTER_STATE);
    }

    public void applyAllDrafts() {
        if (dirtyFilterColumns.isEmpty()) {
            return;
        }
        for (String columnId : dirtyFilterColumns) {
            FilterDraftState draft = draftFilterStatesByColumn.get(columnId);
            if (draft == null || draft.filter() == null) {
                appliedFilterStatesByColumn.remove(columnId);
            } else {
                appliedFilterStatesByColumn.put(columnId, draft);
            }
        }
        draftFilterStatesByColumn.clear();
        dirtyFilterColumns.clear();
        reapplyFilterStates();
        fireChanged(WorkspaceChange.FILTER_STATE);
    }

    public void removeAppliedFilter(PrismFilter filter) {
        PrismFilter unwrapped = FilterListUtil.unwrap(filter);
        if (unwrapped.referencedColumnIds().size() == 1) {
            String columnId = unwrapped.referencedColumnIds().iterator().next();
            if (FilterListUtil.isGuiColumnFilter(unwrapped, columnId)) {
                removeColumnFilterState(columnId);
                return;
            }
        }
        ArrayList<PrismFilter> next = new ArrayList<>(session.viewState().activeFilters());
        next.remove(filter);
        session.setFilters(next);
        fireChanged(WorkspaceChange.FILTER_STATE);
    }

    public void removeColumnFilterState(String columnId) {
        appliedFilterStatesByColumn.remove(columnId);
        draftFilterStatesByColumn.remove(columnId);
        dirtyFilterColumns.remove(columnId);
        reapplyFilterStates();
        fireChanged(WorkspaceChange.FILTER_STATE);
    }

    public void clearAppliedFilters() {
        appliedFilterStatesByColumn.clear();
        draftFilterStatesByColumn.clear();
        dirtyFilterColumns.clear();
        session.setFilters(List.of());
        fireChanged(WorkspaceChange.FILTER_STATE);
    }

    public List<PrismFilter> nonGuiActiveFilters() {
        syncAppliedColumnFiltersFromSession();
        ArrayList<PrismFilter> result = new ArrayList<>();
        for (PrismFilter filter : session.viewState().activeFilters()) {
            if (!isManagedColumnFilter(filter)) {
                result.add(filter);
            }
        }
        return List.copyOf(result);
    }

    public void setColumnVisible(String columnId, boolean visible) {
        table().column(columnId);
        ArrayList<String> next = new ArrayList<>(session.viewState().visibleColumns());
        if (visible && !next.contains(columnId)) {
            int runtimeIndex = table().columnIndex(columnId);
            int insertAt = next.size();
            for (int i = 0; i < next.size(); i++) {
                if (table().columnIndex(next.get(i)) > runtimeIndex) {
                    insertAt = i;
                    break;
                }
            }
            next.add(insertAt, columnId);
        } else if (!visible) {
            next.remove(columnId);
        }
        session.setVisibleColumns(next);
        fireChanged(WorkspaceChange.STRUCTURE);
    }

    public void restoreDefaultColumnOrder() {
        session.setVisibleColumns(table().columns().stream().map(column -> column.id()).toList());
        fireChanged(WorkspaceChange.STRUCTURE);
    }

    public boolean isComputedColumn(String columnId) {
        return session.computedValues().findDefinition(columnId).isPresent();
    }

    public void addListener(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    public void addChangeListener(Consumer<WorkspaceChange> listener) {
        changeListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void removeChangeListener(Consumer<WorkspaceChange> listener) {
        changeListeners.remove(listener);
    }

    private void setDraftFilterState(String columnId, FilterDraftState state) {
        if (state == null) {
            draftFilterStatesByColumn.remove(columnId);
        } else {
            draftFilterStatesByColumn.put(columnId, state);
        }
        dirtyFilterColumns.add(columnId);
        fireChanged(WorkspaceChange.FILTER_STATE);
    }

    private void reapplyFilterStates() {
        ArrayList<PrismFilter> next = new ArrayList<>();
        for (PrismFilter filter : session.viewState().activeFilters()) {
            if (!isManagedColumnFilter(filter)) {
                next.add(filter);
            }
        }
        for (FilterDraftState state : appliedFilterStatesByColumn.values()) {
            PrismFilter effective = FilterListUtil.effectiveFilter(state);
            if (effective != null) {
                next.add(effective);
            }
        }
        session.setFilters(next);
    }

    private void syncAppliedColumnFiltersFromSession() {
        for (PrismFilter filter : session.viewState().activeFilters()) {
            PrismFilter unwrapped = FilterListUtil.unwrap(filter);
            if (unwrapped.referencedColumnIds().size() != 1) {
                continue;
            }
            String columnId = unwrapped.referencedColumnIds().iterator().next();
            if (FilterListUtil.isGuiColumnFilter(unwrapped, columnId) && !appliedFilterStatesByColumn.containsKey(columnId)) {
                appliedFilterStatesByColumn.put(columnId, new FilterDraftState(unwrapped, true, FilterListUtil.isInverted(filter)));
            }
        }
    }

    private boolean isManagedColumnFilter(PrismFilter filter) {
        PrismFilter unwrapped = FilterListUtil.unwrap(filter);
        if (unwrapped.referencedColumnIds().size() != 1) {
            return false;
        }
        String columnId = unwrapped.referencedColumnIds().iterator().next();
        return appliedFilterStatesByColumn.containsKey(columnId) && FilterListUtil.isGuiColumnFilter(unwrapped, columnId);
    }

    private void setFocusedVisibleRowSilently(int visibleRow) {
        focusedPhysicalRow = session.physicalRowAtVisibleIndex(visibleRow);
        focusedRowId = session.rowIdForPhysicalRow(focusedPhysicalRow);
    }

    private void fireChanged(WorkspaceChange change) {
        List.copyOf(changeListeners).forEach(listener -> listener.accept(change));
        List.copyOf(listeners).forEach(Runnable::run);
    }
}
