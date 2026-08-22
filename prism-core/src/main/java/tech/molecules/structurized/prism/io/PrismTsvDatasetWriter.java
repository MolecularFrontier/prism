package tech.molecules.structurized.prism.io;

import tech.molecules.structurized.prism.model.CategoryDefinition;
import tech.molecules.structurized.prism.model.EndpointDefinition;
import tech.molecules.structurized.prism.provider.SubjectRecord;
import tech.molecules.structurized.prism.provider.SubjectSet;
import tech.molecules.structurized.prism.provider.inmemory.InMemoryPrismDataset;
import tech.molecules.structurized.prism.query.EndpointValueRecord;
import tech.molecules.structurized.prism.pack.EndpointResultCodec;
import tech.molecules.structurized.prism.result.AbstractEndpointResult;
import tech.molecules.structurized.prism.result.BooleanResult;
import tech.molecules.structurized.prism.result.CategoricalResult;
import tech.molecules.structurized.prism.result.EndpointResult;
import tech.molecules.structurized.prism.result.NumericResult;
import tech.molecules.structurized.prism.result.OptionalNumericResult;
import tech.molecules.structurized.prism.result.TextResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Writes deterministic canonical PRISM TSV bundles and immutable snapshots. */
public final class PrismTsvDatasetWriter {
    public static final String SNAPSHOT_MANIFEST_FILE_NAME = "snapshot.prism.json";

    private PrismTsvDatasetWriter() {}

    public static void write(Path directory, InMemoryPrismDataset dataset) throws IOException {
        Objects.requireNonNull(directory, "directory must not be null");
        Objects.requireNonNull(dataset, "dataset must not be null");
        Files.createDirectories(directory);
        writeDatasetFiles(directory, dataset);
    }

