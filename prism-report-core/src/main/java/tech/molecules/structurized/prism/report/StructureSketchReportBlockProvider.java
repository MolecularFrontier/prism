package tech.molecules.structurized.prism.report;

import com.actelion.research.chem.SmilesParser;
import com.actelion.research.chem.StereoMolecule;
import com.fasterxml.jackson.databind.JsonNode;
import tech.molecules.structurized.prism.engine.PrismSession;
import tech.molecules.structurized.prism.engine.ocl.StructureSketchEntrySpec;
import tech.molecules.structurized.prism.engine.ocl.StructureSketchViewSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Provider for the {@code structure-sketch} block: a grid of structures given inline as SMILES.
 *
 * <p>The block references no row set and no column, so validation is limited to SMILES
 * parseability. A structure that cannot be parsed is reported as an error naming its index, so the
 * author can find it without bisecting the block.
 */
final class StructureSketchReportBlockProvider implements PrismReportBlockProvider {
    private static final Set<String> FIELDS = Set.of("type", "id", "title", "structures", "gridColumns");
    private static final Set<String> ENTRY_FIELDS = Set.of("smiles", "label", "caption");

    static void register(PrismReportBlockRegistry registry) {
        registry.register(new StructureSketchReportBlockProvider());
    }

    @Override
    public String type() {
        return StructureSketchViewSpec.VIEW_TYPE;
    }

    @Override
    public PrismReportBlock parse(JsonNode json, PrismReportParseContext context) {
        context.rejectUnknownFields(json, FIELDS);
        ArrayList<StructureSketchEntrySpec> structures = new ArrayList<>();
        JsonNode array = json.get("structures");
        if (array == null || !array.isArray() || array.isEmpty()) {
            context.error("MISSING_STRUCTURES", "structure-sketch requires a non-empty 'structures' array.");
        } else if (array.size() > StructureSketchViewSpec.HARD_MAX_STRUCTURES) {
            context.error("TOO_MANY_STRUCTURES", "structure-sketch supports at most "
                    + StructureSketchViewSpec.HARD_MAX_STRUCTURES + " structures.");
        } else {
            for (int index = 0; index < array.size(); index++) {
                JsonNode item = array.get(index);
                if (item.isTextual() && !item.textValue().isBlank()) {
                    structures.add(new StructureSketchEntrySpec(item.textValue(), null, null));
                    continue;
                }
                if (!item.isObject()) {
                    context.error("INVALID_STRUCTURE",
                            "structures[" + index + "] must be a SMILES string or an object.");
                    continue;
                }
                context.rejectUnknownFields(item, ENTRY_FIELDS);
                String smiles = context.requiredText(item, "smiles");
                if (smiles != null) {
                    structures.add(new StructureSketchEntrySpec(smiles,
                            context.optionalText(item, "label"), context.optionalText(item, "caption")));
                }
            }
        }
        int gridColumns = context.optionalInt(json, "gridColumns", StructureSketchViewSpec.DEFAULT_GRID_COLUMNS);
        if (gridColumns < 1 || gridColumns > StructureSketchViewSpec.MAX_GRID_COLUMNS) {
            context.error("INVALID_GRID_COLUMNS", "gridColumns must be between 1 and "
                    + StructureSketchViewSpec.MAX_GRID_COLUMNS + ".");
            gridColumns = Math.max(1, Math.min(StructureSketchViewSpec.MAX_GRID_COLUMNS, gridColumns));
        }
        if (structures.isEmpty()) return null;
        StructureSketchViewSpec spec = new StructureSketchViewSpec(context.blockId(),
                context.optionalText(json, "title"), structures, gridColumns);
        return new PrismViewReportBlock(context.blockId(), type(), spec, context.sourceLine());
    }

    @Override
    public void validate(PrismReportBlock block, PrismSession session,
                         List<PrismReportDiagnostic> diagnostics) {
        EmbeddedPrismViewReportBlock embedded = (EmbeddedPrismViewReportBlock) block;
        StructureSketchViewSpec spec = (StructureSketchViewSpec) embedded.specification();
        for (int index = 0; index < spec.structures().size(); index++) {
            StructureSketchEntrySpec entry = spec.structures().get(index);
            if (!parses(entry.smiles())) {
                diagnostics.add(new PrismReportDiagnostic(PrismReportSeverity.ERROR,
                        "INVALID_SMILES",
                        "structures[" + index + "] is not a parseable SMILES: " + entry.smiles(),
                        embedded.blockId(), embedded.sourceLine(), 1));
            }
        }
    }

    private static boolean parses(String smiles) {
        try {
            StereoMolecule molecule = new StereoMolecule();
            new SmilesParser().parse(molecule, smiles);
            return molecule.getAllAtoms() > 0;
        } catch (Exception exception) {
            return false;
        }
    }
}
