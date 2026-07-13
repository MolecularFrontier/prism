package tech.molecules.structurized.prism.pack;

import tech.molecules.structurized.prism.score.EndpointScoreDefinition;
import tech.molecules.structurized.prism.score.PropertyProfileDefinition;

import java.util.List;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable in-memory representation of a PrismPack v0.1 package.
 */
public record PrismPack(
        Manifest manifest,
        DataFrame dataFrame,
        DataFrameSchema schema,
        MoleculeMetadata molecules,
        EndpointMetadata endpoints,
        TableView tableView,
        VisualizationSet visualizations,
        AttachmentSet attachments,
        ScoreMetadata scores,
        PropertyProfileMetadata propertyProfiles,
        Map<String, Object> provenance,
        List<String> warnings
) {
    public PrismPack {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        provenance = copyMapAllowingNulls(provenance);
    }

    public PrismPack(Manifest manifest,
                     DataFrame dataFrame,
                     DataFrameSchema schema,
                     MoleculeMetadata molecules,
                     EndpointMetadata endpoints,
                     TableView tableView,
                     VisualizationSet visualizations,
                     AttachmentSet attachments,
                     Map<String, Object> provenance,
                     List<String> warnings) {
        this(manifest, dataFrame, schema, molecules, endpoints, tableView, visualizations, attachments,
                null, null, provenance, warnings);
    }


    private static Map<String, Object> copyMapAllowingNulls(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }

    public Optional<Column> findSchemaColumn(String name) {
        if (schema == null || name == null) {
            return Optional.empty();
        }
        return schema.columns().stream()
                .filter(column -> name.equals(column.name()))
                .findFirst();
    }

    public Optional<Endpoint> findEndpointForColumn(String columnName) {
        if (endpoints == null || columnName == null) {
            return Optional.empty();
        }
        return endpoints.endpoints().stream()
                .filter(endpoint -> columnName.equals(endpoint.column()))
                .findFirst();
    }

    public record Manifest(
            String prismPackVersion,
            String id,
            String title,
            String description,
            String createdAt,
            String createdBy,
            DataframeRef dataframe,
            String moleculesPath,
            String endpointsPath,
            String tableViewPath,
            String visualizationsPath,
            String attachmentsPath,
            String scoresPath,
            String propertyProfilesPath,
            String provenancePath,
            Map<String, Object> raw
    ) {
        public Manifest {
            raw = copyMapAllowingNulls(raw);
        }

        public Manifest(String prismPackVersion,
                        String id,
                        String title,
                        String description,
                        String createdAt,
                        String createdBy,
                        DataframeRef dataframe,
                        String moleculesPath,
                        String endpointsPath,
                        String tableViewPath,
                        String visualizationsPath,
                        String attachmentsPath,
                        String provenancePath,
                        Map<String, Object> raw) {
            this(prismPackVersion, id, title, description, createdAt, createdBy, dataframe, moleculesPath,
                    endpointsPath, tableViewPath, visualizationsPath, attachmentsPath, null, null,
                    provenancePath, raw);
        }
    }

    public record DataframeRef(
            String id,
            String path,
            String schema,
            String rowType,
            Map<String, Object> raw
    ) {
        public DataframeRef {
            raw = copyMapAllowingNulls(raw);
        }
    }

    public record DataFrame(List<String> headers, List<List<String>> rows) {
        public DataFrame {
            headers = List.copyOf(headers);
            rows = rows.stream().map(List::copyOf).toList();
        }

        public int columnIndex(String columnName) {
            for (int i = 0; i < headers.size(); i++) {
                if (headers.get(i).equals(columnName)) {
                    return i;
                }
            }
            return -1;
        }

        public String valueAt(int row, String columnName) {
            int column = columnIndex(columnName);
            return column == -1 ? null : rows.get(row).get(column);
        }
    }

    public record DataFrameSchema(List<Column> columns, Map<String, Object> raw) {
        public DataFrameSchema {
            columns = columns == null ? List.of() : List.copyOf(columns);
            raw = copyMapAllowingNulls(raw);
        }
    }

    public record Column(
            String name,
            String type,
            String semanticType,
            String displayName,
            String role,
            String unit,
            String endpointId,
            String direction,
            String structureFormat,
            Map<String, Object> raw
    ) {
        public Column {
            raw = copyMapAllowingNulls(raw);
        }
    }

    public record MoleculeMetadata(
            String primaryStructureColumn,
            String structureFormat,
            String compoundIdColumn,
            Map<String, Object> raw
    ) {
        public MoleculeMetadata {
            raw = copyMapAllowingNulls(raw);
        }
    }

    public record EndpointMetadata(List<Endpoint> endpoints, Map<String, Object> raw) {
        public EndpointMetadata {
            endpoints = endpoints == null ? List.of() : List.copyOf(endpoints);
            raw = copyMapAllowingNulls(raw);
        }
    }

    public record ScoreMetadata(List<EndpointScoreDefinition> scores, Map<String, Object> raw) {
        public ScoreMetadata {
            scores = scores == null ? List.of() : List.copyOf(scores);
            raw = copyMapAllowingNulls(raw);
        }
    }

    public record PropertyProfileMetadata(List<PropertyProfileDefinition> profiles, Map<String, Object> raw) {
        public PropertyProfileMetadata {
            profiles = profiles == null ? List.of() : List.copyOf(profiles);
            raw = copyMapAllowingNulls(raw);
        }
    }

    public record Endpoint(
            String id,
            String column,
            String displayName,
            String unit,
            String direction,
            String assay,
            String protocol,
            Map<String, Object> raw
    ) {
        public Endpoint {
            raw = copyMapAllowingNulls(raw);
        }
    }

    public record TableView(
            String id,
            String title,
            List<String> columns,
            List<String> frozenColumns,
            List<String> hiddenColumns,
            List<Sort> sort,
            List<Filter> filters,
            List<ColorRule> colorRules,
            Map<String, Object> raw
    ) {
        public TableView {
            columns = columns == null ? List.of() : List.copyOf(columns);
            frozenColumns = frozenColumns == null ? List.of() : List.copyOf(frozenColumns);
            hiddenColumns = hiddenColumns == null ? List.of() : List.copyOf(hiddenColumns);
            sort = sort == null ? List.of() : List.copyOf(sort);
            filters = filters == null ? List.of() : List.copyOf(filters);
            colorRules = colorRules == null ? List.of() : List.copyOf(colorRules);
            raw = copyMapAllowingNulls(raw);
        }
    }

    public record Sort(String column, String direction, Map<String, Object> raw) {
        public Sort {
            raw = copyMapAllowingNulls(raw);
        }
    }

    public record Filter(String column, String type, Map<String, Object> raw) {
        public Filter {
            raw = copyMapAllowingNulls(raw);
        }
    }

    public record ColorRule(String column, String type, String direction, Map<String, Object> raw) {
        public ColorRule {
            raw = copyMapAllowingNulls(raw);
        }
    }

    public record VisualizationSet(List<Visualization> visualizations, Map<String, Object> raw) {
        public VisualizationSet {
            visualizations = visualizations == null ? List.of() : List.copyOf(visualizations);
            raw = copyMapAllowingNulls(raw);
        }
    }

    public record Visualization(
            String id,
            String type,
            String title,
            String x,
            String y,
            String colorBy,
            String sizeBy,
            Map<String, Object> raw
    ) {
        public Visualization {
            raw = copyMapAllowingNulls(raw);
        }
    }

    public record AttachmentSet(List<Attachment> attachments, Map<String, Object> raw) {
        public AttachmentSet {
            attachments = attachments == null ? List.of() : List.copyOf(attachments);
            raw = copyMapAllowingNulls(raw);
        }
    }

    public record Attachment(
            String id,
            AttachmentTarget target,
            String name,
            String mimeType,
            AttachmentContent content,
            Map<String, Object> raw
    ) {
        public Attachment {
            raw = copyMapAllowingNulls(raw);
        }
    }

    public record AttachmentTarget(
            String type,
            String rowKeyColumn,
            String rowKey,
            String column,
            Map<String, Object> raw
    ) {
        public AttachmentTarget {
            raw = copyMapAllowingNulls(raw);
        }
    }

    public record AttachmentContent(
            String type,
            String text,
            String path,
            Map<String, Object> raw
    ) {
        public AttachmentContent {
            raw = copyMapAllowingNulls(raw);
        }
    }

}
