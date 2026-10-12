package tech.molecules.structurized.prismlite.swing;

import tech.molecules.structurized.prism.engine.PrismRowSet;
import tech.molecules.structurized.prism.engine.PrismSession;
import tech.molecules.structurized.prism.model.EndpointDataType;
import tech.molecules.structurized.prism.model.EndpointDefinition;
import tech.molecules.structurized.prism.pack.PrismPack;
import tech.molecules.structurized.prism.provider.SubjectRecord;
import tech.molecules.structurized.prism.provider.SubjectSet;
import tech.molecules.structurized.prism.provider.inmemory.InMemoryPrismDataset;
import tech.molecules.structurized.prism.query.EndpointValueRecord;
import tech.molecules.structurized.prism.result.BooleanResult;
import tech.molecules.structurized.prism.result.CategoricalResult;
import tech.molecules.structurized.prism.result.EndpointResult;
import tech.molecules.structurized.prism.result.NumericResult;
import tech.molecules.structurized.prism.result.NumericState;
import tech.molecules.structurized.prism.result.OptionalNumericResult;
import tech.molecules.structurized.prism.result.OptionalNumericState;
import tech.molecules.structurized.prism.result.TextResult;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridLayout;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class PrismLiteDatasetImporter {
    private PrismLiteDatasetImporter() {
    }

    public record ImportSelection(List<String> subjectSetIds, List<String> endpointIds) {
        public ImportSelection {
            subjectSetIds = subjectSetIds == null ? List.of() : List.copyOf(subjectSetIds);
            endpointIds = endpointIds == null ? List.of() : List.copyOf(endpointIds);
        }
    }

    public static ImportSelection allSelected(InMemoryPrismDataset dataset) {
        return new ImportSelection(
                dataset.getSubjectSets().stream().map(SubjectSet::getId).toList(),
                dataset.getEndpointDefinitions().stream().map(EndpointDefinition::getId).toList()
        );
    }

    public static Optional<ImportSelection> chooseImport(Component parent, InMemoryPrismDataset dataset) {
        return chooseImport(parent, dataset, "Import PRISM TSV Dataset");
    }

    public static Optional<ImportSelection> chooseImport(Component parent, InMemoryPrismDataset dataset, String title) {
        List<JCheckBox> setChecks = dataset.getSubjectSets().stream()
                .map(subjectSet -> new JCheckBox(label(subjectSet), true))
                .toList();
        List<JCheckBox> endpointChecks = dataset.getEndpointDefinitions().stream()
                .map(endpoint -> new JCheckBox(label(endpoint), true))
                .toList();

        JPanel content = new JPanel(new GridLayout(1, 2, 8, 0));
        content.add(selectionPanel("Subject sets", setChecks, dataset.getSubjectSets().size()));
        content.add(selectionPanel("Endpoints", endpointChecks, dataset.getEndpointDefinitions().size()));

        int result = JOptionPane.showConfirmDialog(
                parent,
                content,
                title,
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );
        if (result != JOptionPane.OK_OPTION) {
            return Optional.empty();
        }

        ArrayList<String> selectedSets = new ArrayList<>();
        for (int index = 0; index < setChecks.size(); index++) {
            if (setChecks.get(index).isSelected()) {
                selectedSets.add(dataset.getSubjectSets().get(index).getId());
            }
        }
        ArrayList<String> selectedEndpoints = new ArrayList<>();
        for (int index = 0; index < endpointChecks.size(); index++) {
            if (endpointChecks.get(index).isSelected()) {
                selectedEndpoints.add(dataset.getEndpointDefinitions().get(index).getId());
            }
        }
        return Optional.of(new ImportSelection(selectedSets, selectedEndpoints));
    }

    public static PrismSession toSession(InMemoryPrismDataset dataset, ImportSelection selection, Path sourcePath) {
        PrismSession session = PrismSession.from(toPack(dataset, selection, sourcePath));
        Set<String> importedSubjectIds = importedSubjectIds(dataset, selection.subjectSetIds());
        for (SubjectSet subjectSet : dataset.getSubjectSets()) {
            if (!selection.subjectSetIds().contains(subjectSet.getId())) {
                continue;
            }
            LinkedHashSet<String> rowIds = new LinkedHashSet<>(dataset.getSubjectsForSet(subjectSet.getId()));
            rowIds.retainAll(importedSubjectIds);
            if (!rowIds.isEmpty()) {
                session.addRowSet(new PrismRowSet(
                        subjectSet.getId(),
                        subjectSet.getName(),
                        subjectSet.getDescription(),
                        rowIds,
                        Map.of("source", "prism-tsv-import")
                ));
            }
        }
        return session;
    }

    public static PrismPack toPack(InMemoryPrismDataset dataset, ImportSelection selection, Path sourcePath) {
        Set<String> importedSubjectIds = importedSubjectIds(dataset, selection.subjectSetIds());
        List<EndpointDefinition> endpoints = dataset.getEndpointDefinitions().stream()
                .filter(endpoint -> selection.endpointIds().contains(endpoint.getId()))
                .toList();
        List<String> headers = headers(endpoints);
        ArrayList<List<String>> rows = new ArrayList<>();
        for (SubjectRecord subject : dataset.getSubjectRecords()) {
            if (!importedSubjectIds.contains(subject.getSubjectId())) {
                continue;
            }
            rows.add(row(dataset, subject, endpoints));
        }
        PrismPack.DataFrame dataFrame = new PrismPack.DataFrame(headers, rows);
        PrismPack.DataFrameSchema schema = new PrismPack.DataFrameSchema(columns(endpoints), Map.of());
        PrismPack.MoleculeMetadata molecules = new PrismPack.MoleculeMetadata("smiles", "smiles", "subject_id", Map.of());
        PrismPack.EndpointMetadata endpointMetadata = new PrismPack.EndpointMetadata(endpoints.stream()
                .map(endpoint -> new PrismPack.Endpoint(
                        endpoint.getId(),
                        endpoint.getId(),
                        endpoint.getName(),
                        endpoint.getUnit(),
                        null,
                        endpoint.getPath(),
                        null,
                        Map.of()
                ))
                .toList(), Map.of());
        PrismPack.TableView tableView = new PrismPack.TableView(
                "imported",
                "Imported dataset",
                headers,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of()
        );
        return new PrismPack(
                new PrismPack.Manifest(
                        "0.1",
                        "imported-prism-tsv",
                        sourcePath == null ? "Imported PRISM TSV dataset" : sourcePath.getFileName().toString(),
                        "Imported from canonical PRISM TSV dataset",
                        Instant.now().toString(),
                        "PrismLite",
                        new PrismPack.DataframeRef("data", "dataframe.tsv", "schema.json", "subject", Map.of()),
                        "semantics/molecules.json",
                        "semantics/endpoints.json",
                        "views/table.json",
                        "views/visualizations.json",
                        "attachments/attachments.json",
                        "provenance.json",
                        Map.of()
                ),
                dataFrame,
                schema,
                molecules,
                endpointMetadata,
                tableView,
                new PrismPack.VisualizationSet(List.of(), Map.of()),
                new PrismPack.AttachmentSet(List.of(), Map.of()),
                Map.of("sourcePath", sourcePath == null ? "" : sourcePath.toString()),
                List.of()
        );
    }

    private static Set<String> importedSubjectIds(InMemoryPrismDataset dataset, List<String> subjectSetIds) {
        if (subjectSetIds == null || subjectSetIds.isEmpty()) {
            return dataset.getSubjectRecords().stream()
                    .map(SubjectRecord::getSubjectId)
                    .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (String setId : subjectSetIds) {
            ids.addAll(dataset.getSubjectsForSet(setId));
        }
        return ids;
    }

    private static List<String> headers(List<EndpointDefinition> endpoints) {
        ArrayList<String> headers = new ArrayList<>(List.of("subject_id", "structure_id", "batch_id", "project", "series", "smiles"));
        endpoints.stream().map(EndpointDefinition::getId).forEach(headers::add);
        return List.copyOf(headers);
    }

    private static List<PrismPack.Column> columns(List<EndpointDefinition> endpoints) {
        ArrayList<PrismPack.Column> columns = new ArrayList<>();
        columns.add(new PrismPack.Column("subject_id", "text", "compound_id", "Subject ID", "identifier", null, null, null, null, Map.of()));
        columns.add(new PrismPack.Column("structure_id", "text", null, "Structure ID", null, null, null, null, null, Map.of()));
        columns.add(new PrismPack.Column("batch_id", "text", null, "Batch ID", null, null, null, null, null, Map.of()));
        columns.add(new PrismPack.Column("project", "text", "category", "Project", null, null, null, null, null, Map.of()));
        columns.add(new PrismPack.Column("series", "text", "category", "Series", null, null, null, null, null, Map.of()));
        columns.add(new PrismPack.Column("smiles", "text", "chemical_structure", "Structure", null, null, null, null, "smiles", Map.of()));
        for (EndpointDefinition endpoint : endpoints) {
            columns.add(new PrismPack.Column(
                    endpoint.getId(),
                    type(endpoint.getDatatype()),
                    semanticType(endpoint.getDatatype()),
                    endpoint.getName(),
                    null,
                    endpoint.getUnit(),
                    endpoint.getId(),
                    null,
                    null,
                    Map.of("endpointPath", endpoint.getPath())
            ));
        }
        return List.copyOf(columns);
    }

    private static String type(EndpointDataType datatype) {
        return switch (datatype) {
            case NUMERIC, OPTIONAL_NUMERIC -> "number";
            case BOOLEAN -> "boolean";
            case CATEGORICAL -> "text";
            case TEXT -> "text";
        };
    }

    private static String semanticType(EndpointDataType datatype) {
        return datatype == EndpointDataType.CATEGORICAL ? "category" : null;
    }

    private static List<String> row(InMemoryPrismDataset dataset, SubjectRecord subject, List<EndpointDefinition> endpoints) {
        ArrayList<String> row = new ArrayList<>();
        row.add(value(subject.getSubjectId()));
        row.add(value(subject.getStructureId()));
        row.add(value(subject.getBatchId()));
        row.add(value(subject.getProject()));
        row.add(value(subject.getSeries()));
        row.add(value(subject.getSmiles()));
        for (EndpointDefinition endpoint : endpoints) {
            row.add(value(dataset.findEndpointValue(subject.getSubjectId(), endpoint.getId())
                    .map(EndpointValueRecord::getResult)
                    .map(PrismLiteDatasetImporter::displayValue)
                    .orElse(null)));
        }
        return List.copyOf(row);
    }

    private static String displayValue(EndpointResult result) {
        if (result instanceof NumericResult numeric) {
            return numeric.getState() == NumericState.VALUE ? string(numeric.getMean()) : null;
        }
        if (result instanceof OptionalNumericResult numeric) {
            return numeric.getState() == OptionalNumericState.VALUE ? string(numeric.getMean()) : null;
        }
        if (result instanceof BooleanResult bool) {
            return Boolean.toString(bool.getValue());
        }
        if (result instanceof CategoricalResult categorical) {
            return categorical.getValue();
        }
        if (result instanceof TextResult text) {
            return text.getText();
        }
        return result == null ? null : result.toString();
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static String string(Double value) {
        return value == null ? null : Double.toString(value);
    }

    private static JComponent selectionPanel(String title, List<JCheckBox> checks, int count) {
        JPanel list = new JPanel(new GridLayout(0, 1, 0, 2));
        for (JCheckBox check : checks) {
            list.add(check);
        }
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.add(new JLabel(count + " available"), BorderLayout.NORTH);
        panel.add(new JScrollPane(list), BorderLayout.CENTER);
        return panel;
    }

    private static String label(SubjectSet subjectSet) {
        return subjectSet.getName() + " (" + subjectSet.getId() + ")";
    }

    private static String label(EndpointDefinition endpoint) {
        return endpoint.getName() + " (" + endpoint.getId() + ")";
    }
}
