package tech.molecules.structurized.prismlite.swing.workspace.chem;

import com.actelion.research.chem.StereoMolecule;
import tech.molecules.structurized.prism.engine.PrismColumn;
import tech.molecules.structurized.prism.engine.ocl.OclStructureFormat;
import tech.molecules.structurized.prism.engine.ocl.OclStructureParser;

public final class MoleculeRenderUtil {
    private MoleculeRenderUtil() {
    }

    public static StereoMolecule parse(PrismColumn column, int physicalRow) {
        if (column == null || physicalRow < 0 || column.isMissing(physicalRow)) {
            return null;
        }
        return parse(column, column.formattedValueAt(physicalRow), null);
    }

    public static StereoMolecule parse(PrismColumn column, Object value) {
        return parse(column, value, null);
    }

    public static StereoMolecule parse(PrismColumn column, Object value, String coordinates) {
        if (column == null || value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            OclStructureFormat format = OclStructureFormat.fromMetadata(column.schema().structureFormat());
            return new OclStructureParser().parse(text, coordinates, format);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
