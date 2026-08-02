package tech.molecules.structurized.prism.engine;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.molecules.structurized.prism.engine.live.PrismLiveExecutionEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PrismWorkspaceTest {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService computations = Executors.newFixedThreadPool(2);

    @AfterEach
    void shutdown() {
        scheduler.shutdownNow();
        computations.shutdownNow();
    }

    @Test
    void mergesNestedChangesIntoOneOriginAwareRevision() {
        PrismSession session = PrismSession.from(table());
        PrismWorkspace workspace = workspace(session);
        ArrayList<PrismWorkspaceChange> changes = new ArrayList<>();
        workspace.subscribe(changes::add);

        workspace.runAs(PrismWorkspaceChangeOrigin.AGENT, () -> {
            session.setVisibleColumns(List.of("id"));
            workspace.molecules().addDocument(PrismMoleculeWorkspace.SCRATCHPAD_ID, null,
                    "Idea", PrismMoleculeDocumentMode.MOLECULE, "idcode", "");
        });

        assertEquals(2, workspace.revision());
        assertEquals(1, changes.size());
        assertEquals(PrismWorkspaceChangeOrigin.AGENT, changes.getFirst().origin());
        assertEquals(PrismWorkspaceChangeType.STRUCTURE, changes.getFirst().type());
    }

    @Test
    void rejectsStaleWorkspaceMutation() {
        PrismWorkspace workspace = workspace(PrismSession.from(table()));
        long expected = workspace.revision();
        workspace.molecules().addDocument(PrismMoleculeWorkspace.SCRATCHPAD_ID, null,
                "Idea", PrismMoleculeDocumentMode.MOLECULE, "idcode", "");

        assertThrows(PrismWorkspaceRevisionConflictException.class, () ->
                workspace.runAs(PrismWorkspaceChangeOrigin.AGENT, expected, () -> {
                }));
    }

    private PrismWorkspace workspace(PrismSession session) {
        return new PrismWorkspace(
                "test",
                session,
                PrismWorkspaceExecutor.direct(),
                new PrismLiveExecutionEnvironment(scheduler, computations, 32)
        );
    }

    private static PrismTable table() {
        PrismColumn ids = new ArrayColumn(
                new PrismColumnSchema("id", PrismColumnType.TEXT, "ID", "compound_id", "identifier",
                        null, null, null, null, Map.of()),
                new Object[]{"a", "b"});
        PrismColumn values = new ArrayColumn(
                new PrismColumnSchema("value", PrismColumnType.NUMERIC, "Value", null, null,
                        null, null, null, null, Map.of()),
                new Object[]{1.0, 2.0});
        return new ArrayTable(List.of(ids, values));
    }

    private record ArrayTable(List<PrismColumn> columns) implements PrismTable {
        @Override
        public int rowCount() {
            return columns.getFirst().rowCount();
        }

        @Override
        public PrismColumn columnAt(int columnIndex) {
            return columns.get(columnIndex);
        }

        @Override
        public Optional<PrismColumn> findColumn(String columnId) {
            return columns.stream().filter(column -> column.id().equals(columnId)).findFirst();
        }

        @Override
        public int columnIndex(String columnId) {
            for (int index = 0; index < columns.size(); index++) {
                if (columns.get(index).id().equals(columnId)) return index;
            }
            return -1;
        }
    }

    private record ArrayColumn(PrismColumnSchema schema, Object[] values) implements PrismColumn {
        public String id() { return schema.id(); }
        public PrismColumnType type() { return schema.type(); }
        public int rowCount() { return values.length; }
        public boolean isMissing(int physicalRow) { return values[physicalRow] == null; }
        public Object valueAt(int physicalRow) { return values[physicalRow]; }
        public String formattedValueAt(int physicalRow) { return String.valueOf(values[physicalRow]); }
        public double doubleValueAt(int physicalRow) { return ((Number) values[physicalRow]).doubleValue(); }
        public Set<FilterCapability> filterCapabilities() { return Set.of(); }
    }
}
