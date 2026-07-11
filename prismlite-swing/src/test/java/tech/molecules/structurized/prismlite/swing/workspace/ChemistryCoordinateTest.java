package tech.molecules.structurized.prismlite.swing.workspace;

import com.actelion.research.chem.StereoMolecule;
import org.junit.jupiter.api.Test;
import tech.molecules.structurized.prism.engine.FilterCapability;
import tech.molecules.structurized.prism.engine.PrismColumn;
import tech.molecules.structurized.prism.engine.PrismColumnSchema;
import tech.molecules.structurized.prism.engine.PrismColumnType;
import tech.molecules.structurized.prism.engine.PrismSession;
import tech.molecules.structurized.prism.engine.PrismTable;
import tech.molecules.structurized.prismlite.swing.workspace.chem.MoleculeRenderCache;
import tech.molecules.structurized.prismlite.swing.workspace.chem.StructureCoordinateResolver;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChemistryCoordinateTest {
    @Test
    void resolverUsesExplicitCoordinateMetadata() {
        TestColumn structure = new TestColumn(new PrismColumnSchema(
                "idcode",
                PrismColumnType.MOLECULE,
                "Structure",
                "chemical_structure",
                null,
                null,
                null,
                null,
                "idcode",
                Map.of("coordinatesColumnId", "idcoords")
        ), "eM@Hz@", "gJq@@");
        TestColumn coordinates = new TestColumn(textSchema("idcoords"), "coords1", "coords2");
        TestTable table = new TestTable(List.of(structure, coordinates));

        assertEquals("idcoords", StructureCoordinateResolver.coordinateColumnId(table, structure));
        assertEquals("coords2", StructureCoordinateResolver.coordinateValue(table, structure, 1));
    }

    @Test
    void resolverUsesConservativeNameHeuristic() {
        TestColumn structure = new TestColumn(new PrismColumnSchema(
                "mol",
                PrismColumnType.MOLECULE,
                "Structure",
                "chemical_structure",
                null,
                null,
                null,
                null,
                "idcode",
                Map.of()
        ), "eM@Hz@");
        TestColumn coordinates = new TestColumn(textSchema("mol_2d_coordinates"), "coords1");
        TestTable table = new TestTable(List.of(structure, coordinates));

        assertEquals("mol_2d_coordinates", StructureCoordinateResolver.coordinateColumnId(table, structure));
    }

    @Test
    void renderCacheKeepsStableParsedMoleculePerRow() throws Exception {
        PrismSession session = PrismSession.open(Path.of("..", "examples", "example.prismpack"));
        PrismColumn structure = session.table().column("smiles");
        MoleculeRenderCache cache = new MoleculeRenderCache(session.table());

        StereoMolecule first = cache.molecule(structure, 0);
        StereoMolecule second = cache.molecule(structure, 0);

        assertNotNull(first);
        assertNotNull(second);
        assertTrue(first.getAllAtoms() > 0);
        assertEquals(1, cache.cachedMoleculeCount());
    }

    private static PrismColumnSchema textSchema(String id) {
        return new PrismColumnSchema(id, PrismColumnType.TEXT, id, null, null, null, null, null, null, Map.of());
    }

    private record TestTable(List<PrismColumn> columns) implements PrismTable {
        @Override
        public int rowCount() {
            return columns.isEmpty() ? 0 : columns.getFirst().rowCount();
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
                if (columns.get(index).id().equals(columnId)) {
                    return index;
                }
            }
            throw new IllegalArgumentException("unknown column '" + columnId + "'");
        }
    }

    private record TestColumn(PrismColumnSchema schema, String... values) implements PrismColumn {
        @Override
        public String id() {
            return schema.id();
        }

        @Override
        public PrismColumnType type() {
            return schema.type();
        }

        @Override
        public int rowCount() {
            return values.length;
        }

        @Override
        public boolean isMissing(int physicalRow) {
            return values[physicalRow] == null || values[physicalRow].isBlank();
        }

        @Override
        public Object valueAt(int physicalRow) {
            return values[physicalRow];
        }

        @Override
        public String formattedValueAt(int physicalRow) {
            return values[physicalRow];
        }

        @Override
        public Set<FilterCapability> filterCapabilities() {
            return Set.of();
        }
    }
}
