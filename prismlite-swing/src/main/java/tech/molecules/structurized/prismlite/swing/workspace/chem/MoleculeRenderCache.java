package tech.molecules.structurized.prismlite.swing.workspace.chem;

import com.actelion.research.chem.StereoMolecule;
import com.actelion.research.chem.coords.CoordinateInventor;
import tech.molecules.structurized.prism.engine.PrismColumn;
import tech.molecules.structurized.prism.engine.PrismTable;
import tech.molecules.structurized.prism.engine.ocl.OclStructureFormat;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class MoleculeRenderCache {
    private final PrismTable table;
    private final ConcurrentMap<Key, StereoMolecule> molecules = new ConcurrentHashMap<>();

    public MoleculeRenderCache(PrismTable table) {
        this.table = Objects.requireNonNull(table, "table");
    }

    public StereoMolecule molecule(PrismColumn column, int physicalRow) {
        if (column == null || physicalRow < 0 || physicalRow >= column.rowCount() || column.isMissing(physicalRow)) {
            return null;
        }
        String structure = column.formattedValueAt(physicalRow);
        if (structure == null || structure.isBlank()) {
            return null;
        }
        String coordinates = StructureCoordinateResolver.coordinateValue(table, column, physicalRow);
        Key key = new Key(column.id(), physicalRow, structure, coordinates);
        StereoMolecule molecule = molecules.computeIfAbsent(key, ignored -> parse(column, structure, coordinates));
        return molecule == null ? null : new StereoMolecule(molecule);
    }

    public int cachedMoleculeCount() {
        return molecules.size();
    }

    private static StereoMolecule parse(PrismColumn column, String structure, String coordinates) {
        try {
            OclStructureFormat format = OclStructureFormat.fromMetadata(column.schema().structureFormat());
            StereoMolecule molecule = MoleculeRenderUtil.parse(column, structure, coordinates);
            if (molecule != null && coordinates == null && format == OclStructureFormat.SMILES) {
                CoordinateInventor inventor = new CoordinateInventor();
                inventor.setRandomSeed(stableSeed(column.id(), structure));
                inventor.invent(molecule);
                molecule.ensureHelperArrays(StereoMolecule.cHelperCIP);
            }
            return molecule;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static long stableSeed(String columnId, String structure) {
        return 31L * String.valueOf(columnId).hashCode() + String.valueOf(structure).hashCode();
    }

    private record Key(String columnId, int physicalRow, String structure, String coordinates) {
    }
}
