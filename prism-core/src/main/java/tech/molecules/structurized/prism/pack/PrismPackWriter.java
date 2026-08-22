package tech.molecules.structurized.prism.pack;

import tech.molecules.structurized.prism.io.PrismTsvEscaper;
import tech.molecules.structurized.prism.prediction.PredictionCapability;
import tech.molecules.structurized.prism.score.EndpointScoreDefinition;
import tech.molecules.structurized.prism.score.MpoComponentDefinition;
import tech.molecules.structurized.prism.score.MpoDefinition;
import tech.molecules.structurized.prism.score.PropertyProfileDefinition;
import tech.molecules.structurized.prism.score.PropertyProfileItem;
import tech.molecules.structurized.prism.score.ScorePoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writer for PrismPack v0.1 through v0.3 directory and ZIP packages.
 */
public final class PrismPackWriter {
    public static final String MANIFEST_PATH = "prism-pack.json";
    public static final String DATAFRAME_PATH = "data/dataframe.tsv";
    public static final String SCHEMA_PATH = "schema/dataframe.schema.json";
    public static final String MOLECULES_PATH = "semantics/molecules.json";
    public static final String ENDPOINTS_PATH = "semantics/endpoints.json";
    public static final String ENDPOINT_RESULTS_PATH = "semantics/endpoint-results.jsonl";
    public static final String ROW_SETS_PATH = "semantics/row-sets.json";
    public static final String TABLE_VIEW_PATH = "views/table-view.json";
    public static final String VISUALIZATIONS_PATH = "views/visualizations.json";
    public static final String ATTACHMENTS_PATH = "attachments/attachments.json";
    public static final String SCORES_PATH = "semantics/scores.json";
    public static final String PROPERTY_PROFILES_PATH = "semantics/property-profiles.json";
    public static final String PREDICTIONS_PATH = "semantics/predictions.json";
    public static final String PROVENANCE_PATH = "provenance/provenance.json";

    private PrismPackWriter() {
    }

    public static void write(Path path, PrismPack pack) throws IOException {
        if (Files.isDirectory(path)) {
            writeDirectory(path, pack);
        } else {
            writeZip(path, pack);
        }
    }

    public static void writeDirectory(Path directory, PrismPack pack) throws IOException {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(pack, "pack");
        Files.createDirectories(directory);
        writeFile(directory, MANIFEST_PATH, PrismPackJson.stringify(manifestMap(pack)));
        writeFile(directory, dataframePath(pack), dataframeTsv(pack.dataFrame()));
        writeFile(directory, schemaPath(pack), PrismPackJson.stringify(schemaMap(pack.schema())));
        if (pack.molecules() != null) {
            writeFile(directory, moleculesPath(pack), PrismPackJson.stringify(moleculesMap(pack.molecules())));
        }
        if (pack.endpoints() != null) {
            writeFile(directory, endpointsPath(pack), PrismPackJson.stringify(endpointsMap(pack.endpoints())));
        }
        if (pack.endpointResults() != null) writeFile(directory, endpointResultsPath(pack), endpointResultsJsonl(pack.endpointResults()));
        if (pack.rowSets() != null) writeFile(directory, rowSetsPath(pack), PrismPackJson.stringify(rowSetsMap(pack.rowSets())));
        if (pack.tableView() != null) {
            writeFile(directory, tableViewPath(pack), PrismPackJson.stringify(tableViewMap(pack.tableView())));
        }
        if (pack.visualizations() != null) {
            writeFile(directory, visualizationsPath(pack), PrismPackJson.stringify(visualizationsMap(pack.visualizations())));
        }
        if (pack.attachments() != null && !pack.attachments().attachments().isEmpty()) {
            writeFile(directory, attachmentsPath(pack), PrismPackJson.stringify(attachmentsMap(pack.attachments())));
        }
        if (pack.scores() != null && !pack.scores().scores().isEmpty()) {
            writeFile(directory, scoresPath(pack), PrismPackJson.stringify(scoresMap(pack.scores())));
        }
        if (pack.propertyProfiles() != null && !pack.propertyProfiles().profiles().isEmpty()) {
            writeFile(directory, propertyProfilesPath(pack), PrismPackJson.stringify(propertyProfilesMap(pack.propertyProfiles())));
        }
        if (pack.predictions() != null && !pack.predictions().capabilities().isEmpty()) {
            writeFile(directory, predictionsPath(pack), PrismPackJson.stringify(predictionsMap(pack.predictions())));
        }
        if (pack.provenance() != null && !pack.provenance().isEmpty()) {
            writeFile(directory, provenancePath(pack), PrismPackJson.stringify(pack.provenance()));
        }
    }

