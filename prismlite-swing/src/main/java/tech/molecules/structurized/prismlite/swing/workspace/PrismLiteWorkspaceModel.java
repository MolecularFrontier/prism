package tech.molecules.structurized.prismlite.swing.workspace;

import tech.molecules.structurized.prism.engine.PrismFilter;
import tech.molecules.structurized.prism.engine.PrismSession;
import tech.molecules.structurized.prism.engine.PrismTable;
import tech.molecules.structurized.prismlite.swing.workspace.filters.FilterListUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class PrismLiteWorkspaceModel {
    private final PrismSession session;
    private final Map<String, PrismFilter> draftFiltersByColumn = new LinkedHashMap<>();
    private final Set<String> dirtyFilterColumns = new LinkedHashSet<>();
    private final Set<String> pinnedColumns = new LinkedHashSet<>();
    private final Map<String, Integer> preferredWidths = new LinkedHashMap<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private String focusedColumnId;

    public PrismLiteWorkspaceModel(PrismSession session) {
        this.session = Objects.requireNonNull(session, "session");
        if (!session.table().columns().isEmpty()) {
            focusedColumnId = session.table().columns().getFirst().id();
        }
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
        fireChanged();
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
        fireChanged();
    }

    public Map<String, Integer> preferredWidths() {
        return preferredWidths;
    }

    public boolean hasAppliedColumnFilter(String columnId) {
        return activeColumnFilter(columnId) != null;
    }

    public PrismFilter activeColumnFilter(String columnId) {
        return FilterListUtil.activeColumnFilter(session.viewState().activeFilters(), columnId);
    }

    public PrismFilter draftFilter(String columnId) {
        if (!dirtyFilterColumns.contains(columnId)) {
            return activeColumnFilter(columnId);
        }
        return draftFiltersByColumn.get(columnId);
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
        if (filter == null) {
            draftFiltersByColumn.remove(columnId);
        } else {
            draftFiltersByColumn.put(columnId, filter);
        }
        dirtyFilterColumns.add(columnId);
        fireChanged();
    }

    public void discardDraft(String columnId) {
        draftFiltersByColumn.remove(columnId);
        dirtyFilterColumns.remove(columnId);
        fireChanged();
    }

    public void discardAllDrafts() {
        draftFiltersByColumn.clear();
        dirtyFilterColumns.clear();
        fireChanged();
    }

    public void applyDraft(String columnId) {
        if (!dirtyFilterColumns.contains(columnId)) {
            return;
        }
        PrismFilter draft = draftFiltersByColumn.get(columnId);
        session.setFilters(FilterListUtil.replaceColumnFilter(session.viewState().activeFilters(), columnId, draft));
        draftFiltersByColumn.remove(columnId);
        dirtyFilterColumns.remove(columnId);
        fireChanged();
    }

    public void applyAllDrafts() {
        if (dirtyFilterColumns.isEmpty()) {
            return;
        }
        session.setFilters(FilterListUtil.replaceColumnFilters(
                session.viewState().activeFilters(),
                dirtyFilterColumns,
                draftFiltersByColumn
        ));
        draftFiltersByColumn.clear();
        dirtyFilterColumns.clear();
        fireChanged();
    }

    public void removeAppliedFilter(PrismFilter filter) {
        ArrayList<PrismFilter> next = new ArrayList<>(session.viewState().activeFilters());
        next.remove(filter);
        session.setFilters(next);
        fireChanged();
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
        fireChanged();
    }

    public void restoreDefaultColumnOrder() {
        session.setVisibleColumns(table().columns().stream().map(column -> column.id()).toList());
        fireChanged();
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

    private void fireChanged() {
        List.copyOf(listeners).forEach(Runnable::run);
    }
}
