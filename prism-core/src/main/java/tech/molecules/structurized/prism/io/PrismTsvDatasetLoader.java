package tech.molecules.structurized.prism.io;

import tech.molecules.structurized.prism.model.CategoryDefinition;
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
import tech.molecules.structurized.prism.result.AbstractEndpointResult;
import tech.molecules.structurized.prism.result.BooleanResult;
import tech.molecules.structurized.prism.result.CategoricalResult;
import tech.molecules.structurized.prism.result.NumericResult;
import tech.molecules.structurized.prism.result.NumericState;
import tech.molecules.structurized.prism.result.OptionalNumericResult;
import tech.molecules.structurized.prism.result.OptionalNumericState;
import tech.molecules.structurized.prism.result.TextResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Loads an {@link InMemoryPrismDataset} from a canonical PRISM TSV bundle.
 */
public final class PrismTsvDatasetLoader {
    public static final String SUBJECTS_FILE_NAME = "subjects.prism.tsv";
    public static final String ENDPOINTS_FILE_NAME = "endpoints.prism.tsv";
    public static final String VALUES_FILE_NAME = "values.prism.tsv";
    public static final String SUBJECT_SETS_FILE_NAME = "subject_sets.prism.tsv";
    public static final String SUBJECT_SET_MEMBERSHIPS_FILE_NAME = "subject_set_memberships.prism.tsv";

    private static final Set<String> SUBJECT_COLUMNS = Set.of(
            "subject_id", "structure_id", "batch_id", "project", "series", "smiles"
    );

    private PrismTsvDatasetLoader() {}

    public static InMemoryPrismDataset load(Path directory) throws IOException {
        Objects.requireNonNull(directory, "directory must not be null");
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("directory does not exist: " + directory);
        }

        List<Row> endpointRows = readTable(directory.resolve(ENDPOINTS_FILE_NAME), true);
        List<Row> subjectRows = readTable(directory.resolve(SUBJECTS_FILE_NAME), true);
        List<Row> valueRows = readTable(directory.resolve(VALUES_FILE_NAME), true);
        List<Row> subjectSetRows = readTable(directory.resolve(SUBJECT_SETS_FILE_NAME), false);
        List<Row> membershipRows = readTable(directory.resolve(SUBJECT_SET_MEMBERSHIPS_FILE_NAME), false);

        InMemoryPrismDataset.Builder builder = InMemoryPrismDataset.builder();

        LinkedHashMap<String, EndpointDefinition> definitionsById = new LinkedHashMap<>();
        for (Row row : endpointRows) {
            EndpointDefinition definition = parseEndpointDefinition(row);
            EndpointDefinition previous = definitionsById.putIfAbsent(definition.getId(), definition);
            if (previous != null) {
                throw row.error("duplicate endpoint id '" + definition.getId() + "'");
            }
            builder.addEndpointDefinition(definition);
        }

        ArrayList<SubjectRecord> subjectRecords = new ArrayList<>();
        for (Row row : subjectRows) {
            SubjectRecord subjectRecord = parseSubjectRecord(row);
            subjectRecords.add(subjectRecord);
            builder.addSubjectRecord(subjectRecord);
        }

        LinkedHashSet<String> explicitSubjectSetIds = new LinkedHashSet<>();
        for (Row row : subjectSetRows) {
            SubjectSet subjectSet = parseSubjectSet(row);
            explicitSubjectSetIds.add(subjectSet.getId());
            builder.addSubjectSet(subjectSet);
        }

        for (Row row : membershipRows) {
            builder.addSubjectMembership(required(row, "subject_set_id"), required(row, "subject_id"));
        }

        addDerivedProjectAndSeriesSets(builder, subjectRecords, explicitSubjectSetIds);

        for (Row row : valueRows) {
            builder.addEndpointValue(parseEndpointValue(row, definitionsById));
        }

