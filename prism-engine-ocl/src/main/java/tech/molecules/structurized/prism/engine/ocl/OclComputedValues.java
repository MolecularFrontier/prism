package tech.molecules.structurized.prism.engine.ocl;

import com.actelion.research.chem.StereoMolecule;
import com.actelion.research.chem.descriptor.DescriptorHandlerLongFFP512;
import tech.molecules.structurized.prism.engine.CachePolicy;
import tech.molecules.structurized.prism.engine.ComputedValueDefinition;
import tech.molecules.structurized.prism.engine.PrismColumn;
import tech.molecules.structurized.prism.engine.PrismColumnType;
import tech.molecules.structurized.prism.engine.PrismTable;

import java.util.List;
import java.util.Objects;

public final class OclComputedValues {
    private OclComputedValues() {
    }

    public static ComputedValueDefinition<StereoMolecule> parsedMolecule(String structureColumnId) {
        return parsedMolecule(structureColumnId, null, null, CachePolicy.LAZY);
    }

    public static ComputedValueDefinition<StereoMolecule> parsedMolecule(String structureColumnId,
                                                                         OclStructureFormat format,
                                                                         String coordinatesColumnId,
                                                                         CachePolicy cachePolicy) {
        Objects.requireNonNull(structureColumnId, "structureColumnId");
        OclStructureParser parser = new OclStructureParser();
        OclStructureFormat explicitFormat = format;
        return ComputedValueDefinition.builder(OclComputedValueIds.molecule(structureColumnId), StereoMolecule.class)
                .displayName("OCL Molecule " + structureColumnId)
                .columnType(PrismColumnType.MOLECULE)
                .dependencyColumnIds(coordinatesColumnId == null || coordinatesColumnId.isBlank()
                        ? List.of(structureColumnId)
                        : List.of(structureColumnId, coordinatesColumnId))
                .cachePolicy(cachePolicy == null ? CachePolicy.LAZY : cachePolicy)
                .configurationFingerprint("structureColumn=" + structureColumnId
                        + ";coords=" + (coordinatesColumnId == null ? "" : coordinatesColumnId)
                        + ";format=" + (explicitFormat == null ? "metadata" : explicitFormat.name()))
                .provider((table, physicalRow, context) -> {
                    PrismColumn structureColumn = table.column(structureColumnId);
                    if (structureColumn.isMissing(physicalRow)) {
                        return null;
                    }
                    String coordinates = null;
                    if (coordinatesColumnId != null && !coordinatesColumnId.isBlank()) {
                        PrismColumn coordinatesColumn = table.column(coordinatesColumnId);
                        coordinates = coordinatesColumn.isMissing(physicalRow) ? null : coordinatesColumn.formattedValueAt(physicalRow);
                    }
                    OclStructureFormat effectiveFormat = explicitFormat == null
                            ? OclStructureFormat.fromMetadata(structureColumn.schema().structureFormat())
                            : explicitFormat;
                    return parser.parse(structureColumn.formattedValueAt(physicalRow), coordinates, effectiveFormat);
                })
                .build();
    }

    public static ComputedValueDefinition<long[]> ffp512(String structureColumnId) {
        return ffp512(structureColumnId, CachePolicy.LAZY);
    }

    public static ComputedValueDefinition<long[]> ffp512(String structureColumnId, CachePolicy cachePolicy) {
        Objects.requireNonNull(structureColumnId, "structureColumnId");
        String moleculeValueId = OclComputedValueIds.molecule(structureColumnId);
        return ComputedValueDefinition.builder(OclComputedValueIds.ffp512(structureColumnId), long[].class)
                .displayName("FFP512 " + structureColumnId)
                .columnType(PrismColumnType.TEXT)
                .dependencyComputedValueIds(List.of(moleculeValueId))
                .cachePolicy(cachePolicy == null ? CachePolicy.LAZY : cachePolicy)
                .implementationVersion(DescriptorHandlerLongFFP512.VERSION)
                .configurationFingerprint("structureColumn=" + structureColumnId)
                .provider((table, physicalRow, context) -> {
                    StereoMolecule molecule = context.value(moleculeValueId, physicalRow, StereoMolecule.class);
                    if (molecule == null) {
                        return null;
                    }
                    return DescriptorHandlerLongFFP512.getDefaultInstance().createDescriptor(molecule);
                })
                .build();
    }

    public static void registerParsedMoleculeAndFfp512(tech.molecules.structurized.prism.engine.PrismSession session,
                                                       String structureColumnId) {
        registerParsedMoleculeAndFfp512(session, structureColumnId, null, null, CachePolicy.LAZY);
    }

    public static void registerParsedMoleculeAndFfp512(tech.molecules.structurized.prism.engine.PrismSession session,
                                                       String structureColumnId,
                                                       OclStructureFormat format,
                                                       String coordinatesColumnId,
                                                       CachePolicy cachePolicy) {
        session.registerComputedValue(parsedMolecule(structureColumnId, format, coordinatesColumnId, cachePolicy));
        session.registerComputedValue(ffp512(structureColumnId, cachePolicy));
    }
}