    public static void writeZip(Path zipPath, PrismPack pack) throws IOException {
        Objects.requireNonNull(zipPath, "zipPath");
        Objects.requireNonNull(pack, "pack");
        Path parent = zipPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            writeEntry(out, MANIFEST_PATH, PrismPackJson.stringify(manifestMap(pack)));
            writeEntry(out, dataframePath(pack), dataframeTsv(pack.dataFrame()));
            writeEntry(out, schemaPath(pack), PrismPackJson.stringify(schemaMap(pack.schema())));
            if (pack.molecules() != null) {
                writeEntry(out, moleculesPath(pack), PrismPackJson.stringify(moleculesMap(pack.molecules())));
            }
            if (pack.endpoints() != null) {
                writeEntry(out, endpointsPath(pack), PrismPackJson.stringify(endpointsMap(pack.endpoints())));
            }
            if (pack.endpointResults() != null) writeEntry(out, endpointResultsPath(pack), endpointResultsJsonl(pack.endpointResults()));
            if (pack.rowSets() != null) writeEntry(out, rowSetsPath(pack), PrismPackJson.stringify(rowSetsMap(pack.rowSets())));
            if (pack.tableView() != null) {
                writeEntry(out, tableViewPath(pack), PrismPackJson.stringify(tableViewMap(pack.tableView())));
            }
            if (pack.visualizations() != null) {
                writeEntry(out, visualizationsPath(pack), PrismPackJson.stringify(visualizationsMap(pack.visualizations())));
            }
            if (pack.attachments() != null && !pack.attachments().attachments().isEmpty()) {
                writeEntry(out, attachmentsPath(pack), PrismPackJson.stringify(attachmentsMap(pack.attachments())));
            }
            if (pack.scores() != null && !pack.scores().scores().isEmpty()) {
                writeEntry(out, scoresPath(pack), PrismPackJson.stringify(scoresMap(pack.scores())));
            }
            if (pack.propertyProfiles() != null && !pack.propertyProfiles().profiles().isEmpty()) {
                writeEntry(out, propertyProfilesPath(pack), PrismPackJson.stringify(propertyProfilesMap(pack.propertyProfiles())));
            }
            if (pack.predictions() != null && !pack.predictions().capabilities().isEmpty()) {
                writeEntry(out, predictionsPath(pack), PrismPackJson.stringify(predictionsMap(pack.predictions())));
            }
            if (pack.provenance() != null && !pack.provenance().isEmpty()) {
                writeEntry(out, provenancePath(pack), PrismPackJson.stringify(pack.provenance()));
            }
        }
    }

    private static void writeFile(Path directory, String relativePath, String content) throws IOException {
        Path file = directory.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static void writeEntry(ZipOutputStream out, String path, String content) throws IOException {
        out.putNextEntry(new ZipEntry(path));
        out.write(content.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }

    private static String dataframePath(PrismPack pack) {
        return valueOrDefault(pack.manifest().dataframe().path(), DATAFRAME_PATH);
    }

    private static String schemaPath(PrismPack pack) {
        return valueOrDefault(pack.manifest().dataframe().schema(), SCHEMA_PATH);
    }

    private static String moleculesPath(PrismPack pack) {
        return valueOrDefault(pack.manifest().moleculesPath(), MOLECULES_PATH);
    }

    private static String endpointsPath(PrismPack pack) {
        return valueOrDefault(pack.manifest().endpointsPath(), ENDPOINTS_PATH);
    }

    private static String endpointResultsPath(PrismPack pack) {
        return pack.manifest().endpointResults() == null ? ENDPOINT_RESULTS_PATH
                : valueOrDefault(pack.manifest().endpointResults().path(), ENDPOINT_RESULTS_PATH);
    }

    private static String rowSetsPath(PrismPack pack) { return valueOrDefault(pack.manifest().rowSetsPath(), ROW_SETS_PATH); }

    private static String tableViewPath(PrismPack pack) {
        return valueOrDefault(pack.manifest().tableViewPath(), TABLE_VIEW_PATH);
    }

    private static String visualizationsPath(PrismPack pack) {
        return valueOrDefault(pack.manifest().visualizationsPath(), VISUALIZATIONS_PATH);
    }

    private static String attachmentsPath(PrismPack pack) {
        return valueOrDefault(pack.manifest().attachmentsPath(), ATTACHMENTS_PATH);
    }

    private static String scoresPath(PrismPack pack) {
        return valueOrDefault(pack.manifest().scoresPath(), SCORES_PATH);
    }

    private static String propertyProfilesPath(PrismPack pack) {
        return valueOrDefault(pack.manifest().propertyProfilesPath(), PROPERTY_PROFILES_PATH);
    }

    private static String predictionsPath(PrismPack pack) {
        return valueOrDefault(pack.manifest().predictionsPath(), PREDICTIONS_PATH);
    }

    private static String provenancePath(PrismPack pack) {
        return valueOrDefault(pack.manifest().provenancePath(), PROVENANCE_PATH);
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String dataframeTsv(PrismPack.DataFrame dataframe) {
        Objects.requireNonNull(dataframe, "dataframe");
        StringBuilder builder = new StringBuilder();
        appendTsvRow(builder, dataframe.headers());
        for (List<String> row : dataframe.rows()) {
            if (row.size() != dataframe.headers().size()) {
                throw new PrismPackException("dataframe row has " + row.size()
                        + " cells but header has " + dataframe.headers().size());
            }
            appendTsvRow(builder, row);
        }
        return builder.toString();
    }

    private static void appendTsvRow(StringBuilder builder, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                builder.append('\t');
            }
            builder.append(PrismTsvEscaper.escapeCell(cells.get(i)));
        }
        builder.append('\n');
    }

    private static Map<String, Object> manifestMap(PrismPack pack) {
        PrismPack.Manifest manifest = Objects.requireNonNull(pack.manifest(), "manifest");
        LinkedHashMap<String, Object> map = copy(manifest.raw());
        putIfNotNull(map, "prismPackVersion", manifest.prismPackVersion());
        putIfNotNull(map, "id", manifest.id());
        putIfNotNull(map, "title", manifest.title());
        putIfNotNull(map, "description", manifest.description());
        putIfNotNull(map, "createdAt", manifest.createdAt());
        putIfNotNull(map, "createdBy", manifest.createdBy());
        map.put("dataframe", dataframeRefMap(manifest.dataframe()));
        if (pack.molecules() != null) {
            map.put("molecules", moleculesPath(pack));
        }
        if (pack.endpoints() != null) {
            map.put("endpoints", endpointsPath(pack));
        }
        if (pack.endpointResults() != null) {
            LinkedHashMap<String, Object> ref = pack.manifest().endpointResults() == null
                    ? new LinkedHashMap<>() : copy(pack.manifest().endpointResults().raw());
            ref.put("path", endpointResultsPath(pack));
            putIfNotNull(ref, "rowKeyColumn", pack.endpointResults().rowKeyColumn());
            map.put("endpointResults", ref);
        }
        if (pack.rowSets() != null) map.put("rowSets", rowSetsPath(pack));
        if (pack.tableView() != null) {
            map.put("tableView", tableViewPath(pack));
        }
        if (pack.visualizations() != null) {
            map.put("visualizations", visualizationsPath(pack));
        }
        if (pack.attachments() != null && !pack.attachments().attachments().isEmpty()) {
            map.put("attachments", attachmentsPath(pack));
        }
        if (pack.scores() != null && !pack.scores().scores().isEmpty()) {
            map.put("scores", scoresPath(pack));
        }
        if (pack.propertyProfiles() != null && !pack.propertyProfiles().profiles().isEmpty()) {
            map.put("propertyProfiles", propertyProfilesPath(pack));
        }
        if (pack.predictions() != null && !pack.predictions().capabilities().isEmpty()) {
            map.put("predictions", predictionsPath(pack));
        }
        if (pack.provenance() != null && !pack.provenance().isEmpty()) {
            map.put("provenance", provenancePath(pack));
        }
        return map;
    }

    private static Map<String, Object> dataframeRefMap(PrismPack.DataframeRef ref) {
        Objects.requireNonNull(ref, "dataframe ref");
        LinkedHashMap<String, Object> map = copy(ref.raw());
        putIfNotNull(map, "id", ref.id());
        map.put("path", valueOrDefault(ref.path(), DATAFRAME_PATH));
        map.put("schema", valueOrDefault(ref.schema(), SCHEMA_PATH));
        putIfNotNull(map, "rowType", ref.rowType());
        return map;
    }

    private static Map<String, Object> schemaMap(PrismPack.DataFrameSchema schema) {
        Objects.requireNonNull(schema, "schema");
        LinkedHashMap<String, Object> map = copy(schema.raw());
        map.put("columns", schema.columns().stream().map(PrismPackWriter::columnMap).toList());
        return map;
    }

    private static Map<String, Object> columnMap(PrismPack.Column column) {
        LinkedHashMap<String, Object> map = copy(column.raw());
        putIfNotNull(map, "name", column.name());
        putIfNotNull(map, "type", column.type());
        putIfNotNull(map, "semanticType", column.semanticType());
        putIfNotNull(map, "displayName", column.displayName());
        putIfNotNull(map, "role", column.role());
        putIfNotNull(map, "unit", column.unit());
        putIfNotNull(map, "endpointId", column.endpointId());
        putIfNotNull(map, "direction", column.direction());
        putIfNotNull(map, "structureFormat", column.structureFormat());
        return map;
    }

    private static Map<String, Object> moleculesMap(PrismPack.MoleculeMetadata molecules) {
        LinkedHashMap<String, Object> map = copy(molecules.raw());
        putIfNotNull(map, "primaryStructureColumn", molecules.primaryStructureColumn());
        putIfNotNull(map, "structureFormat", molecules.structureFormat());
        putIfNotNull(map, "compoundIdColumn", molecules.compoundIdColumn());
        return map;
    }

    private static Map<String, Object> endpointsMap(PrismPack.EndpointMetadata metadata) {
        LinkedHashMap<String, Object> map = copy(metadata.raw());
        map.put("endpoints", metadata.endpoints().stream().map(PrismPackWriter::endpointMap).toList());
        return map;
    }

    private static Map<String, Object> endpointMap(PrismPack.Endpoint endpoint) {
        LinkedHashMap<String, Object> map = copy(endpoint.raw());
        putIfNotNull(map, "id", endpoint.id());
        putIfNotNull(map, "column", endpoint.column());
        putIfNotNull(map, "displayName", endpoint.displayName());
        putIfNotNull(map, "unit", endpoint.unit());
        putIfNotNull(map, "direction", endpoint.direction());
        putIfNotNull(map, "assay", endpoint.assay());
        putIfNotNull(map, "protocol", endpoint.protocol());
        if (endpoint.definition() != null) map.put("definition", EndpointDefinitionCodec.encode(endpoint.definition()));
        return map;
    }

    private static String endpointResultsJsonl(PrismPack.EndpointResultSet metadata) {
        StringBuilder builder = new StringBuilder();
        for (PrismPack.EndpointResultRecord record : metadata.results()) {
            LinkedHashMap<String, Object> map = copy(record.raw());
            map.put("rowKey", record.rowKey()); map.put("endpointId", record.endpointId());
            map.put("result", EndpointResultCodec.encode(record.result()));
            builder.append(PrismPackJson.stringifyCompact(map)).append('\n');
        }
        return builder.toString();
    }

    private static Map<String, Object> rowSetsMap(PrismPack.RowSetMetadata metadata) {
        LinkedHashMap<String, Object> map = copy(metadata.raw());
        map.put("rowSets", metadata.rowSets().stream().map(rowSet -> {
            LinkedHashMap<String, Object> item = copy(rowSet.raw());
            item.put("id", rowSet.id()); putIfNotNull(item, "name", rowSet.name()); putIfNotNull(item, "description", rowSet.description());
            item.put("rowIds", rowSet.rowIds()); if (!rowSet.provenance().isEmpty()) item.put("provenance", rowSet.provenance());
            return item;
        }).toList());
        return map;
    }

    private static Map<String, Object> scoresMap(PrismPack.ScoreMetadata metadata) {
        LinkedHashMap<String, Object> map = copy(metadata.raw());
        map.put("scores", metadata.scores().stream().map(PrismPackWriter::scoreMap).toList());
        return map;
    }

    private static Map<String, Object> scoreMap(EndpointScoreDefinition score) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("id", score.id());
        map.put("endpointId", score.endpointId());
        map.put("displayName", score.displayName());
        putIfNotNull(map, "description", score.description());
        map.put("scoreType", score.scoreType());
        map.put("xScale", score.xScale());
        map.put("clampOutsideRange", score.clampOutsideRange());
        map.put("points", score.points().stream().map(PrismPackWriter::scorePointMap).toList());
        if (!score.metadata().isEmpty()) map.put("metadata", score.metadata());
        return map;
    }

    private static Map<String, Object> scorePointMap(ScorePoint point) {
        return Map.of("x", point.x(), "score", point.score());
    }

    private static Map<String, Object> propertyProfilesMap(PrismPack.PropertyProfileMetadata metadata) {
        LinkedHashMap<String, Object> map = copy(metadata.raw());
        map.put("profiles", metadata.profiles().stream().map(PrismPackWriter::propertyProfileMap).toList());
        return map;
    }

    private static Map<String, Object> predictionsMap(tech.molecules.structurized.prism.prediction.PredictionMetadata metadata) {
        LinkedHashMap<String, Object> map = copy(metadata.raw());
        map.put("capabilities", metadata.capabilities().stream().map(PrismPackWriter::predictionCapabilityMap).toList());
        return map;
    }

    private static Map<String, Object> predictionCapabilityMap(PredictionCapability capability) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>(capability.metadata().isEmpty() ? Map.of() : Map.of("metadata", capability.metadata()));
        map.put("capabilityId", capability.capabilityId());
        map.put("endpointId", capability.endpointId());
        map.put("predictedEndpointId", capability.predictedEndpointId());
        map.put("displayName", capability.displayName());
        map.put("providerId", capability.providerId());
        map.put("workflowId", capability.workflowId());
        putIfNotNull(map, "workflowVersion", capability.workflowVersion());
        map.put("status", capability.status());
        map.put("priority", capability.priority());
        putIfNotNull(map, "structureColumn", capability.structureColumn());
        putIfNotNull(map, "structureFormat", capability.structureFormat());
        return map;
    }

    private static Map<String, Object> propertyProfileMap(PropertyProfileDefinition profile) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("id", profile.id());
        map.put("title", profile.title());
        putIfNotNull(map, "description", profile.description());
        map.put("items", profile.items().stream().map(PrismPackWriter::profileItemMap).toList());
        map.put("mpos", profile.mpos().stream().map(PrismPackWriter::mpoMap).toList());
        if (!profile.metadata().isEmpty()) map.put("metadata", profile.metadata());
        return map;
    }

    private static Map<String, Object> profileItemMap(PropertyProfileItem item) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("endpointId", item.endpointId());
        putIfNotNull(map, "scoreId", item.scoreId());
        putIfNotNull(map, "label", item.label());
        putIfNotNull(map, "group", item.group());
        map.put("order", item.order());
        map.put("visible", item.visible());
        if (!item.metadata().isEmpty()) map.put("metadata", item.metadata());
        return map;
    }

    private static Map<String, Object> mpoMap(MpoDefinition mpo) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("id", mpo.id());
        map.put("displayName", mpo.displayName());
        map.put("components", mpo.components().stream().map(PrismPackWriter::mpoComponentMap).toList());
        map.put("aggregation", Map.of(
                "type", mpo.aggregation().type(),
                "missing", mpo.aggregation().missing(),
                "warningCoverageBelow", mpo.aggregation().warningCoverageBelow()));
        return map;
    }

    private static Map<String, Object> mpoComponentMap(MpoComponentDefinition component) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("endpointId", component.endpointId());
        map.put("scoreId", component.scoreId());
        map.put("label", component.label());
        map.put("weight", component.weight());
        map.put("required", component.required());
        putIfNotNull(map, "hardFailBelow", component.hardFailBelow());
        return map;
    }

    private static Map<String, Object> tableViewMap(PrismPack.TableView tableView) {
        LinkedHashMap<String, Object> map = copy(tableView.raw());
        putIfNotNull(map, "id", tableView.id());
        putIfNotNull(map, "title", tableView.title());
        map.put("columns", tableView.columns());
        map.put("frozenColumns", tableView.frozenColumns());
        map.put("hiddenColumns", tableView.hiddenColumns());
        map.put("sort", tableView.sort().stream().map(PrismPackWriter::sortMap).toList());
        map.put("filters", tableView.filters().stream().map(PrismPackWriter::filterMap).toList());
        map.put("colorRules", tableView.colorRules().stream().map(PrismPackWriter::colorRuleMap).toList());
        return map;
    }

    private static Map<String, Object> sortMap(PrismPack.Sort sort) {
        LinkedHashMap<String, Object> map = copy(sort.raw());
        putIfNotNull(map, "column", sort.column());
        putIfNotNull(map, "direction", sort.direction());
        return map;
    }

    private static Map<String, Object> filterMap(PrismPack.Filter filter) {
        LinkedHashMap<String, Object> map = copy(filter.raw());
        putIfNotNull(map, "column", filter.column());
        putIfNotNull(map, "type", filter.type());
        return map;
    }

    private static Map<String, Object> colorRuleMap(PrismPack.ColorRule colorRule) {
        LinkedHashMap<String, Object> map = copy(colorRule.raw());
        putIfNotNull(map, "column", colorRule.column());
        putIfNotNull(map, "type", colorRule.type());
        putIfNotNull(map, "direction", colorRule.direction());
        return map;
    }

    private static Map<String, Object> visualizationsMap(PrismPack.VisualizationSet set) {
        LinkedHashMap<String, Object> map = copy(set.raw());
        map.put("visualizations", set.visualizations().stream().map(PrismPackWriter::visualizationMap).toList());
        return map;
    }

    private static Map<String, Object> visualizationMap(PrismPack.Visualization visualization) {
        LinkedHashMap<String, Object> map = copy(visualization.raw());
        putIfNotNull(map, "id", visualization.id());
        putIfNotNull(map, "type", visualization.type());
        putIfNotNull(map, "title", visualization.title());
        putIfNotNull(map, "x", visualization.x());
        putIfNotNull(map, "y", visualization.y());
        putIfNotNull(map, "colorBy", visualization.colorBy());
        putIfNotNull(map, "sizeBy", visualization.sizeBy());
        return map;
    }

    private static Map<String, Object> attachmentsMap(PrismPack.AttachmentSet set) {
        LinkedHashMap<String, Object> map = copy(set.raw());
        map.put("attachments", set.attachments().stream().map(PrismPackWriter::attachmentMap).toList());
        return map;
    }

    private static Map<String, Object> attachmentMap(PrismPack.Attachment attachment) {
        LinkedHashMap<String, Object> map = copy(attachment.raw());
        putIfNotNull(map, "id", attachment.id());
        if (attachment.target() != null) {
            map.put("target", attachmentTargetMap(attachment.target()));
        }
        putIfNotNull(map, "name", attachment.name());
        putIfNotNull(map, "mimeType", attachment.mimeType());
        if (attachment.content() != null) {
            map.put("content", attachmentContentMap(attachment.content()));
        }
        return map;
    }

    private static Map<String, Object> attachmentTargetMap(PrismPack.AttachmentTarget target) {
        LinkedHashMap<String, Object> map = copy(target.raw());
        putIfNotNull(map, "type", target.type());
        putIfNotNull(map, "rowKeyColumn", target.rowKeyColumn());
        putIfNotNull(map, "rowKey", target.rowKey());
        putIfNotNull(map, "column", target.column());
        return map;
    }

    private static Map<String, Object> attachmentContentMap(PrismPack.AttachmentContent content) {
        LinkedHashMap<String, Object> map = copy(content.raw());
        putIfNotNull(map, "type", content.type());
        putIfNotNull(map, "text", content.text());
        putIfNotNull(map, "path", content.path());
        return map;
    }

    private static LinkedHashMap<String, Object> copy(Map<String, Object> raw) {
        return raw == null ? new LinkedHashMap<>() : new LinkedHashMap<>(raw);
    }

    private static void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
