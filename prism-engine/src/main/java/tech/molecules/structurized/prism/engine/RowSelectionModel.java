package tech.molecules.structurized.prism.engine;

import java.util.BitSet;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class RowSelectionModel {
    private final BitSet selectedRows = new BitSet();
    private final CopyOnWriteArrayList<Consumer<BitSet>> listeners = new CopyOnWriteArrayList<>();

    public void clear() {
        if (selectedRows.isEmpty()) return;
        selectedRows.clear();
        publish();
    }

    public void setSelected(int physicalRow, boolean selected) {
        if (selectedRows.get(physicalRow) == selected) return;
        selectedRows.set(physicalRow, selected);
        publish();
    }

    public boolean isSelected(int physicalRow) {
        return selectedRows.get(physicalRow);
    }

    public void replace(BitSet rows) {
        BitSet replacement = rows == null ? new BitSet() : (BitSet) rows.clone();
        if (selectedRows.equals(replacement)) return;
        selectedRows.clear();
        selectedRows.or(replacement);
        publish();
    }

    public BitSet selectedRows() {
        return (BitSet) selectedRows.clone();
    }

    public RowSelectionSubscription subscribe(Consumer<BitSet> listener) {
        Consumer<BitSet> registered = Objects.requireNonNull(listener, "listener");
        listeners.add(registered);
        return () -> listeners.remove(registered);
    }

    private void publish() {
        for (Consumer<BitSet> listener : listeners) {
            listener.accept(selectedRows());
        }
    }
}
