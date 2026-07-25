package tech.molecules.structurized.prism.engine;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PrismSessionChangeTest {
    @Test
    void reportsSuccessfulSemanticChangesAndSupportsUnsubscribe() throws Exception {
        PrismSession session = exampleSession();
        ArrayList<PrismSessionChangeType> changes = new ArrayList<>();
        PrismSessionSubscription subscription = session.subscribe(change -> changes.add(change.type()));

        session.setFilters(List.of(new NumericRangeFilter("pIC50", 6.5, null, false)));
        session.addRowSet(new PrismRowSet("hits", "Hits", "", Set.of("CMPD-001"), Map.of()));
        session.addView(PrismViewRecord.of(new TestViewSpec()));

        assertEquals(List.of(
                PrismSessionChangeType.PROJECTION,
                PrismSessionChangeType.STRUCTURE,
                PrismSessionChangeType.VIEWS
        ), changes);

        subscription.close();
        session.clearFilters();
        assertEquals(3, changes.size());
    }

    @Test
    void failedMutationDoesNotPublishAChange() throws Exception {
        PrismSession session = exampleSession();
        ArrayList<PrismSessionChange> changes = new ArrayList<>();
        session.subscribe(changes::add);

        assertThrows(PrismOperationException.class, () -> session.addRowSet(
                new PrismRowSet("invalid", "Invalid", "", Set.of("missing-row"), Map.of())
        ));

        assertEquals(List.of(), changes);
    }

    private static PrismSession exampleSession() throws Exception {
        return PrismSession.open(Path.of("..", "examples", "example.prismpack"));
    }

    private record TestViewSpec() implements PrismViewSpec {
        @Override
        public String viewId() {
            return "summary";
        }

        @Override
        public String viewType() {
            return "test";
        }

        @Override
        public String title() {
            return "Summary";
        }

        @Override
        public Set<String> referencedRowSetIds() {
            return Set.of();
        }

        @Override
        public Set<String> referencedColumnIds() {
            return Set.of();
        }
    }
}
