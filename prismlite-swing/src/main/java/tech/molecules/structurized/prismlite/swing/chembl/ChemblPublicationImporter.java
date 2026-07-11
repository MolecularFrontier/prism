package tech.molecules.structurized.prismlite.swing.chembl;

import tech.molecules.structurized.prism.model.EndpointDataType;
import tech.molecules.structurized.prism.model.EndpointDefinition;
import tech.molecules.structurized.prism.model.EndpointType;
import tech.molecules.structurized.prism.model.EvaluationMode;
import tech.molecules.structurized.prism.model.NumericEndpointMeta;
import tech.molecules.structurized.prism.model.NumericScale;
import tech.molecules.structurized.prism.provider.SubjectRecord;
import tech.molecules.structurized.prism.provider.SubjectSet;
import tech.molecules.structurized.prism.provider.inmemory.InMemoryPrismDataset;
import tech.molecules.structurized.prism.query.EndpointValueRecord;
import tech.molecules.structurized.prism.result.NumericResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public final class ChemblPublicationImporter {
    private static final String PROJECT = "ChEMBL Publications";
    private static final Set<String> ALLOWED_TYPES = Set.of("IC50", "KI", "KD", "EC50", "XC50", "AC50", "POTENCY", "ED50");
    private static final Pattern NON_ID = Pattern.compile("[^a-z0-9]+");

    public InMemoryPrismDataset importPublication(ChemblPublicationSourceData source, ChemblPublicationImportOptions options) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(options, "options");
        List<UsableActivity> usableActivities = source.activities().stream()
                .map(activity -> usableActivity(source, activity))
                .filter(Objects::nonNull)
                .toList();
        if (usableActivities.isEmpty()) {
            throw new IllegalArgumentException("No usable pChEMBL activities found for " + options.documentChemblId());
        }

        List<EndpointKey> selectedKeys = selectEndpointKeys(usableActivities, options.minCompoundsPerEndpoint(), options.maxEndpoints());
        if (selectedKeys.isEmpty()) {
            throw new IllegalArgumentException("No endpoint groups passed the ChEMBL import filters for " + options.documentChemblId());
        }

        Map<EndpointKey, EndpointBuildData> endpointData = endpointData(usableActivities, selectedKeys, options.documentChemblId());
        LinkedHashMap<String, SubjectBuildData> subjects = new LinkedHashMap<>();
        LinkedHashMap<ValueKey, List<UsableActivity>> valueBuckets = new LinkedHashMap<>();
        Set<EndpointKey> selected = new LinkedHashSet<>(selectedKeys);
        for (UsableActivity activity : usableActivities) {
            if (!selected.contains(activity.key())) {
                continue;
            }
            SubjectBuildData subject = subjects.computeIfAbsent(activity.subjectId(), id -> new SubjectBuildData(id, activity.smiles(), activity.moleculeName()));
            subject.sourceMoleculeIds().add(activity.moleculeChemblId());
            if (hasText(activity.targetName())) {
                subject.targetNames().add(activity.targetName());
            }
            String endpointId = endpointData.get(activity.key()).endpointId();
            valueBuckets.computeIfAbsent(new ValueKey(activity.subjectId(), endpointId), ignored -> new ArrayList<>()).add(activity);
        }

        InMemoryPrismDataset.Builder builder = InMemoryPrismDataset.builder();
        for (SubjectBuildData subject : subjects.values()) {
            builder.addSubjectRecord(SubjectRecord.builder()
                    .subjectId(subject.subjectId())
                    .structureId(subject.subjectId())
                    .project(PROJECT)
                    .series(options.documentChemblId())
                    .smiles(subject.smiles())
                    .metadata(Map.of(
                            "source_document", options.documentChemblId(),
                            "molecule_name", nullToEmpty(subject.moleculeName()),
                            "source_molecule_chembl_ids", String.join("|", subject.sourceMoleculeIds()),
                            "target_names", String.join("|", subject.targetNames())
                    ))
                    .build());
        }

        for (EndpointBuildData endpoint : endpointData.values()) {
            builder.addEndpointDefinition(endpoint.definition());
        }

        for (Map.Entry<ValueKey, List<UsableActivity>> entry : valueBuckets.entrySet()) {
            List<UsableActivity> bucket = entry.getValue();
            List<Double> values = bucket.stream().map(UsableActivity::pchemblValue).sorted().toList();
            List<String> activityIds = bucket.stream().map(UsableActivity::activityId).filter(ChemblPublicationImporter::hasText).toList();
            UsableActivity first = bucket.getFirst();
            builder.addEndpointValue(EndpointValueRecord.builder()
                    .subjectId(entry.getKey().subjectId())
                    .endpointId(entry.getKey().endpointId())
                    .result(NumericResult.builder()
                            .mean(median(values))
                            .n(bucket.size())
                            .rawValues(values)
                            .rawValueIds(activityIds)
                            .putDetail("assay_chembl_id", first.assayChemblId())
                            .putDetail("target_chembl_id", nullToEmpty(first.targetChemblId()))
                            .putDetail("standard_type", first.standardType())
                            .putDetail("standard_relation", first.standardRelation())
                            .putDetail("standard_units", first.standardUnits())
                            .putDetail("standard_values", standardValues(bucket))
                            .build())
                    .build());
        }

        String allSetId = "document:" + options.documentChemblId().toLowerCase(Locale.ROOT) + ":all";
        builder.addSubjectSet(SubjectSet.builder()
                .id(allSetId)
                .name("All compounds in " + options.documentChemblId())
                .setType("DOCUMENT")
                .subjectSetScope("SUBJECTS")
                .description("All compounds imported from this ChEMBL publication.")
                .build());
        subjects.keySet().forEach(subjectId -> builder.addSubjectMembership(allSetId, subjectId));

        for (EndpointBuildData endpoint : endpointData.values()) {
            String setId = "assay:" + endpoint.endpointId() + ":measured";
            builder.addSubjectSet(SubjectSet.builder()
                    .id(setId)
                    .name("Measured: " + endpoint.assayChemblId() + " " + endpoint.standardType())
                    .setType("ASSAY_MEASURED")
                    .subjectSetScope("ASSAYS")
                    .description("Subjects with a measured result for this imported ChEMBL endpoint.")
                    .build());
            endpoint.measuredSubjectIds().forEach(subjectId -> builder.addSubjectMembership(setId, subjectId));
        }

        return builder.build();
    }

    private UsableActivity usableActivity(ChemblPublicationSourceData source, ChemblPublicationSourceData.ActivityInfo activity) {
        if (!isUsableActivity(activity)) {
            return null;
        }
        String subjectId = firstText(activity.parentMoleculeChemblId(), activity.moleculeChemblId());
        ChemblPublicationSourceData.MoleculeInfo parent = source.moleculesById().get(subjectId);
        ChemblPublicationSourceData.MoleculeInfo molecule = source.moleculesById().get(activity.moleculeChemblId());
        String smiles = firstText(parent == null ? null : parent.canonicalSmiles(), molecule == null ? null : molecule.canonicalSmiles(), activity.canonicalSmiles());
        if (!hasText(subjectId) || !hasText(smiles)) {
            return null;
        }
        ChemblPublicationSourceData.AssayInfo assay = source.assaysById().get(activity.assayChemblId());
        String targetId = firstText(activity.targetChemblId(), assay == null ? null : assay.targetChemblId(), "target_unknown");
        String targetName = firstText(activity.targetName(), assay == null ? null : assay.targetName(), targetId);
        Integer confidence = assay == null ? null : assay.confidenceScore();
        String assayDescription = assay == null ? null : assay.description();
        String moleculeName = firstText(parent == null ? null : parent.prefName(), molecule == null ? null : molecule.prefName(), activity.moleculePrefName());
        return new UsableActivity(
                activity.activityId(),
                activity.moleculeChemblId(),
                subjectId,
                smiles,
                moleculeName,
                activity.assayChemblId(),
                assayDescription,
                targetId,
                targetName,
                activity.standardType(),
                activity.standardRelation(),
                activity.standardValue(),
                activity.standardUnits(),
                activity.pchemblValue(),
                confidence == null ? 0 : confidence
        );
    }

    private static boolean isUsableActivity(ChemblPublicationSourceData.ActivityInfo activity) {
        if (activity == null || activity.pchemblValue() == null || !hasText(activity.assayChemblId()) || !hasText(activity.standardType())) {
            return false;
        }
        if (!ALLOWED_TYPES.contains(activity.standardType().trim().toUpperCase(Locale.ROOT))) {
            return false;
        }
        if (!"=".equals(nullToEmpty(activity.standardRelation()))) {
            return false;
        }
        if (!"nM".equals(nullToEmpty(activity.standardUnits()))) {
            return false;
        }
        if ("1".equals(nullToEmpty(activity.potentialDuplicate())) || "true".equalsIgnoreCase(nullToEmpty(activity.potentialDuplicate()))) {
            return false;
        }
        String validity = nullToEmpty(activity.dataValidityComment());
        return validity.isEmpty() || "Manually validated".equals(validity);
    }

    private static List<EndpointKey> selectEndpointKeys(List<UsableActivity> activities, int minCompounds, int maxEndpoints) {
        LinkedHashMap<EndpointKey, EndpointStats> stats = new LinkedHashMap<>();
        for (UsableActivity activity : activities) {
            EndpointStats endpointStats = stats.computeIfAbsent(activity.key(), ignored -> new EndpointStats());
            endpointStats.subjectIds().add(activity.subjectId());
            endpointStats.values().add(activity.pchemblValue());
            endpointStats.confidenceScore(Math.max(endpointStats.confidenceScore(), activity.confidenceScore()));
        }
        return stats.entrySet().stream()
                .filter(entry -> entry.getValue().subjectIds().size() >= minCompounds)
                .sorted(Comparator
                        .<Map.Entry<EndpointKey, EndpointStats>>comparingInt(entry -> entry.getValue().subjectIds().size()).reversed()
                        .thenComparing(Comparator.comparingInt((Map.Entry<EndpointKey, EndpointStats> entry) -> entry.getValue().confidenceScore()).reversed())
                        .thenComparing(Comparator.comparingDouble((Map.Entry<EndpointKey, EndpointStats> entry) -> entry.getValue().range()).reversed())
                        .thenComparing(Comparator.comparingInt((Map.Entry<EndpointKey, EndpointStats> entry) -> entry.getValue().values().size()).reversed())
                        .thenComparing(entry -> entry.getKey().idText()))
                .limit(maxEndpoints)
                .map(Map.Entry::getKey)
                .toList();
    }

    private static Map<EndpointKey, EndpointBuildData> endpointData(List<UsableActivity> activities, List<EndpointKey> selectedKeys, String documentId) {
        LinkedHashMap<EndpointKey, EndpointStats> statsByKey = new LinkedHashMap<>();
        LinkedHashMap<EndpointKey, UsableActivity> firstByKey = new LinkedHashMap<>();
        Set<EndpointKey> selected = new LinkedHashSet<>(selectedKeys);
        for (UsableActivity activity : activities) {
            if (!selected.contains(activity.key())) {
                continue;
            }
            EndpointStats stats = statsByKey.computeIfAbsent(activity.key(), ignored -> new EndpointStats());
            stats.subjectIds().add(activity.subjectId());
            stats.values().add(activity.pchemblValue());
            stats.confidenceScore(Math.max(stats.confidenceScore(), activity.confidenceScore()));
            firstByKey.putIfAbsent(activity.key(), activity);
        }
        LinkedHashMap<EndpointKey, EndpointBuildData> out = new LinkedHashMap<>();
        for (EndpointKey key : selectedKeys) {
            EndpointStats stats = statsByKey.get(key);
            UsableActivity first = firstByKey.get(key);
            if (stats == null || first == null) {
                continue;
            }
            String endpointId = endpointId(documentId, key);
            String name = first.standardType() + " pChEMBL - " + first.targetName() + " [" + first.assayChemblId() + "]";
            EndpointDefinition definition = EndpointDefinition.builder()
                    .id(endpointId)
                    .name(name)
                    .path("Activity/" + first.targetName() + "/" + first.standardType())
                    .datatype(EndpointDataType.NUMERIC)
                    .endpointType(EndpointType.MEASURED)
                    .evaluationMode(EvaluationMode.IMMEDIATE)
                    .numericMeta(NumericEndpointMeta.builder()
                            .scale(NumericScale.LOG)
                            .domainLowerBound(stats.min())
                            .domainUpperBound(stats.max())
                            .build())
                    .description("ChEMBL pChEMBL activity from " + first.assayChemblId() + ". Assay: " + nullToEmpty(first.assayDescription()))
                    .build();
            out.put(key, new EndpointBuildData(endpointId, first.assayChemblId(), first.standardType(), definition, stats.subjectIds()));
        }
        return out;
    }

    private static String endpointId(String documentId, EndpointKey key) {
        return "chembl." + documentId.toLowerCase(Locale.ROOT) + "." + key.assayChemblId().toLowerCase(Locale.ROOT) + "."
                + slug(key.standardType()) + "." + slug(key.targetChemblId());
    }

    private static String standardValues(List<UsableActivity> bucket) {
        return bucket.stream()
                .map(UsableActivity::standardValue)
                .filter(Objects::nonNull)
                .map(value -> Double.toString(value))
                .limit(50)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private static double median(List<Double> values) {
        int size = values.size();
        if (size == 0) {
            throw new IllegalArgumentException("cannot compute median of empty values");
        }
        if (size % 2 == 1) {
            return values.get(size / 2);
        }
        return (values.get(size / 2 - 1) + values.get(size / 2)) / 2.0;
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String slug(String value) {
        String text = nullToEmpty(value).toLowerCase(Locale.ROOT);
        String slug = NON_ID.matcher(text).replaceAll("_").replaceAll("^_+|_+$", "");
        return slug.isEmpty() ? "unknown" : slug;
    }

    private record EndpointKey(String assayChemblId, String standardType, String targetChemblId) {
        String idText() {
            return assayChemblId + ":" + standardType + ":" + targetChemblId;
        }
    }

    private record ValueKey(String subjectId, String endpointId) {
    }

    private record UsableActivity(
            String activityId,
            String moleculeChemblId,
            String subjectId,
            String smiles,
            String moleculeName,
            String assayChemblId,
            String assayDescription,
            String targetChemblId,
            String targetName,
            String standardType,
            String standardRelation,
            Double standardValue,
            String standardUnits,
            Double pchemblValue,
            int confidenceScore
    ) {
        EndpointKey key() {
            return new EndpointKey(assayChemblId, standardType, targetChemblId);
        }
    }

    private record SubjectBuildData(
            String subjectId,
            String smiles,
            String moleculeName,
            LinkedHashSet<String> sourceMoleculeIds,
            LinkedHashSet<String> targetNames
    ) {
        SubjectBuildData(String subjectId, String smiles, String moleculeName) {
            this(subjectId, smiles, moleculeName, new LinkedHashSet<>(), new LinkedHashSet<>());
        }
    }

    private record EndpointBuildData(
            String endpointId,
            String assayChemblId,
            String standardType,
            EndpointDefinition definition,
            Set<String> measuredSubjectIds
    ) {
    }

    private static final class EndpointStats {
        private final LinkedHashSet<String> subjectIds = new LinkedHashSet<>();
        private final ArrayList<Double> values = new ArrayList<>();
        private int confidenceScore;

        LinkedHashSet<String> subjectIds() {
            return subjectIds;
        }

        ArrayList<Double> values() {
            return values;
        }

        int confidenceScore() {
            return confidenceScore;
        }

        void confidenceScore(int confidenceScore) {
            this.confidenceScore = confidenceScore;
        }

        double min() {
            return values.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        }

        double max() {
            return values.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        }

        double range() {
            return max() - min();
        }
    }
}
