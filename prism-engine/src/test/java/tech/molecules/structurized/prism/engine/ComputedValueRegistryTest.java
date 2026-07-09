package tech.molecules.structurized.prism.engine;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComputedValueRegistryTest {
    @Test
    void lazyComputedValueIsCachedPerPhysicalRow() throws Exception {
        PrismSession session = exampleSession();
        AtomicInteger calls = new AtomicInteger();
        session.registerComputedValue(ComputedValueDefinition.builder("comment.length", Integer.class)
                .displayName("Comment Length")
                .columnType(PrismColumnType.INTEGER)
                .dependencyColumnIds(List.of("comment"))
                .cachePolicy(CachePolicy.LAZY)
                .provider((table, physicalRow, context) -> {
                    calls.incrementAndGet();
                    String value = table.formattedValueAt(physicalRow, "comment");
                    return value.length();
                })
                .build());

        assertEquals(11, session.computedValues().value("comment.length", 0, Integer.class));
        assertEquals(11, session.computedValues().value("comment.length", 0, Integer.class));
        assertEquals(1, calls.get());
        assertEquals(1, session.computedValues().cache().size());
    }

    @Test
    void precomputeComputesAllRowsWhenRegistered() throws Exception {
        PrismSession session = exampleSession();
        AtomicInteger calls = new AtomicInteger();

        session.registerComputedValue(ComputedValueDefinition.builder("compound.id.length", Integer.class)
                .displayName("Compound ID Length")
                .columnType(PrismColumnType.INTEGER)
                .dependencyColumnIds(List.of("compound_id"))
                .cachePolicy(CachePolicy.PRECOMPUTE)
                .provider((table, physicalRow, context) -> {
                    calls.incrementAndGet();
                    return table.formattedValueAt(physicalRow, "compound_id").length();
                })
                .build());

        assertEquals(3, calls.get());
        assertEquals(3, session.computedValues().cache().size());
        assertEquals(8, session.computedValues().value("compound.id.length", 1, Integer.class));
        assertEquals(3, calls.get());
    }

    @Test
    void computedColumnCanBeVisibleSortedAndFiltered() throws Exception {
        PrismSession session = exampleSession();
        session.registerComputedValue(ComputedValueDefinition.builder("score.simple", Double.class)
                .displayName("Simple Score")
                .columnType(PrismColumnType.NUMERIC)
                .dependencyColumnIds(List.of("pIC50", "clogP"))
                .cachePolicy(CachePolicy.LAZY)
                .provider((table, physicalRow, context) -> {
                    PrismColumn pIC50 = table.column("pIC50");
                    PrismColumn clogP = table.column("clogP");
                    if (pIC50.isMissing(physicalRow) || clogP.isMissing(physicalRow)) {
                        return null;
                    }
                    return pIC50.doubleValueAt(physicalRow) - clogP.doubleValueAt(physicalRow);
                })
                .build(), true);

        assertTrue(session.viewState().visibleColumns().contains("score.simple"));
        session.setSortKeys(List.of(SortKey.desc("score.simple")));
        assertEquals("CMPD-001", session.table().formattedValueAt(session.physicalRowAtVisibleIndex(0), "compound_id"));
        assertEquals(4.1, session.table().column("score.simple").doubleValueAt(session.physicalRowAtVisibleIndex(0)), 0.0001);

        session.setFilters(List.of(new NumericRangeFilter("score.simple", 3.85, null, false)));
        assertEquals(2, session.visibleRowCount());
        assertEquals("CMPD-001", session.table().formattedValueAt(session.physicalRowAtVisibleIndex(0), "compound_id"));
        assertEquals("CMPD-002", session.table().formattedValueAt(session.physicalRowAtVisibleIndex(1), "compound_id"));
    }

    @Test
    void replacingDefinitionInvalidatesCachedValues() throws Exception {
        PrismSession session = exampleSession();
        AtomicInteger calls = new AtomicInteger();
        session.registerComputedValue(scaledPIC50(2.0, "two", calls));

        assertEquals(14.4, session.computedValues().value("scaled.pIC50", 0, Double.class), 0.0001);
        assertEquals(14.4, session.computedValues().value("scaled.pIC50", 0, Double.class), 0.0001);
        assertEquals(1, calls.get());

        session.replaceComputedValue(scaledPIC50(3.0, "three", calls));

        assertEquals(21.6, session.computedValues().value("scaled.pIC50", 0, Double.class), 0.0001);
        assertEquals(2, calls.get());
        assertEquals(1, session.computedValues().cache().size());
    }

    @Test
    void noCachePolicyComputesEveryTime() throws Exception {
        PrismSession session = exampleSession();
        AtomicInteger calls = new AtomicInteger();
        session.registerComputedValue(ComputedValueDefinition.builder("volatile.length", Integer.class)
                .displayName("Volatile Length")
                .columnType(PrismColumnType.INTEGER)
                .dependencyColumnIds(List.of("compound_id"))
                .cachePolicy(CachePolicy.NO_CACHE)
                .provider((table, physicalRow, context) -> {
                    calls.incrementAndGet();
                    return table.formattedValueAt(physicalRow, "compound_id").length();
                })
                .build());

        assertEquals(8, session.computedValues().value("volatile.length", 0, Integer.class));
        assertEquals(8, session.computedValues().value("volatile.length", 0, Integer.class));
        assertEquals(2, calls.get());
        assertEquals(0, session.computedValues().cache().size());
    }

    @Test
    void runtimeTableContainsBaseAndComputedColumnsButBaseTableDoesNot() throws Exception {
        PrismSession session = exampleSession();
        session.registerComputedValue(ComputedValueDefinition.builder("comment.length", Integer.class)
                .displayName("Comment Length")
                .columnType(PrismColumnType.INTEGER)
                .dependencyColumnIds(List.of("comment"))
                .provider((table, physicalRow, context) -> table.formattedValueAt(physicalRow, "comment").length())
                .build());

        assertTrue(session.table().findColumn("comment.length").isPresent());
        assertFalse(session.baseTable().findColumn("comment.length").isPresent());
    }

    private static ComputedValueDefinition<Double> scaledPIC50(double scale, String fingerprint, AtomicInteger calls) {
        return ComputedValueDefinition.builder("scaled.pIC50", Double.class)
                .displayName("Scaled pIC50")
                .columnType(PrismColumnType.NUMERIC)
                .dependencyColumnIds(List.of("pIC50"))
                .configurationFingerprint(fingerprint)
                .provider((table, physicalRow, context) -> {
                    calls.incrementAndGet();
                    PrismColumn column = table.column("pIC50");
                    return column.isMissing(physicalRow) ? null : column.doubleValueAt(physicalRow) * scale;
                })
                .build();
    }

    private static PrismSession exampleSession() throws Exception {
        return PrismSession.open(Path.of("..", "examples", "example.prismpack"));
    }
}
