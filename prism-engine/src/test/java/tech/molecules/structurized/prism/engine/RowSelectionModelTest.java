package tech.molecules.structurized.prism.engine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.BitSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RowSelectionModelTest {
    @Test
    void publishesAtomicSnapshotsAndSupportsUnsubscribe() {
        RowSelectionModel model = new RowSelectionModel();
        ArrayList<BitSet> changes = new ArrayList<>();
        RowSelectionSubscription subscription = model.subscribe(changes::add);
        BitSet selected = new BitSet();
        selected.set(1);
        selected.set(3);

        model.replace(selected);
        model.replace(selected);
        subscription.close();
        model.clear();

        assertEquals(1, changes.size());
        assertEquals(selected, changes.getFirst());
    }
}
