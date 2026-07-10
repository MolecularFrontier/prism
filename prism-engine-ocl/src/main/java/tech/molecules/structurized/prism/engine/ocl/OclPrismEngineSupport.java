package tech.molecules.structurized.prism.engine.ocl;

import tech.molecules.structurized.prism.engine.CachePolicy;
import tech.molecules.structurized.prism.engine.PrismColumn;
import tech.molecules.structurized.prism.engine.PrismColumnType;
import tech.molecules.structurized.prism.engine.PrismSession;

import java.util.ArrayList;
import java.util.List;

public final class OclPrismEngineSupport {
    private OclPrismEngineSupport() {
    }

    public static List<String> registerAllStructureColumns(PrismSession session) {
        return registerAllStructureColumns(session, CachePolicy.LAZY);
    }

    public static List<String> registerCapabilities(PrismSession session) {
        List<String> registeredColumns = registerAllStructureColumns(session);
        session.operationRegistry().register(new OclCreateSubstructureRowSetOperation());
        return registeredColumns;
    }

    public static List<String> registerAllStructureColumns(PrismSession session, CachePolicy cachePolicy) {
        ArrayList<String> registered = new ArrayList<>();
        for (PrismColumn column : session.baseTable().columns()) {
            if (column.type() == PrismColumnType.MOLECULE) {
                registerStructureColumn(session, column.id(), cachePolicy);
                registered.add(column.id());
            }
        }
        return List.copyOf(registered);
    }

    public static void registerStructureColumn(PrismSession session, String structureColumnId) {
        registerStructureColumn(session, structureColumnId, CachePolicy.LAZY);
    }

    public static void registerStructureColumn(PrismSession session, String structureColumnId, CachePolicy cachePolicy) {
        OclComputedValues.registerParsedMoleculeAndFfp512(session, structureColumnId, null, null, cachePolicy);
    }

    public static void registerStructureColumn(PrismSession session,
                                               String structureColumnId,
                                               OclStructureFormat format,
                                               String coordinatesColumnId,
                                               CachePolicy cachePolicy) {
        OclComputedValues.registerParsedMoleculeAndFfp512(session, structureColumnId, format, coordinatesColumnId, cachePolicy);
    }
}
