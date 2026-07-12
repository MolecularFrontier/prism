package tech.molecules.structurized.prism.engine.ocl;

import com.actelion.research.chem.StereoMolecule;
import tech.molecules.structurized.prism.engine.PrismEvaluationContext;
import tech.molecules.structurized.prism.engine.PrismExecutionProfile;
import tech.molecules.structurized.prism.engine.PrismOperation;
import tech.molecules.structurized.prism.engine.PrismOperationException;
import tech.molecules.structurized.prism.engine.PrismOperationDescriptor;
import tech.molecules.structurized.prism.engine.PrismOperationEffect;
import tech.molecules.structurized.prism.engine.PrismOperationParameter;
import tech.molecules.structurized.prism.engine.PrismOperationResult;
import tech.molecules.structurized.prism.engine.PrismRowSet;
import tech.molecules.structurized.prism.engine.PrismSessionSnapshot;

import java.time.Instant;
import java.util.BitSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class OclCreateSubstructureRowSetOperation implements PrismOperation {
    public static final String ID = "ocl.create_substructure_row_set";

    private static final PrismOperationDescriptor DESCRIPTOR = new PrismOperationDescriptor(
            ID,
            "1",
            "Create substructure row set",
            "Find structures containing the query and create a named row set.",
            List.of(
                    PrismOperationParameter.requiredColumn("structureColumn", "Structure column", "chemical_structure"),
                    PrismOperationParameter.optionalRowSet("sourceRowSetId", "Source row set"),
                    PrismOperationParameter.requiredString("query", "Query structure"),
                    PrismOperationParameter.requiredEnum("queryFormat", "Query format", List.of("SMILES", "IDCODE", "MOLFILE")),
                    PrismOperationParameter.requiredEnum("stereoMode", "Stereo mode", List.of("IGNORE_STEREO", "REQUIRE_STEREO")),
                    PrismOperationParameter.requiredString("rowSetName", "Row-set name")
            ),
            Set.of(PrismOperationEffect.ADD_ROW_SETS),
            PrismExecutionProfile.INTERACTIVE
    );

    @Override
    public PrismOperationDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public PrismOperationResult execute(PrismSessionSnapshot snapshot, Map<String, Object> parameters) {
        String structureColumn = required(parameters, "structureColumn");
        String queryText = required(parameters, "query");
        String rowSetName = required(parameters, "rowSetName");
        String sourceRowSetId = optionalString(parameters, "sourceRowSetId");
        OclStructureFormat queryFormat = enumValue(parameters, "queryFormat", OclStructureFormat.SMILES, OclStructureFormat.class);
        OclStereoMode stereoMode = enumValue(parameters, "stereoMode", OclStereoMode.IGNORE_STEREO, OclStereoMode.class);

        if (snapshot.computedValues().findDefinition(OclComputedValueIds.molecule(structureColumn)).isEmpty()
                || snapshot.computedValues().findDefinition(OclComputedValueIds.ffp512(structureColumn)).isEmpty()) {
            throw new PrismOperationException(
                    "PRECONDITION_FAILED",
                    "OCL structure support is not registered for column '" + structureColumn + "'",
                    "structureColumn",
                    Map.of("columnId", structureColumn)
            );
        }

        StereoMolecule query;
        try {
            query = new OclStructureParser().parse(queryText, queryFormat);
        } catch (IllegalArgumentException exception) {
            throw new PrismOperationException(
                    "INVALID_PARAMETER",
                    "query structure is invalid",
                    "query",
                    Map.of("queryFormat", queryFormat.name()),
                    exception
            );
        }
        if (query == null) {
            throw new PrismOperationException("INVALID_PARAMETER", "query structure is empty", "query");
        }

        OclSubstructureFilter filter = new OclSubstructureFilter(structureColumn, query, stereoMode);
        BitSet matches = filter.evaluate(snapshot.table(), new PrismEvaluationContext(null, snapshot.computedValues(), snapshot.rowIdIndex()));
        if (sourceRowSetId != null) {
            BitSet scopedRows = new BitSet(snapshot.rowIdIndex().rowCount());
            PrismRowSet sourceRowSet = snapshot.rowSet(sourceRowSetId).orElseThrow(() -> new PrismOperationException(
                    "UNKNOWN_ROW_SET",
                    "unknown source row set '" + sourceRowSetId + "'",
                    "sourceRowSetId",
                    Map.of("rowSetId", sourceRowSetId)
            ));
            for (String rowId : sourceRowSet.rowIds()) {
                snapshot.rowIdIndex().physicalRow(rowId).ifPresent(row -> scopedRows.set(row));
            }
            matches.and(scopedRows);
        }
        LinkedHashSet<String> rowIds = new LinkedHashSet<>();
        for (int row = matches.nextSetBit(0); row >= 0; row = matches.nextSetBit(row + 1)) {
            rowIds.add(snapshot.rowIdIndex().rowId(row));
        }

        String rowSetId = "ocl:substructure:" + slug(rowSetName);
        PrismRowSet rowSet = new PrismRowSet(
                rowSetId,
                rowSetName,
                "Rows matching substructure query in " + structureColumn,
                rowIds,
                Map.of(
                        "operationId", ID,
                        "structureColumn", structureColumn,
                        "queryFormat", queryFormat.name(),
                        "stereoMode", stereoMode.name(),
                        "sourceRowSetId", sourceRowSetId == null ? "" : sourceRowSetId,
                        "createdAt", Instant.now().toString()
                )
        );

        return PrismOperationResult.builder()
                .addRowSet(rowSet)
                .provenance("operationId", ID)
                .provenance("matchCount", rowIds.size())
                .build();
    }

    private static String required(Map<String, Object> parameters, String id) {
        Object value = parameters.get(id);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException("missing required parameter '" + id + "'");
        }
        return String.valueOf(value).trim();
    }

    private static String optionalString(Map<String, Object> parameters, String id) {
        Object value = parameters.get(id);
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return String.valueOf(value).trim();
    }

    private static <E extends Enum<E>> E enumValue(Map<String, Object> parameters, String id, E defaultValue, Class<E> type) {
        Object value = parameters.get(id);
        if (value == null || String.valueOf(value).isBlank()) {
            return defaultValue;
        }
        return Enum.valueOf(type, String.valueOf(value).trim().toUpperCase(Locale.ROOT));
    }

    private static String slug(String value) {
        String slug = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        return slug.isBlank() ? "matches" : slug;
    }
}