        return builder.build();
    }

    private static EndpointDefinition parseEndpointDefinition(Row row) {
        EndpointDefinition.Builder builder = EndpointDefinition.builder()
                .id(required(row, "endpoint_id"))
                .name(required(row, "name"))
                .path(required(row, "path"))
                .datatype(parseEnum(required(row, "datatype"), EndpointDataType.class, row, "datatype"))
                .endpointType(parseEnum(required(row, "endpoint_type"), EndpointType.class, row, "endpoint_type"))
                .evaluationMode(parseEnum(required(row, "evaluation_mode"), EvaluationMode.class, row, "evaluation_mode"))
                .unit(optional(row, "unit"))
                .description(optional(row, "description"));

        String scale = optional(row, "scale");
        String domainLowerBound = optional(row, "domain_lower_bound");
        String domainUpperBound = optional(row, "domain_upper_bound");
        if (scale != null || domainLowerBound != null || domainUpperBound != null) {
            NumericEndpointMeta.Builder metaBuilder = NumericEndpointMeta.builder();
            if (scale != null) {
                metaBuilder.scale(parseEnum(scale, NumericScale.class, row, "scale"));
            }
            if (domainLowerBound != null) {
                metaBuilder.domainLowerBound(parseDouble(domainLowerBound, row, "domain_lower_bound"));
            }
            if (domainUpperBound != null) {
                metaBuilder.domainUpperBound(parseDouble(domainUpperBound, row, "domain_upper_bound"));
            }
            builder.numericMeta(metaBuilder.build());
        }

        String categoriesCell = optional(row, "categories");
        if (categoriesCell != null) {
            for (Map.Entry<String, String> category : parseDelimitedPairs(categoriesCell, ';', '=', row, "categories").entrySet()) {
                builder.addCategory(CategoryDefinition.builder()
                        .id(category.getKey())
                        .name(category.getValue())
                        .build());
            }
        }
        return builder.build();
    }

    private static SubjectRecord parseSubjectRecord(Row row) {
        SubjectRecord.Builder builder = SubjectRecord.builder()
                .subjectId(required(row, "subject_id"))
                .structureId(optional(row, "structure_id"))
                .batchId(optional(row, "batch_id"))
                .project(optional(row, "project"))
                .series(optional(row, "series"))
                .smiles(optional(row, "smiles"));

        for (Map.Entry<String, String> entry : row.cells().entrySet()) {
            if (SUBJECT_COLUMNS.contains(entry.getKey())) {
                continue;
            }
            String value = normalize(entry.getValue());
            if (value != null) {
                builder.putMetadata(entry.getKey(), value);
            }
        }
        return builder.build();
    }

    private static SubjectSet parseSubjectSet(Row row) {
        return SubjectSet.builder()
                .id(required(row, "subject_set_id"))
                .name(required(row, "name"))
                .setType(required(row, "set_type"))
                .subjectSetScope(optional(row, "subject_set_scope"))
                .parentSetId(optional(row, "parent_set_id"))
                .description(optional(row, "description"))
                .build();
    }

    private static EndpointValueRecord parseEndpointValue(Row row, Map<String, EndpointDefinition> definitionsById) {
        String subjectId = required(row, "subject_id");
        String endpointId = required(row, "endpoint_id");
        EndpointDefinition definition = definitionsById.get(endpointId);
        if (definition == null) {
            throw row.error("unknown endpoint id '" + endpointId + "'");
        }

        return EndpointValueRecord.builder()
                .subjectId(subjectId)
                .endpointId(endpointId)
                .result(switch (definition.getDatatype()) {
                    case NUMERIC -> parseNumericResult(row);
                    case OPTIONAL_NUMERIC -> parseOptionalNumericResult(row);
                    case BOOLEAN -> parseBooleanResult(row);
                    case CATEGORICAL -> parseCategoricalResult(row);
                    case TEXT -> parseTextResult(row);
                })
                .build();
    }

    private static NumericResult parseNumericResult(Row row) {
        NumericResult.Builder builder = NumericResult.builder();
        applySharedMetadata(builder, row);
        String stateCell = optional(row, "state");
        String meanCell = optional(row, "mean");
        String lower = optional(row, "lower");
        String upper = optional(row, "upper");
        List<Double> rawValues = parseDoubleList(optional(row, "raw_values"), row, "raw_values");

        NumericState state;
        if (stateCell != null) {
            state = parseEnum(stateCell, NumericState.class, row, "state");
        } else if (meanCell != null || lower != null || upper != null || !rawValues.isEmpty()) {
            state = NumericState.VALUE;
        } else {
            state = NumericState.NOT_MEASURED;
        }
        builder.state(state);

        if (meanCell != null) {
            builder.mean(parseDouble(meanCell, row, "mean"));
        }
        if (lower != null) {
            builder.lower(parseDouble(lower, row, "lower"));
        }
        if (upper != null) {
            builder.upper(parseDouble(upper, row, "upper"));
        }
        builder.rawValues(rawValues);
        return builder.build();
    }

    private static OptionalNumericResult parseOptionalNumericResult(Row row) {
        OptionalNumericResult.Builder builder = OptionalNumericResult.builder();
        applySharedMetadata(builder, row);

        String stateCell = optional(row, "state");
        String meanCell = optional(row, "mean");
        String lowerCell = optional(row, "lower");
        String upperCell = optional(row, "upper");
        List<Double> rawValues = parseDoubleList(optional(row, "raw_values"), row, "raw_values");

        OptionalNumericState state;
        if (stateCell != null) {
            state = parseEnum(stateCell, OptionalNumericState.class, row, "state");
        } else if (meanCell != null || lowerCell != null || upperCell != null || !rawValues.isEmpty()) {
            state = OptionalNumericState.VALUE;
        } else {
            throw row.error("optional numeric rows must define 'state' unless numeric value fields imply VALUE");
        }
        builder.state(state);

        if (meanCell != null) {
            builder.mean(parseDouble(meanCell, row, "mean"));
        }
        if (lowerCell != null) {
            builder.lower(parseDouble(lowerCell, row, "lower"));
        }
        if (upperCell != null) {
            builder.upper(parseDouble(upperCell, row, "upper"));
        }
        builder.rawValues(rawValues);
        return builder.build();
    }

    private static BooleanResult parseBooleanResult(Row row) {
        BooleanResult.Builder builder = BooleanResult.builder()
                .value(parseBoolean(required(row, "value"), row, "value"));
        applySharedMetadata(builder, row);
        return builder.build();
    }

    private static CategoricalResult parseCategoricalResult(Row row) {
        CategoricalResult.Builder builder = CategoricalResult.builder()
                .value(required(row, "value"));
        applySharedMetadata(builder, row);
        return builder.build();
    }

    private static TextResult parseTextResult(Row row) {
        String text = optional(row, "text");
        if (text == null) {
            text = required(row, "value");
        }
        TextResult.Builder builder = TextResult.builder()
                .text(text);
        applySharedMetadata(builder, row);
        return builder.build();
    }

    private static void applySharedMetadata(AbstractEndpointResult.Builder<?> builder, Row row) {
        String nCell = optional(row, "n");
        if (nCell != null) {
            builder.n(parseInteger(nCell, row, "n"));
        }
        builder.rawValueIds(parseStringList(optional(row, "raw_value_ids"), '|'));
        builder.firstMeasurement(optional(row, "first_measurement"));
        builder.lastMeasurement(optional(row, "last_measurement"));
        builder.details(parseDetails(optional(row, "details"), row));
    }

    private static void addDerivedProjectAndSeriesSets(InMemoryPrismDataset.Builder builder,
                                                       List<SubjectRecord> subjectRecords,
                                                       Set<String> explicitSubjectSetIds) {
        LinkedHashMap<String, SubjectSet> derivedSets = new LinkedHashMap<>();

        for (SubjectRecord subjectRecord : subjectRecords) {
            String project = subjectRecord.getProject();
            String series = subjectRecord.getSeries();
            String projectSetId = null;

            if (project != null) {
                projectSetId = "project:" + project;
                if (!explicitSubjectSetIds.contains(projectSetId) && !derivedSets.containsKey(projectSetId)) {
                    derivedSets.put(projectSetId, SubjectSet.builder()
                            .id(projectSetId)
                            .name(project)
                            .setType("PROJECT")
                            .subjectSetScope("PROJECTS")
                            .description("Auto-derived from subject project metadata")
                            .build());
                }
                builder.addSubjectMembership(projectSetId, subjectRecord.getSubjectId());
            }

            if (series != null) {
                String seriesSetId = project == null ? "series:" + series : "series:" + project + ":" + series;
                if (!explicitSubjectSetIds.contains(seriesSetId) && !derivedSets.containsKey(seriesSetId)) {
                    SubjectSet.Builder seriesBuilder = SubjectSet.builder()
                            .id(seriesSetId)
                            .name(series)
                            .setType("SERIES")
                            .subjectSetScope("SERIES")
                            .description("Auto-derived from subject series metadata");
                    if (projectSetId != null) {
                        seriesBuilder.parentSetId(projectSetId);
                    }
                    derivedSets.put(seriesSetId, seriesBuilder.build());
                }
                builder.addSubjectMembership(seriesSetId, subjectRecord.getSubjectId());
            }
        }

        for (SubjectSet subjectSet : derivedSets.values()) {
            builder.addSubjectSet(subjectSet);
        }
    }

    private static List<Row> readTable(Path path, boolean required) throws IOException {
        if (!Files.exists(path)) {
            if (required) {
                throw new IllegalArgumentException("required TSV file not found: " + path);
            }
            return List.of();
        }

        List<String> lines = Files.readAllLines(path);
        String[] header = null;
        int headerLineNumber = -1;
        ArrayList<Row> rows = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            if (header == null) {
                header = splitTsv(line);
                headerLineNumber = i + 1;
                continue;
            }

            String[] values = splitTsv(line);
            if (values.length != header.length) {
                throw new IllegalArgumentException("invalid TSV row in " + path.getFileName() + " line " + (i + 1)
                        + ": expected " + header.length + " columns from header line " + headerLineNumber
                        + " but found " + values.length);
            }
            LinkedHashMap<String, String> cells = new LinkedHashMap<>();
            for (int c = 0; c < header.length; c++) {
                cells.put(header[c].trim(), values[c]);
            }
            rows.add(new Row(path.getFileName().toString(), i + 1, Map.copyOf(cells)));
        }

        if (header == null) {
            throw new IllegalArgumentException("TSV file has no header: " + path);
        }
        return List.copyOf(rows);
    }

    private static String[] splitTsv(String line) {
        return line.split("\t", -1);
    }

    private static String required(Row row, String column) {
        String value = optional(row, column);
        if (value == null) {
            throw row.error("missing required column value '" + column + "'");
        }
        return value;
    }

    private static String optional(Row row, String column) {
        if (!row.cells().containsKey(column)) {
            return null;
        }
        return normalize(row.cells().get(column));
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static <E extends Enum<E>> E parseEnum(String value, Class<E> type, Row row, String column) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw row.error("invalid value '" + value + "' for column '" + column + "'");
        }
    }

    private static Double parseRequiredDouble(Row row, String column) {
        return parseDouble(required(row, column), row, column);
    }

    private static Double parseDouble(String value, Row row, String column) {
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException ex) {
            throw row.error("invalid double '" + value + "' for column '" + column + "'");
        }
    }

    private static Integer parseInteger(String value, Row row, String column) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ex) {
            throw row.error("invalid integer '" + value + "' for column '" + column + "'");
        }
    }

    private static boolean parseBoolean(String value, Row row, String column) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "true", "1", "yes", "y" -> true;
            case "false", "0", "no", "n" -> false;
            default -> throw row.error("invalid boolean '" + value + "' for column '" + column + "'");
        };
    }

    private static List<String> parseStringList(String cell, char separator) {
        if (cell == null) {
            return List.of();
        }
        return Arrays.stream(cell.split("\\" + separator, -1))
                .map(PrismTsvDatasetLoader::normalize)
                .filter(Objects::nonNull)
                .toList();
    }

    private static List<Double> parseDoubleList(String cell, Row row, String column) {
        List<String> tokens = parseStringList(cell, '|');
        if (tokens.isEmpty()) {
            return List.of();
        }
        ArrayList<Double> values = new ArrayList<>();
        for (String token : tokens) {
            values.add(parseDouble(token, row, column));
        }
        return List.copyOf(values);
    }

    private static Map<String, Object> parseDetails(String cell, Row row) {
        if (cell == null) {
            return Map.of();
        }
        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : parseDelimitedPairs(cell, ';', '=', row, "details").entrySet()) {
            details.put(entry.getKey(), entry.getValue());
        }
        return Map.copyOf(details);
    }

    private static Map<String, String> parseDelimitedPairs(String cell,
                                                           char entrySeparator,
                                                           char keyValueSeparator,
                                                           Row row,
                                                           String column) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        if (cell == null) {
            return Map.of();
        }

        String[] entries = cell.split("\\" + entrySeparator, -1);
        for (String entry : entries) {
            String trimmedEntry = normalize(entry);
            if (trimmedEntry == null) {
                continue;
            }

            int splitIndex = trimmedEntry.indexOf(keyValueSeparator);
            String key;
            String value;
            if (splitIndex < 0) {
                key = trimmedEntry;
                value = trimmedEntry;
            } else {
                key = normalize(trimmedEntry.substring(0, splitIndex));
                value = normalize(trimmedEntry.substring(splitIndex + 1));
            }
            if (key == null || value == null) {
                throw row.error("invalid key/value entry '" + trimmedEntry + "' in column '" + column + "'");
            }
            values.put(key, value);
        }
        return Map.copyOf(values);
    }

    private record Row(String source, int lineNumber, Map<String, String> cells) {
        IllegalArgumentException error(String message) {
            return new IllegalArgumentException(source + ":" + lineNumber + ": " + message);
        }
    }
}
