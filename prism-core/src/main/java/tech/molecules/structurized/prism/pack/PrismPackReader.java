package tech.molecules.structurized.prism.pack;

import tech.molecules.structurized.prism.io.PrismTsvEscaper;
import tech.molecules.structurized.prism.prediction.PredictionCapability;
import tech.molecules.structurized.prism.prediction.PredictionMetadata;
import tech.molecules.structurized.prism.score.EndpointScoreDefinition;
import tech.molecules.structurized.prism.score.MpoAggregationDefinition;
import tech.molecules.structurized.prism.score.MpoComponentDefinition;
import tech.molecules.structurized.prism.score.MpoDefinition;
import tech.molecules.structurized.prism.score.PropertyProfileDefinition;
import tech.molecules.structurized.prism.score.PropertyProfileItem;
import tech.molecules.structurized.prism.score.ScorePoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reader for PrismPack v0.1 through v0.3 directory and ZIP packages.
 */
public final class PrismPackReader {
    private static final String MANIFEST_PATH = "prism-pack.json";
    private static final String DEFAULT_SCHEMA_PATH = "schema/dataframe.schema.json";

    private PrismPackReader() {}

    public static PrismPack read(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            return read(new DirectorySource(path));
        }
        return read(new ZipSource(path));
    }

    private static PrismPack read(Source source) throws IOException {
        ArrayList<String> warnings = new ArrayList<>();
        Map<String, Object> manifestJson = readJsonObject(source, MANIFEST_PATH, true);
        PrismPack.Manifest manifest = parseManifest(manifestJson);

        PrismPack.DataframeRef dataframeRef = manifest.dataframe();
        String dataframePath = require(dataframeRef.path(), "manifest dataframe.path is required");
        String schemaPath = dataframeRef.schema() == null ? DEFAULT_SCHEMA_PATH : dataframeRef.schema();

        PrismPack.DataFrame dataframe = readDataFrame(source.readRequired(dataframePath), dataframePath);
        PrismPack.DataFrameSchema schema = parseSchema(readJsonObject(source, schemaPath, true));
        validateDataframe(dataframe, schema, warnings);

        PrismPack.MoleculeMetadata molecules = manifest.moleculesPath() == null
                ? null
                : parseMolecules(readJsonObject(source, manifest.moleculesPath(), false));
        PrismPack.EndpointMetadata endpoints = manifest.endpointsPath() == null
                ? null
                : parseEndpoints(readJsonObject(source, manifest.endpointsPath(), false));
        PrismPack.EndpointResultSet endpointResults = manifest.endpointResults() == null
                ? null
                : parseEndpointResults(source.readRequired(manifest.endpointResults().path()), manifest.endpointResults());
        PrismPack.RowSetMetadata rowSets = manifest.rowSetsPath() == null
                ? null
                : parseRowSets(readJsonObject(source, manifest.rowSetsPath(), false));
        PrismPack.TableView tableView = manifest.tableViewPath() == null
                ? null
                : parseTableView(readJsonObject(source, manifest.tableViewPath(), false));
        PrismPack.VisualizationSet visualizations = manifest.visualizationsPath() == null
                ? null
                : parseVisualizations(readJsonObject(source, manifest.visualizationsPath(), false));
        PrismPack.AttachmentSet attachments = manifest.attachmentsPath() == null
                ? null
                : parseAttachments(readJsonObject(source, manifest.attachmentsPath(), false));
        PrismPack.ScoreMetadata scores = manifest.scoresPath() == null
                ? null
                : parseScores(readJsonObject(source, manifest.scoresPath(), false));
        PrismPack.PropertyProfileMetadata propertyProfiles = manifest.propertyProfilesPath() == null
                ? null
                : parsePropertyProfiles(readJsonObject(source, manifest.propertyProfilesPath(), false));
        PredictionMetadata predictions = manifest.predictionsPath() == null
                ? null
                : parsePredictions(readJsonObject(source, manifest.predictionsPath(), false));
        Map<String, Object> provenance = manifest.provenancePath() == null
                ? Map.of()
                : readJsonObject(source, manifest.provenancePath(), false);

        validateReferences(dataframe, molecules, endpoints, tableView, visualizations, attachments,
                scores, propertyProfiles, predictions, warnings);
        validateSnapshotSemantics(dataframe, endpoints, endpointResults, rowSets);
        return new PrismPack(manifest, dataframe, schema, molecules, endpoints, endpointResults, rowSets, tableView, visualizations,
                attachments, scores, propertyProfiles, predictions, provenance, warnings);
    }

    private static PrismPack.Manifest parseManifest(Map<String, Object> json) {
        Map<String, Object> dataframe = object(json.get("dataframe"));
        PrismPack.DataframeRef dataframeRef = new PrismPack.DataframeRef(
                string(dataframe.get("id")),
                string(dataframe.get("path")),
                string(dataframe.get("schema")),
                string(dataframe.get("rowType")),
                dataframe);
        Map<String, Object> endpointResults = object(json.get("endpointResults"));
        PrismPack.EndpointResultsRef endpointResultsRef = endpointResults.isEmpty() ? null : new PrismPack.EndpointResultsRef(
                string(endpointResults.get("path")), string(endpointResults.get("rowKeyColumn")), endpointResults);
        return new PrismPack.Manifest(
                string(json.get("prismPackVersion")),
                string(json.get("id")),
                string(json.get("title")),
                string(json.get("description")),
                string(json.get("createdAt")),
                string(json.get("createdBy")),
                dataframeRef,
                string(json.get("molecules")),
                string(json.get("endpoints")),
                endpointResultsRef,
                string(json.get("rowSets")),
                string(json.get("tableView")),
                string(json.get("visualizations")),
                string(json.get("attachments")),
                string(json.get("scores")),
                string(json.get("propertyProfiles")),
                string(json.get("predictions")),
                string(json.get("provenance")),
                json);
    }

    private static PrismPack.ScoreMetadata parseScores(Map<String, Object> json) {
        if (json.isEmpty()) return null;
        ArrayList<EndpointScoreDefinition> definitions = new ArrayList<>();
        for (Map<String, Object> item : objectList(json.get("scores"))) {
            ArrayList<ScorePoint> points = new ArrayList<>();
            for (Map<String, Object> point : objectList(item.get("points"))) {
                points.add(new ScorePoint(number(point.get("x")), number(point.get("score"))));
            }
            definitions.add(new EndpointScoreDefinition(
                    string(item.get("id")), string(item.get("endpointId")), string(item.get("displayName")),
                    string(item.get("description")), string(item.get("scoreType")), string(item.get("xScale")),
                    bool(item.get("clampOutsideRange"), true), points, object(item.get("metadata"))));
        }
        return new PrismPack.ScoreMetadata(definitions, json);
    }

    private static PrismPack.PropertyProfileMetadata parsePropertyProfiles(Map<String, Object> json) {
        if (json.isEmpty()) return null;
        ArrayList<PropertyProfileDefinition> profiles = new ArrayList<>();
        for (Map<String, Object> item : objectList(json.get("profiles"))) {
            ArrayList<PropertyProfileItem> profileItems = new ArrayList<>();
            int fallbackOrder = 0;
            for (Map<String, Object> profileItem : objectList(item.get("items"))) {
                profileItems.add(new PropertyProfileItem(
                        string(profileItem.get("endpointId")), string(profileItem.get("scoreId")),
                        string(profileItem.get("label")), string(profileItem.get("group")),
                        integer(profileItem.get("order"), fallbackOrder++ * 10),
                        bool(profileItem.get("visible"), true), object(profileItem.get("metadata"))));
            }
            ArrayList<MpoDefinition> mpos = new ArrayList<>();
            for (Map<String, Object> mpo : objectList(item.get("mpos"))) {
                ArrayList<MpoComponentDefinition> components = new ArrayList<>();
                for (Map<String, Object> component : objectList(mpo.get("components"))) {
                    components.add(new MpoComponentDefinition(
                            string(component.get("endpointId")), string(component.get("scoreId")),
                            string(component.get("label")), number(component.getOrDefault("weight", 1.0)),
                            bool(component.get("required"), false), nullableNumber(component.get("hardFailBelow"))));
                }
                Map<String, Object> aggregation = object(mpo.get("aggregation"));
                mpos.add(new MpoDefinition(string(mpo.get("id")), string(mpo.get("displayName")), components,
                        new MpoAggregationDefinition(string(aggregation.get("type")), string(aggregation.get("missing")),
                                number(aggregation.getOrDefault("warningCoverageBelow", 0.5)))));
            }
            profiles.add(new PropertyProfileDefinition(string(item.get("id")), string(item.get("title")),
                    string(item.get("description")), profileItems, mpos, object(item.get("metadata"))));
        }
        return new PrismPack.PropertyProfileMetadata(profiles, json);
    }

    private static PredictionMetadata parsePredictions(Map<String, Object> json) {
        if (json.isEmpty()) return null;
        ArrayList<PredictionCapability> capabilities = new ArrayList<>();
        for (Map<String, Object> item : objectList(json.get("capabilities"))) {
            capabilities.add(new PredictionCapability(
                    string(item.get("capabilityId")),
                    string(item.get("endpointId")),
                    string(item.get("predictedEndpointId")),
                    string(item.get("displayName")),
                    string(item.get("providerId")),
                    string(item.get("workflowId")),
                    string(item.get("workflowVersion")),
                    string(item.get("status")),
                    integer(item.get("priority"), 0),
                    string(item.get("structureColumn")),
                    string(item.get("structureFormat")),
                    object(item.get("metadata"))));
        }
        return new PredictionMetadata(capabilities, json);
    }


    private static PrismPack.DataFrameSchema parseSchema(Map<String, Object> json) {
        ArrayList<PrismPack.Column> columns = new ArrayList<>();
        for (Map<String, Object> column : objectList(json.get("columns"))) {
            columns.add(new PrismPack.Column(
                    string(column.get("name")),
                    string(column.get("type")),
                    string(column.get("semanticType")),
                    string(column.get("displayName")),
                    string(column.get("role")),
                    string(column.get("unit")),
                    string(column.get("endpointId")),
                    string(column.get("direction")),
                    string(column.get("structureFormat")),
                    column));
        }
        return new PrismPack.DataFrameSchema(columns, json);
    }

    private static PrismPack.MoleculeMetadata parseMolecules(Map<String, Object> json) {
        if (json.isEmpty()) {
            return null;
        }
        return new PrismPack.MoleculeMetadata(
                string(json.get("primaryStructureColumn")),
                string(json.get("structureFormat")),
                string(json.get("compoundIdColumn")),
                json);
    }

    private static PrismPack.EndpointMetadata parseEndpoints(Map<String, Object> json) {
        if (json.isEmpty()) {
            return null;
        }
        ArrayList<PrismPack.Endpoint> endpoints = new ArrayList<>();
        for (Map<String, Object> endpoint : objectList(json.get("endpoints"))) {
            endpoints.add(new PrismPack.Endpoint(
                    string(endpoint.get("id")),
                    string(endpoint.get("column")),
                    string(endpoint.get("displayName")),
                    string(endpoint.get("unit")),
                    string(endpoint.get("direction")),
                    string(endpoint.get("assay")),
                    string(endpoint.get("protocol")),
                    object(endpoint.get("definition")).isEmpty() ? null : EndpointDefinitionCodec.decode(object(endpoint.get("definition"))),
                    endpoint));
        }
        return new PrismPack.EndpointMetadata(endpoints, json);
    }

    private static PrismPack.EndpointResultSet parseEndpointResults(String jsonl, PrismPack.EndpointResultsRef ref) {
        ArrayList<PrismPack.EndpointResultRecord> results = new ArrayList<>();
        String normalized = jsonl.replace("\r\n", "\n").replace('\r', '\n');
        int lineNumber = 0;
        for (String line : normalized.split("\n")) {
            lineNumber++;
            if (line.isBlank()) continue;
            try {
                Object parsed = PrismPackJson.parse(line);
                if (!(parsed instanceof Map<?, ?> raw)) throw new IllegalArgumentException("entry must be an object");
                Map<String, Object> item = object(raw);
                results.add(new PrismPack.EndpointResultRecord(
                        string(item.get("rowKey")), string(item.get("endpointId")),
                        EndpointResultCodec.decode(object(item.get("result"))), item));
            } catch (RuntimeException error) {
                throw new PrismPackException(ref.path() + " line " + lineNumber + ": " + error.getMessage(), error);
            }
        }
        return new PrismPack.EndpointResultSet(ref.rowKeyColumn(), results, ref.raw());
    }

    private static PrismPack.RowSetMetadata parseRowSets(Map<String, Object> json) {
        if (json.isEmpty()) return null;
        ArrayList<PrismPack.RowSet> rowSets = new ArrayList<>();
        for (Map<String, Object> item : objectList(json.get("rowSets"))) {
            rowSets.add(new PrismPack.RowSet(string(item.get("id")), string(item.get("name")),
                    string(item.get("description")), stringList(item.get("rowIds")),
                    object(item.get("provenance")), item));
        }
        return new PrismPack.RowSetMetadata(rowSets, json);
    }

    private static void validateSnapshotSemantics(PrismPack.DataFrame dataframe,
                                                   PrismPack.EndpointMetadata endpoints,
                                                   PrismPack.EndpointResultSet endpointResults,
                                                   PrismPack.RowSetMetadata rowSets) {
        Set<String> endpointIds = new HashSet<>();
        if (endpoints != null) endpoints.endpoints().forEach(endpoint -> endpointIds.add(endpoint.id()));
        Set<String> rowIds = new HashSet<>();
        String rowKeyColumn = endpointResults == null ? null : endpointResults.rowKeyColumn();
        if (rowKeyColumn != null) {
            int index = dataframe.columnIndex(rowKeyColumn);
            if (index < 0) throw new PrismPackException("endpointResults.rowKeyColumn does not exist: " + rowKeyColumn);
            for (List<String> row : dataframe.rows()) {
                if (!rowIds.add(row.get(index))) throw new PrismPackException("duplicate dataframe row key: " + row.get(index));
            }
        }
        if (endpointResults != null) {
            Set<String> keys = new HashSet<>();
            for (PrismPack.EndpointResultRecord result : endpointResults.results()) {
                if (!rowIds.contains(result.rowKey())) throw new PrismPackException("endpoint result references unknown row: " + result.rowKey());
                if (!endpointIds.contains(result.endpointId())) throw new PrismPackException("endpoint result references unknown endpoint: " + result.endpointId());
                if (!keys.add(result.rowKey() + "\u0000" + result.endpointId())) throw new PrismPackException("duplicate endpoint result: " + result.rowKey() + "/" + result.endpointId());
            }
        }
        if (rowSets != null) {
            Set<String> ids = new HashSet<>();
            for (PrismPack.RowSet rowSet : rowSets.rowSets()) {
                if (!ids.add(rowSet.id())) throw new PrismPackException("duplicate row set id: " + rowSet.id());
                if (!rowIds.isEmpty()) for (String rowId : rowSet.rowIds()) if (!rowIds.contains(rowId))
                    throw new PrismPackException("row set '" + rowSet.id() + "' references unknown row: " + rowId);
            }
        }
    }

    private static PrismPack.TableView parseTableView(Map<String, Object> json) {
        if (json.isEmpty()) {
            return null;
        }
        ArrayList<PrismPack.Sort> sort = new ArrayList<>();
        for (Map<String, Object> item : objectList(json.get("sort"))) {
            sort.add(new PrismPack.Sort(string(item.get("column")), string(item.get("direction")), item));
        }
        ArrayList<PrismPack.Filter> filters = new ArrayList<>();
        for (Map<String, Object> item : objectList(json.get("filters"))) {
            filters.add(new PrismPack.Filter(string(item.get("column")), string(item.get("type")), item));
        }
        ArrayList<PrismPack.ColorRule> colorRules = new ArrayList<>();
        for (Map<String, Object> item : objectList(json.get("colorRules"))) {
            colorRules.add(new PrismPack.ColorRule(
                    string(item.get("column")),
                    string(item.get("type")),
                    string(item.get("direction")),
                    item));
        }
        return new PrismPack.TableView(
                string(json.get("id")),
                string(json.get("title")),
                stringList(json.get("columns")),
                stringList(json.get("frozenColumns")),
                stringList(json.get("hiddenColumns")),
                sort,
                filters,
                colorRules,
                json);
    }

    private static PrismPack.VisualizationSet parseVisualizations(Map<String, Object> json) {
        if (json.isEmpty()) {
            return null;
        }
        ArrayList<PrismPack.Visualization> visualizations = new ArrayList<>();
        for (Map<String, Object> item : objectList(json.get("visualizations"))) {
            visualizations.add(new PrismPack.Visualization(
                    string(item.get("id")),
                    string(item.get("type")),
                    string(item.get("title")),
                    string(item.get("x")),
                    string(item.get("y")),
                    string(item.get("colorBy")),
                    string(item.get("sizeBy")),
                    item));
        }
        return new PrismPack.VisualizationSet(visualizations, json);
    }

    private static PrismPack.AttachmentSet parseAttachments(Map<String, Object> json) {
        if (json.isEmpty()) {
            return null;
        }
        ArrayList<PrismPack.Attachment> attachments = new ArrayList<>();
        for (Map<String, Object> item : objectList(json.get("attachments"))) {
            Map<String, Object> target = object(item.get("target"));
            Map<String, Object> content = object(item.get("content"));
            attachments.add(new PrismPack.Attachment(
                    string(item.get("id")),
                    new PrismPack.AttachmentTarget(
                            string(target.get("type")),
                            string(target.get("rowKeyColumn")),
                            string(target.get("rowKey")),
                            string(target.get("column")),
                            target),
                    string(item.get("name")),
                    string(item.get("mimeType")),
                    new PrismPack.AttachmentContent(
                            string(content.get("type")),
                            string(content.get("text")),
                            string(content.get("path")),
                            content),
                    item));
        }
        return new PrismPack.AttachmentSet(attachments, json);
    }

    private static PrismPack.DataFrame readDataFrame(String tsv, String path) {
        String normalized = tsv.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        int lineCount = lines.length;
        while (lineCount > 0 && lines[lineCount - 1].isEmpty()) {
            lineCount--;
        }
        if (lineCount == 0) {
            throw new PrismPackException(path + " is empty");
        }

        List<String> headers = parseTsvLine(lines[0]);
        if (headers.isEmpty()) {
            throw new PrismPackException(path + " has no columns");
        }
        Set<String> seen = new HashSet<>();
        for (String header : headers) {
            if (header == null || header.isBlank()) {
                throw new PrismPackException(path + " contains an empty column name");
            }
            if (!seen.add(header)) {
                throw new PrismPackException(path + " contains duplicate column '" + header + "'");
            }
        }

        ArrayList<List<String>> rows = new ArrayList<>();
        for (int i = 1; i < lineCount; i++) {
            List<String> row = parseTsvLine(lines[i]);
            if (row.size() > headers.size()) {
                throw new PrismPackException(path + " line " + (i + 1) + " has more cells than the header");
            }
            ArrayList<String> padded = new ArrayList<>(row);
            while (padded.size() < headers.size()) {
                padded.add("");
            }
            rows.add(padded);
        }
        return new PrismPack.DataFrame(headers, rows);
    }

    private static List<String> parseTsvLine(String line) {
        String[] cells = line.split("\t", -1);
        ArrayList<String> values = new ArrayList<>(cells.length);
        for (String cell : cells) {
            values.add(PrismTsvEscaper.unescapeCell(cell));
        }
        return values;
    }

    private static void validateDataframe(PrismPack.DataFrame dataframe, PrismPack.DataFrameSchema schema, List<String> warnings) {
        Set<String> dataframeColumns = new HashSet<>(dataframe.headers());
        Set<String> schemaColumns = new HashSet<>();
        for (PrismPack.Column column : schema.columns()) {
            if (column.name() == null || column.name().isBlank()) {
                warnings.add("schema contains a column without a name");
                continue;
            }
            if (!schemaColumns.add(column.name())) {
                warnings.add("schema contains duplicate column '" + column.name() + "'");
            }
            if (!dataframeColumns.contains(column.name())) {
                warnings.add("schema column '" + column.name() + "' is not present in the dataframe");
            }
        }
        for (String column : dataframe.headers()) {
            if (!schemaColumns.contains(column)) {
                warnings.add("dataframe column '" + column + "' has no schema metadata");
            }
        }
    }

    private static void validateReferences(PrismPack.DataFrame dataframe,
                                           PrismPack.MoleculeMetadata molecules,
                                           PrismPack.EndpointMetadata endpoints,
                                           PrismPack.TableView tableView,
                                           PrismPack.VisualizationSet visualizations,
                                           PrismPack.AttachmentSet attachments,
                                           PrismPack.ScoreMetadata scores,
                                           PrismPack.PropertyProfileMetadata propertyProfiles,
                                           PredictionMetadata predictions,
                                           List<String> warnings) {
        if (molecules != null) {
            warnIfMissing(dataframe, molecules.primaryStructureColumn(), "molecules.primaryStructureColumn", warnings);
            warnIfMissing(dataframe, molecules.compoundIdColumn(), "molecules.compoundIdColumn", warnings);
        }
        if (endpoints != null) {
            for (PrismPack.Endpoint endpoint : endpoints.endpoints()) {
                warnIfMissing(dataframe, endpoint.column(), "endpoint '" + endpoint.id() + "'", warnings);
            }
        }
        if (tableView != null) {
            for (String column : tableView.columns()) {
                warnIfMissing(dataframe, column, "tableView.columns", warnings);
            }
            for (String column : tableView.hiddenColumns()) {
                warnIfMissing(dataframe, column, "tableView.hiddenColumns", warnings);
            }
            for (PrismPack.Sort sort : tableView.sort()) {
                warnIfMissing(dataframe, sort.column(), "tableView.sort", warnings);
            }
            for (PrismPack.Filter filter : tableView.filters()) {
                warnIfMissing(dataframe, filter.column(), "tableView.filters", warnings);
            }
            for (PrismPack.ColorRule colorRule : tableView.colorRules()) {
                warnIfMissing(dataframe, colorRule.column(), "tableView.colorRules", warnings);
            }
        }
        if (visualizations != null) {
            for (PrismPack.Visualization visualization : visualizations.visualizations()) {
                warnIfMissing(dataframe, visualization.x(), "visualization '" + visualization.id() + "' x", warnings);
                warnIfMissing(dataframe, visualization.y(), "visualization '" + visualization.id() + "' y", warnings);
                warnIfMissing(dataframe, visualization.colorBy(), "visualization '" + visualization.id() + "' colorBy", warnings);
                warnIfMissing(dataframe, visualization.sizeBy(), "visualization '" + visualization.id() + "' sizeBy", warnings);
            }
        }
        if (attachments != null) {
            for (PrismPack.Attachment attachment : attachments.attachments()) {
                if (attachment.target() != null && "cell".equals(attachment.target().type())) {
                    warnIfMissing(dataframe, attachment.target().rowKeyColumn(), "attachment '" + attachment.id() + "' rowKeyColumn", warnings);
                    warnIfMissing(dataframe, attachment.target().column(), "attachment '" + attachment.id() + "' column", warnings);
                }
            }
        }
        Set<String> endpointIds = new HashSet<>();
        if (endpoints != null) endpoints.endpoints().forEach(endpoint -> endpointIds.add(endpoint.id()));
        Set<String> scoreIds = new HashSet<>();
        if (scores != null) {
            for (EndpointScoreDefinition score : scores.scores()) {
                if (!scoreIds.add(score.id())) warnings.add("duplicate score id '" + score.id() + "'");
                if (!endpointIds.isEmpty() && !endpointIds.contains(score.endpointId())) {
                    warnings.add("score '" + score.id() + "' references unknown endpoint '" + score.endpointId() + "'");
                }
            }
        }
        if (propertyProfiles != null) {
            Set<String> profileIds = new HashSet<>();
            for (PropertyProfileDefinition profile : propertyProfiles.profiles()) {
                if (!profileIds.add(profile.id())) warnings.add("duplicate property profile id '" + profile.id() + "'");
                for (PropertyProfileItem item : profile.items()) {
                    if (!endpointIds.isEmpty() && !endpointIds.contains(item.endpointId())) {
                        warnings.add("property profile '" + profile.id() + "' references unknown endpoint '" + item.endpointId() + "'");
                    }
                    if (item.scoreId() != null && !scoreIds.contains(item.scoreId())) {
                        warnings.add("property profile '" + profile.id() + "' references unknown score '" + item.scoreId() + "'");
                    }
                }
                for (MpoDefinition mpo : profile.mpos()) {
                    for (MpoComponentDefinition component : mpo.components()) {
                        if (!scoreIds.contains(component.scoreId())) {
                            warnings.add("MPO '" + mpo.id() + "' references unknown score '" + component.scoreId() + "'");
                        }
                    }
                }
            }
        }
        if (predictions != null) {
            Set<String> capabilityIds = new HashSet<>();
            for (PredictionCapability capability : predictions.capabilities()) {
                if (!capabilityIds.add(capability.capabilityId())) {
                    warnings.add("duplicate prediction capability id '" + capability.capabilityId() + "'");
                }
                if (!endpointIds.isEmpty() && !endpointIds.contains(capability.endpointId())) {
                    warnings.add("prediction capability '" + capability.capabilityId()
                            + "' references unknown endpoint '" + capability.endpointId() + "'");
                }
                warnIfMissing(dataframe, capability.structureColumn(),
                        "prediction capability '" + capability.capabilityId() + "' structureColumn", warnings);
            }
        }
    }

    private static void warnIfMissing(PrismPack.DataFrame dataframe, String column, String context, List<String> warnings) {
        if (column != null && dataframe.columnIndex(column) == -1) {
            warnings.add(context + " references unknown column '" + column + "'");
        }
    }

    private static Map<String, Object> readJsonObject(Source source, String path, boolean required) throws IOException {
        String content = required ? source.readRequired(path) : source.readOptional(path);
        if (content == null) {
            return Map.of();
        }
        Object parsed;
        try {
            parsed = PrismPackJson.parse(content);
        }
        catch (PrismPackException e) {
            throw new PrismPackException("invalid JSON in " + path + ": " + e.getMessage(), e);
        }
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new PrismPackException(path + " must contain a JSON object");
        }
        return castObject(map);
    }

    private static String require(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new PrismPackException(message);
        }
        return value;
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static double number(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (RuntimeException exception) {
            throw new PrismPackException("expected numeric JSON value but found: " + value);
        }
    }

    private static Double nullableNumber(Object value) {
        return value == null ? null : number(value);
    }

    private static int integer(Object value, int fallback) {
        return value == null ? fallback : (int) number(value);
    }

    private static boolean bool(Object value, boolean fallback) {
        return value == null ? fallback : value instanceof Boolean booleanValue
                ? booleanValue : Boolean.parseBoolean(String.valueOf(value));
    }

    private static Map<String, Object> object(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        return castObject(map);
    }

    private static List<Map<String, Object>> objectList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        ArrayList<Map<String, Object>> objects = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                objects.add(castObject(map));
            }
        }
        return objects;
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        ArrayList<String> strings = new ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                strings.add(String.valueOf(item));
            }
        }
        return strings;
    }

    private static Map<String, Object> castObject(Map<?, ?> map) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private interface Source {
        String readRequired(String path) throws IOException;

        String readOptional(String path) throws IOException;
    }

    private record DirectorySource(Path root) implements Source {
        @Override
        public String readRequired(String path) throws IOException {
            String content = readOptional(path);
            if (content == null) {
                throw new PrismPackException("missing required PrismPack file: " + path);
            }
            return content;
        }

        @Override
        public String readOptional(String path) throws IOException {
            Path resolved = root.resolve(normalize(path)).normalize();
            if (!resolved.startsWith(root.normalize()) || !Files.exists(resolved)) {
                return null;
            }
            return Files.readString(resolved, StandardCharsets.UTF_8);
        }
    }

    private record ZipSource(Path path) implements Source {
        @Override
        public String readRequired(String entryPath) throws IOException {
            String content = readOptional(entryPath);
            if (content == null) {
                throw new PrismPackException("missing required PrismPack file: " + entryPath);
            }
            return content;
        }

        @Override
        public String readOptional(String entryPath) throws IOException {
            String normalized = normalize(entryPath);
            try (ZipFile zipFile = new ZipFile(path.toFile())) {
                ZipEntry entry = zipFile.getEntry(normalized);
                if (entry == null) {
                    entry = findEntryWithSingleRoot(zipFile, normalized);
                }
                if (entry == null) {
                    return null;
                }
                return new String(zipFile.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        private ZipEntry findEntryWithSingleRoot(ZipFile zipFile, String normalized) {
            return zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .filter(entry -> normalize(entry.getName()).endsWith("/" + normalized))
                    .findFirst()
                    .orElse(null);
        }
    }

    private static String normalize(String path) {
        return path.replace('\\', '/').replaceAll("^/+", "");
    }
}