    public static PrismDatasetSnapshot writeSnapshot(Path directory,
                                                     InMemoryPrismDataset dataset,
                                                     PrismSnapshotDescriptor descriptor) throws IOException {
        Objects.requireNonNull(directory, "directory must not be null");
        Objects.requireNonNull(dataset, "dataset must not be null");
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        Path target = directory.toAbsolutePath().normalize();
        if (Files.exists(target)) {
            throw new IllegalArgumentException("snapshot destination already exists: " + target);
        }
        Path parent = target.getParent();
        if (parent == null) throw new IllegalArgumentException("snapshot destination requires a parent directory");
        Files.createDirectories(parent);
        Path temporary = Files.createTempDirectory(parent, "." + target.getFileName() + ".tmp-");
        boolean published = false;
        try {
            Map<String, Long> rowCounts = writeDatasetFiles(temporary, dataset);
            ArrayList<PrismSnapshotFile> files = new ArrayList<>();
            for (Map.Entry<String, Long> entry : rowCounts.entrySet()) {
                Path file = temporary.resolve(entry.getKey());
                files.add(new PrismSnapshotFile(entry.getKey(), sha256(file), entry.getValue()));
            }
            files.sort(Comparator.comparing(PrismSnapshotFile::path));
            String snapshotId = identity(descriptor, files);
            PrismSnapshotManifest manifest = new PrismSnapshotManifest(
                    PrismSnapshotManifest.CURRENT_SCHEMA_VERSION,
                    PrismSnapshotManifest.FORMAT,
                    snapshotId,
                    descriptor,
                    files
            );
            Files.writeString(temporary.resolve(SNAPSHOT_MANIFEST_FILE_NAME),
                    PrismSnapshotJson.stringify(canonicalValue(manifestMap(manifest, true))), StandardCharsets.UTF_8);
            validateSnapshotDirectory(temporary, manifest);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target);
            }
            published = true;
            return new PrismDatasetSnapshot(dataset, manifest);
        } finally {
            if (!published) deleteTree(temporary);
        }
    }

    static PrismSnapshotManifest readManifest(Path directory) throws IOException {
        Path path = directory.resolve(SNAPSHOT_MANIFEST_FILE_NAME);
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("snapshot manifest not found: " + path);
        }
        Object parsed = PrismSnapshotJson.parse(Files.readString(path, StandardCharsets.UTF_8));
        return parseManifest(mapping(parsed, "snapshot manifest"));
    }

    static void validateSnapshotDirectory(Path directory, PrismSnapshotManifest manifest) throws IOException {
        Set<String> expected = new LinkedHashSet<>();
        expected.add(SNAPSHOT_MANIFEST_FILE_NAME);
        for (PrismSnapshotFile file : manifest.files()) {
            Path resolved = directory.resolve(file.path()).normalize();
            if (!resolved.startsWith(directory.normalize()) || Files.isSymbolicLink(resolved)) {
                throw new IllegalArgumentException("unsafe snapshot file path: " + file.path());
            }
            if (!Files.isRegularFile(resolved)) {
                throw new IllegalArgumentException("snapshot file not found: " + file.path());
            }
            String actual = sha256(resolved);
            if (!actual.equals(file.sha256())) {
                throw new IllegalArgumentException("snapshot file digest mismatch: " + file.path());
            }
            expected.add(file.path());
        }
        try (var paths = Files.list(directory)) {
            for (Path path : paths.toList()) {
                String relative = directory.relativize(path).toString().replace('\\', '/');
                if (Files.isSymbolicLink(path) || !Files.isRegularFile(path)) {
                    throw new IllegalArgumentException("unexpected snapshot entry: " + relative);
                }
                if (!expected.contains(relative)) {
                    throw new IllegalArgumentException("unexpected snapshot file: " + relative);
                }
            }
        }
        String calculated = identity(manifest.descriptor(), manifest.files());
        if (!calculated.equals(manifest.snapshotId())) {
            throw new IllegalArgumentException("snapshot identity mismatch: expected " + manifest.snapshotId() + " but calculated " + calculated);
        }
    }

    private static Map<String, Long> writeDatasetFiles(Path directory, InMemoryPrismDataset dataset) throws IOException {
        LinkedHashMap<String, Long> rows = new LinkedHashMap<>();
        List<EndpointDefinition> endpoints = dataset.getEndpointDefinitions().stream()
                .sorted(Comparator.comparing(EndpointDefinition::getId)).toList();
        List<SubjectRecord> subjects = dataset.getSubjectRecords().stream()
                .sorted(Comparator.comparing(SubjectRecord::getSubjectId)).toList();
        List<EndpointValueRecord> values = dataset.getEndpointValues().stream()
                .sorted(Comparator.comparing(EndpointValueRecord::getSubjectId)
                        .thenComparing(EndpointValueRecord::getEndpointId)).toList();
        List<SubjectSet> subjectSets = dataset.getSubjectSets().stream()
                .sorted(Comparator.comparing(SubjectSet::getId)).toList();

        write(directory, PrismTsvDatasetLoader.ENDPOINTS_FILE_NAME, endpointsTsv(endpoints));
        rows.put(PrismTsvDatasetLoader.ENDPOINTS_FILE_NAME, (long) endpoints.size());
        write(directory, PrismTsvDatasetLoader.SUBJECTS_FILE_NAME, subjectsTsv(subjects));
        rows.put(PrismTsvDatasetLoader.SUBJECTS_FILE_NAME, (long) subjects.size());
        write(directory, PrismTsvDatasetLoader.VALUES_FILE_NAME, valuesTsv(values));
        rows.put(PrismTsvDatasetLoader.VALUES_FILE_NAME, (long) values.size());
        if (!subjectSets.isEmpty()) {
            write(directory, PrismTsvDatasetLoader.SUBJECT_SETS_FILE_NAME, subjectSetsTsv(subjectSets));
            rows.put(PrismTsvDatasetLoader.SUBJECT_SETS_FILE_NAME, (long) subjectSets.size());
            long membershipCount = subjectSets.stream().mapToLong(set -> dataset.getSubjectsForSet(set.getId()).size()).sum();
            write(directory, PrismTsvDatasetLoader.SUBJECT_SET_MEMBERSHIPS_FILE_NAME, membershipsTsv(dataset, subjectSets));
            rows.put(PrismTsvDatasetLoader.SUBJECT_SET_MEMBERSHIPS_FILE_NAME, membershipCount);
        }
        return Map.copyOf(rows);
    }

    private static String endpointsTsv(List<EndpointDefinition> endpoints) {
        StringBuilder out = new StringBuilder("endpoint_id\tname\tpath\tdatatype\tendpoint_type\tevaluation_mode\tunit\tscale\tdomain_lower_bound\tdomain_upper_bound\tdescription\tcategories\n");
        for (EndpointDefinition endpoint : endpoints) {
            List<String> categories = endpoint.getCategories().stream()
                    .sorted(Comparator.comparing(CategoryDefinition::getId))
                    .map(category -> category.getId() + "=" + category.getName()).toList();
            out.append(row(List.of(
                    endpoint.getId(), endpoint.getName(), endpoint.getPath(), endpoint.getDatatype().name(),
                    endpoint.getEndpointType().name(), endpoint.getEvaluationMode().name(), nullable(endpoint.getUnit()),
                    endpoint.getNumericMeta() == null || endpoint.getNumericMeta().getScale() == null ? "" : endpoint.getNumericMeta().getScale().name(),
                    endpoint.getNumericMeta() == null || endpoint.getNumericMeta().getDomainLowerBound() == null ? "" : String.valueOf(endpoint.getNumericMeta().getDomainLowerBound()),
                    endpoint.getNumericMeta() == null || endpoint.getNumericMeta().getDomainUpperBound() == null ? "" : String.valueOf(endpoint.getNumericMeta().getDomainUpperBound()),
                    nullable(endpoint.getDescription()), String.join(";", categories)
            )));
        }
        return out.toString();
    }

    private static String subjectsTsv(List<SubjectRecord> subjects) {
        LinkedHashSet<String> metadataColumns = new LinkedHashSet<>();
        subjects.stream().flatMap(subject -> subject.getMetadata().keySet().stream()).sorted().forEach(metadataColumns::add);
        ArrayList<String> header = new ArrayList<>(List.of("subject_id", "structure_id", "batch_id", "project", "series", "smiles"));
        header.addAll(metadataColumns);
        StringBuilder out = new StringBuilder(String.join("\t", header)).append('\n');
        for (SubjectRecord subject : subjects) {
            ArrayList<String> cells = new ArrayList<>(List.of(
                    subject.getSubjectId(), nullable(subject.getStructureId()), nullable(subject.getBatchId()),
                    nullable(subject.getProject()), nullable(subject.getSeries()), nullable(subject.getSmiles())
            ));
            for (String column : metadataColumns) cells.add(nullable(subject.getMetadata().get(column)));
            out.append(row(cells));
        }
        return out.toString();
    }

    private static String subjectSetsTsv(List<SubjectSet> sets) {
        StringBuilder out = new StringBuilder("subject_set_id\tname\tset_type\tsubject_set_scope\tparent_set_id\tdescription\n");
        for (SubjectSet set : sets) {
            out.append(row(List.of(set.getId(), set.getName(), set.getSetType(), nullable(set.getSubjectSetScope()),
                    nullable(set.getParentSetId()), nullable(set.getDescription()))));
        }
        return out.toString();
    }

    private static String membershipsTsv(InMemoryPrismDataset dataset, List<SubjectSet> sets) {
        StringBuilder out = new StringBuilder("subject_set_id\tsubject_id\n");
        for (SubjectSet set : sets) {
            for (String subjectId : dataset.getSubjectsForSet(set.getId()).stream().sorted().toList()) {
                out.append(row(List.of(set.getId(), subjectId)));
            }
        }
        return out.toString();
    }

    private static String valuesTsv(List<EndpointValueRecord> values) {
        StringBuilder out = new StringBuilder("subject_id\tendpoint_id\tstate\tmean\tlower\tupper\traw_values\tvalue\ttext\tn\traw_value_ids\tfirst_measurement\tlast_measurement\tdetails\tresult_json\n");
        for (EndpointValueRecord value : values) {
            EndpointResult result = value.getResult();
            String state = "", mean = "", lower = "", upper = "", rawValues = "", scalar = "", text = "";
            if (result instanceof NumericResult numeric) {
                state = numeric.getState().name(); mean = nullable(numeric.getMean()); lower = nullable(numeric.getLower());
                upper = nullable(numeric.getUpper()); rawValues = join(numeric.getRawValues());
            } else if (result instanceof OptionalNumericResult numeric) {
                state = numeric.getState().name(); mean = nullable(numeric.getMean()); lower = nullable(numeric.getLower());
                upper = nullable(numeric.getUpper()); rawValues = join(numeric.getRawValues());
            } else if (result instanceof BooleanResult bool) scalar = Boolean.toString(bool.getValue());
            else if (result instanceof CategoricalResult categorical) scalar = categorical.getValue();
            else if (result instanceof TextResult textResult) text = textResult.getText();
            else throw new IllegalArgumentException("unsupported endpoint result type " + result.getClass().getName());
            AbstractEndpointResult shared = (AbstractEndpointResult) result;
            out.append(row(List.of(value.getSubjectId(), value.getEndpointId(), state, mean, lower, upper, rawValues,
                    scalar, text, nullable(shared.getN()), join(shared.getRawValueIds()), nullable(shared.getFirstMeasurement()),
                    nullable(shared.getLastMeasurement()), details(shared.getDetails()), EndpointResultCodec.encodeJson(result))));
        }
        return out.toString();
    }

    private static String details(Map<String, Object> details) {
        return details.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue()).collect(Collectors.joining(";"));
    }

    private static String join(List<?> values) { return values.stream().map(String::valueOf).collect(Collectors.joining("|")); }
    private static String nullable(Object value) { return value == null ? "" : String.valueOf(value); }
    private static String row(List<String> cells) { return cells.stream().map(PrismTsvEscaper::escapeCell).collect(Collectors.joining("\t")) + "\n"; }
    private static void write(Path directory, String name, String value) throws IOException {
        Files.writeString(directory.resolve(name), value, StandardCharsets.UTF_8);
    }

    private static String identity(PrismSnapshotDescriptor descriptor, List<PrismSnapshotFile> files) {
        String serialized = PrismSnapshotJson.stringify(canonicalValue(identityMap(descriptor, files)));
        Object jsonStable = PrismSnapshotJson.parse(serialized);
        return "sha256:" + sha256(PrismSnapshotJson.stringify(canonicalValue(jsonStable)).getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, Object> identityMap(PrismSnapshotDescriptor descriptor, List<PrismSnapshotFile> files) {
        LinkedHashMap<String, Object> identity = new LinkedHashMap<>();
        identity.put("format", PrismSnapshotManifest.FORMAT);
        identity.put("schemaVersion", PrismSnapshotManifest.CURRENT_SCHEMA_VERSION);
        identity.put("publisher", publisherMap(descriptor));
        identity.put("sourceRef", descriptor.sourceRef());
        identity.put("subjectIdentity", subjectIdentityMap(descriptor));
        identity.put("selection", selectionMap(descriptor.selection()));
        identity.put("endpoints", endpointMaps(descriptor.endpoints()));
        identity.put("subjectSetRevisions", canonicalValue(descriptor.subjectSetRevisions()));
        identity.put("metadata", canonicalValue(descriptor.metadata()));
        identity.put("files", fileMaps(files));
        return identity;
    }

    private static Map<String, Object> manifestMap(PrismSnapshotManifest manifest, boolean includeId) {
        PrismSnapshotDescriptor descriptor = manifest.descriptor();
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", manifest.schemaVersion());
        result.put("format", manifest.format());
        if (includeId) result.put("snapshotId", manifest.snapshotId());
        result.put("capture", Map.of("startedAt", descriptor.captureStartedAt(), "completedAt", descriptor.captureCompletedAt(), "semantics", "current_state_export"));
        result.put("publisher", publisherMap(descriptor));
        result.put("sourceRef", descriptor.sourceRef());
        result.put("subjectIdentity", subjectIdentityMap(descriptor));
        result.put("selection", selectionMap(descriptor.selection()));
        result.put("endpoints", endpointMaps(descriptor.endpoints()));
        result.put("subjectSetRevisions", canonicalValue(descriptor.subjectSetRevisions()));
        result.put("metadata", canonicalValue(descriptor.metadata()));
        result.put("files", fileMaps(manifest.files()));
        return result;
    }

    private static Map<String, Object> publisherMap(PrismSnapshotDescriptor descriptor) {
        return Map.of("id", descriptor.publisherId(), "version", descriptor.publisherVersion());
    }

    private static Map<String, Object> subjectIdentityMap(PrismSnapshotDescriptor descriptor) {
        return Map.of("aggregationLevel", descriptor.subjectAggregationLevel(), "structureColumn", descriptor.structureColumn(),
                "structureFormat", descriptor.structureFormat(), "standardization", descriptor.structureStandardization());
    }

    private static Map<String, Object> selectionMap(PrismSnapshotSelection selection) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("kind", selection.kind());
        if (selection.ref() != null) result.put("ref", selection.ref());
        result.put("revision", selection.revision());
        return result;
    }

    private static List<Map<String, Object>> endpointMaps(List<PrismSnapshotEndpoint> endpoints) {
        return endpoints.stream().map(endpoint -> {
            LinkedHashMap<String, Object> value = new LinkedHashMap<>();
            value.put("endpointId", endpoint.endpointId()); value.put("revision", endpoint.revision());
            value.put("metadata", canonicalValue(endpoint.metadata())); return Map.copyOf(value);
        }).toList();
    }

    private static List<Map<String, Object>> fileMaps(List<PrismSnapshotFile> files) {
        return files.stream().sorted(Comparator.comparing(PrismSnapshotFile::path))
                .map(file -> Map.<String, Object>of("path", file.path(), "sha256", file.sha256(), "rowCount", file.rowCount())).toList();
    }

    private static Object canonicalValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            map.forEach((key, child) -> sorted.put(String.valueOf(key), canonicalValue(child)));
            return new LinkedHashMap<>(sorted);
        }
        if (value instanceof Iterable<?> iterable) {
            ArrayList<Object> result = new ArrayList<>(); iterable.forEach(child -> result.add(canonicalValue(child))); return List.copyOf(result);
        }
        return value;
    }

    private static PrismSnapshotManifest parseManifest(Map<String, Object> root) {
        int schema = number(root.get("schemaVersion"), "schemaVersion").intValue();
        String format = string(root.get("format"), "format");
        String id = string(root.get("snapshotId"), "snapshotId");
        Map<String, Object> capture = mapping(root.get("capture"), "capture");
        Map<String, Object> publisher = mapping(root.get("publisher"), "publisher");
        Map<String, Object> subject = mapping(root.get("subjectIdentity"), "subjectIdentity");
        Map<String, Object> selectionValue = mapping(root.get("selection"), "selection");
        PrismSnapshotSelection selection = new PrismSnapshotSelection(string(selectionValue.get("kind"), "selection.kind"),
                optionalString(selectionValue.get("ref")), string(selectionValue.get("revision"), "selection.revision"));
        ArrayList<PrismSnapshotEndpoint> endpoints = new ArrayList<>();
        for (Object value : list(root.get("endpoints"), "endpoints")) {
            Map<String, Object> endpoint = mapping(value, "endpoint");
            endpoints.add(new PrismSnapshotEndpoint(string(endpoint.get("endpointId"), "endpoint.endpointId"),
                    string(endpoint.get("revision"), "endpoint.revision"), mappingOrEmpty(endpoint.get("metadata"))));
        }
        LinkedHashMap<String, String> revisions = new LinkedHashMap<>();
        mappingOrEmpty(root.get("subjectSetRevisions")).forEach((key, value) -> revisions.put(key, string(value, "subjectSetRevision")));
        PrismSnapshotDescriptor descriptor = new PrismSnapshotDescriptor(
                string(capture.get("startedAt"), "capture.startedAt"), string(capture.get("completedAt"), "capture.completedAt"),
                string(publisher.get("id"), "publisher.id"), string(publisher.get("version"), "publisher.version"),
                string(root.get("sourceRef"), "sourceRef"), string(subject.get("aggregationLevel"), "subjectIdentity.aggregationLevel"),
                string(subject.get("structureColumn"), "subjectIdentity.structureColumn"), string(subject.get("structureFormat"), "subjectIdentity.structureFormat"),
                string(subject.get("standardization"), "subjectIdentity.standardization"), selection, endpoints, revisions, mappingOrEmpty(root.get("metadata")));
        ArrayList<PrismSnapshotFile> files = new ArrayList<>();
        for (Object value : list(root.get("files"), "files")) {
            Map<String, Object> file = mapping(value, "file");
            files.add(new PrismSnapshotFile(string(file.get("path"), "file.path"), string(file.get("sha256"), "file.sha256"),
                    number(file.get("rowCount"), "file.rowCount").longValue()));
        }
        return new PrismSnapshotManifest(schema, format, id, descriptor, files);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapping(Object value, String field) {
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException(field + " must be an object");
        LinkedHashMap<String, Object> result = new LinkedHashMap<>(); map.forEach((key, child) -> result.put(String.valueOf(key), child)); return result;
    }
    private static Map<String, Object> mappingOrEmpty(Object value) { return value == null ? Map.of() : mapping(value, "metadata"); }
    private static List<?> list(Object value, String field) { if (!(value instanceof List<?> result)) throw new IllegalArgumentException(field + " must be an array"); return result; }
    private static String string(Object value, String field) { if (!(value instanceof String result) || result.isBlank()) throw new IllegalArgumentException(field + " must be text"); return result; }
    private static String optionalString(Object value) { return value == null ? null : string(value, "value"); }
    private static Number number(Object value, String field) { if (!(value instanceof Number result)) throw new IllegalArgumentException(field + " must be numeric"); return result; }

    private static String sha256(Path path) throws IOException { return sha256(Files.readAllBytes(path)); }
    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
