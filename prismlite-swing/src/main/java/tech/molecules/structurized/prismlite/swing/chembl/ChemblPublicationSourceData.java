package tech.molecules.structurized.prismlite.swing.chembl;

import java.util.List;
import java.util.Map;

public record ChemblPublicationSourceData(
        DocumentInfo document,
        List<ActivityInfo> activities,
        Map<String, AssayInfo> assaysById,
        Map<String, MoleculeInfo> moleculesById
) {
    public ChemblPublicationSourceData {
        activities = activities == null ? List.of() : List.copyOf(activities);
        assaysById = assaysById == null ? Map.of() : Map.copyOf(assaysById);
        moleculesById = moleculesById == null ? Map.of() : Map.copyOf(moleculesById);
    }

    public record DocumentInfo(
            String documentChemblId,
            String title,
            String journal,
            String year,
            String doi,
            String pubmedId
    ) {
    }

    public record ActivityInfo(
            String activityId,
            String moleculeChemblId,
            String parentMoleculeChemblId,
            String assayChemblId,
            String targetChemblId,
            String targetName,
            String standardType,
            String standardRelation,
            Double standardValue,
            String standardUnits,
            Double pchemblValue,
            String dataValidityComment,
            String potentialDuplicate,
            String canonicalSmiles,
            String moleculePrefName
    ) {
    }

    public record AssayInfo(
            String assayChemblId,
            String description,
            String assayType,
            Integer confidenceScore,
            String targetChemblId,
            String targetName,
            String targetOrganism
    ) {
    }

    public record MoleculeInfo(
            String moleculeChemblId,
            String prefName,
            String canonicalSmiles
    ) {
    }
}
