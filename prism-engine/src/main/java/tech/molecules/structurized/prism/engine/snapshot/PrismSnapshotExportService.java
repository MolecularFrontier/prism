package tech.molecules.structurized.prism.engine.snapshot;

import tech.molecules.structurized.prism.engine.PrismColumn;
import tech.molecules.structurized.prism.engine.PrismSession;
import tech.molecules.structurized.prism.pack.PrismPack;
import tech.molecules.structurized.prism.pack.PrismPackReader;
import tech.molecules.structurized.prism.pack.PrismPackWriter;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PrismSnapshotExportService {
    private PrismSnapshotExportService() {
    }

    public static PrismSnapshotExportResult export(PrismSnapshotDataset snapshot,
                                                   PrismSession session,
                                                   Path outputPath,
                                                   String title,
                                                   String createdBy) throws IOException {
        if (!(snapshot instanceof PrismPackBackedSnapshotDataset packBacked)) {
            throw new IllegalArgumentException("snapshot export requires a PrismPack-backed source snapshot");
        }
        if (session == null) throw new IllegalArgumentException("session must not be null");
        if (outputPath == null) throw new IllegalArgumentException("output path must not be null");
        Path target = outputPath.toAbsolutePath().normalize();
        if (!target.getFileName().toString().toLowerCase().endsWith(".prismpack")) {
            throw new IllegalArgumentException("snapshot export path must end in .prismpack");
        }
        if (Files.exists(target)) throw new IllegalArgumentException("snapshot export target already exists: " + target);
        Path parent = target.getParent();
        if (parent != null) Files.createDirectories(parent);

        PrismPack source = packBacked.sourcePack();
        List<PrismColumn> derivedColumns = session.table().columns().stream()
                .filter(column -> "endpoint_score".equals(column.schema().semanticType()))
                .filter(column -> !source.dataFrame().headers().contains(column.id()))
                .toList();
        PrismPack exported = build(source, session, derivedColumns, title, createdBy);

        Path temporary = Files.createTempFile(parent, target.getFileName().toString() + ".", ".tmp");
        try {
            PrismPackWriter.writeZip(temporary, exported);
            PrismPackReader.read(temporary);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return new PrismSnapshotExportResult(target, exported.manifest().id(), exported.dataFrame().rows().size(),
                session.scoreDefinitions().stream().map(definition -> definition.id()).toList(),
                derivedColumns.stream().map(PrismColumn::id).toList());
    }

    private static PrismPack build(PrismPack source,
                                   PrismSession session,
                                   List<PrismColumn> derivedColumns,
                                   String title,
                                   String createdBy) {
        ArrayList<String> headers = new ArrayList<>(source.dataFrame().headers());
        derivedColumns.forEach(column -> headers.add(column.id()));
        ArrayList<List<String>> rows = new ArrayList<>(source.dataFrame().rows().size());
        for (int row = 0; row < source.dataFrame().rows().size(); row++) {
            ArrayList<String> values = new ArrayList<>(source.dataFrame().rows().get(row));
            for (PrismColumn column : derivedColumns) {
                Object value = column.isMissing(row) ? null : column.valueAt(row);
                values.add(value == null ? "" : String.valueOf(value));
            }
            rows.add(List.copyOf(values));
        }

        ArrayList<PrismPack.Column> schemaColumns = new ArrayList<>(source.schema().columns());
        for (PrismColumn column : derivedColumns) {
            var schema = column.schema();
            schemaColumns.add(new PrismPack.Column(schema.id(), "number", schema.semanticType(),
                    schema.displayName(), schema.role(), schema.unit(), schema.endpointId(),
                    schema.direction(), schema.structureFormat(), schema.raw()));
        }

        Map<String, PrismPack.RowSet> sourceRowSets = new LinkedHashMap<>();
        if (source.rowSets() != null) {
            source.rowSets().rowSets().forEach(rowSet -> sourceRowSets.put(rowSet.id(), rowSet));
        }
        List<PrismPack.RowSet> rowSets = session.rowSets().stream().map(rowSet -> {
            PrismPack.RowSet original = sourceRowSets.get(rowSet.id());
            return new PrismPack.RowSet(rowSet.id(), rowSet.name(), rowSet.description(),
                    List.copyOf(rowSet.rowIds()), rowSet.provenance(), original == null ? Map.of() : original.raw());
        }).toList();
        PrismPack.ScoreMetadata scores = new PrismPack.ScoreMetadata(session.scoreDefinitions(),
                source.scores() == null ? Map.of() : source.scores().raw());

        PrismPack.TableView sourceView = source.tableView();
        ArrayList<String> displayed = new ArrayList<>(sourceView == null ? source.dataFrame().headers() : sourceView.columns());
        derivedColumns.stream().map(PrismColumn::id).filter(id -> !displayed.contains(id)).forEach(displayed::add);
        PrismPack.TableView tableView = sourceView == null
                ? new PrismPack.TableView("analysis", "Analysis snapshot", displayed, List.of(), List.of(),
                        List.of(), List.of(), List.of(), Map.of())
                : new PrismPack.TableView(sourceView.id(), sourceView.title(), displayed,
                        sourceView.frozenColumns(), sourceView.hiddenColumns(), sourceView.sort(),
                        sourceView.filters(), sourceView.colorRules(), sourceView.raw());

        Instant exportedAt = Instant.now();
        LinkedHashMap<String, Object> provenance = new LinkedHashMap<>(source.provenance());
        LinkedHashMap<String, Object> analysisExport = new LinkedHashMap<>();
        putIfPresent(analysisExport, "parentSnapshotId", source.manifest().id());
        putIfPresent(analysisExport, "parentCreatedAt", source.manifest().createdAt());
        analysisExport.put("exportedAt", exportedAt.toString());
        analysisExport.put("createdBy", normalized(createdBy, "Structurized MCP"));
        analysisExport.put("scoreIds", session.scoreDefinitions().stream().map(definition -> definition.id()).toList());
        analysisExport.put("derivedColumnIds", derivedColumns.stream().map(PrismColumn::id).toList());
        provenance.put("analysisExport", Map.copyOf(analysisExport));

        PrismPack.Manifest manifest = source.manifest();
        PrismPack.Manifest exportedManifest = new PrismPack.Manifest(
                "0.3",
                normalized(manifest.id(), "snapshot") + "-analysis-" + exportedAt.toEpochMilli(),
                normalized(title, manifest.title()),
                manifest.description(),
                exportedAt.toString(),
                normalized(createdBy, "Structurized MCP"),
                manifest.dataframe(),
                manifest.moleculesPath(),
                manifest.endpointsPath(),
                source.endpointResults() == null ? null
                        : new PrismPack.EndpointResultsRef(PrismPackWriter.ENDPOINT_RESULTS_PATH,
                        source.endpointResults().rowKeyColumn(), manifest.endpointResults() == null
                        ? Map.of() : manifest.endpointResults().raw()),
                PrismPackWriter.ROW_SETS_PATH,
                manifest.tableViewPath() == null ? PrismPackWriter.TABLE_VIEW_PATH : manifest.tableViewPath(),
                manifest.visualizationsPath(),
                manifest.attachmentsPath(),
                PrismPackWriter.SCORES_PATH,
                manifest.propertyProfilesPath(),
                manifest.predictionsPath(),
                manifest.provenancePath() == null ? PrismPackWriter.PROVENANCE_PATH : manifest.provenancePath(),
                manifest.raw());

        return new PrismPack(
                exportedManifest,
                new PrismPack.DataFrame(List.copyOf(headers), rows),
                new PrismPack.DataFrameSchema(schemaColumns, source.schema().raw()),
                source.molecules(),
                source.endpoints(),
                source.endpointResults(),
                new PrismPack.RowSetMetadata(rowSets,
                        source.rowSets() == null ? Map.of() : source.rowSets().raw()),
                tableView,
                source.visualizations(),
                source.attachments(),
                scores,
                source.propertyProfiles(),
                source.predictions(),
                provenance,
                source.warnings());
    }

    private static String normalized(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) target.put(key, value);
    }
}
