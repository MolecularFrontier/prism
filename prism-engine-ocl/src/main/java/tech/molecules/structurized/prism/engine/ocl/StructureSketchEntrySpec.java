package tech.molecules.structurized.prism.engine.ocl;

/**
 * One depicted structure in a {@code structure-sketch} block. The SMILES is carried literally in
 * the report, so these structures need not exist in the dataset.
 */
public record StructureSketchEntrySpec(
        String smiles,
        String label,
        String caption
) {
    public StructureSketchEntrySpec {
        if (smiles == null || smiles.isBlank()) {
            throw new IllegalArgumentException("structure-sketch entry smiles must not be blank");
        }
        smiles = smiles.trim();
        label = label == null || label.isBlank() ? null : label.trim();
        caption = caption == null || caption.isBlank() ? null : caption.trim();
    }
}
